using Dsl.Port.MyShop.Then.Steps;

namespace Dsl.Port.MyShop.Then;

public interface IThenResultStage
{
    IThenSuccess ShouldSucceed();

    IThenFailure ShouldFail();
}
