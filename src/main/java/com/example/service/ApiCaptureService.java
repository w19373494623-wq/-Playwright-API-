package com.example.service;

import com.example.model.CapturedRequest;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ApiCaptureService {

    private static final Logger log = LoggerFactory.getLogger(ApiCaptureService.class);

    private Playwright playwright;
    private Browser browser;
    private Page page;
    private final List<CapturedRequest> capturedRequests = new CopyOnWriteArrayList<>();

    public void start(String url) {
        capturedRequests.clear();
        log.info("启动录制，目标地址: {}", url);

        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false));
        page = browser.newPage();

        // 监听每一个请求的响应
        page.onResponse(response -> {
            String reqUrl = response.request().url();
            String method = response.request().method();

            // 跳过 data: 和 blob: 协议
            if (reqUrl.startsWith("data:") || reqUrl.startsWith("blob:")) {
                return;
            }

            int statusCode;
            try {
                statusCode = response.status();
            } catch (Exception e) {
                log.warn("获取状态码失败: {} {}", method, reqUrl);
                return;
            }

            String requestBody = null;
            try {
                requestBody = response.request().postData();
            } catch (Exception ignored) {
            }

            String responseBody = null;
            try {
                responseBody = new String(response.body());
            } catch (Exception e) {
                log.warn("读取响应体失败: {} {} (status={})", method, reqUrl, statusCode);
            }

            CapturedRequest captured = new CapturedRequest(reqUrl, method, requestBody, statusCode, responseBody);
            capturedRequests.add(captured);
            log.info("捕获: {} {} -> {}", method, reqUrl, statusCode);
        });

        page.navigate(url);
        log.info("浏览器已打开，等待用户操作...");
    }

    public List<CapturedRequest> stopAndFilter() {
        log.info("停止录制，关闭浏览器...");
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }

        List<CapturedRequest> apiRequests = filterApiRequests(capturedRequests);
        log.info("录制结束: 共捕获 {} 条请求，过滤后保留 {} 条 API 请求",
                capturedRequests.size(), apiRequests.size());
        return apiRequests;
    }

    private List<CapturedRequest> filterApiRequests(List<CapturedRequest> all) {
        List<CapturedRequest> result = new ArrayList<>();
        for (CapturedRequest req : all) {
            String url = req.getUrl().toLowerCase();

            // 跳过静态资源文件
            if (url.endsWith(".js") || url.endsWith(".css") ||
                url.endsWith(".png") || url.endsWith(".jpg") ||
                url.endsWith(".jpeg") || url.endsWith(".gif") ||
                url.endsWith(".ico") || url.endsWith(".svg") ||
                url.endsWith(".woff") || url.endsWith(".woff2") ||
                url.endsWith(".ttf") || url.endsWith(".eot") ||
                url.endsWith(".map")) {
                continue;
            }

            result.add(req);
        }
        return result;
    }
}
