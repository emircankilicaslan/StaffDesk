package com.staffdesk.service;

/** İş kuralı ihlali (ör. yetersiz izin bakiyesi, geçersiz durum geçişi); HTTP 409 olarak döner. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
