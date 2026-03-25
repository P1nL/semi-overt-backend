package com.platform.common.api;

import com.platform.common.exception.RemoteCallException;
import com.platform.util.Result;

/**
 * 第一阶段继续复用 Result 包装，避免服务拆分时同时大改外部与内部协议。
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
