package com.example.service;

import com.example.model.ApiAsset;
import com.example.model.BusinessAction;
import com.example.model.BusinessFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 API 资源路径识别为业务动作。
 *
 * 职责：
 * 1. 根据 URL pattern + HTTP method 识别业务动作（如 /api/ugc/followUser → "关注用户"）
 * 2. 对动作分类（社交互动 / 内容互动 / 用户认证 / ……）
 * 3. 从动作集合推断业务场景（如 "创作者社交与内容互动"）
 * 4. 按执行顺序组装 BusinessFlow
 */
@Service
public class ActionRecognizer {

    private static final Logger log = LoggerFactory.getLogger(ActionRecognizer.class);

    // ========== 模式注册表 ==========
    // 每个条目：(resource关键字, HTTP方法, 动作名, 分类)
    // 更长的关键字优先匹配（更精确）

    private static final List<ActionPattern> PATTERNS = buildPatterns();

    public ActionRecognizer() {
        log.info("ActionRecognizer 已初始化，共 {} 个识别模式", PATTERNS.size());
    }

    // ========== 对外接口 ==========

    /** 识别单个 ApiAsset → BusinessAction，返回 null 表示无法识别 */
    public BusinessAction recognize(ApiAsset asset, int index) {
        String resource = asset.getResource();
        String method = asset.getMethod();
        if (resource == null) return null;

        // 按 keyword 长度降序匹配（长 keyword 更精确）
        ActionPattern best = null;
        int bestLen = 0;
        for (ActionPattern p : PATTERNS) {
            String lower = resource.toLowerCase();
            String kw = p.keyword.toLowerCase();
            if (lower.contains(kw) && kw.length() > bestLen) {
                // 如果指定了 method，需要匹配
                if (p.method != null && !p.method.equalsIgnoreCase(method)) continue;
                best = p;
                bestLen = kw.length();
            }
        }

        if (best == null) {
            // 无法识别 → 用 method + resource 推断一个通用动作
            return fallbackAction(asset, index);
        }

        return new BusinessAction(
                asset.getSequence(),
                best.action,
                best.category,
                method,
                asset.getUrl(),
                resource,
                index
        );
    }

    /** 从资产列表构建完整业务流 */
    public BusinessFlow buildFlow(List<ApiAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return new BusinessFlow("无操作", "没有捕获到业务请求", List.of());
        }

        List<BusinessAction> actions = new ArrayList<>();
        for (int i = 0; i < assets.size(); i++) {
            ApiAsset a = assets.get(i);
            // 只对 business 和 auth 类接口做动作识别
            String cat = a.getCategory();
            if (cat == null || (!"business".equals(cat) && !"auth".equals(cat))) continue;
            BusinessAction ba = recognize(a, i);
            if (ba != null) {
                actions.add(ba);
            }
        }

        // 按 sequence 排序
        actions.sort(Comparator.comparingInt(BusinessAction::getSequence));

