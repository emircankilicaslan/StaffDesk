package com.staffdesk.service;

import com.staffdesk.config.StaffDeskProperties;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** İki tarih (dahil) arasındaki iş günü sayısı: hafta sonları ve tanımlı resmi tatiller sayılmaz. */
@Component
public class BusinessDayCalculator {

    private final Set<LocalDate> holidays;

    @Autowired
    public BusinessDayCalculator(StaffDeskProperties props) {
        this(Set.copyOf(props.holidays()));
    }

    public BusinessDayCalculator(Set<LocalDate> holidays) {
        this.holidays = holidays;
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
