package com.staffdesk.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LeaveEntitlementCalculatorTest {

    @ParameterizedTest(name = "işe giriş {0}, referans {1} -> {2} gün")
    @CsvSource({
            "2020-01-15, 2020-12-31, 0",   // ilk yıl dolmadı
            "2020-01-15, 2021-01-14, 0",   // bir gün eksik
            "2020-01-15, 2021-01-15, 20",  // tam 1 yıl
            "2020-01-15, 2029-01-14, 20",  // 9 yıl 11 ay
            "2020-01-15, 2030-01-14, 20",  // 10 yıldan 1 gün eksik
            "2020-01-15, 2030-01-15, 30",  // tam 10 yıl
            "2000-05-01, 2035-01-01, 30",
            "2016-02-29, 2017-02-28, 0",   // artık gün: yıldönümü henüz gelmedi
            "2016-02-29, 2017-03-01, 20",
            "2025-01-01, 2024-12-31, 0"    // referans tarihi işe girişten önce
    })
    void annualDays(LocalDate hire, LocalDate asOf, int expected) {
        assertThat(LeaveEntitlementCalculator.annualDays(hire, asOf)).isEqualTo(expected);
    }
}
