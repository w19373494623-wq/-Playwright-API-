package com.example.smoke.service;

import com.example.smoke.assertion.Assertion;
import com.example.smoke.assertion.DefaultAssertion;
import com.example.smoke.executor.DefaultHttpExecutor;
import com.example.smoke.executor.HttpExecutor;
import com.example.smoke.model.PostmanCollection;
import com.example.smoke.model.PostmanItem;
import com.example.smoke.model.SmokeTestReport;
import com.example.smoke.model.SmokeTestResult;
import com.example.smoke.parser.CollectionParser;
import com.example.smoke.report.HtmlReportGenerator;
import com.example.smoke.report.JsonReportGenerator;
import com.example.smoke.report.ReportGenerator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 烟雾测试服务：编排 解析 → 执行 → 断言 → 报告 全流程。
 *
 * 扩展点：
 * - 可替换 HttpExecutor 实现（如代理、重试）
 * - 可替换 Assertion 实现（如自定义业务断言）
 * - 可替换 ReportGenerator 实现（如 Allure 报告）
 * - 预留 Token 自动刷新、变量提取等钩子
 */
@Service
public class SmokeTestService {

    private static final Logger log = LoggerFactory.getLogger(SmokeTestService.class);

    private static final Path REPORTS_DIR = Paths.get("storage", "smoke-reports");

    private final CollectionParser parser;
    private final HttpExecutor executor;
    private final Assertion assertion;
    private final ReportGenerator jsonReportGenerator;
    private final ReportGenerator htmlReportGenerator;

    private final Map<String, SmokeTestReport> savedReports = new LinkedHashMap<>();
    private String latestReportId;

