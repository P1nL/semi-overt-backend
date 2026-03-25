package com.platform.controller;

import com.platform.dto.resp.UploadResp;
import com.platform.service.UploadService;
import com.platform.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传接口
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /**
     * 上传图片
     * POST /api/v1/uploads/images
     * 需要登录（SecurityConfig anyRequest().authenticated() 覆盖）
     *
     * @param file      上传文件（multipart/form-data）
     * @param bizType   业务类型：AVATAR / COVER / ARTICLE_IMAGE
     * @param articleId 文章 ID，正文图片场景可选传
     */
    @PostMapping("/images")
    public Result<UploadResp> uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam String bizType,
            @RequestParam(required = false) Long articleId) {

        return Result.ok(uploadService.upload(file, bizType, articleId));
    }
}