package com.example.filter;

import com.example.model.ApiAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(3)
public class DedupFilter implements AssetFilter {

    private static final Logger log = LoggerFactory.getLogger(DedupFilter.class);

    @Override
    public String name() {
        return "URL 去重";
    }

    @Override
    public List<ApiAsset> filter(List<ApiAsset> all) {
        Map<String, ApiAsset> seen = new LinkedHashMap<>();
        for (ApiAsset a : all) {
            String key = a.getMethod() + " " + a.getUrl();
            seen.putIfAbsent(key, a);
        }
        List<ApiAsset> result = new ArrayList<>(seen.values());
        log.info("  [{}] {} -> {} (去重 {} 条)", name(), all.size(), result.size(), all.size() - result.size());
        return result;
    }
}
