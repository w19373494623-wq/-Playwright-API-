package com.example.service;

import com.example.model.AiContext;
import com.example.model.ApiAsset;
import com.example.model.CapturedRequest;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ApiCaptureService {

    private static final Logger log = LoggerFactory.getLogger(ApiCaptureService.class);

    // ========== 第一层过滤：规则（黑名单，确定性）==========
    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".woff", ".woff2", ".ttf", ".eot", ".map", ".webp", ".mp4",
            ".mp3", ".avi", ".mov", ".webm", ".pdf", ".zip", ".gz", ".tar"
    );

    private static final Set<String> TRACKING_PATTERNS = Set.of(
            "google-analytics.com", "googletagmanager.com", "gtag",
            "baidu.com/hm.js", "hm.baidu.com", "tongji.baidu.com",
            "analytics", "doubleclick.net", "facebook.net/tr",
            "hotjar.com", "clarity.ms", "mouseflow.com",
            "tracking", "beacon", "collect", "log.gif",
            "rum", "telemetry", "metrics", "sentry", "sentry.io"
    );

    // ========== 浏览器单例（复用，避免每次启动重建）==========
    private static volatile Playwright sharedPlaywright;
    private static volatile Browser sharedBrowser;

    private Page page;
    private String sessionId;
    private String pageUrl;
    private boolean capturing;
    private final AtomicInteger sequence = new AtomicInteger(0);
    private final List<CapturedRequest> capturedRequests = new CopyOnWriteArrayList<>();
    private List<ApiAsset> lastResult;
    private AiContext aiContext;

    /**
     * 懒加载 Playwright + Browser，只初始化一次
     */
    private synchronized Browser getOrCreateBrowser() {
        if (sharedPlaywright == null) {
            log.info("首次启动 Playwright 引擎...");
            sharedPlaywright = Playwright.create();
        }
        if (sharedBrowser == null || !sharedBrowser.isConnected()) {
            log.info("启动 Chromium 浏览器...");
            sharedBrowser = sharedPlaywright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false));
        }
        return sharedBrowser;
    }

    @PreDestroy
    public synchronized void destroy() {
        log.info("销毁浏览器资源...");
        if (sharedBrowser != null) {
            try { sharedBrowser.close(); } catch (Exception ignored) {}
            sharedBrowser = null;
        }
        if (sharedPlaywright != null) {
            try { sharedPlaywright.close(); } catch (Exception ignored) {}
            sharedPlaywright = null;
        }
    }

    public synchronized void start(String url) {
        capturedRequests.clear();
        lastResult = null;
        aiContext = null;
        sequence.set(0);
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.pageUrl = url;

        Browser browser = getOrCreateBrowser();
        // 复用已有页面或创建新页面
        if (page != null) {
            try { page.close(); } catch (Exception ignored) {}
        }
        page = browser.newPage();

        page.onResponse(response -> {
            if (!capturing) return;
            String reqUrl = response.request().url();
            String method = response.request().method();

            if (shouldSkipByRules(reqUrl, method)) return;

            int statusCode;
            try {
                statusCode = response.status();
            } catch (Exception e) {
                log.warn("获取状态码失败: {} {}", method, reqUrl);
                return;
            }

            Map<String, String> requestHeaders = extractHeaders(response.request().headers());
            Map<String, String> responseHeaders = extractHeaders(response.headers());

            String requestBody = null;
            try {
                requestBody = response.request().postData();
            } catch (Exception ignored) {}

            String responseBody = null;
            String contentType = responseHeaders.getOrDefault("content-type", "");
            if (isReadableContent(contentType)) {
                try {
                    byte[] bodyBytes = response.body();
                    if (bodyBytes != null && bodyBytes.length > 0 && bodyBytes.length < 512 * 1024) {
                        responseBody = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    log.warn("读取响应体失败: {} {} (status={})", method, reqUrl, statusCode);
                }
            }

            int seq = sequence.incrementAndGet();
            CapturedRequest captured = new CapturedRequest(
                    reqUrl, method, requestHeaders, requestBody,
                    statusCode, responseHeaders, responseBody,
                    System.currentTimeMillis(), seq);
            capturedRequests.add(captured);
            log.info("捕获 [{}]: {} {} -> {}", seq, method, reqUrl, statusCode);
        });

        capturing = true;
        page.navigate(url);
        log.info("浏览器已就绪，等待用户操作... (session: {})", sessionId);
    }

    /**
     * 停止录制（不关浏览器，只关闭页面，下次启动秒开）
     */
    public List<ApiAsset> stopAndFilter() {
        capturing = false;
        log.info("停止录制...");

        if (page != null) {
            try { page.close(); } catch (Exception ignored) {}
            page = null;
        }

        // 全部转为 ApiAsset（不做 URL 去重，所有真实 API 请求全部保留）
        List<ApiAsset> assets = new ArrayList<>();
        for (int i = 0; i < capturedRequests.size(); i++) {
            assets.add(new ApiAsset(capturedRequests.get(i), i + 1, pageUrl, sessionId));
        }

        log.info("录制结束: 共捕获 {} 条请求，保留 {} 条 API 请求",
                capturedRequests.size(), assets.size());

        this.lastResult = assets;
        this.aiContext = new AiContext();
        this.aiContext.setSessionId(sessionId);
        this.aiContext.setAssets(assets);
        return assets;
    }

    public List<ApiAsset> applyAiFilter(List<Integer> invalidIndices) {
        if (lastResult == null || invalidIndices.isEmpty()) return lastResult;

        Set<Integer> removeSet = Set.copyOf(invalidIndices);
        List<ApiAsset> filtered = new ArrayList<>();
        List<Map<String, Object>> removedDetails = new ArrayList<>();

        for (int i = 0; i < lastResult.size(); i++) {
            if (removeSet.contains(i)) {
                ApiAsset a = lastResult.get(i);
                removedDetails.add(Map.of(
                        "index", i, "url", a.getUrl(), "method", a.getMethod(),
                        "domain", a.getDomain(), "resource", a.getResource()));
            } else {
                filtered.add(lastResult.get(i));
            }
        }

        if (aiContext != null) {
            aiContext.setRemovedIndices(new ArrayList<>(removeSet));
            aiContext.setRemovedDetails(removedDetails);
            aiContext.setAssets(filtered);
        }

        this.lastResult = filtered;
        log.info("AI 二次过滤: 移除 {}, 剩余 {}", invalidIndices.size(), filtered.size());
        return filtered;
    }

    public List<ApiAsset> getLastResult() { return lastResult; }
    public AiContext getAiContext() { return aiContext; }

    // ========== 规则过滤 ==========

    private boolean shouldSkipByRules(String url, String method) {
        if (url.startsWith("data:") || url.startsWith("blob:")
                || url.startsWith("ws:") || url.startsWith("wss:")) return true;
        if ("OPTIONS".equalsIgnoreCase(method)) return true;

        String lowerUrl = url.toLowerCase();
        for (String ext : STATIC_EXTENSIONS) {
            int idx = lowerUrl.indexOf(ext);
            if (idx >= 0 && (idx + ext.length() == lowerUrl.length()
                    || lowerUrl.charAt(idx + ext.length()) == '?'
                    || lowerUrl.charAt(idx + ext.length()) == '#')) return true;
        }
        for (String kw : TRACKING_PATTERNS) {
            if (lowerUrl.contains(kw)) return true;
        }
        return false;
    }

    private boolean isReadableContent(String contentType) {
        if (contentType == null || contentType.isEmpty()) return false;
        String ct = contentType.toLowerCase();
        return ct.contains("json") || ct.contains("xml")
                || ct.contains("text") || ct.contains("javascript")
                || ct.contains("x-www-form-urlencoded");
    }

    private Map<String, String> extractHeaders(Map<String, String> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw != null) raw.forEach((k, v) -> result.put(k.toLowerCase(), v));
        return result;
    }

}
