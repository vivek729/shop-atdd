using Dsl.Port.MyShop.Given;
using Dsl.Port.MyShop.Then;
using Dsl.Port.MyShop.When;

namespace Dsl.Port.MyShop.Given.Steps.Base;

public interface IGivenStep
{
    IGivenStage And();

    IWhenStage When();

    IThenStage Then();
}
