using Dsl.Port.MyShop.When.Steps.Base;

namespace Dsl.Port.MyShop.When.Steps;

public interface IWhenCancelOrder : IWhenStep
{
    IWhenCancelOrder WithOrderNumber(string? orderNumber);
}
