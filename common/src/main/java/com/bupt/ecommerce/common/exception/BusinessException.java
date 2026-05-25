package com.bupt.ecommerce.common.exception;

import com.bupt.ecommerce.common.api.ErrorCode;

public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode.code(), errorCode.message());
    }

    public int getCode() {
        return code;
    }
}
