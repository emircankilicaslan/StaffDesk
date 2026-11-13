package com.staffdesk.service;

import com.staffdesk.config.StaffDeskProperties;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** İki tarih (dahil) arasındaki iş günü sayısı: hafta sonları ve tanımlı resmi tatiller sayılmaz. */
@Component
public class BusinessDayCalculator {

    private final Set<LocalDate> holidays;
    private final Set<Integer> coveredYears;

    @Autowired
    public BusinessDayCalculator(StaffDeskProperties props) {
        this(Set.copyOf(props.holidays()));
    }

    public BusinessDayCalculator(Set<LocalDate> holidays) {
        this.holidays = holidays;
        this.coveredYears = holidays.stream().map(LocalDate::getYear).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Aralıktaki her yıl için en az bir resmi tatil tanımlı olmalıdır. Hiç tatil tanımlı olmayan yıl
     * için iş günü hesabı sessizce yanlış çıkacağından, bu durumda açık bir hata fırlatılır.
     */
    public void requireCalendar(LocalDate start, LocalDate end) {
        for (int year = start.getYear(); year <= end.getYear(); year++) {
            if (!coveredYears.contains(year)) {
                throw new BusinessRuleException(
                        year + " yılı için resmi tatil takvimi tanımlı değil; iş günü hesaplanamıyor");
            }
        }
    }

    public int count(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Bitiş tarihi başlangıçtan önce olamaz");
        }
        int days = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isBusinessDay(d)) {
                days++;
            }
        }
        return days;
    }

    public boolean isBusinessDay(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !holidays.contains(d);
    }
}
