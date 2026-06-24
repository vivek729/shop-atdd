using Microsoft.EntityFrameworkCore;
using MyCompany.MyShop.Monolith.Core.Entities;
using Xunit;

namespace MyCompany.MyShop.Monolith.Tests.Integration;

[Trait("Category", "Integration")]
public class OrderRepositoryIntegrationTest : AbstractIntegrationTest
{
    [Fact]
    public async Task SavesAndReadsBackOrder()
    {
        var order = new Order
        {
            OrderNumber = "ORD-001",
            OrderTimestamp = new DateTime(2026, 1, 1, 0, 0, 0, DateTimeKind.Utc),
            Country = "US",
            Sku = "BOOK-123",
            Quantity = 2,
            UnitPrice = 10.00m,
            BasePrice = 20.00m,
            DiscountRate = 0.0000m,
            DiscountAmount = 0.00m,
            SubtotalPrice = 20.00m,
            TaxRate = 0.1000m,
            TaxAmount = 2.00m,
            TotalPrice = 22.00m,
            Status = OrderStatus.PLACED,
            AppliedCouponCode = null
        };

        DbContext.Orders.Add(order);
        await DbContext.SaveChangesAsync();

        DbContext.ChangeTracker.Clear();

        var found = await DbContext.Orders.FirstOrDefaultAsync(o => o.OrderNumber == "ORD-001");
        Assert.NotNull(found);
        Assert.Equal("BOOK-123", found.Sku);
        Assert.Equal(22.00m, found.TotalPrice);
        Assert.Equal(OrderStatus.PLACED, found.Status);
    }
}
