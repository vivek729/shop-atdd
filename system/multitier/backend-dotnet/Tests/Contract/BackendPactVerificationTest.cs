using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using MyCompany.MyShop.Backend.Core.Entities;
using MyCompany.MyShop.Backend.Data;
using PactNet.Infrastructure.Outputters;
using PactNet.Verifier;
using System.Net;
using System.Text.Json;
using Testcontainers.PostgreSql;
using WireMock.RequestBuilders;
using WireMock.ResponseBuilders;
using WireMock.Server;
using Xunit;
using Xunit.Abstractions;

namespace MyCompany.MyShop.Backend.Tests.Contract;

/// <summary>
/// Replays the frontend consumer contract against the in-process provider, with external systems
/// (ERP / Tax / Clock) stubbed by in-process WireMock and provider states seeded into a
/// Testcontainers-managed Postgres. Mirrors the Java provider-verification harness: real Postgres
/// (so numeric / timestamptz semantics match the contract), in-process external stubs. Fails the
/// build if the backend drifts from the contract.
///
/// <para>Test-layer separation is by namespace (the .NET equivalent of Java's source sets):
/// suites select with <c>--filter "FullyQualifiedName~...Tests.Contract"</c>.</para>
/// </summary>
public class BackendPactVerificationTest : IAsyncLifetime
{
    private readonly ITestOutputHelper _output;

    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder()
        .WithImage("postgres:16-alpine")
        .WithDatabase("app")
        .WithUsername("app")
        .WithPassword("app")
        .Build();

    private WireMockServer _erp = null!;
    private WireMockServer _tax = null!;
    private WireMockServer _clock = null!;
    private WebApplicationFactory<Program> _factory = null!;
    private HttpListener _stateListener = null!;
    private Thread _stateThread = null!;
    private int _statePort;
    private AppDbContext _db = null!;
    private bool _running = true;

    public BackendPactVerificationTest(ITestOutputHelper output)
    {
        _output = output;
    }