        // 重排序号
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).setSequence(i + 1);
        }

        // 推断场景
        String scenario = inferScenario(actions);
        String description = buildDescription(scenario, actions);

        return new BusinessFlow(scenario, description, actions);
    }

    // ========== 模式定义 ==========

    private static List<ActionPattern> buildPatterns() {
        List<ActionPattern> list = new ArrayList<>();

        // ── 用户认证 ──
        list.add(new ActionPattern("/login",         null,   "用户登录",     "用户认证"));
        list.add(new ActionPattern("/signin",         null,   "用户登录",     "用户认证"));
        list.add(new ActionPattern("/signup",         null,   "用户注册",     "用户认证"));
        list.add(new ActionPattern("/register",       null,   "用户注册",     "用户认证"));
        list.add(new ActionPattern("/logout",         null,   "退出登录",     "用户认证"));
        list.add(new ActionPattern("/signout",        null,   "退出登录",     "用户认证"));
        list.add(new ActionPattern("/oauth",          null,   "第三方登录",   "用户认证"));
        list.add(new ActionPattern("/token/refresh",  null,   "刷新令牌",     "用户认证"));
        list.add(new ActionPattern("/forgotPassword", null,   "忘记密码",     "用户认证"));
        list.add(new ActionPattern("/resetPassword",  null,   "重置密码",     "用户认证"));
        list.add(new ActionPattern("/captcha",        null,   "获取验证码",   "用户认证"));
        list.add(new ActionPattern("/verify",         null,   "验证身份",     "用户认证"));

        // ── 用户资料 ──
        list.add(new ActionPattern("/user/profile",  null,   "查看用户信息",  "用户管理"));
        list.add(new ActionPattern("/my/profile",    null,   "查看个人信息",  "用户管理"));
        list.add(new ActionPattern("/profile",       "GET",  "查看用户信息",  "用户管理"));
        list.add(new ActionPattern("/updateProfile", null,   "更新个人信息",  "用户管理"));
        list.add(new ActionPattern("/avatar",        null,   "更换头像",     "用户管理"));
        list.add(new ActionPattern("/setting",       null,   "系统设置",     "用户管理"));
        list.add(new ActionPattern("/password",      null,   "修改密码",     "用户管理"));

        // ── 社交关系 ──
        list.add(new ActionPattern("/followUser",    "POST", "关注用户",     "社交互动"));
        list.add(new ActionPattern("/follow",        "POST", "关注用户",     "社交互动"));
        list.add(new ActionPattern("/unfollowUser",  "POST", "取消关注",     "社交互动"));
        list.add(new ActionPattern("/unfollow",      "POST", "取消关注",     "社交互动"));
        list.add(new ActionPattern("/followers",     "GET",  "查看粉丝列表", "社交互动"));
        list.add(new ActionPattern("/following",     "GET",  "查看关注列表", "社交互动"));
        list.add(new ActionPattern("/block",         null,   "屏蔽用户",     "社交互动"));
        list.add(new ActionPattern("/unblock",       null,   "取消屏蔽",     "社交互动"));
        list.add(new ActionPattern("/report",        null,   "举报用户",     "社交互动"));

        // ── 内容互动 ──
        list.add(new ActionPattern("/likeUgc",       "POST", "点赞内容",     "内容互动"));
        list.add(new ActionPattern("/like",          "POST", "点赞内容",     "内容互动"));
        list.add(new ActionPattern("/unlikeUgc",     "POST", "取消点赞",     "内容互动"));
        list.add(new ActionPattern("/unlike",        "POST", "取消点赞",     "内容互动"));
        list.add(new ActionPattern("/favoriteUgc",   "POST", "收藏内容",     "内容互动"));
        list.add(new ActionPattern("/favorite",      "POST", "收藏内容",     "内容互动"));
        list.add(new ActionPattern("/unfavoriteUgc", "POST", "取消收藏",     "内容互动"));
        list.add(new ActionPattern("/unfavorite",    "POST", "取消收藏",     "内容互动"));
        list.add(new ActionPattern("/share",         "POST", "分享内容",     "内容互动"));
        list.add(new ActionPattern("/comment",       "POST", "评论内容",     "内容互动"));
        list.add(new ActionPattern("/reply",         "POST", "回复评论",     "内容互动"));
        list.add(new ActionPattern("/review",        "POST", "发表评价",     "内容互动"));

        // ── 内容浏览 ──
        list.add(new ActionPattern("/feed",          "GET",  "查看信息流",   "内容浏览"));
        list.add(new ActionPattern("/recommend",     "GET",  "查看推荐内容", "内容浏览"));
        list.add(new ActionPattern("/explore",       "GET",  "探索发现",     "内容浏览"));
        list.add(new ActionPattern("/search",        "GET",  "搜索内容",     "内容浏览"));
        list.add(new ActionPattern("/list",          "GET",  "查询列表",     "内容浏览"));
        list.add(new ActionPattern("/page",          "GET",  "分页查询",     "内容浏览"));
        list.add(new ActionPattern("/detail",        "GET",  "查看详情",     "内容浏览"));

        // ── 内容管理 ──
        list.add(new ActionPattern("/create",        null,   "创建内容",     "内容管理"));
        list.add(new ActionPattern("/publish",       null,   "发布内容",     "内容管理"));
        list.add(new ActionPattern("/add",           "POST", "新增内容",     "内容管理"));
        list.add(new ActionPattern("/update",        null,   "编辑内容",     "内容管理"));
        list.add(new ActionPattern("/edit",          null,   "编辑内容",     "内容管理"));
        list.add(new ActionPattern("/delete",        null,   "删除内容",     "内容管理"));
        list.add(new ActionPattern("/remove",        null,   "删除内容",     "内容管理"));
        list.add(new ActionPattern("/upload",        null,   "上传文件",     "内容管理"));
        list.add(new ActionPattern("/download",      null,   "下载文件",     "内容管理"));

        // ── 消息通知 ──
        list.add(new ActionPattern("/message",       "POST", "发送消息",     "消息通知"));
        list.add(new ActionPattern("/notification",  "GET",  "查看通知",     "消息通知"));
        list.add(new ActionPattern("/notice",        "GET",  "查看公告",     "消息通知"));

        // ── 数据统计 ──
        list.add(new ActionPattern("/statistics",    "GET",  "查看统计",     "数据统计"));
        list.add(new ActionPattern("/stats",         "GET",  "查看统计",     "数据统计"));
        list.add(new ActionPattern("/rank",          "GET",  "查看排行榜",   "数据统计"));
        list.add(new ActionPattern("/dashboard",     "GET",  "查看仪表盘",   "数据统计"));

        // ── 交易流程 ──
        list.add(new ActionPattern("/order/create",  null,   "创建订单",     "交易流程"));
        list.add(new ActionPattern("/order/pay",     null,   "支付订单",     "交易流程"));
        list.add(new ActionPattern("/payment",       null,   "发起支付",     "交易流程"));
        list.add(new ActionPattern("/order/cancel",  null,   "取消订单",     "交易流程"));
        list.add(new ActionPattern("/order/detail",  "GET",  "查看订单详情", "交易流程"));
        list.add(new ActionPattern("/order/list",    "GET",  "查看订单列表", "交易流程"));
        list.add(new ActionPattern("/cart/add",      null,   "加入购物车",   "交易流程"));
        list.add(new ActionPattern("/cart/remove",   null,   "移除购物车",   "交易流程"));
        list.add(new ActionPattern("/cart/list",     "GET",  "查看购物车",   "交易流程"));
        list.add(new ActionPattern("/checkout",      null,   "结算下单",     "交易流程"));

        // ── 通用 CRUD ──
        list.add(new ActionPattern("/api/",          "GET",  "查询数据",     "数据操作"));
        list.add(new ActionPattern("/api/",          "POST", "提交数据",     "数据操作"));
        list.add(new ActionPattern("/api/",          "PUT",  "更新数据",     "数据操作"));
        list.add(new ActionPattern("/api/",          "DELETE", "删除数据",   "数据操作"));

        return list;
    }

    // ========== 场景推断 ==========

    private String inferScenario(List<BusinessAction> actions) {
        if (actions.isEmpty()) return "无操作";

        Set<String> cats = actions.stream()
                .map(BusinessAction::getCategory)
                .collect(Collectors.toSet());
        Set<String> names = actions.stream()
                .map(BusinessAction::getAction)
                .collect(Collectors.toSet());

        // 交易流程优先
        if (cats.contains("交易流程")) {
            if (names.contains("支付订单") || names.contains("发起支付")) return "下单支付流程";
            return "电商交易流程";
        }

        // 社交 + 内容互动 → 创作者场景
        boolean hasSocial = cats.contains("社交互动");
        boolean hasContent = cats.contains("内容互动");
        boolean hasBrowse = cats.contains("内容浏览");
        if (hasSocial && hasContent) return "创作者社交与内容互动";
        if (hasSocial && hasBrowse) return "社交浏览场景";
        if (hasContent && hasBrowse) return "内容浏览与互动";

        // 用户认证
        if (cats.contains("用户认证")) {
            if (names.contains("用户注册")) return "用户注册流程";
            if (names.contains("用户登录")) return "用户登录流程";
            return "用户认证流程";
        }

        // 内容管理
        if (cats.contains("内容管理")) {
            if (names.contains("创建内容") || names.contains("发布内容")) return "内容发布流程";
            if (names.contains("编辑内容")) return "内容编辑流程";
            if (names.contains("删除内容")) return "内容删除流程";
            if (names.contains("上传文件")) return "文件上传流程";
            return "内容管理流程";
        }

        // 消息通知
        if (cats.contains("消息通知")) return "消息通知流程";

        // 数据统计
        if (cats.contains("数据统计")) return "数据查看流程";

        // 单一分类
        if (cats.size() == 1) {
            String onlyCat = cats.iterator().next();
            Map<String, String> catToScenario = new LinkedHashMap<>();
            catToScenario.put("社交互动", "社交互动场景");
            catToScenario.put("内容互动", "内容互动场景");
            catToScenario.put("内容浏览", "内容浏览场景");
            catToScenario.put("用户管理", "用户管理流程");
            catToScenario.put("数据操作", "数据操作流程");
            String s = catToScenario.get(onlyCat);
            if (s != null) return s;
        }

        return "综合操作流程";
    }

    private String buildDescription(String scenario, List<BusinessAction> actions) {
        int total = actions.size();
        long distinct = actions.stream()
                .map(BusinessAction::getAction)
                .distinct()
                .count();
        String cats = actions.stream()
                .map(BusinessAction::getCategory)
                .distinct()
                .collect(Collectors.joining(" → "));
        return String.format("共 %d 个业务动作（%d 种），涉及: %s", total, distinct, cats);
    }

    /** 无法匹配精确模式时，按 HTTP method 推断通用动作 */
    private BusinessAction fallbackAction(ApiAsset asset, int index) {
        String method = asset.getMethod();
        String action;
        String category;

        if ("GET".equalsIgnoreCase(method)) {
            action = "查询数据";
            category = "数据操作";
        } else if ("POST".equalsIgnoreCase(method)) {
            action = "提交数据";
            category = "数据操作";
        } else if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            action = "更新数据";
            category = "数据操作";
        } else if ("DELETE".equalsIgnoreCase(method)) {
            action = "删除数据";
            category = "数据操作";
        } else {
            return null;
        }

        return new BusinessAction(
                asset.getSequence(),
                action, category,
                method, asset.getUrl(),
                asset.getResource(), index
        );
    }

    // ========== 内部类 ==========

    private record ActionPattern(String keyword, String method, String action, String category) {}
}
