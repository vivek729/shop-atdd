using Common;
using Driver.Adapter.External.Clock.Client.Dtos;
using Driver.Adapter.External.Clock.Client.Dtos.Error;

namespace Driver.Adapter.External.Clock.Client;

public static class ClockRealClient
{
    public static Task<Result<VoidValue, ExtClockErrorResponse>> CheckHealthAsync()
    {
        var _ = Now;
        return Task.FromResult(Result.Success<ExtClockErrorResponse>());
    }

    public static Task<Result<ExtGetTimeResponse, ExtClockErrorResponse>> GetTimeAsync()
        => Task.FromResult(Result<ExtGetTimeResponse, ExtClockErrorResponse>.Success(
            new ExtGetTimeResponse { Time = Now }));

    private static DateTimeOffset Now => DateTimeOffset.UtcNow;
}

