package com.example.ai.filter;

import com.example.model.ApiAsset;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI 分析前的 Java 层硬过滤。
 * 在调用 AI 之前排除明显非业务的接口，减少 token 消耗和 AI 误判。
 *
 * 规则均为静态集合匹配，不依赖外部配置。如需新增规则，在对应集合添加即可。
 */
@Component
public class ApiPreFilter {

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/", "video/"
    );

    private static final Set<String> NON_BUSINESS_URL_KEYWORDS = Set.of(
            "encrypted-image",
            "analytics", "tracking", "beacon", "monitor", "collect",
            "rum", "sentry", "metrics",
            "hm.gif", "hm.png",
            "report"
    );

    private static final Set<String> STATIC_FILE_EXTENSIONS = Set.of(
            ".js", ".css", ".map", ".json",
            ".woff", ".woff2", ".eot", ".ttf", ".otf",
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".ico",
            ".mp4", ".webm", ".ogg",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx"
    );

    private static final Set<String> NON_BUSINESS_HOSTS = Set.of(
            "www.googletagmanager.com",
            "www.google-analytics.com",
            "googleads.g.doubleclick.net",
            "analytics.google.com",
            "metrics.g.alicdn.com",
            "cnzz.mmstat.com",
            "g.alicdn.com",
            "s19.cnzz.com",
            "hm.baidu.com",
            "zz.bdstatic.com"
    );

    /**
     * 过滤非业务接口。返回的列表中只保留业务相关接口。
     */
    public List<ApiAsset> filter(List<ApiAsset> assets) {
        if (assets == null || assets.isEmpty()) return List.of();
        List<ApiAsset> result = new ArrayList<>(assets.size());
        for (ApiAsset a : assets) {
            if (isBusiness(a)) {
                result.add(a);
            }
        }
        return result;
    }

    private boolean isBusiness(ApiAsset asset) {
        String url = asset.getUrl();
        if (url == null || url.isEmpty()) return false;

        // 1. host 黑名单
        String domain = asset.getDomain();
        if (domain != null && NON_BUSINESS_HOSTS.contains(domain)) return false;

        String lowerUrl = url.toLowerCase();

        // 2. URL 关键字黑名单
        for (String kw : NON_BUSINESS_URL_KEYWORDS) {
            if (lowerUrl.contains(kw)) return false;
        }

        // 3. 静态文件后缀
        for (String ext : STATIC_FILE_EXTENSIONS) {
            if (lowerUrl.endsWith(ext)) return false;
        }

        // 4. content-type 检查（仅在 headers 中有值时判断）
        if (asset.getHeaders() != null) {
            String ct = asset.getHeaders().get("content-type");
            if (ct != null) {
                String lowerCt = ct.toLowerCase();
                for (String prefix : IMAGE_CONTENT_TYPES) {
                    if (lowerCt.startsWith(prefix)) return false;
                }
            }
        }

        // 5. data: / blob: 协议（浏览器内部数据）
        if (url.startsWith("data:") || url.startsWith("blob:")) return false;

        return true;
    }
}
