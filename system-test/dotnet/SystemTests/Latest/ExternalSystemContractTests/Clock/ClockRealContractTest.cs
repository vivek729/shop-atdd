using Dsl.Port.MyShop;

namespace SystemTests.Latest.ExternalSystemContractTests.Clock;

public class ClockRealContractTest : BaseClockContractTest
{
    protected override ExternalSystemMode? FixedExternalSystemMode => ExternalSystemMode.Real;
}












