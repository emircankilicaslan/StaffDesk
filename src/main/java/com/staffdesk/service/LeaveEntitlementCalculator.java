package com.staffdesk.service;

import java.time.LocalDate;
import java.time.Period;

/**
 * Yıllık izin hakkı (iş günü). Örnek kural, 657 sayılı Kanun'un 102. maddesindeki kademeye benzer:
 * hizmet süresi 1 yıldan az ise hak yok, 1–10 yıl arası 20 gün, 10 yıl ve üzeri 30 gün.
 * Gerçek uygulamada kurumun mevzuatına göre (ör. sözleşmeli personel hükümleri) uyarlanmalıdır.
 */
public final class LeaveEntitlementCalculator {

    public static final int FIRST_TIER_DAYS = 20;
    public static final int SECOND_TIER_DAYS = 30;

    private LeaveEntitlementCalculator() {}

    public static int annualDays(LocalDate hireDate, LocalDate asOf) {
        if (asOf.isBefore(hireDate)) {
            return 0;
        }
        int completedYears = Period.between(hireDate, asOf).getYears();
        if (completedYears < 1) {
            return 0;
        }
        return completedYears < 10 ? FIRST_TIER_DAYS : SECOND_TIER_DAYS;
    }
}
