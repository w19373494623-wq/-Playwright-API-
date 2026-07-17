package com.example.controller;

import com.example.model.AiContext;
import com.example.model.ApiAsset;
import com.example.model.BusinessFlow;
import com.example.model.DedupResult;
import com.example.model.HistoryRecord;
import com.example.model.ApiSummary;
import com.example.service.ActionRecognizer;
import com.example.service.AiAnalyzeService;
import com.example.service.AiChatService;
import com.example.service.ApiCaptureService;
import com.example.service.DedupService;
import com.example.service.HistoryService;
import com.example.service.ApifoxMergeService;
import com.example.ai.parse.AiResultParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
public class CaptureController {

    private static final Logger log = LoggerFactory.getLogger(CaptureController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ApiCaptureService apiCaptureService;
    private final AiChatService aiChatService;
    private final AiAnalyzeService aiAnalyzeService;
    private final DedupService dedupService;
    private final AiResultParser aiResultParser;
    private final HistoryService historyService;
    private final ApifoxMergeService apifoxMergeService;

    /** 最近一次 AI 去重结果，供导出使用 */
    private DedupResult lastDedupResult;

    /** 录制开始时间戳 */
    private long captureStartTime;

    /** 当前加载到会话中的历史记录 ID（用于 AI 缓存判断） */
    private String currentHistoryId;

    public CaptureController(ApiCaptureService apiCaptureService,
                             AiChatService aiChatService,
                             AiAnalyzeService aiAnalyzeService,
                             DedupService dedupService,
                             AiResultParser aiResultParser,
                             HistoryService historyService,
                             ApifoxMergeService apifoxMergeService) {
        this.apiCaptureService = apiCaptureService;
        this.aiChatService = aiChatService;
        this.aiAnalyzeService = aiAnalyzeService;
        this.dedupService = dedupService;
        this.aiResultParser = aiResultParser;
        this.historyService = historyService;
        this.apifoxMergeService = apifoxMergeService;
    }

    // ==================== 基础录制 ====================

    @PostMapping("/capture/start")
    public String start(@RequestParam(defaultValue = "") String url) {
        if (url.isBlank()) {
            throw new IllegalArgumentException("url 不能为空");
        }
        captureStartTime = System.currentTimeMillis();
        apiCaptureService.start(url);
        return "浏览器已打开，请手动操作页面，操作完后调用 POST /capture/stop";
    }

    @PostMapping("/capture/stop")
    public Map<String, Object> stop() {
        List<ApiAsset> assets = apiCaptureService.stopAndFilter();

        String historyId = null;
        // 保存历史记录（第一阶段，不依赖 AI）
        try {
            String pageUrl = assets.isEmpty() ? null : assets.get(0).getPageUrl();
            HistoryRecord record = historyService.save(assets, apiCaptureService.getMainDomain(),
                    pageUrl, captureStartTime > 0 ? captureStartTime : System.currentTimeMillis());
            historyId = record.getId();
            this.currentHistoryId = historyId;
        } catch (Exception e) {
            log.error("保存历史记录失败", e);
        }

        captureStartTime = 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assets", assets);
        result.put("historyId", historyId);
        return result;
    }

    /** 实时查看原始捕获（未经过滤），操作过程中可随时查看 */
    @GetMapping("/capture/raw")
    public Map<String, Object> rawCaptures() {
        List<ApiAsset> raw = apiCaptureService.getRawCaptures();
        List<Map<String, String>> list = new ArrayList<>();
        for (ApiAsset a : raw) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("method", a.getMethod());
            item.put("url", a.getUrl());
            item.put("status", String.valueOf(a.getStatusCode()));
            list.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", raw.size());
        result.put("captures", list);
        return result;
    }

    // ==================== 业务动作流 ====================

    @GetMapping("/capture/flow")
    public BusinessFlow getFlow() {
        BusinessFlow flow = apiCaptureService.getLastFlow();
        if (flow == null) {
            return new BusinessFlow("无录制数据", "请先执行录制操作", List.of());
        }
        return flow;
    }

    // ==================== 接口去重（按 resource 聚合，同一接口只留一个）====================

    @GetMapping("/capture/unique")
    public Map<String, Object> uniqueApis() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return Map.of("total", 0, "totalRaw", 0, "apis", List.of());
        }

        // 按 method + resource 去重，保留第一个出现的
        Map<String, ApiAsset> seen = new LinkedHashMap<>();
        for (ApiAsset a : assets) {
            String key = a.getMethod() + " " + (a.getResource() != null ? a.getResource() : a.getUrl());
            seen.putIfAbsent(key, a);
        }

        int idx = 1;
        List<Map<String, Object>> apis = new ArrayList<>();
        for (ApiAsset a : seen.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", idx++);
            item.put("method", a.getMethod());
            item.put("url", a.getUrl());
            item.put("resource", a.getResource());
            item.put("status", a.getStatusCode());
            item.put("category", a.getCategory());
            item.put("domain", a.getDomain());
            apis.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", apis.size());
        result.put("totalRaw", assets.size());
        result.put("apis", apis);
        return result;
    }

    // ==================== 统计 ====================

