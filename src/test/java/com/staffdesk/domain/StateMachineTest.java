package com.staffdesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.staffdesk.service.BusinessRuleException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Alan nesnelerinin durum geçiş kuralları (veritabanı gerektirmez). */
class StateMachineTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2035, 1, 1, 9, 0);

    private LeaveRequest pendingLeave() {
        Employee e = new Employee("S1", "Ali Veli", "ali@test.local", "x", Role.EMPLOYEE, null, null,
                LocalDate.of(2020, 1, 1), NOW);
        return new LeaveRequest(e, LeaveType.ANNUAL, LocalDate.of(2035, 1, 8), LocalDate.of(2035, 1, 9), 2, null, NOW);
    }

    @Test
    void pendingLeaveCanBeApprovedOnce() {
        LeaveRequest l = pendingLeave();
        l.approve(7L, NOW);

        assertThat(l.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(l.getDecidedBy()).isEqualTo(7L);
        assertThatThrownBy(() -> l.approve(7L, NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> l.reject(7L, NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> l.cancel(NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectedLeaveCannotBeApproved() {
        LeaveRequest l = pendingLeave();
        l.reject(7L, NOW);

        assertThat(l.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThatThrownBy(() -> l.approve(7L, NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void assetLifecycle() {
        Asset a = new Asset("DMB-1", "Dizüstü", "BİLGİSAYAR");
        assertThat(a.getStatus()).isEqualTo(AssetStatus.AVAILABLE);

        a.markAssigned();
        assertThat(a.getStatus()).isEqualTo(AssetStatus.ASSIGNED);
        assertThatThrownBy(a::markAssigned).isInstanceOf(BusinessRuleException.class); // çift zimmet
        assertThatThrownBy(a::retire).isInstanceOf(BusinessRuleException.class);       // zimmetliyken düşülemez

        a.markReturned();
        assertThatThrownBy(a::markReturned).isInstanceOf(BusinessRuleException.class); // zaten boşta

        a.retire();
        assertThat(a.getStatus()).isEqualTo(AssetStatus.RETIRED);
        assertThatThrownBy(a::markAssigned).isInstanceOf(BusinessRuleException.class); // düşülen zimmetlenemez
    }
}
