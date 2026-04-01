package com.platform.kernel.api;

import com.platform.kernel.exception.RemoteCallException;
import com.platform.kernel.util.Result;

/**
 * 统一返回结果工具类，提供标准响应对象的便捷构造方法。
 */

public final class ResultUtils {

    private ResultUtils() {
    }

    public static <T> T requireOk(Result<T> result) {
        if (result == null) {
            throw new RemoteCallException("远程调用返回为空");
        }
        if (result.getCode() == null || result.getCode() != 200) {
            throw new RemoteCallException(result.getMessage(), result.getCode());
        }
        return result.getData();
    }
}
