package com.bupt.ecommerce.common.exception;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        return ApiResponse.failure(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        return ApiResponse.failure(ErrorCode.BAD_REQUEST.code(), ErrorCode.BAD_REQUEST.message());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception ex) {
        return ApiResponse.failure(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message());
    }
}
