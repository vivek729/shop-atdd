using System.Runtime.CompilerServices;

namespace Dsl.Port.MyShop.Then.Steps;

public interface IThenSuccess
{
    IThenSuccessAnd And();

    TaskAwaiter GetAwaiter();
}