    public async Task InitializeAsync()
    {
        await _postgres.StartAsync();

        _erp = WireMockServer.Start();
        _tax = WireMockServer.Start();
        _clock = WireMockServer.Start();

        _factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            builder.ConfigureServices(services =>
            {
                var descriptor = services.SingleOrDefault(
                    d => d.ServiceType == typeof(DbContextOptions<AppDbContext>));
                if (descriptor != null)
                    services.Remove(descriptor);

                services.AddDbContext<AppDbContext>(options =>
                    options.UseNpgsql(_postgres.GetConnectionString()));
            });

            builder.UseSetting("ERP_API_URL", _erp.Url!);
            builder.UseSetting("TAX_API_URL", _tax.Url!);
            builder.UseSetting("CLOCK_API_URL", _clock.Url!);
        });

        // Resolve a scoped AppDbContext from the test factory and create the schema.
        var scope = _factory.Services.CreateScope();
        _db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await _db.Database.EnsureCreatedAsync();

        // Start a standalone HttpListener to serve /_pact/provider-states.
        _statePort = FindFreePort();
        _stateListener = new HttpListener();
        _stateListener.Prefixes.Add($"http://127.0.0.1:{_statePort}/");
        _stateListener.Start();

        _stateThread = new Thread(ServeProviderStates) { IsBackground = true };
        _stateThread.Start();
    }

    [Fact]
    public void Verify()
    {
        var pactPath = Path.GetFullPath(
            Path.Combine(AppContext.BaseDirectory,
                "../../../../../../../contracts/frontend-backend.json"));

        var config = new PactVerifierConfig
        {
            Outputters = new List<IOutput> { new XUnitOutput(_output) },
        };

        new PactVerifier("backend", config)
            .WithHttpEndpoint(_factory.Server.BaseAddress)
            .WithFileSource(new FileInfo(pactPath))
            .WithProviderStateUrl(new Uri($"http://127.0.0.1:{_statePort}/"))
            .Verify();
    }

    public async Task DisposeAsync()
    {
        _running = false;
        _stateListener.Stop();
        await _db.DisposeAsync();
        _factory.Dispose();
        _erp.Stop();
        _tax.Stop();
        _clock.Stop();
        await _postgres.DisposeAsync();
    }

    // --- Provider-states HTTP listener ---

    private void ServeProviderStates()
    {
        while (_running)
        {
            HttpListenerContext ctx;
            try { ctx = _stateListener.GetContext(); }
            catch (HttpListenerException) { break; }
            catch (ObjectDisposedException) { break; }

            using var body = new StreamReader(ctx.Request.InputStream);
            var json = body.ReadToEnd();
            var doc = JsonDocument.Parse(json);
            var state = doc.RootElement.GetProperty("state").GetString() ?? string.Empty;
            var action = doc.RootElement.TryGetProperty("action", out var a) ? a.GetString() : "setup";

            if (action == "setup")
            {
                ResetState();
                ApplyState(state);
            }

            ctx.Response.StatusCode = 200;
            ctx.Response.Close();
        }
    }

    private void ResetState()
    {
        _erp.Reset();
        _tax.Reset();
        _clock.Reset();
        _db.Orders.RemoveRange(_db.Orders);
        _db.Coupons.RemoveRange(_db.Coupons);
        _db.SaveChanges();
    }

    private void ApplyState(string state)
    {
        switch (state)
        {
            case "product BOOK-123 exists and US is taxable":
                StubClock("2026-03-10T12:00:00Z");
                StubProduct("BOOK-123", "10.00");
                StubPromotion(false, "1.0");
                StubTax("US", "0.10");
                break;

            case "order placement is blocked by the New Year blackout":
                StubClock("2026-12-31T23:59:00Z");
                break;

            case "at least one order exists":
                _db.Orders.Add(SampleOrder("ORD-HIST-1"));
                _db.SaveChanges();
                break;

            case "order ORD-1 exists":
                _db.Orders.Add(SampleOrder("ORD-1"));
                _db.SaveChanges();
                break;

            case "no order UNKNOWN exists":
                break; // cleared in ResetState

            case "at least one coupon exists":
                _db.Coupons.Add(new Coupon
                {
                    Code = "SAVE10",
                    DiscountRate = 0.20m,
                    UsageLimit = 100,
                    UsedCount = 0,
                });
                _db.SaveChanges();
                break;

            case "no coupon SAVE10 exists yet":
                break; // cleared in ResetState
        }
    }

    private void StubClock(string isoInstant) =>
        _clock.Given(Request.Create().WithPath("/api/time").UsingGet())
              .RespondWith(Response.Create().WithStatusCode(200)
                  .WithBody($"{{\"time\":\"{isoInstant}\"}}")
                  .WithHeader("Content-Type", "application/json"));

    private void StubProduct(string sku, string price) =>
        _erp.Given(Request.Create().WithPath($"/api/products/{sku}").UsingGet())
            .RespondWith(Response.Create().WithStatusCode(200)
                .WithBody($"{{\"id\":\"{sku}\",\"price\":{price}}}")
                .WithHeader("Content-Type", "application/json"));

    private void StubPromotion(bool active, string discount) =>
        _erp.Given(Request.Create().WithPath("/api/promotion").UsingGet())
            .RespondWith(Response.Create().WithStatusCode(200)
                .WithBody($"{{\"promotionActive\":{active.ToString().ToLower()},\"discount\":{discount}}}")
                .WithHeader("Content-Type", "application/json"));

    private void StubTax(string country, string rate) =>
        _tax.Given(Request.Create().WithPath($"/api/countries/{country}").UsingGet())
            .RespondWith(Response.Create().WithStatusCode(200)
                .WithBody($"{{\"id\":\"{country}\",\"countryName\":\"{country}\",\"taxRate\":{rate}}}")
                .WithHeader("Content-Type", "application/json"));

    private static Order SampleOrder(string orderNumber) => new()
    {
        OrderNumber = orderNumber,
        OrderTimestamp = DateTime.Parse("2026-03-10T12:00:00Z").ToUniversalTime(),
        Country = "US",
        Sku = "BOOK-123",
        Quantity = 2,
        UnitPrice = 10.00m,
        BasePrice = 20.00m,
        DiscountRate = 0m,
        DiscountAmount = 0m,
        SubtotalPrice = 20.00m,
        TaxRate = 0.10m,
        TaxAmount = 2.00m,
        TotalPrice = 22.00m,
        Status = OrderStatus.PLACED,
        AppliedCouponCode = null,
    };

    private static int FindFreePort()
    {
        var listener = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }

    /// <summary>Routes the Pact native verifier output to xUnit's test output.</summary>
    private sealed class XUnitOutput : IOutput
    {
        private readonly ITestOutputHelper _output;

        public XUnitOutput(ITestOutputHelper output) => _output = output;

        public void WriteLine(string line) => _output.WriteLine(line);
    }
}
