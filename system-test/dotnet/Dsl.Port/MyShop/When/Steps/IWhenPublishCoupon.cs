using Dsl.Port.MyShop.When.Steps.Base;

namespace Dsl.Port.MyShop.When.Steps;

public interface IWhenPublishCoupon : IWhenStep
{
    IWhenPublishCoupon WithCouponCode(string? couponCode);

    IWhenPublishCoupon WithDiscountRate(string? discountRate);

    IWhenPublishCoupon WithDiscountRate(decimal discountRate);

    IWhenPublishCoupon WithValidFrom(string? validFrom);

    IWhenPublishCoupon WithValidTo(string? validTo);

    IWhenPublishCoupon WithUsageLimit(string? usageLimit);

    IWhenPublishCoupon WithUsageLimit(int usageLimit);
}
