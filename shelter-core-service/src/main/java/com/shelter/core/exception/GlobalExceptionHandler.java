package com.shelter.core.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ошибки в общем формате: {timestamp, status, error, message, path}. Внутренние детали наружу не отдаются. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleRule(BusinessRuleException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(InvalidRequestException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleUnavailable(ServiceUnavailableException ex,
                                                                 HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenActionException.class)
    public ResponseEntity<Map<String, Object>> handleForbiddenAction(ForbiddenActionException ex,
                                                                     HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /** Отказ @PreAuthorize: без этого обработчика его перехватил бы общий обработчик ниже как 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "Недостаточно прав для этого действия", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Ошибка валидации");
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleParamValidation(HandlerMethodValidationException ex,
                                                                     HttpServletRequest request) {
        String message = ex.getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Ошибка валидации");
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "Некорректное тело запроса: проверьте JSON, даты (ГГГГ-ММ-ДД) и значения полей", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                  HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Некорректное значение параметра «" + ex.getName() + "»", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex,
                                                                  HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Не передан параметр «" + ex.getParameterName() + "»", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex,
                                                              HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Файл слишком большой: допускается не больше 5 МБ", request);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<Map<String, Object>> handleMultipart(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST,
                "Ожидается файл в поле «file» (multipart/form-data)", request);
    }

    /** Ограничения базы, которые не удалось проверить заранее (например, одновременное создание). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex,
                                                               HttpServletRequest request) {
        log.warn("Нарушено ограничение базы данных {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT,
                "Операция нарушает ограничения данных: возможно, запись изменилась. Обновите данные и повторите", request);
    }

    /** Стандартные ошибки Spring MVC (404 пути, 405 метода и т. п.) и всё непредвиденное. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return error(HttpStatus.valueOf(status.value()), standardMessage(status), request);
        }
        log.error("Необработанная ошибка {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", request);
    }

    private static String standardMessage(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Ресурс не найден";
            case 405 -> "Метод не поддерживается для этого пути";
            case 406 -> "Запрошенный формат ответа не поддерживается";
            case 415 -> "Неподдерживаемый тип содержимого";
            default -> "Некорректный запрос";
        };
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
