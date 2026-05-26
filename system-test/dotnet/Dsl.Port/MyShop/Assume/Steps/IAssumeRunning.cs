using Dsl.Port.MyShop.Assume;

namespace Dsl.Port.MyShop.Assume.Steps;

public interface IAssumeRunning
{
    Task<IAssumeStage> ShouldBeRunning();
}