using Dsl.Port.MyShop.Given.Steps.Base;

namespace Dsl.Port.MyShop.Given.Steps;

public interface IGivenClock : IGivenStep
{
    IGivenClock WithTime();
    IGivenClock WithTime(string? time);
    IGivenClock WithWeekday();
    IGivenClock WithWeekend();
}
