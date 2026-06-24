package com.example.service;

import com.example.analysis.SchemaInferrer;
import com.example.analysis.UrlParser;
import com.example.capture.BrowserManager;
import com.example.capture.FetchXhrInterceptor;
import com.example.capture.ResponseInterceptor;
import com.example.filter.AssetFilter;
import com.example.filter.ClassifyFilter;
import com.example.model.AiContext;
import com.example.model.ApiAsset;
import com.example.model.BusinessFlow;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ApiCaptureService {

    private static final Logger log = LoggerFactory.getLogger(ApiCaptureService.class);

    // ========== 依赖注入 ==========

    private final BrowserManager browserManager;
    private final ResponseInterceptor responseInterceptor;
    private final FetchXhrInterceptor fetchXhrInterceptor;
    private final List<AssetFilter> assetFilters;
    private final SchemaInferrer schemaInferrer;
    private final UrlParser urlParser;
    private final ClassifyFilter classifyFilter;
    private final ActionRecognizer actionRecognizer;

    public ApiCaptureService(BrowserManager browserManager,
                             ResponseInterceptor responseInterceptor,
                             FetchXhrInterceptor fetchXhrInterceptor,
                             List<AssetFilter> assetFilters,
                             SchemaInferrer schemaInferrer,
                             UrlParser urlParser,
                             ClassifyFilter classifyFilter,
                             ActionRecognizer actionRecognizer) {
        this.browserManager = browserManager;
        this.responseInterceptor = responseInterceptor;
        this.fetchXhrInterceptor = fetchXhrInterceptor;
        this.assetFilters = assetFilters;
        this.schemaInferrer = schemaInferrer;
        this.urlParser = urlParser;
        this.classifyFilter = classifyFilter;
        this.actionRecognizer = actionRecognizer;
    }

    // ========== 运行时状态 ==========

    private Page page;
    private BrowserContext browserContext;
    private volatile boolean capturing;
    private final List<ApiAsset> capturedRequests = new CopyOnWriteArrayList<>();
    private volatile int totalResponsesSeen;
    private List<ApiAsset> lastResult;
    private BusinessFlow lastFlow;
    private AiContext aiContext;
    private String pageUrl;
    private String sessionId;
    private String mainDomain;

    // ========== 启动/停止 ==========

    public synchronized void start(String url) {
        capturedRequests.clear();
        lastResult = null;
        aiContext = null;
        this.pageUrl = url;
        this.mainDomain = urlParser.extractDomain(url);
        this.sessionId = String.valueOf(System.currentTimeMillis());
        this.totalResponsesSeen = 0;

        log.info("启动录制，目标地址: {} (主域名: {})", url, mainDomain);

        Browser browser = browserManager.getOrCreateBrowser();

        // 关闭旧页面和旧上下文
        browserManager.closePage(page);
        browserManager.closeContext(browserContext);

        // 创建独立浏览器上下文（隔离 cookie/缓存）
        browserContext = browser.newContext();

        // 隐藏自动化标记
        browserContext.addInitScript(
                "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });"
        );

        // 注入 fetch/XHR 补丁：拦截所有 API 调用（包括 MSW/proxy 层拦截的请求）
        fetchXhrInterceptor.install(browserContext);

        page = browserContext.newPage();

        // ========== 诊断监听 ==========

        page.onConsoleMessage(msg -> {
            String type = msg.type();
            String text = msg.text();
            if ("error".equals(type) || "warning".equals(type)) {
                log.warn("🛑 页面 JS [{}]: {}", type, text);
            } else {
                log.info("📋 页面控制台 [{}]: {}", type, text);
            }
        });

        page.onPageError(error -> {
            log.error("🔥 页面运行时错误: {}", error);
        });

        page.onRequest(request -> {
            if (!capturing) return;
            String reqUrl = request.url();
            if (reqUrl.startsWith("data:") || reqUrl.startsWith("blob:")) return;
            String rt = request.resourceType();
            String mark = "xhr".equals(rt) || "fetch".equals(rt) ? "✅" : "⏭️";
            log.info("{} 请求: {} {} (resourceType={})", mark, request.method(), reqUrl, rt);
        });

        // ========== 响应捕获（通过 ResponseInterceptor）==========

        responseInterceptor.install(page, asset -> {
            if (!capturing) return;
            totalResponsesSeen++;
            asset.setPageUrl(pageUrl);
            asset.setSessionId(sessionId);
            capturedRequests.add(asset);
        });

        capturing = true;
        page.navigate(url);
        log.info("浏览器已就绪，等待用户操作... sessionId={}", sessionId);
    }

    public synchronized List<ApiAsset> stopAndFilter() {
        log.info("停止录制...");

        // 1) 先提取 fetch/XHR 补丁数据（页面还活着）
        int patchedCount = fetchXhrInterceptor.extractPatchedData(page, pageUrl, sessionId,
                asset -> capturedRequests.add(asset));
        if (patchedCount > 0) {
            log.info("从 fetch/XHR 补丁读取到 {} 条 API 调用", patchedCount);
        }

        // 2) 关闭页面/上下文（此时 capturing 仍为 true，关闭触发的响应回调和 onResponse 事件仍可被捕获）
        browserManager.closePage(page);
        browserManager.closeContext(browserContext);
        page = null;
        browserContext = null;

        // 3) 关闭后再停止收集，避免错过关闭瞬间到达的响应
        capturing = false;

        // ---- 过滤链：静态资源 → 埋点/监控 → 去重 ----
        List<ApiAsset> current = new ArrayList<>(capturedRequests);
        for (AssetFilter f : assetFilters) {
            current = f.filter(current);
        }

        // ---- 补充 domain / resource ----
        enrichAssets(current);

        // ---- 分配序号 ----
        for (int i = 0; i < current.size(); i++) {
            current.get(i).setSequence(i + 1);
        }

        // ---- 接口分类 ----
        classifyFilter.setMainDomain(mainDomain);
        classifyFilter.classify(current);

        lastResult = current;

        // ---- 业务动作识别 ----
        lastFlow = actionRecognizer.buildFlow(current);
        log.info("业务动作识别完成: 场景={}, 动作数={}", lastFlow.getScenario(), lastFlow.getActions().size());

        log.info("录制结束: 总计看到 {} 个响应，原始捕获 {} 条 → 过滤后 {} 条",
                totalResponsesSeen, capturedRequests.size(), current.size());
        return current;
    }

    // ========== 公开接口 ==========

    public List<ApiAsset> getRawCaptures() {
        return new ArrayList<>(capturedRequests);
    }

    public List<ApiAsset> getLastResult() {
        return lastResult;
    }

    public BusinessFlow getLastFlow() {
        return lastFlow;
    }

    public AiContext getAiContext() {
        return aiContext;
    }

    public String getMainDomain() {
        return mainDomain;
    }

    // ========== 内部方法 ==========

    private void enrichAssets(List<ApiAsset> assets) {
        for (ApiAsset a : assets) {
            String url = a.getUrl();
            if (url == null) { a.setDomain("unknown"); a.setResource("/"); continue; }
            try {
                URI uri = URI.create(url);
                a.setDomain(uri.getHost());
                String path = uri.getPath();
                a.setResource(path != null && !path.isEmpty() ? path : "/");
            } catch (Exception e) {
                a.setDomain("unknown");
                a.setResource(url);
            }
        }
    }
}
