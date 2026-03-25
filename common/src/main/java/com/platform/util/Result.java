package com.platform.util;

import lombok.Data;

/**
 * 统一响应结构沿用旧包名，避免业务模块在拆分第一阶段大面积改签名。
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    private Result() {
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return fail(code, message, null);
    }

    public static <T> Result<T> fail(Integer code, String message, T data) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        result.data = data;
        return result;
    }

    public static <T> Result<T> badRequest(String message) {
        return fail(400, message);
    }

    public static <T> Result<T> unauthorized(String message) {
        return fail(401, message);
    }

    public static <T> Result<T> forbidden(String message) {
        return fail(403, message);
    }

    public static <T> Result<T> notFound(String message) {
        return fail(404, message);
    }

    public static <T> Result<T> conflict(String message) {
        return fail(409, message);
    }

    public static <T> Result<T> tooManyRequests(String message) {
        return fail(429, message);
    }

    public static <T> Result<T> serverError(String message) {
        return fail(500, message);
    }
}