    @GetMapping("/capture/stats")
    public Map<String, Object> stats() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) return Map.of("total", 0);

        int success = 0, redirect = 0, clientError = 0, serverError = 0;
        Map<String, Integer> byDomain = new LinkedHashMap<>();
        Map<String, Integer> byIntent = new LinkedHashMap<>();

        for (ApiAsset a : assets) {
            int code = a.getStatusCode();
            if (code >= 200 && code < 300) success++;
            else if (code >= 300 && code < 400) redirect++;
            else if (code >= 400 && code < 500) clientError++;
            else if (code >= 500) serverError++;

            byDomain.merge(a.getDomain(), 1, Integer::sum);
            if (a.getIntent() != null) byIntent.merge(a.getIntent(), 1, Integer::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", assets.size());
        result.put("success", success);
        result.put("redirect", redirect);
        result.put("clientError", clientError);
        result.put("serverError", serverError);
        result.put("byDomain", byDomain);
        if (!byIntent.isEmpty()) result.put("byIntent", byIntent);
        return result;
    }

    // ==================== 三层结构输出 ====================

    @GetMapping("/capture/layers")
    public Map<String, Object> layers() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return Map.of("layer1", List.of(), "layer2", Map.of(), "layer3", Map.of());
        }

        // ---- Layer 1: 请求去重列表 ----
        List<Map<String, Object>> layer1 = new ArrayList<>();
        for (ApiAsset a : assets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", a.getSequence());
            item.put("method", a.getMethod());
            item.put("url", a.getUrl());
            item.put("status", a.getStatusCode());
            item.put("domain", a.getDomain());
            item.put("resource", a.getResource());
            layer1.add(item);
        }

        // ---- Layer 2: 接口分类 ----
        Map<String, List<Map<String, Object>>> layer2 = new LinkedHashMap<>();
        for (ApiAsset a : assets) {
            String cat = a.getCategory() != null ? a.getCategory() : "unknown";
            layer2.computeIfAbsent(cat, k -> new ArrayList<>());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("method", a.getMethod());
            item.put("url", a.getUrl());
            item.put("resource", a.getResource());
            layer2.get(cat).add(item);
        }

        // ---- Layer 3: 业务链路 AI 分析（触发式）----
        Map<String, Object> layer3 = new LinkedHashMap<>();
        AiContext ctx = apiCaptureService.getAiContext();
        if (ctx != null && ctx.isAnalyzeDone() && ctx.getFlowResult() != null) {
            layer3 = ctx.getFlowResult();
        } else {
            layer3.put("status", "not_analyzed");
            layer3.put("message", "请先调用 POST /capture/analyze 生成业务链路分析");
        }

        // ---- 分类统计 ----
        Map<String, Integer> categorySummary = new LinkedHashMap<>();
        for (ApiAsset a : assets) {
            String cat = a.getCategory() != null ? a.getCategory() : "unknown";
            categorySummary.merge(cat, 1, Integer::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", assets.size());
        result.put("categorySummary", categorySummary);
        result.put("layer1", layer1);
        result.put("layer2", layer2);
        result.put("layer3", layer3);
        return result;
    }

    // ==================== 业务接口过滤 ====================

    @GetMapping("/capture/business")
    public Map<String, Object> businessApis() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return Map.of("total", 0, "totalRaw", 0, "apis", List.of());
        }

        // 只保留 business + auth
        String mainDomain = apiCaptureService.getMainDomain();
        List<Map<String, Object>> apis = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ApiAsset a : assets) {
            String cat = a.getCategory();
            if (!"business".equals(cat) && !"auth".equals(cat)) continue;

            // 去重：同一 method + resource 只保留一次
            String dedupKey = a.getMethod() + " " + a.getResource();
            if (!seen.add(dedupKey)) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("method", a.getMethod());
            item.put("url", a.getUrl());
            item.put("resource", a.getResource());
            item.put("category", cat);
            item.put("status", a.getStatusCode());
            item.put("domain", a.getDomain());
            item.put("scene", inferScene(a));
            apis.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRaw", assets.size());
        result.put("total", apis.size());
        result.put("mainDomain", mainDomain);
        result.put("apis", apis);
        return result;
    }

    /** 根据 URL 推断业务场景（按优先级从高到低匹配） */
    private String inferScene(ApiAsset a) {
        String url = a.getUrl();
        String method = a.getMethod();
        if (url == null) return "";
        String lower = url.toLowerCase();

        // ── 鉴权类 ──
        if (lower.contains("login") || lower.contains("signin")) return "用户登录";
        if (lower.contains("register") || lower.contains("signup")) return "用户注册";
        if (lower.contains("logout") || lower.contains("signout")) return "退出登录";
        if (lower.contains("captcha")) return "获取验证码";
        if (lower.contains("token") || lower.contains("refresh")) return "刷新令牌";
        if (lower.contains("verify") || lower.contains("reset")) return "验证身份";

        // ── 搜索/查询 ──
        if (lower.contains("search")) return "搜索内容";
        if (lower.contains("list") || lower.contains("page") || lower.contains("feed")) return "查询列表";

        // ── 用户信息 ──
        if (lower.contains("profile") || lower.contains("avatar") || lower.contains("identity")) return "用户信息";

        // ── 内容操作 ──
        if (lower.contains("detail")) return "查看详情";
        if (lower.contains("favorite") || lower.contains("like") || lower.contains("star") || lower.contains("collect")) return "收藏/点赞";
        if (lower.contains("follow") || lower.contains("subscribe") || lower.contains("unfollow")) return "关注/订阅";
        if (lower.contains("comment") || lower.contains("reply") || lower.contains("review")) return "评论/回复";
        if (lower.contains("share")) return "分享内容";
        if (lower.contains("create") || lower.contains("add") || lower.contains("new") || lower.contains("publish")) return "创建内容";
        if (lower.contains("update") || lower.contains("edit") || lower.contains("modify") || lower.contains("change")) return "编辑内容";
        if (lower.contains("delete") || lower.contains("remove") || lower.contains("cancel")) return "删除内容";
        if (lower.contains("upload")) return "上传文件";
        if (lower.contains("download") || lower.contains("export") || lower.contains("import")) return "导入/导出";

        // ── 其他 ──
        if (lower.contains("setting") || lower.contains("config")) return "系统设置";
        if (lower.contains("notification") || lower.contains("notice") || lower.contains("message")) return "消息通知";
        if (lower.contains("recommend") || lower.contains("suggest") || lower.contains("hot")) return "推荐内容";
        if (lower.contains("report") || lower.contains("stat") || lower.contains("rank")) return "数据统计";

        // ── 按 HTTP 方法推断 ──
        if ("GET".equalsIgnoreCase(method)) return "查询数据";
        if ("POST".equalsIgnoreCase(method)) return "提交数据";
        if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) return "更新数据";
        if ("DELETE".equalsIgnoreCase(method)) return "删除数据";

        return "其他操作";
    }

    // ==================== 历史录制管理 ====================

    @GetMapping("/capture/history")
    public List<Map<String, Object>> historyList() {
        return historyService.findAll();
    }

    @GetMapping("/capture/history/{id}")
    public ResponseEntity<?> historyDetail(@PathVariable String id) {
        HistoryRecord record = historyService.findById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(record);
    }

    /**
     * 获取完整项目快照。
     * 包含完整接口资产和 AI 分析结果，用于项目恢复和详情展示。
     */
    @GetMapping("/capture/project/{id}")
    public ResponseEntity<?> projectDetail(@PathVariable String id) {
        HistoryRecord record = historyService.findById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId());
        result.put("title", record.getTitle());
        result.put("mainDomain", record.getMainDomain());
        result.put("createdAt", record.getCreatedAt());
        result.put("updateTime", record.getUpdateTime());
        result.put("duration", record.getDuration());
        result.put("version", record.getVersion());
        result.put("totalRaw", record.getTotalRaw());
        result.put("totalFiltered", record.getTotalFiltered());
        result.put("apiCount", record.getApiCount());

        // 接口资产
        result.put("apis", record.getApis());
        result.put("assets", record.getAssets());

        // 环境变量
        result.put("envVars", record.getEnvVars());

        // AI 分析结果（如果存在）
        result.put("summary", record.getSummary());
        result.put("businessFlow", record.getBusinessFlow());
        result.put("scenarios", record.getScenarios());
        result.put("dedupResult", record.getDedupResult());

        // 扩展数据
        result.put("aiAnalysis", record.getAiAnalysis());
        result.put("smokeTestResults", record.getSmokeTestResults());

        // 烟雾测试结果摘要
        result.put("smokeTestResult", record.getSmokeTestResult());

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/capture/history/{id}")
    public ResponseEntity<?> historyDelete(@PathVariable String id) {
        historyService.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true, "id", id));
    }

    /** 从历史记录导出 Apifox Collection（支持对历史数据做烟雾测试） */
    @GetMapping("/capture/history/{id}/export/apifox")
    public ResponseEntity<?> historyExportApifox(@PathVariable String id) {
        HistoryRecord record = historyService.findById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> collection = buildApifoxCollectionFromHistory(record);
        return ResponseEntity.ok(collection);
    }

    /**
     * 合并已有 Apifox Collection 与录制数据。
     * 通过 method + path 匹配接口，补充请求/响应示例、请求头、状态码和环境变量。
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/capture/apifox/merge")
    public ResponseEntity<?> mergeApifox(@RequestBody Map<String, Object> body) {
        String historyId = (String) body.get("historyId");
        Map<String, Object> apifoxCollection = (Map<String, Object>) body.get("apifoxCollection");
        if (apifoxCollection == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "apifoxCollection 不能为空"));
        }
        try {
            Map<String, Object> result = apifoxMergeService.merge(historyId, apifoxCollection);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Apifox 合并失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "合并失败: " + e.getMessage()));
        }
    }

    /**
     * 将历史记录加载到当前录制会话中。
     * 调用后，统计/AI去重/导出/场景分析等所有功能都会针对该历史记录操作。
     */
    @PostMapping("/capture/load-history/{id}")
    public ResponseEntity<?> loadHistoryIntoSession(@PathVariable String id) {
        HistoryRecord record = historyService.findById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        List<ApiSummary> apis = record.getApis();
        if (apis == null || apis.isEmpty()) {
            return ResponseEntity.ok(Map.of("loaded", false, "message", "历史记录无接口数据"));
        }

        String domain = record.getMainDomain();
        List<ApiAsset> assets = new ArrayList<>();
        int seq = 1;
        for (ApiSummary api : apis) {
            ApiAsset a = new ApiAsset();
            a.setMethod(api.getMethod());
            a.setResource(api.getResource());

            // 优先使用存储的完整 URL，否则拼接
            String fullUrl = api.getUrl();
            if (fullUrl != null && !fullUrl.isBlank()) {
                a.setUrl(fullUrl);
            } else {
                a.setUrl("https://" + (domain != null ? domain : "unknown") + api.getResource());
            }

            a.setDomain(domain);
            a.setStatusCode(200);
            a.setSequence(seq++);
            a.setCategory(api.getCategory());
            a.setSessionId(record.getId());
            a.setPageUrl(record.getUrl());

            // 还原请求体
            a.setRequestBody(api.getRequestBody());

            // 还原请求/响应 map
            Map<String, Object> req = new LinkedHashMap<>();
            if (api.getHeaders() != null) {
                req.put("headers", new LinkedHashMap<>(api.getHeaders()));
            } else {
                req.put("headers", new LinkedHashMap<String, String>());
            }
            req.put("body", api.getRequestBody());
            a.setRequest(req);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("headers", new LinkedHashMap<String, String>());
            res.put("body", null);
            a.setResponse(res);

            assets.add(a);
        }

        apiCaptureService.setLastResult(assets);
        this.currentHistoryId = id;
        log.info("历史记录已加载到会话: id={}, title={}, apis={}", id, record.getTitle(), assets.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loaded", true);
        result.put("total", assets.size());
        result.put("title", record.getTitle());
        return ResponseEntity.ok(result);
    }

    /** 重命名历史记录标题 */
    @PutMapping("/capture/history/{id}/rename")
    public ResponseEntity<?> renameHistory(@PathVariable String id,
                                            @RequestBody Map<String, String> body) {
        HistoryRecord record = historyService.findById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        String newTitle = body.get("title");
        if (newTitle == null || newTitle.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "标题不能为空"));
        }
        historyService.updateTitle(id, newTitle);
        return ResponseEntity.ok(Map.of("renamed", true, "id", id, "title", newTitle));
    }

    // ==================== 环境变量管理 ====================

    /**
     * 获取历史记录的环境变量。
     */
    @GetMapping("/capture/history/{id}/env-vars")
    public ResponseEntity<?> getEnvVars(@PathVariable String id) {
        HistoryRecord record = historyService.findById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, String> envVars = record.getEnvVars();
        if (envVars == null) envVars = Map.of();
        return ResponseEntity.ok(Map.of("envVars", envVars));
    }

    /**
     * 更新历史记录的环境变量。
     */
    @PutMapping("/capture/history/{id}/env-vars")
    public ResponseEntity<?> updateEnvVars(@PathVariable String id,
                                            @RequestBody Map<String, Object> body) {
        HistoryRecord record = historyService.findById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        @SuppressWarnings("unchecked")
        Map<String, String> envVars = (Map<String, String>) body.get("envVars");
        if (envVars == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "envVars 不能为空"));
        }
        record.setEnvVars(envVars);
        historyService.updateEnvVars(id, envVars);
        return ResponseEntity.ok(Map.of("updated", true, "envVars", envVars));
    }

    /**
     * 获取当前会话的环境变量。
     */
    @GetMapping("/capture/env-vars")
    public ResponseEntity<?> getSessionEnvVars() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return ResponseEntity.ok(Map.of("envVars", Map.of()));
        }
        Map<String, String> envVars = extractAllVariables(assets);
        // 额外从最近的历史记录中读取（如果有匹配的 sessionId）
        return ResponseEntity.ok(Map.of("envVars", envVars));
    }

    // ==================== AI 业务去重 ====================

    @PostMapping("/capture/ai-dedup")
    public Map<String, Object> aiDedup() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return Map.of("total", 0, "apis", List.of());
        }

        // 检查缓存：如果 HistoryRecord 已有去重结果，直接返回
        if (currentHistoryId != null) {
            HistoryRecord record = historyService.findById(currentHistoryId);
            if (record != null && record.getDedupResult() != null) {
                DedupResult cached = record.getDedupResult();
                log.info("AI去重返回缓存: historyId={}, total={}", currentHistoryId, cached.getTotal());
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("total", cached.getTotal());
                map.put("totalRaw", cached.getTotalRaw());
                map.put("apis", cached.getApis());
                if (cached.getRawAiResponse() != null) map.put("raw", cached.getRawAiResponse());
                if (cached.isFallback()) map.put("fallback", true);
                return map;
            }
        }

        lastDedupResult = dedupService.dedup(assets);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", lastDedupResult.getTotal());
        map.put("totalRaw", lastDedupResult.getTotalRaw());
        map.put("apis", lastDedupResult.getApis());
        if (lastDedupResult.getRawAiResponse() != null) map.put("raw", lastDedupResult.getRawAiResponse());
        if (lastDedupResult.isFallback()) map.put("fallback", true);

        // 保存到缓存
        if (currentHistoryId != null) {
            historyService.updateDedupResult(currentHistoryId, lastDedupResult);
        }

        return map;
    }


    // ==================== AI 业务链路分析 ====================

    @PostMapping("/capture/analyze")
    public Map<String, Object> analyze() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) return Map.of("error", "没有捕获到任何请求");

        // 检查缓存
        if (currentHistoryId != null) {
            HistoryRecord record = historyService.findById(currentHistoryId);
            if (record != null && record.getAiAnalysis() != null
                    && record.getAiAnalysis().containsKey("flowAnalysis")) {
                log.info("AI业务链路返回缓存: historyId={}", currentHistoryId);
                @SuppressWarnings("unchecked")
                Map<String, Object> cached = (Map<String, Object>) record.getAiAnalysis().get("flowAnalysis");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("structured", cached);
                result.put("assets", assets);
                return result;
            }
        }

        AiContext ctx = apiCaptureService.getAiContext();

        String prompt = buildAnalyzePrompt(assets, ctx != null ? ctx.getSemanticSummary() : null);
        String aiResponse;
        try {
            aiResponse = aiAnalyzeService.chat(prompt);
        } catch (Exception e) {
            log.warn("AI 链路分析调用失败: {}", e.getMessage());
            return Map.of("error", "AI 服务不可用: " + e.getMessage());
        }

        Map<String, Object> parsed = aiResultParser.parseObject(aiResponse);
        if (parsed == null) {
            parsed = Map.of("flowName", "业务链路", "description", aiResponse);
        }

        // 回填 AI 结果到 assets
        fillAiLayer(assets, parsed);

        if (ctx != null) {
            ctx.setFlowResult(parsed);
        }

        // 保存到缓存
        if (currentHistoryId != null) {
            historyService.updateAiAnalysis(currentHistoryId, "flowAnalysis", parsed);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("raw", aiResponse);
        result.put("structured", parsed);
        result.put("assets", assets);
        return result;
    }

    private String buildAnalyzePrompt(List<ApiAsset> assets, String semanticSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在分析用户在浏览器中操作时产生的 API 调用链路。\n\n");

        if (semanticSummary != null) {
            sb.append("【已有上下文（来自之前的验证阶段）】\n");
            sb.append(semanticSummary).append("\n\n");
        }

        sb.append("请分析这些 API 的顺序和逻辑关系，推断用户的操作场景（如：游客浏览帖子、用户登录、下单支付等）。\n");
        sb.append("请以 JSON 格式返回分析结果：\n");
        sb.append("{\n");
        sb.append("  \"scenario\": \"用户操作场景名称（如：游客浏览帖子、用户注册登录流程、下单支付流程等）\",\n");
        sb.append("  \"description\": \"整体流程的详细描述\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\"step\": 1, \"action\": \"操作名称（如：打开社区首页）\", \"api\": \"/api/xxx\", \"method\": \"GET\", \"description\": \"该步骤说明\", \"intent\": \"查询|提交|更新|删除\"}\n");
        sb.append("  ],\n");
        sb.append("  \"dataFlow\": [\n");
        sb.append("    {\"from\": \"源接口.response字段\", \"to\": \"目标接口.request字段\", \"value\": \"流转的数据含义\"}\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("说明：\n");
        sb.append("- scenario 描述整体场景（如「游客浏览帖子」）\n");
        sb.append("- steps 数组按用户操作顺序排列，step从1开始递增\n");
        sb.append("- api 和 method 来自下面列表中的实际接口\n");
        sb.append("- intent 为接口的业务意图：查询(query)、提交(submit)、更新(update)、删除(delete)\n");
        sb.append("- 只返回 JSON，不要多余内容\n\n");

        sb.append("请求列表（按捕获顺序）：\n");
        for (int i = 0; i < assets.size(); i++) {
            ApiAsset a = assets.get(i);
            sb.append("[").append(i).append("] ")
                    .append(a.getMethod()).append(" ").append(a.getUrl())
                    .append(" status=").append(a.getStatusCode())
                    .append(" domain=").append(a.getDomain())
                    .append(" resource=").append(a.getResource())
                    .append(" category=").append(a.getCategory())
                    .append(" tags=").append(a.getRuleTags());
            Object reqBody = a.getRequest().get("body");
            if (reqBody != null) {
                String s = reqBody.toString();
                if (s.length() > 150) s = s.substring(0, 150) + "...";
                sb.append(" req=").append(s);
            }
            Object resBody = a.getResponse().get("body");
            if (resBody != null) {
                String s = resBody.toString();
                if (s.length() > 150) s = s.substring(0, 150) + "...";
                sb.append(" res=").append(s);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void fillAiLayer(List<ApiAsset> assets, Map<String, Object> parsed) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aiAssets = (List<Map<String, Object>>) parsed.get("assets");
        if (aiAssets == null) return;

        for (Map<String, Object> ai : aiAssets) {
            Object rawIndex = ai.getOrDefault("index", -1);
            int idx = rawIndex instanceof Number ? ((Number) rawIndex).intValue()
                    : Integer.parseInt(rawIndex.toString());
            if (idx >= 0 && idx < assets.size()) {
                ApiAsset a = assets.get(idx);
                if (ai.get("businessStep") != null) a.setBusinessStep((String) ai.get("businessStep"));
                if (ai.get("intent") != null) a.setIntent((String) ai.get("intent"));
                if (ai.get("confidence") != null) {
                    a.setConfidence(((Number) ai.get("confidence")).floatValue());
                }
            }
        }
    }

    // ==================== 生成接口文档（Markdown）====================

    @GetMapping("/capture/docs")
    public ResponseEntity<String> docs() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return ResponseEntity.ok("暂无捕获的接口数据");
        }
        String markdown = buildApiDocs(assets);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/markdown;charset=UTF-8")
                .body(markdown);
    }

    private String buildApiDocs(List<ApiAsset> assets) {
        StringBuilder md = new StringBuilder();
        md.append("# API 接口文档\n\n");
        md.append("> 录制地址: ").append(assets.get(0).getPageUrl()).append("\n");
        md.append("> Session: ").append(assets.get(0).getSessionId()).append("\n");
        md.append("> 共 ").append(assets.size()).append(" 个接口\n\n---\n\n");

        Map<String, List<ApiAsset>> grouped = assets.stream()
                .filter(a -> a.getDomain() != null)
                .collect(Collectors.groupingBy(ApiAsset::getDomain,
                        LinkedHashMap::new, Collectors.toList()));

        int idx = 1;
        for (Map.Entry<String, List<ApiAsset>> entry : grouped.entrySet()) {
            md.append("## ").append(entry.getKey()).append("\n\n");
            for (ApiAsset a : entry.getValue()) {
                StringBuilder title = new StringBuilder();
                title.append(idx++).append(". ").append(a.getMethod()).append(" `").append(a.getUrl()).append("`");
                if (a.getBusinessStep() != null) title.append(" — *").append(a.getBusinessStep()).append("*");
                md.append("### ").append(title).append("\n\n");

                md.append("| 属性 | 值 |\n|------|----|\n");
                md.append("| 状态码 | ").append(a.getStatusCode()).append(" |\n");
                md.append("| 领域 | ").append(a.getDomain()).append(" |\n");
                md.append("| 资源 | ").append(a.getResource()).append(" |\n");
                if (a.getIntent() != null) md.append("| 意图 | ").append(a.getIntent()).append(" |\n");
                if (a.getConfidence() != null) md.append("| AI 置信度 | ").append(String.format("%.0f%%", a.getConfidence() * 100)).append(" |\n");
                if (a.getMergedFrom() != null && !a.getMergedFrom().isEmpty()) {
                    md.append("| 重复合并 | ").append(a.getMergedFrom().size()).append(" 次 |\n");
                }
                md.append("\n");

                Map<String, Object> req = a.getRequest();
                if (req.containsKey("headers") && !req.get("headers").toString().equals("{}")) {
                    md.append("**请求头:**\n```json\n").append(toJson(req.get("headers"))).append("\n```\n\n");
                }
                if (req.containsKey("body")) {
                    md.append("**请求体:**\n```json\n").append(formatBody(req.get("body"))).append("\n```\n\n");
                }
                Map<String, Object> res = a.getResponse();
                if (res.containsKey("headers") && !res.get("headers").toString().equals("{}")) {
                    md.append("**响应头:**\n```json\n").append(toJson(res.get("headers"))).append("\n```\n\n");
                }
                if (res.containsKey("body")) {
                    md.append("**响应体:**\n```json\n").append(formatBody(res.get("body"))).append("\n```\n\n");
                }
                md.append("---\n\n");
            }
        }
        return md.toString();
    }

    // ==================== 导出 Postman/Apifox（自动提取环境变量）====================

    @GetMapping("/capture/export/postman")
    public ResponseEntity<Map<String, Object>> exportPostman() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        Map<String, Object> collection = buildPostmanCollection(assets);
        return ResponseEntity.ok(collection);
    }

    /** Apifox 格式导出 — Postman Collection v2.1 格式，Apifox 原生支持导入 */
    @GetMapping("/capture/export/apifox")
    public ResponseEntity<Map<String, Object>> exportApifox() {
        List<ApiAsset> allAssets = apiCaptureService.getLastResult();

        // 优先使用 AI 去重结果（如果有）
        if (lastDedupResult != null && !lastDedupResult.getApis().isEmpty()) {
            Map<String, Object> collection = buildApifoxCollectionFromDedup(
                    apiCaptureService.getMainDomain(), lastDedupResult.getApis(), allAssets);
            return ResponseEntity.ok(collection);
        }

        if (allAssets == null || allAssets.isEmpty()) {
            return ResponseEntity.ok(buildApifoxCollection(null, List.of()));
        }

        // 全部接口导出（不做 AI category 过滤，用户导入后再自行整理）
        String mainDomain = apiCaptureService.getMainDomain();
        Set<String> seen = new HashSet<>();
        List<ApiAsset> exportAssets = new ArrayList<>();

        for (ApiAsset a : allAssets) {
            String dedupKey = a.getMethod() + " " + a.getResource();
            if (!seen.add(dedupKey)) continue;
            exportAssets.add(a);
        }

        Map<String, Object> collection = buildApifoxCollection(mainDomain, exportAssets);
        return ResponseEntity.ok(collection);
    }

    /** 从 AI 去重结果构建 Apifox Collection（含环境变量 + 中文分组 + Pre-request Script） */
    private Map<String, Object> buildApifoxCollectionFromDedup(String mainDomain,
                                                                List<Map<String, Object>> dedupApis,
                                                                List<ApiAsset> allAssets) {
        Map<String, Object> collection = new LinkedHashMap<>();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", (mainDomain != null ? mainDomain : "API") + " - 业务接口");
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.put("info", info);

        // 从原始捕获数据中提取 token / userId
        Map<String, String> extractedVars = allAssets != null ? extractAllVariables(allAssets) : Map.of();

        // 环境变量（token 优先从捕获数据填充）
        List<Map<String, String>> varList = new ArrayList<>();
        varList.add(varEntry("baseUrl", "https://" + (mainDomain != null ? mainDomain : "unknown")));
        varList.add(varEntry("token", extractedVars.getOrDefault("token", "")));
        varList.add(varEntry("userId", extractedVars.getOrDefault("userId", "")));
        varList.add(varEntry("username", ""));
        varList.add(varEntry("password", ""));
        collection.put("variable", varList);

        // Pre-request Script: 自动获取 Token
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> preReqEvent = new LinkedHashMap<>();
        preReqEvent.put("listen", "prerequest");
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("type", "text/javascript");
        script.put("exec", List.of(
                "// 如果 token 为空，尝试从登录接口获取",
                "// 根据实际情况修改 loginUrl、字段名和凭证来源",
                "var token = pm.environment.get('token');",
                "if (!token) {",
                "    var loginUrl = pm.environment.get('baseUrl') + '/api/login';",
                "    var credentials = {",
                "        username: pm.environment.get('username') || 'test',",
                "        password: pm.environment.get('password') || 'test'",
                "    };",
                "    pm.sendRequest({",
                "        url: loginUrl,",
                "        method: 'POST',",
                "        header: { 'Content-Type': 'application/json' },",
                "        body: {",
                "            mode: 'raw',",
                "            raw: JSON.stringify(credentials)",
                "        }",
                "    }, function (err, res) {",
                "        if (!err) {",
                "            try {",
                "                var json = res.json();",
                "                var t = json.token || json.accessToken || (json.data && json.data.token);",
                "                if (t) {",
                "                    pm.environment.set('token', t);",
                "                    console.log('Token 已自动获取');",
                "                }",
                "            } catch(e) { console.log('解析登录响应失败: ' + e); }",
                "        } else {",
                "            console.log('登录请求失败: ' + err);",
                "        }",
                "    });",
                "}"
        ));
        preReqEvent.put("script", script);
        events.add(preReqEvent);
        collection.put("event", events);

        // 按 category 分组（中文模块名）
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> api : dedupApis) {
            String url = (String) api.getOrDefault("urlExample", "");
            String module = inferModuleFromUrl(url);
            grouped.computeIfAbsent(module, k -> new ArrayList<>()).add(api);
        }

        // 构建 ApiAsset 查找表（method + resource → ApiAsset）
        Map<String, ApiAsset> assetLookup = new LinkedHashMap<>();
        if (allAssets != null) {
            for (ApiAsset a : allAssets) {
                String res = a.getResource() != null ? a.getResource() : getPath(a.getUrl());
                String key = a.getMethod() + " " + res;
                assetLookup.putIfAbsent(key, a);
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            Map<String, Object> folder = new LinkedHashMap<>();
            folder.put("name", entry.getKey());
            List<Map<String, Object>> folderItems = new ArrayList<>();
            for (Map<String, Object> dedupApi : entry.getValue()) {
                folderItems.add(buildApifoxItemFromDedup(dedupApi, assetLookup));
            }
            folder.put("item", folderItems);
            items.add(folder);
        }
        collection.put("item", items);
        return collection;
    }

    /** 从 URL 推断所属中文模块名 */
    private String inferModuleFromUrl(String url) {
        if (url == null) return "其他";
        String lower = url.toLowerCase();
        if (lower.contains("/user/") || lower.contains("/user?")) return "用户模块";
        if (lower.contains("/login") || lower.contains("/auth/") || lower.contains("/token")) return "登录鉴权";
        if (lower.contains("/order/") || lower.contains("/pay/") || lower.contains("/payment")) return "订单支付";
        if (lower.contains("/content/") || lower.contains("/ugc/") || lower.contains("/post/") || lower.contains("/feed")) return "内容模块";
        if (lower.contains("/comment/") || lower.contains("/reply/")) return "评论模块";
        if (lower.contains("/follow") || lower.contains("/fans") || lower.contains("/relation")) return "关注模块";
        if (lower.contains("/message/") || lower.contains("/im/") || lower.contains("/chat")) return "消息模块";
        if (lower.contains("/search")) return "搜索模块";
        if (lower.contains("/commerce/") || lower.contains("/product/") || lower.contains("/shop")) return "商城模块";
        if (lower.contains("/upload") || lower.contains("/file")) return "文件模块";
        if (lower.contains("/setting") || lower.contains("/config") || lower.contains("/privacy")) return "设置模块";
        return "其他";
    }

    /** 从 AI 去重单条记录构建 Postman 条目（含请求体、请求头、响应示例） */
    private Map<String, Object> buildApifoxItemFromDedup(Map<String, Object> dedupApi,
                                                          Map<String, ApiAsset> assetLookup) {
        String method = (String) dedupApi.getOrDefault("method", "GET");
        String urlExample = (String) dedupApi.getOrDefault("urlExample", "");
        String resource = (String) dedupApi.getOrDefault("resource", "");
        String apiName = (String) dedupApi.getOrDefault("apiName", "");
        String name = !apiName.isBlank() && !apiName.startsWith("GET") && !apiName.startsWith("POST")
                ? apiName : method + " " + resource;

        // 查找原始 ApiAsset（优先按 method+resource 匹配，再按 url 路径匹配）
        ApiAsset original = null;
        if (assetLookup != null) {
            original = assetLookup.get(method + " " + resource);
            if (original == null) {
                original = assetLookup.get(method + " " + getPath(urlExample));
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("description", apiName + "\n\n资源路径: " + resource);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", method);
        request.put("description", apiName);

        // URL 对象格式（支持 {{baseUrl}} 变量替换 + query 参数解析）
        Map<String, Object> urlObj = new LinkedHashMap<>();
        urlObj.put("raw", "{{baseUrl}}" + resource);
        try {
            URI uri = URI.create(urlExample);
            String path = uri.getPath();
            if (path != null && !path.isEmpty()) {
                String[] segments = path.split("/");
                List<String> pathList = new ArrayList<>();
                for (String seg : segments) {
                    if (!seg.isEmpty()) pathList.add(seg);
                }
                if (!pathList.isEmpty()) urlObj.put("path", pathList);
            }
            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                List<Map<String, String>> qp = new ArrayList<>();
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    String val = kv.length > 1 ? kv[1] : "";
                    if (val.isEmpty()) continue; // 跳过空值，避免 400
                    qp.add(paramEntry(kv[0], val));
                }
                if (!qp.isEmpty()) urlObj.put("query", qp);
            }
        } catch (Exception ignored) {}
        request.put("url", urlObj);

        // 请求头（始终包含 Authorization: Bearer {{token}}）
        List<Map<String, String>> headerList = new ArrayList<>();
        headerList.add(headerEntry("Content-Type", "application/json"));
        boolean hasAuth = false;
        if (original != null && original.getHeaders() != null) {
            for (Map.Entry<String, String> h : original.getHeaders().entrySet()) {
                String hk = h.getKey();
                if ("host".equalsIgnoreCase(hk) || "content-length".equalsIgnoreCase(hk)) continue;
                if ("authorization".equalsIgnoreCase(hk)) {
                    hasAuth = true;
                    headerList.add(headerEntry(hk, "Bearer {{token}}"));
                } else if ("content-type".equalsIgnoreCase(hk)) {
                    continue;
                } else {
                    headerList.add(headerEntry(hk, h.getValue()));
                }
            }
        }
        if (!hasAuth) {
            headerList.add(headerEntry("Authorization", "Bearer {{token}}"));
        }
        request.put("header", headerList);

        // 请求体（从原始捕获数据还原）
        if (original != null) {
            Object reqBody = original.getRequest().get("body");
            if (reqBody != null && !reqBody.toString().isBlank() && !"null".equals(reqBody.toString())) {
                Map<String, Object> bodyObj = new LinkedHashMap<>();
                bodyObj.put("mode", "raw");
                try {
                    Object parsed = objectMapper.readValue(reqBody.toString().replace('\n', ' ').trim(), Object.class);
                    bodyObj.put("raw", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed));
                } catch (Exception e) {
                    bodyObj.put("raw", reqBody.toString());
                }
                Map<String, Object> options = new LinkedHashMap<>();
                options.put("raw", Map.of("language", "json"));
                bodyObj.put("options", options);
                request.put("body", bodyObj);
            }
        }

        item.put("request", request);

        // 响应示例（code/data/message 结构，优先使用真实响应体）
        List<Map<String, Object>> responses = new ArrayList<>();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "成功响应");
        resp.put("code", 200);
        resp.put("status", "OK");
        Map<String, Object> respBody = new LinkedHashMap<>();
        respBody.put("mode", "raw");
        if (original != null) {
            Map<String, Object> example = buildResponseExample(original);
            try {
                respBody.put("raw", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example));
            } catch (Exception e) {
                respBody.put("raw", "{\"code\": 200, \"message\": \"操作成功\", \"data\": {}}");
            }
        } else {
            respBody.put("raw", "{\"code\": 200, \"message\": \"操作成功\", \"data\": {}}");
        }
        resp.put("body", respBody);
        responses.add(resp);
        item.put("response", responses);

        return item;
    }

    /** 从历史记录构建 Apifox Collection（用于 Apifox 导入） */
    private Map<String, Object> buildApifoxCollectionFromHistory(HistoryRecord record) {
        String baseUrl = "https://" + (record.getMainDomain() != null ? record.getMainDomain() : "unknown");
        String name = (record.getTitle() != null ? record.getTitle() : "历史记录") + " - Apifox导出";

        Map<String, Object> collection = new LinkedHashMap<>();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", name);
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.put("info", info);

        // 环境变量 - 优先使用历史记录中保存的 envVars
        List<Map<String, String>> varList = new ArrayList<>();
        varList.add(varEntry("baseUrl", baseUrl));
        if (record.getEnvVars() != null && !record.getEnvVars().isEmpty()) {
            for (Map.Entry<String, String> e : record.getEnvVars().entrySet()) {
                varList.add(varEntry(e.getKey(), e.getValue() != null ? e.getValue() : ""));
            }
        } else {
            varList.add(varEntry("token", ""));
            varList.add(varEntry("userId", ""));
        }
        collection.put("variable", varList);

        // 优先使用完整资产（ApiAsset），降级到精简列表（ApiSummary）
        List<ApiAsset> assets = record.getAssets();
        List<ApiSummary> apis = record.getApis();
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();

        if (assets != null && !assets.isEmpty()) {
            // 完整资产模式：中文名称 + 真实响应示例 + 状态码
            for (ApiAsset a : assets) {
                String resPath = a.getResource() != null ? a.getResource() : getPath(a.getUrl());
                String module = inferModuleFromUrl(resPath);
                grouped.computeIfAbsent(module, k -> new ArrayList<>())
                       .add(buildApifoxItemFromHistoryAsset(a, baseUrl));
            }
        } else if (apis != null) {
            // 降级到精简列表
            for (ApiSummary api : apis) {
                String module = inferModuleFromUrl(api.getResource());
                Map<String, Object> apiMap = new LinkedHashMap<>();
                apiMap.put("method", api.getMethod());
                apiMap.put("resource", api.getResource());
                apiMap.put("apiName", api.getApiName());
                apiMap.put("url", api.getUrl());
                apiMap.put("headers", api.getHeaders());
                apiMap.put("requestBody", api.getRequestBody());
                grouped.computeIfAbsent(module, k -> new ArrayList<>())
                       .add(buildApifoxItemFromHistoryApi(apiMap));
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            Map<String, Object> folder = new LinkedHashMap<>();
            folder.put("name", entry.getKey());
            folder.put("item", entry.getValue());
            items.add(folder);
        }
        collection.put("item", items);

        // Pre-request Script: 当存在认证接口时，生成自动获取 Token 脚本
        if (assets != null) {
            boolean hasAuth = assets.stream().anyMatch(a -> "auth".equals(a.getCategory()));
            if (hasAuth) {
                List<Map<String, Object>> events = new ArrayList<>();
                Map<String, Object> preReqEvent = new LinkedHashMap<>();
                preReqEvent.put("listen", "prerequest");
                Map<String, Object> script = new LinkedHashMap<>();
                script.put("type", "text/javascript");
                script.put("exec", List.of(
                        "// 环境变量中无 token 时，自动从登录接口获取",
                        "var token = pm.environment.get('token');",
                        "if (!token) {",
                        "    var loginUrl = pm.environment.get('baseUrl') + '/api/login';",
                        "    var credentials = {",
                        "        username: pm.environment.get('username') || 'test',",
                        "        password: pm.environment.get('password') || 'test'",
                        "    };",
                        "    pm.sendRequest({",
                        "        url: loginUrl,",
                        "        method: 'POST',",
                        "        header: { 'Content-Type': 'application/json' },",
                        "        body: {",
                        "            mode: 'raw',",
                        "            raw: JSON.stringify(credentials)",
                        "        }",
                        "    }, function (err, res) {",
                        "        if (!err) {",
                        "            try {",
                        "                var json = res.json();",
                        "                var t = json.token || json.accessToken || (json.data && json.data.token);",
                        "                if (t) {",
                        "                    pm.environment.set('token', t);",
                        "                    console.log('Token 已自动获取');",
                        "                }",
                        "            } catch(e) { console.log('解析登录响应失败: ' + e); }",
                        "        } else {",
                        "            console.log('登录请求失败: ' + err);",
                        "        }",
                        "    });",
                        "}"
                ));
                preReqEvent.put("script", script);
                events.add(preReqEvent);
                collection.put("event", events);
            }
        }

        return collection;
    }

    /**
     * 从历史记录完整资产（ApiAsset）构建 Apifox 条目。
     * 包含：中文名称、真实响应示例、状态码、请求体、query 参数。
     */
    private Map<String, Object> buildApifoxItemFromHistoryAsset(ApiAsset a, String baseUrl) {
        // 中文接口名称：优先使用 AI 识别的业务名，其次 URL 推断
        String businessName = a.getBusinessStep() != null && !a.getBusinessStep().isBlank()
                ? a.getBusinessStep() : inferExportApiName(a.getMethod(), a.getUrl());
        String resPath = a.getResource() != null ? a.getResource() : getPath(a.getUrl());
        String displayName = !businessName.equals(a.getMethod()) ? businessName : a.getMethod() + " " + resPath;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", displayName);
        item.put("description", businessName + "\n\n完整地址: " + a.getUrl());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", a.getMethod());
        request.put("description", businessName);

        // URL — {{baseUrl}} + path，含 path 参数和 query 参数
        Map<String, Object> urlObj = new LinkedHashMap<>();
        urlObj.put("raw", "{{baseUrl}}" + resPath);
        try {
            URI uri = URI.create(a.getUrl());
            // path segments
            String path = uri.getPath();
            if (path != null && !path.isEmpty()) {
                String[] segments = path.split("/");
                List<String> pathList = new ArrayList<>();
                for (String seg : segments) {
                    if (!seg.isEmpty()) pathList.add(seg);
                }
                if (!pathList.isEmpty()) urlObj.put("path", pathList);
            }
            // query params
            String q = uri.getQuery();
            if (q != null && !q.isEmpty()) {
                List<Map<String, String>> qp = new ArrayList<>();
                for (String param : q.split("&")) {
                    String[] kv = param.split("=", 2);
                    String val = kv.length > 1 ? kv[1] : "";
                    if (val.isEmpty()) continue;
                    qp.add(paramEntry(kv[0], val));
                }
                if (!qp.isEmpty()) urlObj.put("query", qp);
            }
        } catch (Exception ignored) {}
        request.put("url", urlObj);

        // 请求头
        List<Map<String, String>> headerList = new ArrayList<>();
        if (a.getHeaders() != null && !a.getHeaders().isEmpty()) {
            boolean hasAuth = false;
            for (Map.Entry<String, String> h : a.getHeaders().entrySet()) {
                String hk = h.getKey();
                if ("host".equalsIgnoreCase(hk) || "content-length".equalsIgnoreCase(hk)) continue;
                if ("authorization".equalsIgnoreCase(hk)) {
                    hasAuth = true;
                    headerList.add(headerEntry(hk, "Bearer {{token}}"));
                } else if ("content-type".equalsIgnoreCase(hk)) {
                    headerList.add(headerEntry("Content-Type", "application/json"));
                } else {
                    headerList.add(headerEntry(hk, h.getValue()));
                }
            }
            if (!hasAuth) {
                headerList.add(headerEntry("Authorization", "Bearer {{token}}"));
            }
        } else {
            headerList.add(headerEntry("Content-Type", "application/json"));
            headerList.add(headerEntry("Authorization", "Bearer {{token}}"));
        }
        request.put("header", headerList);

        // 请求体
        String reqBody = a.getRequestBody();
        if (reqBody != null && !reqBody.isBlank() && !"null".equals(reqBody)) {
            Map<String, Object> bodyObj = new LinkedHashMap<>();
            bodyObj.put("mode", "raw");
            try {
                Object parsed = objectMapper.readValue(reqBody.replace('\n', ' ').trim(), Object.class);
                bodyObj.put("raw", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed));
            } catch (Exception e) {
                bodyObj.put("raw", reqBody);
            }
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("raw", Map.of("language", "json"));
            bodyObj.put("options", options);
            request.put("body", bodyObj);
        }

        item.put("request", request);

        // 响应示例 — 使用真实响应数据
        List<Map<String, Object>> responses = new ArrayList<>();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "响应 " + a.getStatusCode());
        resp.put("code", a.getStatusCode());
        resp.put("status", String.valueOf(a.getStatusCode()));
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("mode", "raw");
        Map<String, Object> example = buildResponseExample(a);
        try {
            responseBody.put("raw", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example));
        } catch (Exception e) {
            responseBody.put("raw", "{\"code\": 200, \"message\": \"操作成功\", \"data\": {}}");
        }
        resp.put("body", responseBody);
        responses.add(resp);
        item.put("response", responses);

        return item;
    }

    /** 从历史记录单条 API（精简模式）构建 Apifox 条目 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildApifoxItemFromHistoryApi(Map<String, Object> apiMap) {
        String method = (String) apiMap.getOrDefault("method", "GET");
        String resource = (String) apiMap.getOrDefault("resource", "");
        String apiName = (String) apiMap.getOrDefault("apiName", "");
        String name = apiName != null && !apiName.isBlank() ? apiName : method + " " + resource;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("description", "历史录制接口\n\n资源路径: " + resource);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", method);

        // URL 对象格式（含 query 参数）
        String fullUrl = (String) apiMap.get("url");
        String resourcePath = resource;
        Map<String, List<Map<String, String>>> queryParams = null;
        if (fullUrl != null && !fullUrl.isBlank()) {
            try {
                URI uri = URI.create(fullUrl);
                String q = uri.getQuery();
                if (q != null && !q.isEmpty()) {
                    List<Map<String, String>> qp = new ArrayList<>();
                    for (String param : q.split("&")) {
                        String[] kv = param.split("=", 2);
                        String val = kv.length > 1 ? kv[1] : "";
                        if (val.isEmpty()) continue; // 跳过空值，避免 400
                        qp.add(paramEntry(kv[0], val));
                    }
                    if (!qp.isEmpty()) queryParams = Map.of("query", qp);
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> urlObj = new LinkedHashMap<>();
        urlObj.put("raw", "{{baseUrl}}" + resourcePath);
        if (queryParams != null) {
            urlObj.put("query", queryParams.get("query"));
        }
        request.put("url", urlObj);

        // 请求头 - 优先使用历史记录的 headers
        List<Map<String, String>> headerList = new ArrayList<>();
        Object headersObj = apiMap.get("headers");
        if (headersObj instanceof Map) {
            Map<String, String> storedHeaders = (Map<String, String>) headersObj;
            boolean hasAuth = false;
            for (Map.Entry<String, String> h : storedHeaders.entrySet()) {
                String hk = h.getKey();
                if ("host".equalsIgnoreCase(hk) || "content-length".equalsIgnoreCase(hk)) continue;
                if ("authorization".equalsIgnoreCase(hk)) {
                    hasAuth = true;
                    headerList.add(headerEntry(hk, "Bearer {{token}}"));
                } else if ("content-type".equalsIgnoreCase(hk)) {
                    headerList.add(headerEntry("Content-Type", "application/json"));
                } else {
                    headerList.add(headerEntry(hk, h.getValue()));
                }
            }
            if (!hasAuth) {
                headerList.add(headerEntry("Authorization", "Bearer {{token}}"));
            }
        } else {
            headerList.add(headerEntry("Content-Type", "application/json"));
            headerList.add(headerEntry("Authorization", "Bearer {{token}}"));
        }
        request.put("header", headerList);

        // 请求体（从历史记录还原）
        String requestBody = (String) apiMap.get("requestBody");
        if (requestBody != null && !requestBody.isBlank() && !"null".equals(requestBody)) {
            Map<String, Object> bodyObj = new LinkedHashMap<>();
            bodyObj.put("mode", "raw");
            try {
                Object parsed = objectMapper.readValue(requestBody.replace('\n', ' ').trim(), Object.class);
                bodyObj.put("raw", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed));
            } catch (Exception e) {
                bodyObj.put("raw", requestBody);
            }
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("raw", Map.of("language", "json"));
            bodyObj.put("options", options);
            request.put("body", bodyObj);
        }

        item.put("request", request);

        // 标准响应示例
        List<Map<String, Object>> responses = new ArrayList<>();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "成功响应");
        resp.put("code", 200);
        resp.put("status", "OK");
        Map<String, Object> respBody = new LinkedHashMap<>();
        respBody.put("mode", "raw");
        respBody.put("raw", "{\"code\": 200, \"message\": \"操作成功\", \"data\": {}}");
        resp.put("body", respBody);
        responses.add(resp);
        item.put("response", responses);

        return item;
    }

    /** 构建 Apifox 兼容的 Postman Collection */
    private Map<String, Object> buildApifoxCollection(String mainDomain,
                                                       List<ApiAsset> assets) {
        Map<String, Object> collection = new LinkedHashMap<>();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", (mainDomain != null ? mainDomain : "API") + " - 业务接口");
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.put("info", info);

        // 按 domain 分组
        Map<String, List<ApiAsset>> grouped = assets.stream()
                .filter(a -> a.getDomain() != null)
                .collect(Collectors.groupingBy(ApiAsset::getDomain,
                        LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, List<ApiAsset>> entry : grouped.entrySet()) {
            Map<String, Object> folder = new LinkedHashMap<>();
            folder.put("name", entry.getKey());
            folder.put("item", entry.getValue().stream()
                    .map(a -> buildApifoxItem(a))
                    .collect(Collectors.toList()));
            items.add(folder);
        }
        collection.put("item", items);
        return collection;
    }

    /** Apifox 条目 — 中文名称 + 完整响应示例 */
    private Map<String, Object> buildApifoxItem(ApiAsset a) {
        // 中文接口名称
        String apiName = inferExportApiName(a.getMethod(), a.getUrl());
        String resPath = a.getResource() != null ? a.getResource() : getPath(a.getUrl());
        String displayName = !apiName.equals(a.getMethod()) ? apiName : a.getMethod() + " " + resPath;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", displayName);
        item.put("description", apiName + "\n\n完整地址: " + a.getUrl());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", a.getMethod());
        request.put("description", apiName);

        // Apifox 兼容: url 直接使用字符串格式
        request.put("url", a.getUrl());

        // Headers
        List<Map<String, String>> headerList = new ArrayList<>();
        Map<String, String> headers = a.getHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                if ("host".equalsIgnoreCase(h.getKey())) continue;
                Map<String, String> he = new LinkedHashMap<>();
                he.put("key", h.getKey());
                he.put("value", h.getValue());
                he.put("description", "请求头");
                headerList.add(he);
            }
        }
        if (headerList.isEmpty()) {
            headerList.add(Map.of("key", "Content-Type", "value", "application/json", "description", "请求体格式"));
        }
        request.put("header", headerList);

        // Body
        Object reqBody = a.getRequest().get("body");
        if (reqBody != null && !reqBody.toString().isBlank() && !"null".equals(reqBody.toString())) {
            Map<String, Object> bodyObj = new LinkedHashMap<>();
            bodyObj.put("mode", "raw");
            bodyObj.put("raw", formatBody(reqBody));
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("raw", Map.of("language", "json"));
            bodyObj.put("options", options);
            request.put("body", bodyObj);
        }

        item.put("request", request);

        // Response — 结构化 code/data/message
        List<Map<String, Object>> responses = new ArrayList<>();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "成功响应 " + a.getStatusCode());
        resp.put("code", a.getStatusCode());
        resp.put("status", String.valueOf(a.getStatusCode()));
        Map<String, Object> bodyObj = new LinkedHashMap<>();
        bodyObj.put("mode", "raw");
        Map<String, Object> example = buildResponseExample(a);
        try {
            bodyObj.put("raw", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example));
        } catch (Exception e) {
            bodyObj.put("raw", "{\"code\": 200, \"message\": \"操作成功\", \"data\": {}}");
        }
        resp.put("body", bodyObj);
        responses.add(resp);
        item.put("response", responses);

        return item;
    }

    /** Apifox 导出（AI 去重版）— 先用 AI 合并重复接口，再导出为 Apifox 格式 */
    @PostMapping("/capture/export/apifox-dedup")
    public ResponseEntity<Map<String, Object>> exportApifoxDedup() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return ResponseEntity.ok(Map.of("total", 0, "apis", List.of()));
        }

        DedupResult dedupResult = dedupService.dedup(assets);
        String mainDomain = apiCaptureService.getMainDomain();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "AI 去重导出 - " + mainDomain);
        result.put("total", dedupResult.getTotal());
        result.put("totalRaw", dedupResult.getTotalRaw());
        result.put("apis", dedupResult.getApis());
        result.put("exportFormat", "apifox");
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> buildPostmanCollection(List<ApiAsset> assets) {
        Map<String, Object> collection = new LinkedHashMap<>();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", assets.isEmpty() ? "API 录制" : extractHost(assets.get(0).getUrl()) + " - API 录制");
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.put("info", info);

        // 自动提取环境变量
        Map<String, String> extractedVars = extractAllVariables(assets);
        List<Map<String, String>> varList = new ArrayList<>();
        extractedVars.forEach((k, v) -> varList.add(varEntry(k, v)));
        if (!assets.isEmpty()) {
            varList.add(0, varEntry("baseUrl", extractBaseUrl(assets.get(0).getUrl())));
        }
        collection.put("variable", varList);

        // 按 domain 分组
        Map<String, List<ApiAsset>> grouped = assets.stream()
                .filter(a -> a.getDomain() != null)
                .collect(Collectors.groupingBy(ApiAsset::getDomain,
                        LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, List<ApiAsset>> entry : grouped.entrySet()) {
            Map<String, Object> folder = new LinkedHashMap<>();
            folder.put("name", entry.getKey());
            folder.put("item", entry.getValue().stream()
                    .map(a -> buildPostmanItem(a, extractedVars))
                    .collect(Collectors.toList()));
            items.add(folder);
        }
        collection.put("item", items);
        return collection;
    }

    /**
     * 自动从请求中提取环境变量
     */
    private Map<String, String> extractAllVariables(List<ApiAsset> assets) {
        Map<String, String> vars = new LinkedHashMap<>();

        for (ApiAsset a : assets) {
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) a.getRequest().get("headers");
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    String key = h.getKey().toLowerCase();
                    String value = h.getValue();
                    if (key.equals("authorization") && value.toLowerCase().startsWith("bearer ")) {
                        vars.putIfAbsent("token", value.substring(7));
                    } else if (key.equals("cookie") || key.equals("set-cookie")) {
                        extractCookieVars(value, vars);
                    }
                }
            }

            // 从响应体中提取常见变量名
            Object resBody = a.getResponse().get("body");
            if (resBody != null) {
                extractResponseVars(resBody.toString(), vars);
            }
        }

        // 去重同值变量
        Map<String, String> deduped = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : vars.entrySet()) {
            boolean isDuplicate = deduped.values().stream()
                    .anyMatch(v -> v.equals(e.getValue()));
            if (!isDuplicate) deduped.put(e.getKey(), e.getValue());
        }
        return deduped;
    }

    private void extractCookieVars(String cookieValue, Map<String, String> vars) {
        for (String part : cookieValue.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[1].length() > 8 && !kv[1].matches("\\d+")) {
                String key = kv[0].trim().toLowerCase();
                if (key.contains("session") || key.contains("token") || key.contains("auth") || key.contains("sid")) {
                    vars.putIfAbsent(key, kv[1].trim());
                }
            }
        }
    }

    private void extractResponseVars(String body, Map<String, String> vars) {
        // 匹配常见字段: "token": "xxx", "userId": "123", "orderId": "456"
        String[] patterns = {"token", "accessToken", "refreshToken", "userId", "uid", "orderId",
                "sessionId", "sid", "apiKey", "appId"};
        for (String field : patterns) {
            Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]{4,})\"").matcher(body);
            if (m.find()) {
                vars.putIfAbsent(field, m.group(1));
            }
        }
    }

    private Map<String, Object> buildPostmanItem(ApiAsset a, Map<String, String> envVars) {
        Map<String, Object> item = new LinkedHashMap<>();
        String apiName = inferExportApiName(a.getMethod(), a.getUrl());
        String resPath = getPath(a.getUrl());
        String displayName = !apiName.equals(a.getMethod()) ? apiName : a.getMethod() + " " + resPath;
        if (a.getBusinessStep() != null) displayName += " (" + a.getBusinessStep() + ")";
        item.put("name", displayName);
        item.put("description", apiName + "\n\n完整地址: " + a.getUrl());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", a.getMethod());
        request.put("description", apiName);
        request.put("url", buildUrlObj(a.getUrl(), envVars));

        // Headers（自动替换变量）
        List<Map<String, String>> headerList = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) a.getRequest().get("headers");
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                headerList.add(headerEntry(h.getKey(), replaceWithVars(h.getValue(), envVars)));
            }
        }
        request.put("header", headerList);

        Object body = a.getRequest().get("body");
        if (body != null) {
            Map<String, Object> bodyObj = new LinkedHashMap<>();
            bodyObj.put("mode", "raw");
            bodyObj.put("raw", replaceWithVars(formatBody(body), envVars));
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("raw", Map.of("language", "json"));
            bodyObj.put("options", options);
            request.put("body", bodyObj);
        }

        item.put("request", request);

        // Response — 结构化 code/data/message
        List<Map<String, Object>> responses = new ArrayList<>();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "成功响应");
        resp.put("status", String.valueOf(a.getStatusCode()));
        resp.put("code", a.getStatusCode());
        Map<String, Object> example = buildResponseExample(a);
        try {
            resp.put("body", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example));
        } catch (Exception e) {
            resp.put("body", "{\"code\": 200, \"message\": \"ok\"}");
        }
        responses.add(resp);
        item.put("response", responses);

        return item;
    }

    private String replaceWithVars(String value, Map<String, String> vars) {
        if (value == null) return null;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            if (e.getValue() != null && e.getValue().length() > 4) {
                value = value.replace(e.getValue(), "{{" + e.getKey() + "}}");
            }
        }
        return value;
    }

    private Map<String, Object> buildUrlObj(String fullUrl, Map<String, String> envVars) {
        Map<String, Object> urlObj = new LinkedHashMap<>();
        try {
            URI uri = URI.create(fullUrl);
            urlObj.put("protocol", uri.getScheme() != null ? uri.getScheme() : "https");
            urlObj.put("host", List.of("{{baseUrl}}"));
            String path = uri.getPath();
            if (path != null && !path.isEmpty()) {
                String[] segments = path.split("/");
                List<String> pathList = new ArrayList<>();
                for (String seg : segments) {
                    if (!seg.isEmpty()) pathList.add(seg);
                }
                if (!pathList.isEmpty()) {
                    urlObj.put("path", pathList);
                }
            }
            // 过滤空值 query 参数，避免服务器 400
            String query = uri.getQuery();
            String cleanQuery = "";
            if (query != null && !query.isEmpty()) {
                List<Map<String, String>> qp = new ArrayList<>();
                StringBuilder qb = new StringBuilder();
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    String val = kv.length > 1 ? kv[1] : "";
                    if (val.isEmpty()) continue;
                    qp.add(paramEntry(kv[0], val));
                    if (qb.length() > 0) qb.append("&");
                    qb.append(kv[0]).append("=").append(val);
                }
                if (!qp.isEmpty()) {
                    urlObj.put("query", qp);
                    cleanQuery = qb.toString();
                }
            }
            urlObj.put("raw", "{{baseUrl}}" + (path != null ? path : "")
                    + (cleanQuery.isEmpty() ? "" : "?" + cleanQuery));
        } catch (Exception e) {
            urlObj.put("raw", fullUrl);
        }
        return urlObj;
    }

    // ==================== AI 生成测试用例（继承 analyze 上下文）====================

    @PostMapping("/capture/testcases")
    public Map<String, Object> testCases() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) return Map.of("error", "没有捕获到任何请求");

        AiContext ctx = apiCaptureService.getAiContext();
        String flowCtx = "";
        if (ctx != null && ctx.isAnalyzeDone() && ctx.getFlowResult() != null) {
            try {
                flowCtx = objectMapper.writeValueAsString(ctx.getFlowResult());
            } catch (Exception ignored) {}
        }

        String prompt = buildTestCasesPrompt(assets, flowCtx);
        String aiResponse;
        try {
            aiResponse = aiAnalyzeService.chat(prompt);
        } catch (Exception e) {
            log.warn("AI 测试用例生成调用失败: {}", e.getMessage());
            return Map.of("error", "AI 服务不可用: " + e.getMessage());
        }

        List<Map<String, Object>> cases = aiResultParser.parseArray(aiResponse);
        if (ctx != null) ctx.setTestCases(cases);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("raw", aiResponse);
        result.put("testCases", cases);
        return result;
    }

    private String buildTestCasesPrompt(List<ApiAsset> assets, String flowContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在为 API 录制的接口生成测试用例。\n\n");

        if (!flowContext.isEmpty()) {
            sb.append("【业务链路上下文（从分析阶段获得）】\n");
            sb.append(flowContext).append("\n\n");
        }

        sb.append("请返回 JSON 数组，每个元素格式：\n");
        sb.append("{\n");
        sb.append("  \"api\": \"接口 URL\",\n");
        sb.append("  \"method\": \"请求方法\",\n");
        sb.append("  \"businessStep\": \"对应的业务流程步骤\",\n");
        sb.append("  \"cases\": [\n");
        sb.append("    {\"name\": \"用例名称\", \"type\": \"normal|param_error|auth_error|boundary\",\n");
        sb.append("     \"input\": {...}, \"expectStatus\": 200,\n");
        sb.append("     \"assert\": {\"statusCode\": 200, \"bodyKeys\": [\"field1\"], \"bodyType\": \"object\"},\n");
        sb.append("     \"description\": \"用例说明\"}\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("每个接口至少覆盖 4 类用例：正常、参数异常、鉴权异常、边界值。只返回 JSON 数组。\n\n");

        for (int i = 0; i < assets.size(); i++) {
            ApiAsset a = assets.get(i);
            sb.append("[").append(i).append("] ")
                    .append(a.getMethod()).append(" ").append(a.getUrl())
                    .append(" domain=").append(a.getDomain())
                    .append(" resource=").append(a.getResource());
            if (a.getBusinessStep() != null)
                sb.append(" step=").append(a.getBusinessStep());
            if (a.getIntent() != null)
                sb.append(" intent=").append(a.getIntent());
            if (a.getRuleTags() != null)
                sb.append(" tags=").append(a.getRuleTags());
            sb.append(" status=").append(a.getStatusCode());
            Object reqBody = a.getRequest().get("body");
            if (reqBody != null) {
                String s = reqBody.toString();
                if (s.length() > 200) s = s.substring(0, 200) + "...";
                sb.append(" req=").append(s);
            }
            Object resBody = a.getResponse().get("body");
            if (resBody != null) {
                String s = resBody.toString();
                if (s.length() > 200) s = s.substring(0, 200) + "...";
                sb.append(" res=").append(s);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ==================== 业务场景链路（多场景识别）====================

    /**
     * 从录制的接口中识别多个业务场景，每个场景包含有序的 API 步骤。
     *
     * 示例输出：
     * {
     *   "scenarios": [
     *     {
     *       "name": "内容详情页",
     *       "steps": [
     *         {"step": 1, "action": "查看推荐列表", "method": "GET", "resource": "/api/recommand/ugcList"},
     *         {"step": 2, "action": "查看帖子详情", "method": "GET", "resource": "/api/ugc/detail"}
     *       ]
     *     }
     *   ]
     * }
     */
    @PostMapping("/capture/scenarios")
    @SuppressWarnings("unchecked")
    public Map<String, Object> scenarios() {
        // 缓存检查：当前项目已识别过场景则直接返回
        if (currentHistoryId != null) {
            HistoryRecord record = historyService.findById(currentHistoryId);
            if (record != null && record.getScenarios() != null && !record.getScenarios().isEmpty()) {
                log.info("AI场景返回缓存: historyId={}", currentHistoryId);
                return Map.of("scenarios", record.getScenarios());
            }
        }

        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return Map.of("scenarios", List.of());
        }

        String prompt = buildScenariosPrompt(assets);
        String aiResponse;
        try {
            aiResponse = aiAnalyzeService.chat(prompt);
        } catch (Exception e) {
            log.warn("AI 场景识别调用失败: {}", e.getMessage());
            return Map.of("error", "AI 服务不可用: " + e.getMessage());
        }

        Map<String, Object> parsed = aiResultParser.parseObject(aiResponse);
        if (parsed == null || !parsed.containsKey("scenarios")) {
            return Map.of("error", "AI 返回格式异常", "raw", aiResponse);
        }

        List<Map<String, Object>> scenarios = (List<Map<String, Object>>) parsed.get("scenarios");
        if (currentHistoryId != null) {
            historyService.updateScenarios(currentHistoryId, scenarios);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarios", scenarios);
        result.put("raw", aiResponse);
        return result;
    }

    private String buildScenariosPrompt(List<ApiAsset> assets) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在分析用户在浏览器操作中产生的 API 调用记录。\n\n");
        sb.append("【任务】\n");
        sb.append("从以下 API 列表中识别出独立的业务场景，每个场景代表一个完整的用户操作流程。\n\n");
        sb.append("判断规则：\n");
        sb.append("- 同一场景的 API 在时间上连续、功能上关联\n");
        sb.append("- 不同场景之间有明显边界（如页面跳转、功能切换）\n");
        sb.append("- 场景名称用中文，简洁明确，如\"内容详情页\"、\"个人主页\"、\"点赞收藏\"、\"发布内容\"、\"用户登录\"\n");
        sb.append("- 每个场景的 steps 保持原始 API 调用顺序\n");
        sb.append("- 每个 step 的 action 描述该步骤的业务操作（中文）\n\n");
        sb.append("返回 JSON 格式：\n");
        sb.append("{\n");
        sb.append("  \"scenarios\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"内容详情页\",\n");
        sb.append("      \"description\": \"用户浏览帖子详情并进行互动\",\n");
        sb.append("      \"steps\": [\n");
        sb.append("        {\"step\": 1, \"action\": \"查看推荐列表\", \"method\": \"GET\", \"resource\": \"/api/recommand/ugcList\", \"url\": \"完整URL\"},\n");
        sb.append("        {\"step\": 2, \"action\": \"查看帖子详情\", \"method\": \"GET\", \"resource\": \"/api/ugc/detail\", \"url\": \"完整URL\"}\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("只返回 JSON，不要多余内容。\n\n");
        sb.append("API 列表（按捕获顺序）：\n");

        for (int i = 0; i < assets.size(); i++) {
            ApiAsset a = assets.get(i);
            String cat = a.getCategory();
            // 只把 business / auth 类接口传给 AI
            if (!"business".equals(cat) && !"auth".equals(cat)) continue;

            sb.append("[").append(i).append("] ")
                    .append(a.getMethod()).append(" ").append(a.getUrl())
                    .append(" status=").append(a.getStatusCode())
                    .append(" resource=").append(a.getResource())
                    .append("\n");
        }
        return sb.toString();
    }

    // ==================== JSON 工具 ====================

    private String toJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String formatBody(Object body) {
        if (body == null) return "";
        try {
            Object parsed = objectMapper.readValue(body.toString().replace('\n', ' ').trim(), Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return body.toString();
        }
    }

    private String extractHost(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return "unknown"; }
    }

    private String extractBaseUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) { return url; }
    }

    private String getPath(String url) {
        try { return URI.create(url).getPath(); } catch (Exception e) { return url; }
    }

    private Map<String, String> varEntry(String key, String value) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("key", key);
        v.put("value", value);
        return v;
    }

    private Map<String, String> headerEntry(String key, String value) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("key", key);
        h.put("value", value);
        return h;
    }

    private Map<String, String> paramEntry(String key, String value) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("key", key);
        p.put("value", value);
        return p;
    }

    // ==================== 导出增强：中文名称 + 环境变量 ====================

    /** 从 URL 推断中文接口名称，用于导出 */
    private String inferExportApiName(String method, String url) {
        if (url == null) return method;
        String lower = url.toLowerCase();

        if (lower.contains("login") || lower.contains("signin")) return "用户登录";
        if (lower.contains("register") || lower.contains("signup")) return "用户注册";
        if (lower.contains("logout") || lower.contains("signout")) return "退出登录";
        if (lower.contains("captcha")) return "获取验证码";
        if (lower.contains("refresh") || lower.contains("token")) return "刷新令牌";
        if (lower.contains("search")) return "搜索";
        if (lower.contains("follow") && (lower.contains("unfollow") || lower.contains("batch"))) return "";
        if (lower.contains("follow")) return "关注用户";
        if (lower.contains("unfollow")) return "取消关注";
        if (lower.contains("comment") || lower.contains("reply")) return "评论";
        if (lower.contains("like") || lower.contains("favorite") || lower.contains("star")) return "收藏/点赞";
        if (lower.contains("share")) return "分享";
        if (lower.contains("detail") || lower.contains("info")) return "查看详情";
        if (lower.contains("list") || lower.contains("page") || lower.contains("feed")) return "列表查询";
        if (lower.contains("create") || lower.contains("publish") || lower.contains("add")) return "创建";
        if (lower.contains("update") || lower.contains("edit") || lower.contains("modify")) return "编辑";
        if (lower.contains("delete") || lower.contains("remove")) return "删除";
        if (lower.contains("upload")) return "上传文件";
        if (lower.contains("download") || lower.contains("export")) return "下载/导出";
        if (lower.contains("setting") || lower.contains("config")) return "系统设置";
        if (lower.contains("notification") || lower.contains("notice") || lower.contains("message")) return "消息通知";
        if (lower.contains("recommend") || lower.contains("suggest") || lower.contains("hot")) return "推荐/热门";
        if (lower.contains("report") || lower.contains("stat") || lower.contains("rank")) return "数据统计";
        if (lower.contains("membership") || lower.contains("vip")) return "会员";
        if (lower.contains("points") || lower.contains("coin") || lower.contains("balance")) return "积分/余额";
        if (lower.contains("view") || lower.contains("get") || lower.contains("query")) return "查询";

        if ("GET".equalsIgnoreCase(method)) return "查询";
        if ("POST".equalsIgnoreCase(method)) return "提交";
        if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) return "更新";
        if ("DELETE".equalsIgnoreCase(method)) return "删除";
        return method;
    }

    /** 生成模拟响应示例，用于导出时 Apifox 可识别结构 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResponseExample(ApiAsset a) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("code", 200);
        example.put("message", "操作成功");
        example.put("data", null);

        Object resBody = a.getResponse().get("body");
        if (resBody != null && !resBody.toString().isBlank() && !"null".equals(resBody.toString())) {
            try {
                Object parsed = objectMapper.readValue(resBody.toString(), Object.class);
                example.put("data", parsed);
            } catch (Exception e) {
                example.put("data", resBody.toString());
            }
        }
        return example;
    }

    @GetMapping("/capture/export/environment")
    public ResponseEntity<Map<String, Object>> exportEnvironment() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        String mainDomain = apiCaptureService.getMainDomain();
        String baseUrl = "https://" + (mainDomain != null ? mainDomain : "unknown");

        Map<String, String> vars = extractAllVariables(assets);

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("name", (mainDomain != null ? mainDomain : "API") + " 环境");
        List<Map<String, Object>> values = new ArrayList<>();

        // baseUrl 总是第一个
        Map<String, Object> baseEntry = new LinkedHashMap<>();
        baseEntry.put("key", "baseUrl");
        baseEntry.put("value", baseUrl);
        baseEntry.put("type", "default");
        values.add(baseEntry);

        // 提取的变量
        for (Map.Entry<String, String> e : vars.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", e.getKey());
            entry.put("value", e.getValue());
            entry.put("type", "default");
            values.add(entry);
        }

        env.put("values", values);
        return ResponseEntity.ok(env);
    }
}
