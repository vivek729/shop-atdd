package com.mycompany.myshop.backend.support.core.scenario.given;

import com.mycompany.myshop.backend.support.core.ScenarioDslImpl;
import com.mycompany.myshop.backend.support.core.scenario.given.steps.GivenClockImpl;
import com.mycompany.myshop.backend.support.core.scenario.given.steps.GivenCouponImpl;
import com.mycompany.myshop.backend.support.core.scenario.given.steps.GivenCountryImpl;
import com.mycompany.myshop.backend.support.core.scenario.given.steps.GivenProductImpl;
import com.mycompany.myshop.backend.support.core.scenario.given.steps.GivenPromotionImpl;
import com.mycompany.myshop.backend.support.core.scenario.then.ThenImpl;
import com.mycompany.myshop.backend.support.core.scenario.when.WhenImpl;
import com.mycompany.myshop.backend.support.core.usecase.UseCaseDsl;
import com.mycompany.myshop.backend.support.port.given.GivenStage;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the given steps and, on the hop into {@code when()} or {@code then()}, executes them
 * against the use case layer — the ERP / Tax / Clock steps program their WireMock stubs, {@code
 * coupon()} publishes through the SUT's own {@code POST /api/coupons}.
 *
 * <p>{@code promotion()} is always executed, defaulting to inactive: a scenario that does not care
 * about promotions stays silent about them, and the default keeps the ERP stub answering
 * consistently rather than 404-ing the promotion lookup.
 *
 * <p>What is <em>not</em> stated here is filled by {@link WhenImpl} at action time — see its {@code
 * ensureDefaults()}.
 */
public class GivenImpl implements GivenStage {

    private final UseCaseDsl app;
    private final ScenarioDslImpl scenario;

    private GivenClockImpl clock;
    private GivenPromotionImpl promotion;
    private final List<GivenProductImpl> products = new ArrayList<>();
    private final List<GivenCountryImpl> countries = new ArrayList<>();
    private final List<GivenCouponImpl> coupons = new ArrayList<>();

    public GivenImpl(UseCaseDsl app, ScenarioDslImpl scenario) {
        this.app = app;
        this.scenario = scenario;
        this.promotion = new GivenPromotionImpl(this);
    }

    @Override
    public GivenClockImpl clock() {
        clock = new GivenClockImpl(this);
        return clock;
    }

    @Override
    public GivenProductImpl product() {
        var product = new GivenProductImpl(this);
        products.add(product);
        return product;
    }

    @Override
    public GivenPromotionImpl promotion() {
        promotion = new GivenPromotionImpl(this);
        return promotion;
    }

    @Override
    public GivenCountryImpl country() {
        var country = new GivenCountryImpl(this);
        countries.add(country);
        return country;
    }

    @Override
    public GivenCouponImpl coupon() {
        var coupon = new GivenCouponImpl(this);
        coupons.add(coupon);
        return coupon;
    }

    @Override
    public WhenImpl when() {
        setup();
        return new WhenImpl(
            app, scenario, clock != null, !products.isEmpty(), !countries.isEmpty(), true);
    }

    @Override
    public ThenImpl then() {
        setup();
        return new ThenImpl(app);
    }

    private void setup() {
        if (clock != null) {
            clock.execute(app);
        }
        products.forEach(product -> product.execute(app));
        countries.forEach(country -> country.execute(app));
        promotion.execute(app);
        coupons.forEach(coupon -> coupon.execute(app));
    }
}
