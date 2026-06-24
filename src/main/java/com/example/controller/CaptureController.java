package com.example.controller;

import com.example.model.AiContext;
import com.example.model.ApiAsset;
import com.example.model.BusinessFlow;
import com.example.service.ActionRecognizer;
import com.example.service.AiChatService;
import com.example.service.ApiCaptureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public CaptureController(ApiCaptureService apiCaptureService, AiChatService aiChatService) {
        this.apiCaptureService = apiCaptureService;
        this.aiChatService = aiChatService;
    }

    // ==================== 基础录制 ====================

    @PostMapping("/capture/start")
    public String start(@RequestParam String url) {
        apiCaptureService.start(url);
        return "浏览器已打开，请手动操作页面，操作完后调用 POST /capture/stop";
    }

    @PostMapping("/capture/stop")
    public List<ApiAsset> stop() {
        return apiCaptureService.stopAndFilter();
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

    // ==================== AI 业务去重 ====================

    @PostMapping("/capture/ai-dedup")
    public Map<String, Object> aiDedup() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return Map.of("total", 0, "apis", List.of());
        }

        String prompt = buildAiDedupPrompt(assets);
        String aiResponse;
        try {
            aiResponse = aiChatService.chat("ai-dedup", prompt);
        } catch (Exception e) {
            log.warn("AI 去重调用失败: {}", e.getMessage());
            return Map.of("total", 0, "error", "AI 服务不可用: " + e.getMessage());
        }

        List<Map<String, Object>> deduped = parseArrayJson(aiResponse);
        if (deduped.isEmpty()) {
            // AI 返回格式异常时，回退到按 resource 去重
            return fallbackDedup(assets);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", deduped.size());
        result.put("totalRaw", assets.size());
        result.put("apis", deduped);
        result.put("raw", aiResponse);
        return result;
    }

    private String buildAiDedupPrompt(List<ApiAsset> assets) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在分析用户在一次浏览器操作中产生的 API 调用列表。");
        sb.append("同一个业务接口因为参数不同（如分页、不同用户ID）被调用了多次，请合并去重。\n\n");
        sb.append("合并规则：\n");
        sb.append("- 相同 method + 相同 resource path 的视为同一接口，只保留一个\n");
        sb.append("- query 参数不同但 resource path 相同的接口合并（如 /api/list?page=1 和 /api/list?page=2 都是 /api/list）\n");
        sb.append("- 路径参数不同的 RESTful 接口视为同一接口（如 /api/user/1 和 /api/user/2 都是 /api/user/{id}）\n");
        sb.append("- 从 url 中提取有意义的 apiName（中文），如 /api/follow → \"关注用户\"\n\n");
        sb.append("请返回 JSON 数组，每个元素格式：\n");
        sb.append("{\n");
        sb.append("  \"method\": \"POST\",\n");
        sb.append("  \"resource\": \"/api/follow\",\n");
        sb.append("  \"apiName\": \"关注用户\",\n");
        sb.append("  \"urlExample\": \"http://xxx/api/follow\",\n");
        sb.append("  \"callCount\": 3\n");
        sb.append("}\n\n");
        sb.append("只返回 JSON 数组，不要多余内容。\n\n");
        sb.append("API 列表（按捕获顺序）：\n");

        for (int i = 0; i < assets.size(); i++) {
            ApiAsset a = assets.get(i);
            sb.append("[").append(i).append("] ")
                    .append(a.getMethod()).append(" ").append(a.getUrl())
                    .append(" status=").append(a.getStatusCode())
                    .append(" resource=").append(a.getResource())
                    .append(" category=").append(a.getCategory())
                    .append("\n");
        }
        return sb.toString();
    }

    /** AI 返回格式异常时的降级方案：按 method + resource 机械去重 */
    private Map<String, Object> fallbackDedup(List<ApiAsset> assets) {
        Map<String, ApiAsset> seen = new LinkedHashMap<>();
        for (ApiAsset a : assets) {
            String key = a.getMethod() + " " + (a.getResource() != null ? a.getResource() : a.getUrl());
            seen.putIfAbsent(key, a);
        }
        List<Map<String, Object>> apis = new ArrayList<>();
        for (ApiAsset a : seen.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("method", a.getMethod());
            item.put("resource", a.getResource());
            item.put("apiName", inferScene(a));
            item.put("urlExample", a.getUrl());
            apis.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", apis.size());
        result.put("totalRaw", assets.size());
        result.put("apis", apis);
        result.put("fallback", true);
        return result;
    }

    // ==================== AI 业务链路分析 ====================

    @PostMapping("/capture/analyze")
    public Map<String, Object> analyze() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) return Map.of("error", "没有捕获到任何请求");

        AiContext ctx = apiCaptureService.getAiContext();

        String prompt = buildAnalyzePrompt(assets, ctx != null ? ctx.getSemanticSummary() : null);
        String aiResponse;
        try {
            aiResponse = aiChatService.chat("analyze", prompt);
        } catch (Exception e) {
            log.warn("AI 链路分析调用失败: {}", e.getMessage());
            return Map.of("error", "AI 服务不可用: " + e.getMessage());
        }

        Map<String, Object> parsed = parseStructuredJson(aiResponse);
        if (parsed == null) {
            parsed = Map.of("flowName", "业务链路", "description", aiResponse);
        }

        // 回填 AI 结果到 assets
        fillAiLayer(assets, parsed);

        if (ctx != null) {
            ctx.setFlowResult(parsed);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("raw", aiResponse);
        result.put("structured", parsed);
        result.put("assets", assets); // 回传含 AI 字段的完整资产
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

    /** Apifox 格式导出 — 轻量接口资产清单，可直接粘贴导入 Apifox */
    @GetMapping("/capture/export/apifox")
    public ResponseEntity<Map<String, Object>> exportApifox() {
        List<ApiAsset> allAssets = apiCaptureService.getLastResult();
        if (allAssets == null || allAssets.isEmpty()) {
            return ResponseEntity.ok(Map.of("total", 0, "apis", List.of()));
        }

        // 只取 business + auth，去重
        String mainDomain = apiCaptureService.getMainDomain();
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> apis = new ArrayList<>();

        for (ApiAsset a : allAssets) {
            String cat = a.getCategory();
            if (!"business".equals(cat) && !"auth".equals(cat)) continue;
            String dedupKey = a.getMethod() + " " + a.getResource();
            if (!seen.add(dedupKey)) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("method", a.getMethod());
            item.put("url", a.getUrl());
            item.put("scene", inferScene(a));
            apis.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "API Assets - " + mainDomain);
        result.put("total", apis.size());
        result.put("apis", apis);
        return ResponseEntity.ok(result);
    }

    /** Apifox 导出（AI 去重版）— 先用 AI 合并重复接口，再导出为 Apifox 格式 */
    @PostMapping("/capture/export/apifox-dedup")
    public ResponseEntity<Map<String, Object>> exportApifoxDedup() {
        List<ApiAsset> assets = apiCaptureService.getLastResult();
        if (assets == null || assets.isEmpty()) {
            return ResponseEntity.ok(Map.of("total", 0, "apis", List.of()));
        }

        String prompt = buildAiDedupPrompt(assets);
        String aiResponse;
        try {
            aiResponse = aiChatService.chat("ai-dedup-export", prompt);
        } catch (Exception e) {
            log.warn("AI 去重导出调用失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("total", 0, "error", "AI 服务不可用: " + e.getMessage()));
        }

        List<Map<String, Object>> deduped = parseArrayJson(aiResponse);
        if (deduped.isEmpty()) {
            // AI 失败时回退到机械去重
            Map<String, ApiAsset> seen = new LinkedHashMap<>();
            for (ApiAsset a : assets) {
                String key = a.getMethod() + " " + (a.getResource() != null ? a.getResource() : a.getUrl());
                seen.putIfAbsent(key, a);
            }
            for (ApiAsset a : seen.values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("method", a.getMethod());
                item.put("url", a.getUrl());
                item.put("apiName", inferScene(a));
                deduped.add(item);
            }
        }

        String mainDomain = apiCaptureService.getMainDomain();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "AI 去重导出 - " + mainDomain);
        result.put("total", deduped.size());
        result.put("totalRaw", assets.size());
        result.put("apis", deduped);
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
        Map<String, Object> varObj = new LinkedHashMap<>();
        varObj.put("variable", varList);
        collection.put("variable", varObj);

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
        String itemName = a.getMethod() + " " + getPath(a.getUrl());
        if (a.getBusinessStep() != null) itemName += " (" + a.getBusinessStep() + ")";
        item.put("name", itemName);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", a.getMethod());
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

        // Response
        List<Map<String, Object>> responses = new ArrayList<>();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("name", "Response " + a.getStatusCode());
        resp.put("status", String.valueOf(a.getStatusCode()));
        resp.put("code", a.getStatusCode());
        Object resBody = a.getResponse().get("body");
        if (resBody != null) resp.put("body", formatBody(resBody));
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
            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                List<Map<String, String>> qp = new ArrayList<>();
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    qp.add(paramEntry(kv[0], kv.length > 1 ? kv[1] : ""));
                }
                urlObj.put("query", qp);
            }
            urlObj.put("raw", "{{baseUrl}}" + (path != null ? path : "") + (query != null ? "?" + query : ""));
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
            aiResponse = aiChatService.chat("testcases", prompt);
        } catch (Exception e) {
            log.warn("AI 测试用例生成调用失败: {}", e.getMessage());
            return Map.of("error", "AI 服务不可用: " + e.getMessage());
        }

        List<Map<String, Object>> cases = parseArrayJson(aiResponse);
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

    // ==================== JSON 解析工具 ====================

    private Map<String, Object> parseStructuredJson(String aiResponse) {
        try {
            String json = cleanJson(aiResponse);
            json = json.replaceAll("^[^{]*", "").replaceAll("[^}]*$", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> parseArrayJson(String aiResponse) {
        try {
            String json = cleanJson(aiResponse);
            json = json.replaceAll("^[^\\[]*", "").replaceAll("[^\\]]*$", "");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = objectMapper.readValue(json, List.class);
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String cleanJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("```\\w*", "").replace("```", "").trim();
        }
        return s;
    }



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
}
