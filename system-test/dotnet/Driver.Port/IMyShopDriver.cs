using Common;
using Driver.Port.Dtos;
using Driver.Port.Dtos.Error;

namespace Driver.Port;

public interface IMyShopDriver : IAsyncDisposable
{
    Task<Result<GoToMyShopResponse, SystemError>> GoToMyShopAsync(GoToMyShopRequest request);
    Task<Result<PlaceOrderResponse, SystemError>> PlaceOrderAsync(PlaceOrderRequest request);
    Task<Result<CancelOrderResponse, SystemError>> CancelOrderAsync(CancelOrderRequest request);
    Task<Result<DeliverOrderResponse, SystemError>> DeliverOrderAsync(DeliverOrderRequest request);
    Task<Result<ViewOrderResponse, SystemError>> ViewOrderAsync(ViewOrderRequest request);
    Task<Result<PublishCouponResponse, SystemError>> PublishCouponAsync(PublishCouponRequest request);
    Task<Result<BrowseCouponsResponse, SystemError>> BrowseCouponsAsync(BrowseCouponsRequest request);
}

