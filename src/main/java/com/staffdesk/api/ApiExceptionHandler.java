package com.staffdesk.api;

import com.staffdesk.service.BusinessRuleException;
import com.staffdesk.service.NotFoundException;
import com.staffdesk.service.TooManyAttemptsException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Tüm hataları RFC 7807 (ProblemDetail) biçiminde döndürür; beklenmeyen hatalarda ayrıntı sızdırılmaz. */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Bulunamadı", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail rule(BusinessRuleException e) {
        return problem(HttpStatus.CONFLICT, "İş kuralı ihlali", e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Yetkisiz", e.getMessage());
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    ResponseEntity<ProblemDetail> tooManyAttempts(TooManyAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Çok fazla deneme", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Erişim reddedildi", "Bu işlem için yetkiniz yok");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .sorted().collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Geçersiz istek", detail);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception e) {
        String detail = e instanceof IllegalArgumentException ? e.getMessage() : "İstek gövdesi veya parametre okunamadı";
        return problem(HttpStatus.BAD_REQUEST, "Geçersiz istek", detail);
    }

    /** Eşzamanlı kayıtlarda benzersizlik kısıtı (sicil no, e-posta vb.) ihlali. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        log.warn("Veri bütünlüğü ihlali", e);
        return problem(HttpStatus.CONFLICT, "Çakışma", "Kayıt mevcut bir kayıtla çakışıyor");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Beklenmeyen hata", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Sunucu hatası", "Beklenmeyen bir hata oluştu");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        return p;
    }
}
