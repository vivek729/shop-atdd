using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using MyCompany.MyShop.Backend.Core.Entities;
using MyCompany.MyShop.Backend.Data;
using PactNet;
using PactNet.Infrastructure.Outputters;
using System.Text.Json;
using WireMock.RequestBuilders;
using WireMock.ResponseBuilders;
using WireMock.Server;
using Xunit;

namespace MyCompany.MyShop.Backend.Tests;

[Trait("Category", "Contract")]
public class BackendPactVerificationTest : IDisposable
{
    private readonly WireMockServer _erp;
    private readonly WireMockServer _tax;
    private readonly WireMockServer _clock;
    private readonly WebApplicationFactory<Program> _factory;

    public BackendPactVerificationTest()
    {
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
                    options.UseInMemoryDatabase("PactTestDb"));
            });

            builder.UseSetting("ERP_API_URL", _erp.Url!);
            builder.UseSetting("TAX_API_URL", _tax.Url!);
            builder.UseSetting("CLOCK_API_URL", _clock.Url!);

            builder.Configure(app =>
            {
                // Provider-states endpoint consumed by PactNet before each interaction.
                app.Map("/_pact/provider-states", stateApp =>
                {
                    stateApp.Run(async context =>
                    {
                        using var reader = new StreamReader(context.Request.Body);
                        var body = await reader.ReadToEndAsync();
                        var doc = JsonDocument.Parse(body);
                        var state = doc.RootElement.GetProperty("state").GetString() ?? string.Empty;
                        var action = doc.RootElement.TryGetProperty("action", out var a)
                            ? a.GetString()
                            : "setup";

                        if (action == "setup")
                        {
                            using var scope = context.RequestServices.CreateScope();
                            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
                            ResetState(db);
                            ApplyState(state, db);
                        }

                        context.Response.StatusCode = 200;
                    });
                });

                app.UseRouting();
                app.UseAuthorization();
                app.UseEndpoints(endpoints => endpoints.MapControllers());
            });
        });
    }

    [Fact]
    public void Verify()
    {
        var pactPath = Path.GetFullPath(
            Path.Combine(AppContext.BaseDirectory,
                "../../../../../../../contracts/frontend-backend.json"));

        new PactVerifier("backend", new PactVerifierConfig
        {
            Outputters = [new ConsoleOutput()],
        })
            .WithHttpEndpoint(_factory.Server.BaseAddress)
            .WithFileSource(new FileInfo(pactPath))
            .WithProviderStateUrl(new Uri(_factory.Server.BaseAddress, "/_pact/provider-states"))
            .Verify();
    }

    public void Dispose()
    {
        _factory.Dispose();
        _erp.Stop();
        _tax.Stop();
        _clock.Stop();
    }

    private void ResetState(AppDbContext db)
    {
        _erp.Reset();
        _tax.Reset();
        _clock.Reset();
        db.Orders.RemoveRange(db.Orders);
        db.Coupons.RemoveRange(db.Coupons);
        db.SaveChanges();
    }

    private void ApplyState(string state, AppDbContext db)
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
                db.Orders.Add(SampleOrder("ORD-HIST-1"));
                db.SaveChanges();
                break;

            case "order ORD-1 exists":
                db.Orders.Add(SampleOrder("ORD-1"));
                db.SaveChanges();
                break;

            case "no order UNKNOWN exists":
                break; // DB cleared in ResetState

            case "at least one coupon exists":
                db.Coupons.Add(new Coupon
                {
                    Code = "SAVE10",
                    DiscountRate = 0.20m,
                    UsageLimit = 100,
                    UsedCount = 0,
                });
                db.SaveChanges();
                break;

            case "no coupon SAVE10 exists yet":
                break; // DB cleared in ResetState
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
}
