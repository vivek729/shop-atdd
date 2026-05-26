using Dsl.Port.MyShop;

namespace SystemTests.Legacy.Mod11.ExternalSystemContractTests.Clock;

public class ClockStubContractTest : BaseClockContractTest
{
    protected override ExternalSystemMode? FixedExternalSystemMode => ExternalSystemMode.Stub;
}












