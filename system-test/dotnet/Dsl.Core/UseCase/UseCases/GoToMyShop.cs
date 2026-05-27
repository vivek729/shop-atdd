using Driver.Port;
using Dsl.Core.UseCase.UseCases.Base;
using Driver.Port.Dtos.Error;
using Common;
using Dsl.Core.Shared;

namespace Dsl.Core.UseCase.UseCases;

public class GoToMyShop : BaseMyShopUseCase<VoidValue, VoidVerification>
{
    public GoToMyShop(IMyShopDriver driver, UseCaseContext context)
        : base(driver, context)
    {
    }

    public override async Task<MyShopUseCaseResult<VoidValue, VoidVerification>> Execute()
    {
        var result = await _driver.GoToMyShopAsync();

        return new MyShopUseCaseResult<VoidValue, VoidVerification>(
            result,
            _context,
            (response, ctx) => new VoidVerification(response, ctx));
    }
}



