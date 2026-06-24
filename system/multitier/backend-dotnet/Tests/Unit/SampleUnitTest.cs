using Xunit;

namespace MyCompany.MyShop.Backend.Tests.Unit;

/// <summary>
/// Placeholder unit test so the commit-stage unit step has a fast, Docker-free test to run.
/// Mirrors the Java <c>onePlusOne</c> sample. Test-layer separation is by namespace (the .NET
/// equivalent of Java's source sets): the unit suite selects with
/// <c>--filter "FullyQualifiedName~...Tests.Unit"</c>.
/// </summary>
public class SampleUnitTest
{
    [Fact]
    public void OnePlusOne()
    {
        Assert.Equal(2, 1 + 1);
    }
}
