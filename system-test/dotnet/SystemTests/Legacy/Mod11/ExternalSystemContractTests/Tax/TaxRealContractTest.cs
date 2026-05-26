using Dsl.Port.MyShop;

namespace SystemTests.Legacy.Mod11.ExternalSystemContractTests.Tax;

public class TaxRealContractTest : BaseTaxContractTest
{
    protected override ExternalSystemMode? FixedExternalSystemMode => ExternalSystemMode.Real;
}
