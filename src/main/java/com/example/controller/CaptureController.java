package com.example.controller;

import com.example.model.CapturedRequest;
import com.example.service.ApiCaptureService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CaptureController {

    private final ApiCaptureService apiCaptureService;

    public CaptureController(ApiCaptureService apiCaptureService) {
        this.apiCaptureService = apiCaptureService;
    }

    @PostMapping("/capture/start")
    public String start(@RequestParam String url) {
        apiCaptureService.start(url);
        return "浏览器已打开，请手动操作页面，操作完后调用 POST /capture/stop";
    }

    @PostMapping("/capture/stop")
    public List<CapturedRequest> stop() {
        return apiCaptureService.stopAndFilter();
    }
}