    public SmokeTestService() {
        this.parser = new CollectionParser();
        this.executor = new DefaultHttpExecutor();
        this.assertion = new DefaultAssertion();
        this.jsonReportGenerator = new JsonReportGenerator();
        this.htmlReportGenerator = new HtmlReportGenerator();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(REPORTS_DIR);
            log.info("烟雾测试报告存储目录: {}", REPORTS_DIR.toAbsolutePath());
        } catch (IOException e) {
            log.error("创建烟雾测试报告目录失败", e);
        }
    }

    /**
     * 完整构造器（方便扩展替换实现）
     */
    public SmokeTestService(CollectionParser parser,
                             HttpExecutor executor,
                             Assertion assertion,
                             ReportGenerator jsonReportGenerator,
                             ReportGenerator htmlReportGenerator) {
        this.parser = parser;
        this.executor = executor;
        this.assertion = assertion;
        this.jsonReportGenerator = jsonReportGenerator;
        this.htmlReportGenerator = htmlReportGenerator;
    }

    /**
     * 执行烟雾测试。
     *
     * @param collectionJson Postman Collection v2.1 JSON 字符串
     * @return 测试报告
     */
    public SmokeTestReport runSmokeTest(String collectionJson) {
        return runSmokeTest(collectionJson, null);
    }

    /**
     * 执行烟雾测试，支持每条接口执行后的回调。
     * <p>
     * 回调接收参数：(item, result, variables)
     * <ul>
     *   <li>item — 当前执行的接口条目</li>
     *   <li>result — 执行结果（含响应 body）</li>
     *   <li>variables — 可变变量列表，回调可新增变量供后续接口使用</li>
     * </ul>
     *
     * @param collectionJson Postman Collection v2.1 JSON 字符串
     * @param itemCallback   执行回调（可为 null）
     * @return 测试报告
     */
    public SmokeTestReport runSmokeTest(String collectionJson,
                                         ItemExecutionCallback itemCallback) {
        log.info("========================================");
        log.info("  烟雾测试开始");
        log.info("========================================");

        SmokeTestReport report = new SmokeTestReport();

        try {
            // 1. 解析
            PostmanCollection collection = parser.parseJson(collectionJson);
            String name = collection.getInfo() != null ? collection.getInfo().getName() : "未命名集合";
            report.setCollectionName(name);
            log.info("集合: {}", name);

            // 2. 展平条目
            List<PostmanItem> items = parser.flattenItems(collection);
            log.info("共 {} 个接口", items.size());

            // 3. 接口排序：认证优先 > 创建 > 修改 > 查询 > 其他
            sortItems(items);

            // 4. 提取变量（使用可变列表，支持回调新增变量）
            List<Map<String, String>> variables = new ArrayList<>();
            if (collection.getVariable() != null) {
                variables.addAll(collection.getVariable());
            }

            // 5. 逐条执行
            int index = 1;
            for (PostmanItem item : items) {
                log.info("[{}/{}] {} - {}", index, items.size(), item.getName(),
                        item.getRequest() != null ? item.getRequest().getMethod() : "N/A");

                // 执行 HTTP 请求
                SmokeTestResult result = executor.execute(item, variables);

                // 执行断言
                boolean passed = assertion.assertApi(item, result);
                result.setPassed(passed);

                if (passed) {
                    log.info("  ✓ PASS ({}ms)", result.getDurationMs());
                } else {
                    log.warn("  ✗ FAIL - {}", result.getFailureReason());
                }

                // 自动提取变量：将响应中提取的变量注入后续接口
                if (result.getExtractedVariables() != null && !result.getExtractedVariables().isEmpty()) {
                    for (Map.Entry<String, String> entry : result.getExtractedVariables().entrySet()) {
                        String varName = entry.getKey();
                        String varValue = entry.getValue();
                        // 检查变量是否已存在，不存在则新增
                        boolean exists = variables.stream().anyMatch(v -> varName.equals(v.get("key")));
                        if (!exists) {
                            Map<String, String> newVar = new LinkedHashMap<>();
                            newVar.put("key", varName);
                            newVar.put("value", varValue);
                            variables.add(newVar);
                            log.info("  → 自动提取变量: {} = {}", varName,
                                    varValue.length() > 32 ? varValue.substring(0, 32) + "..." : varValue);
                        }
                    }
                }

                // 回调：可提取变量、新增到 variables 列表
                if (itemCallback != null) {
                    itemCallback.afterItem(item, result, variables);
                }

                report.getResults().add(result);
                index++;
            }

            // 6. 汇总统计
            report.summarize();

        } catch (Exception e) {
            log.error("烟雾测试执行异常", e);
            report.setErrorMessage("执行异常: " + e.getMessage());
        }

        // 7. 打印统计
        log.info("----------------------------------------");
        log.info("  完成: {} 通过 / {} 失败 / {} 总计  (成功率 {}%)",
                report.getPassed(), report.getFailed(), report.getTotal(), String.format("%.1f", report.getSuccessRate()));
        log.info("  总耗时: {}ms", report.getTotalDurationMs());
        log.info("========================================");

        this.latestReportId = persistReport(report);
        return report;
    }

    /**
     * 接口排序：认证类优先执行，保证 token/userId 等变量先就绪。
     * <p>
     * 排序优先级（从高到低）：
     * <ol>
     *   <li>认证类 — login, register, token, oauth, captcha, forgotPassword, resetPassword, verify</li>
     *   <li>创建类 — create, add, publish, upload, set</li>
     *   <li>修改类 — update, edit, modify, delete, remove, change</li>
     *   <li>查询类 — list, page, detail, search, get, query, find</li>
     *   <li>其他</li>
     * </ol>
     * 相同优先级内保持原始顺序。
     */
    static void sortItems(List<PostmanItem> items) {
        if (items == null || items.size() <= 1) return;

        // 预先计算每个 item 的 URL 字符串（避免 sort 中重复解析）
        Map<PostmanItem, String> urlCache = new LinkedHashMap<>();
        for (PostmanItem item : items) {
            urlCache.put(item, extractUrl(item));
        }

        items.sort(Comparator.comparingInt((PostmanItem a) -> {
            String url = urlCache.get(a);
            String name = a.getName() != null ? a.getName().toLowerCase() : "";
            String method = a.getRequest() != null && a.getRequest().getMethod() != null
                    ? a.getRequest().getMethod().toUpperCase() : "";

            int p = priority(url, name, method);
            log.debug("  sort: [{}] {} {} -> {}", p, method, name, url);
            return p;
        }).thenComparingInt(items::indexOf)); // 同优先级保持原序
    }

    /** 提取 PostmanItem 的 URL 字符串 */
    private static String extractUrl(PostmanItem item) {
        if (item == null || item.getRequest() == null) return "";
        Object urlObj = item.getRequest().getUrl();
        if (urlObj == null) return "";
        if (urlObj instanceof String) return (String) urlObj;
        if (urlObj instanceof Map) {
            Object raw = ((Map<?, ?>) urlObj).get("raw");
            return raw != null ? raw.toString() : "";
        }
        return urlObj.toString();
    }

    /** 计算优先级：0=认证 1=创建 2=修改 3=查询 4=其他 */
    private static int priority(String url, String name, String method) {
        String lower = (url + " " + name).toLowerCase();

        // 认证类 — 最高优先级
        if (containsAny(lower, "/login", "/signin", "/signup", "/register",
                "/token", "/oauth", "/captcha",
                "/forgotpassword", "/resetpassword", "/verify",
                "forgotPassword", "resetPassword",
                // HTTP 基本认证 / 登录语义
                "login", "signin", "signup", "register")) {
            return 0;
        }

        // 创建类
        if (containsAny(lower, "/create", "/add", "/publish", "/upload",
                "/set", "create", "新增", "创建", "发布", "上传")) {
            return 1;
        }

        // 修改类
        if ("PUT".equals(method) || "PATCH".equals(method)) return 2;
        if (containsAny(lower, "/update", "/edit", "/modify", "/delete",
                "/remove", "/change",
                "update", "edit", "modify", "delete", "remove", "change",
                "编辑", "修改", "删除")) {
            return 2;
        }

        // 查询类
        if ("GET".equals(method)) return 3;
        if (containsAny(lower, "/list", "/page", "/detail", "/search",
                "/get", "/query", "/find",
                "list", "page", "detail", "search", "get", "query", "find",
                "列表", "详情", "搜索", "查询")) {
            return 3;
        }

        return 4;
    }

    private static boolean containsAny(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 持久化报告到磁盘文件。
     */
    private String persistReport(SmokeTestReport report) {
        String id = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())
                + "-" + report.getPassed() + "p" + report.getFailed() + "f";
        try {
            // 保存 JSON
            Path jsonFile = REPORTS_DIR.resolve(id + ".json");
            String json = jsonReportGenerator.generate(report);
            Files.writeString(jsonFile, json, StandardCharsets.UTF_8);

            // 保存 HTML
            Path htmlFile = REPORTS_DIR.resolve(id + ".html");
            String html = htmlReportGenerator.generate(report);
            Files.writeString(htmlFile, html, StandardCharsets.UTF_8);

            // 内存缓存
            savedReports.put(id, report);

            log.info("烟雾测试报告已保存: {}/{} (json), {}/{} (html)",
                    jsonFile.getFileName(), json.length(),
                    htmlFile.getFileName(), html.length());
        } catch (IOException e) {
            log.error("保存烟雾测试报告失败", e);
        }
        return id;
    }

    /**
     * 获取最后一次测试报告
     */
    public SmokeTestReport getLastReport() {
        SmokeTestReport report = null;
        if (latestReportId != null) {
            report = savedReports.get(latestReportId);
        }
        // 如果内存中没有但 latestReportId 有值，尝试从文件加载
        if (report == null && latestReportId != null) {
            report = loadReportFromFile(latestReportId);
            if (report != null) savedReports.put(latestReportId, report);
        }
        return report;
    }

    /**
     * 获取最新报告的 ID
     */
    public String getLatestReportId() {
        return latestReportId;
    }

    /**
     * 获取所有已保存报告的列表（摘要）
     */
    public List<Map<String, Object>> listReports() {
        List<Map<String, Object>> list = new ArrayList<>();
        File dir = REPORTS_DIR.toFile();
        if (!dir.exists()) return list;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return list;
        for (File f : files) {
            String name = f.getName();
            String id = name.substring(0, name.length() - 5); // 去掉 .json
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("file", name);
            entry.put("path", "/smoke/report/file/" + id + ".html");
            // 读取时间信息
            String[] parts = id.split("-");
            if (parts.length >= 2) {
                entry.put("date", parts[0] + " " + parts[1].substring(0, Math.min(6, parts[1].length())));
            }
            list.add(entry);
        }
        list.sort((a, b) -> ((String) b.get("id")).compareTo((String) a.get("id")));
        return list;
    }

    /**
     * 从文件加载报告
     */
    private SmokeTestReport loadReportFromFile(String reportId) {
        try {
            Path jsonFile = REPORTS_DIR.resolve(reportId + ".json");
            if (Files.exists(jsonFile)) {
                String json = Files.readString(jsonFile);
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(json, SmokeTestReport.class);
            }
        } catch (IOException e) {
            log.warn("从文件加载报告失败: {}", reportId, e);
        }
        return null;
    }

    /**
     * 获取报告 HTML 内容（优先文件，降级到内存生成）
     */
    public String getHtmlReport() {
        // 优先读取最新的 HTML 文件
        if (latestReportId != null) {
            Path htmlFile = REPORTS_DIR.resolve(latestReportId + ".html");
            if (Files.exists(htmlFile)) {
                try {
                    return Files.readString(htmlFile, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.warn("读取 HTML 报告文件失败", e);
                }
            }
        }
        // 降级：从内存报告生成
        SmokeTestReport report = getLastReport();
        if (report == null) return "<html><body><h1>尚无测试报告</h1></body></html>";
        return htmlReportGenerator.generate(report);
    }

    /**
     * 获取 JSON 格式报告
     */
    public String getJsonReport() {
        SmokeTestReport report = getLastReport();
        if (report == null) return "{\"error\": \"尚无测试报告\"}";
        return jsonReportGenerator.generate(report);
    }

    /**
     * 烟雾测试执行回调。
     * 每条接口执行完成后调用，可用于变量提取、日志等。
     */
    @FunctionalInterface
    public interface ItemExecutionCallback {
        /**
         * @param item      当前执行的接口条目
         * @param result    执行结果（含响应 body）
         * @param variables 可变变量列表，实现可新增变量供后续接口使用
         */
        void afterItem(PostmanItem item, SmokeTestResult result,
                       List<Map<String, String>> variables);
    }
}
