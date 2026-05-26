using Dsl.Port.MyShop.Given.Steps.Base;

namespace Dsl.Port.MyShop.Given.Steps;

public interface IGivenProduct : IGivenStep
{
    IGivenProduct WithSku(string? sku);

    IGivenProduct WithUnitPrice(string? unitPrice);

    IGivenProduct WithUnitPrice(decimal? unitPrice);
}
