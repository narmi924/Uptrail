package com.uptrail.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.uptrail.shared.web.Formats;

/**
 * Display helpers shared by all pages.
 */
class FormatsTest {

    private final Formats formats = new Formats();

    @Test
    void formatsHalfDayUnitsAndMoney() {
        assertThat(formats.days(3)).isEqualTo("1.5 days");
        assertThat(formats.days(2)).isEqualTo("1 day");
        assertThat(formats.money(new BigDecimal("1234.5"))).isEqualTo("SGD 1,234.50");
    }

    @Test
    void percentOfALimit() {
        assertThat(formats.percent(5, 20)).isEqualTo(25);
        assertThat(formats.percent(3, 0)).isZero();
        assertThat(formats.percent(new BigDecimal("450.00"), new BigDecimal("3000.00"))).isEqualTo(15);
        assertThat(formats.percent(new BigDecimal("10.00"), BigDecimal.ZERO)).isZero();
    }

    @Test
    void meterUsesFivePercentStepsAndTonesNearAndOverTheLimit() {
        assertThat(formats.meter(0)).isEqualTo("ut-fill-0");
        assertThat(formats.meter(1)).isEqualTo("ut-fill-5");
        assertThat(formats.meter(42)).isEqualTo("ut-fill-40");
        assertThat(formats.meter(85)).isEqualTo("ut-fill-85");
        assertThat(formats.meter(86)).isEqualTo("ut-fill-85 tone-warning");
        assertThat(formats.meter(100)).isEqualTo("ut-fill-100 tone-warning");
        assertThat(formats.meter(130)).isEqualTo("ut-fill-100 tone-danger");
    }
}
