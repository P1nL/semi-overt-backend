package com.platform.file.api.resp;

import lombok.Builder;
import lombok.Data;

/**
 * 上传结果响应。
 */
@Data
@Builder
public class UploadResp {

    private String url;
    private Integer width;
    private Integer height;
    private Long size;
    private String dominantColor;
}
