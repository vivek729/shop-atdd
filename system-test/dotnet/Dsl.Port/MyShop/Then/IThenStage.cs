using Dsl.Port.MyShop.Then.Steps;

namespace Dsl.Port.MyShop.Then;

public interface IThenStage
{
    Task<IThenClock> Clock();

    Task<IThenProduct> Product(string skuAlias);

    Task<IThenCountry> Country(string countryAlias);
}
