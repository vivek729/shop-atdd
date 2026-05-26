namespace Dsl.Port.MyShop.Assume;

using Dsl.Port.MyShop.Assume.Steps;

public interface IAssumeStage
{
    IAssumeRunning MyShop();

    IAssumeRunning Erp();

    IAssumeRunning Tax();

    IAssumeRunning Clock();
}
