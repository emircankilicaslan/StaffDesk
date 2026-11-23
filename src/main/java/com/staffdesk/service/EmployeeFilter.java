package com.staffdesk.service;

import com.staffdesk.domain.Role;

/**
 * Personel listesi süzgeci; boş alanlar süzgece katılmaz.
 *
 * @param q arama metni (ad soyad, e-posta, sicil no)
 * @param active true: yalnızca aktif, false: yalnızca pasif
 */
public record EmployeeFilter(String q, Long departmentId, Boolean active, Role role) {

    public static final EmployeeFilter ALL = new EmployeeFilter(null, null, null, null);
}
