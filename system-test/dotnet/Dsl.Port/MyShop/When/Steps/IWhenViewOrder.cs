using Dsl.Port.MyShop.When.Steps.Base;

namespace Dsl.Port.MyShop.When.Steps;

public interface IWhenViewOrder : IWhenStep
{
    IWhenViewOrder WithOrderNumber(string? orderNumber);
}
