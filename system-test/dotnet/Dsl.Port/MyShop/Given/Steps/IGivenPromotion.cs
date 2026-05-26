using Dsl.Port.MyShop.Given.Steps.Base;

namespace Dsl.Port.MyShop.Given.Steps;

public interface IGivenPromotion : IGivenStep
{
    IGivenPromotion WithActive(bool promotionActive);
    IGivenPromotion WithDiscount(decimal discount);
    IGivenPromotion WithDiscount(string? discount);
}
