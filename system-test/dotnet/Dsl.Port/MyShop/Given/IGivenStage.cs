using Dsl.Port.MyShop.Given.Steps;
using Dsl.Port.MyShop.Then;
using Dsl.Port.MyShop.When;

namespace Dsl.Port.MyShop.Given;

public interface IGivenStage
{
    IGivenProduct Product();

    IGivenOrder Order();

    IGivenClock Clock();

    IGivenCountry Country();

    IGivenPromotion Promotion();

    IGivenCoupon Coupon();

    IWhenStage When();

    IThenStage Then();
}
