using Dsl.Port.MyShop.Assume;
using Dsl.Port.MyShop.Given;
using Dsl.Port.MyShop.When;

namespace Dsl.Port.MyShop;

public interface IScenarioDsl
{
    IAssumeStage Assume();

    IGivenStage Given();

    IWhenStage When();
}
