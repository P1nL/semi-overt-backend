package com.platform.util;

import lombok.Data;

/**
 * 统一响应结构
 * 成功：code=200, message="success", data=业务数据
 * 失败：code=错误码, message=错误提示, data=null
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    private Result() {}

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

    /** 快捷方法：400 参数错误 */
    public static <T> Result<T> badRequest(String message) {
        return fail(400, message);
    }

    /** 快捷方法：401 未登录 */
    public static <T> Result<T> unauthorized(String message) {
        return fail(401, message);
    }

    /** 快捷方法：403 权限不足 */
    public static <T> Result<T> forbidden(String message) {
        return fail(403, message);
    }

    /** 快捷方法：404 资源不存在 */
    public static <T> Result<T> notFound(String message) {
        return fail(404, message);
    }

    /** 快捷方法：409 状态冲突 */
    public static <T> Result<T> conflict(String message) {
        return fail(409, message);
    }

    /** 快捷方法：429 操作过于频繁 */
    public static <T> Result<T> tooManyRequests(String message) {
        return fail(429, message);
    }

    /** 快捷方法：500 服务器内部异常 */
    public static <T> Result<T> serverError(String message) {
        return fail(500, message);
    }
}
