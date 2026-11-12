package com.staffdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BusinessDayCalculatorTest {

    // 2035-01-08 Pazartesi
    private static final LocalDate MON = LocalDate.of(2035, 1, 8);

    private final BusinessDayCalculator calc = new BusinessDayCalculator(Set.of(LocalDate.of(2035, 1, 17)));

    @Test
    void fullWorkWeekIsFiveDays() {
        assertThat(calc.count(MON, MON.plusDays(4))).isEqualTo(5);
    }

    @Test
    void weekendsAreNotCounted() {
        // Pazartesi'den sonraki Pazartesi'ye kadar: 6 iş günü (Pzt..Cum + Pzt)
        assertThat(calc.count(MON, MON.plusDays(7))).isEqualTo(6);
    }

    @Test
    void holidaysAreNotCounted() {
        // 15-19 Ocak haftası, 17 Ocak (Çarşamba) tatil: 4 iş günü
        assertThat(calc.count(LocalDate.of(2035, 1, 15), LocalDate.of(2035, 1, 19))).isEqualTo(4);
    }

    @Test
    void onlyWeekendIsZero() {
        assertThat(calc.count(MON.minusDays(2), MON.minusDays(1))).isZero();
    }

    @Test
    void singleDayRange() {
        assertThat(calc.count(MON, MON)).isEqualTo(1);
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThatThrownBy(() -> calc.count(MON, MON.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
