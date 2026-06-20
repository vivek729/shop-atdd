using Driver.Port;
using Dsl.Core.UseCase.UseCases.Base;
using Driver.Port.Dtos;
using Driver.Port.Dtos.Error;
using Common;
using Dsl.Core.Shared;

namespace Dsl.Core.UseCase.UseCases;

public class CancelOrder : BaseMyShopUseCase<VoidValue, VoidVerification>
{
    private string? _orderNumberResultAlias;

    public CancelOrder(IMyShopDriver driver, UseCaseContext context)
        : base(driver, context)
    {
    }

    public CancelOrder OrderNumber(string? orderNumberResultAlias)
    {
        _orderNumberResultAlias = orderNumberResultAlias;
        return this;
    }

    public override async Task<MyShopUseCaseResult<VoidValue, VoidVerification>> Execute()
    {
        var orderNumber = _context.GetResultValue(_orderNumberResultAlias);
        var request = new CancelOrderRequest { OrderNumber = orderNumber };
        var result = await _driver.CancelOrderAsync(request).MapVoidAsync();

        return new MyShopUseCaseResult<VoidValue, VoidVerification>(
            result,
            _context,
            (response, ctx) => new VoidVerification(response, ctx));
    }
}
