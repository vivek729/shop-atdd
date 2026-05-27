using Driver.Port;
using Driver.Port.Dtos;
using Driver.Port.Dtos.Error;
using Dsl.Core.Shared;
using Dsl.Core.UseCase.UseCases.Base;

namespace Dsl.Core.UseCase.UseCases;

public class BrowseCoupons : BaseMyShopUseCase<BrowseCouponsResponse, BrowseCouponsVerification>
{
    public BrowseCoupons(IMyShopDriver driver, UseCaseContext context)
        : base(driver, context)
    {
    }

    public override async Task<MyShopUseCaseResult<BrowseCouponsResponse, BrowseCouponsVerification>> Execute()
    {
        var result = await _driver.BrowseCouponsAsync();

        return new MyShopUseCaseResult<BrowseCouponsResponse, BrowseCouponsVerification>(
            result,
            _context,
            (response, ctx) => new BrowseCouponsVerification(response, ctx));
    }
}
