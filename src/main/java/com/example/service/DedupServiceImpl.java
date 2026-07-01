package com.example.service;

import com.example.ai.filter.ApiPreFilter;
import com.example.ai.parse.AiResultParser;
import com.example.model.ApiAsset;
import com.example.model.DedupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DedupServiceImpl implements DedupService {

    private static final Logger log = LoggerFactory.getLogger(DedupServiceImpl.class);

    private final AiChatService aiChatService;
    private final ApiPreFilter apiPreFilter;
    private final AiResultParser aiResultParser;

    public DedupServiceImpl(AiChatService aiChatService,
                            ApiPreFilter apiPreFilter,
                            AiResultParser aiResultParser) {
        this.aiChatService = aiChatService;
        this.apiPreFilter = apiPreFilter;
        this.aiResultParser = aiResultParser;
    }

    @Override
    public DedupResult dedup(List<ApiAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return new DedupResult(List.of(), 0, false, null);
        }

        // Java 层硬过滤（图片、埋点、静态资源）
        List<ApiAsset> filtered = apiPreFilter.filter(assets);

        // 只保留 business / auth 类接口
        List<ApiAsset> businessOnly = new ArrayList<>();
        for (ApiAsset a : filtered) {
            String cat = a.getCategory();
            if ("business".equals(cat) || "auth".equals(cat)) {
                businessOnly.add(a);
            }
        }
        if (businessOnly.isEmpty()) {
            return new DedupResult(List.of(), filtered.size(), false, null);
        }

        // AI 去重
        try {
            String prompt = buildPrompt(businessOnly);
            String aiResponse = aiChatService.chat("dedup", prompt);
            List<Map<String, Object>> parsed = aiResultParser.parseArray(aiResponse);
            if (parsed != null && !parsed.isEmpty()) {
                log.info("AI 去重成功: {} 项（原始 {} 条）", parsed.size(), businessOnly.size());
                return new DedupResult(parsed, businessOnly.size(), false, aiResponse);
            }
        } catch (Exception e) {
            log.warn("AI 去重失败: {}", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }

        // 回退到机械去重
        return fallbackDedup(businessOnly, null);
    }

    private String buildPrompt(List<ApiAsset> assets) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在分析用户在一次浏览器操作中产生的 API 调用列表。\n\n");
        sb.append("【任务】\n");
        sb.append("对以下业务接口进行去重合并。\n\n");
        sb.append("合并规则（按优先级）：\n");
        sb.append("1. 仅 query 参数不同的接口合并为一个\n");
        sb.append("   例: GET /api/list?page=1 和 GET /api/list?page=2 → method=GET, resource=/api/list\n");
        sb.append("2. URL 路径中的 ID 参数（UUID/哈希/数字）视为路径参数，不同 ID 的请求合并为一个\n");
        sb.append("   例: DELETE /api/user/69cf3e7b65d7 和 DELETE /api/user/69e0b310f9da → method=DELETE, resource=/api/user/{id}\n");
        sb.append("   例: PUT /api/topics/123 和 PUT /api/topics/456 → method=PUT, resource=/api/topics/{id}\n");
        sb.append("3. 路径中嵌套 ID 同样处理\n");
        sb.append("   例: POST /api/contents/6a3e5233/status 和 POST /api/contents/6a3e5242/status → method=POST, resource=/api/contents/{id}/status\n\n");
        sb.append("返回 JSON 数组，每个元素格式：\n");
        sb.append("{\n");
        sb.append("  \"method\": \"POST\",\n");
        sb.append("  \"resource\": \"/api/follow\",\n");
        sb.append("  \"apiName\": \"关注用户\",\n");
        sb.append("  \"urlExample\": \"http://xxx/api/follow\",\n");
        sb.append("  \"callCount\": 3,\n");
        sb.append("  \"mergedUrls\": [\"http://xxx/api/follow?page=1\", \"http://xxx/api/follow?page=2\"]\n");
        sb.append("}\n\n");
        sb.append("如果列表为空，返回空数组 []。\n");
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

    private DedupResult fallbackDedup(List<ApiAsset> assets, String rawAiResponse) {
        // 先对 resource 做归一化：把路径中的 UUID/哈希片段替换为 {id}
        Map<String, ApiAsset> seen = new LinkedHashMap<>();
        Map<String, List<String>> mergedUrls = new LinkedHashMap<>();
        for (ApiAsset a : assets) {
            String res = a.getResource() != null ? a.getResource() : a.getUrl();
            String normalized = normalizeResource(res);
            String key = a.getMethod() + " " + normalized;
            seen.putIfAbsent(key, a);
            mergedUrls.computeIfAbsent(key, k -> new ArrayList<>()).add(a.getUrl());
        }
        List<Map<String, Object>> apis = new ArrayList<>();
        for (Map.Entry<String, ApiAsset> entry : seen.entrySet()) {
            ApiAsset a = entry.getValue();
            String key = entry.getKey();
            String normalizedRes = key.substring(key.indexOf(' ') + 1);
            Map<String, Object> item = new LinkedHashMap<>();
            String apiName = inferApiName(a.getMethod(), a.getUrl());
            item.put("method", a.getMethod());
            item.put("resource", normalizedRes);
            item.put("apiName", apiName);
            item.put("urlExample", a.getUrl());
            item.put("callCount", mergedUrls.get(key).size());
            if (mergedUrls.get(key).size() > 1) {
                item.put("mergedUrls", mergedUrls.get(key));
            }
            apis.add(item);
        }
        log.info("Dedup 机械去重: {} 项（原始 {} 条）", apis.size(), assets.size());
        return new DedupResult(apis, assets.size(), false, rawAiResponse);
    }

    /** 将 resource path 中的 UUID/长哈希替换为 {id}，用于机械去重 */
    private String normalizeResource(String resource) {
        if (resource == null) return null;
        // 匹配 24 位以上十六进制字符串（MongoDB ObjectId / 哈希值）
        String normalized = resource.replaceAll("/[0-9a-fA-F]{24,}", "/{id}");
        // 匹配 8-23 位十六进制（较短的哈希）
        normalized = normalized.replaceAll("/[0-9a-fA-F]{8,23}(?=/|$)", "/{id}");
        // 匹配纯数字 ID
        normalized = normalized.replaceAll("/\\d{3,}(?=/|$)", "/{id}");
        return normalized;
    }

    /** 从 URL 推断接口名称（备注），用于机械去重兜底 */
    private String inferApiName(String method, String url) {
        if (url == null) return method;
        String lower = url.toLowerCase();
        boolean isGet = "GET".equalsIgnoreCase(method);
        boolean isPost = "POST".equalsIgnoreCase(method);
        boolean isWrite = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);

        // ── 鉴权类（不分方法）──
        if (lower.contains("login") || lower.contains("signin")) return "用户登录";
        if (lower.contains("register") || lower.contains("signup")) return "用户注册";
        if (lower.contains("logout") || lower.contains("signout")) return "退出登录";
        if (lower.contains("captcha")) return "验证码";
        if (lower.contains("token") || lower.contains("refresh")) return "刷新令牌";

        // ── GET 请求默认为查询类 ──
        if (isGet) {
            if (lower.contains("search")) return "搜索";
            if (lower.contains("list") || lower.contains("page") || lower.contains("feed")) return "列表查询";
            if (lower.contains("detail") || lower.contains("info")) return "查看详情";
            if (lower.contains("profile") || lower.contains("avatar") || lower.contains("identity")
                    || lower.contains("viewUser") || lower.contains("getUser")) return "用户信息";
            if (lower.contains("membership") || lower.contains("membership") || lower.contains("coin")
                    || lower.contains("points") || lower.contains("account") || lower.contains("balance")) return "账户信息";
            if (lower.contains("hot") || lower.contains("recommend") || lower.contains("suggest")) return "推荐/热门";
            if (lower.contains("setting") || lower.contains("config")) return "系统设置";
            if (lower.contains("notification") || lower.contains("notice") || lower.contains("message")) return "消息通知";
            if (lower.contains("stat") || lower.contains("rank") || lower.contains("report")) return "数据统计";
            return "查询";
        }

        // ── 写操作 ──
        if (lower.contains("follow") || lower.contains("subscribe")) return "关注/订阅";
        if (lower.contains("unfollow") || lower.contains("unsubscribe")) return "取消关注";
        if (lower.contains("favorite") || lower.contains("like") || lower.contains("star") || lower.contains("collect")) return "收藏/点赞";
        if (lower.contains("comment") || lower.contains("reply") || lower.contains("review")) return "评论/回复";
        if (lower.contains("share")) return "分享";
        if (lower.contains("upload")) return "上传文件";
        if (lower.contains("download") || lower.contains("export")) return "导出/下载";
        if (lower.contains("delete") || lower.contains("remove") || lower.contains("cancel")) return "删除";
        if (lower.contains("update") || lower.contains("edit") || lower.contains("modify") || lower.contains("change")) return "编辑/更新";
        if (lower.contains("create") || lower.contains("add") || lower.contains("publish") || lower.contains("new")) return "创建/发布";
        if (lower.contains("generate")) return "生成";

        // ── 按 HTTP 方法兜底 ──
        if (isWrite) {
            if (lower.contains("list") || lower.contains("page") || lower.contains("detail") || lower.contains("info")) return "查询";
            if (lower.contains("setting") || lower.contains("config")) return "系统设置";
            if (lower.contains("stat") || lower.contains("report")) return "数据统计";
            return "提交";
        }
        return "查询";
    }

    /** 从多个候选字段名中提取第一个非空值 */
    private String extractField(Map<String, Object> item, String... candidates) {
        for (String key : candidates) {
            Object val = item.get(key);
            if (val != null && val instanceof String && !((String) val).isBlank()) {
                return (String) val;
            }
        }
        return null;
    }

    private static final java.util.Set<String> HTTP_METHODS = java.util.Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE"
    );

    private boolean isHttpMethod(String method) {
        return method != null && HTTP_METHODS.contains(method);
    }
}
