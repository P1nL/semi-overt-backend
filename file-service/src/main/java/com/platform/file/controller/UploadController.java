package com.platform.file.controller;

import com.platform.file.api.resp.UploadResp;
import com.platform.file.service.UploadService;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

        @PostMapping("/images")
    public Result<UploadResp> uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam String bizType,
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) String oldUrl) {

        return Result.ok(uploadService.upload(file, bizType, articleId, oldUrl));
    }
}

