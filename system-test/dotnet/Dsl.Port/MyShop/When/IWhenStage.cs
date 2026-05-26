using Dsl.Port.MyShop.When.Steps;

namespace Dsl.Port.MyShop.When;

public interface IWhenStage
{
    IWhenPlaceOrder PlaceOrder();

    IWhenCancelOrder CancelOrder();

    IWhenViewOrder ViewOrder();

    IWhenPublishCoupon PublishCoupon();

    IWhenBrowseCoupons BrowseCoupons();
}
