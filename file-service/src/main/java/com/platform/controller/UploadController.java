package com.platform.controller;

import com.platform.dto.resp.UploadResp;
import com.platform.service.UploadService;
import com.platform.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传接口。
 * 统一承载头像、封面和正文配图上传请求。
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /**
     * 上传图片。
     * 认证要求由 SecurityConfig 统一控制。
     */
    @PostMapping("/images")
    public Result<UploadResp> uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam String bizType,
            @RequestParam(required = false) Long articleId) {

        return Result.ok(uploadService.upload(file, bizType, articleId));
    }
}
