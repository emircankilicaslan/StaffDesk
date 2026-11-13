package com.staffdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BusinessDayCalendarCoverageTest {

    private final BusinessDayCalculator calc = new BusinessDayCalculator(Set.of(
            LocalDate.of(2035, 1, 17), LocalDate.of(2036, 1, 1)));

    @Test
    void definedYearPasses() {
        assertThatCode(() -> calc.requireCalendar(LocalDate.of(2035, 3, 1), LocalDate.of(2035, 3, 5)))
                .doesNotThrowAnyException();
    }

    @Test
    void rangeSpanningTwoDefinedYearsPasses() {
        assertThatCode(() -> calc.requireCalendar(LocalDate.of(2035, 12, 29), LocalDate.of(2036, 1, 2)))
                .doesNotThrowAnyException();
    }

    @Test
    void undefinedYearIsRejectedWithClearMessage() {
        assertThatThrownBy(() -> calc.requireCalendar(LocalDate.of(2037, 3, 2), LocalDate.of(2037, 3, 5)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2037");
    }

    @Test
    void rangeEndingInUndefinedYearIsRejected() {
        assertThatThrownBy(() -> calc.requireCalendar(LocalDate.of(2036, 12, 29), LocalDate.of(2037, 1, 2)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2037");
    }

    @Test
    void ramazanBayramiDaysAreNotCounted() {
        // 2026: Ramazan Bayramı 20-22 Mart (Cuma-Pazar). 16-20 Mart haftasında yalnızca 4 iş günü kalır.
        BusinessDayCalculator c = new BusinessDayCalculator(Set.of(LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 21), LocalDate.of(2026, 3, 22)));
        assertThat(c.count(LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 20))).isEqualTo(4);
    }

    @Test
    void kurbanBayramiDaysAreNotCounted() {
        // 2026: Kurban Bayramı 27-30 Mayıs. 25-29 Mayıs haftasında (Çarşamba-Cuma tatil) 2 iş günü kalır.
        BusinessDayCalculator c = new BusinessDayCalculator(Set.of(LocalDate.of(2026, 5, 27),
                LocalDate.of(2026, 5, 28), LocalDate.of(2026, 5, 29), LocalDate.of(2026, 5, 30)));
        assertThat(c.count(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 29))).isEqualTo(2);
    }
}
