package com.staffdesk.service;

/** Çok sayıda başarısız giriş denemesi sonrası geçici kilit; HTTP 429 olarak döndürülür. */
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Çok fazla başarısız giriş denemesi. Lütfen daha sonra tekrar deneyin");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
