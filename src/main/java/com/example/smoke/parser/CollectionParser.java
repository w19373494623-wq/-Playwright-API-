package com.example.smoke.parser;

import com.example.smoke.model.PostmanCollection;
import com.example.smoke.model.PostmanItem;
import com.example.smoke.model.PostmanRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Postman Collection v2.1 JSON 解析器。
 * 将 JSON 文件或字符串解析为 PostmanCollection 对象。
 */
public class CollectionParser {

    private final ObjectMapper objectMapper;

    public CollectionParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public CollectionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 JSON 文件解析
     */
    public PostmanCollection parseFile(String filePath) throws IOException {
        return objectMapper.readValue(new File(filePath), PostmanCollection.class);
    }

    /**
     * 从 JSON 字符串解析
     */
    public PostmanCollection parseJson(String json) throws IOException {
        return objectMapper.readValue(json, PostmanCollection.class);
    }

    /**
     * 将嵌套的 Collection 展平为一条条的接口条目（跳过文件夹）。
     */
    public List<PostmanItem> flattenItems(PostmanCollection collection) {
        List<PostmanItem> result = new ArrayList<>();
        if (collection.getItem() != null) {
            for (PostmanItem item : collection.getItem()) {
                flattenItem(item, result);
            }
        }
        return result;
    }

    private void flattenItem(PostmanItem item, List<PostmanItem> result) {
        if (item.isFolder()) {
            for (PostmanItem child : item.getItem()) {
                flattenItem(child, result);
            }
        } else if (item.getRequest() != null) {
            // 有 request 对象才算有效接口条目
            result.add(item);
        }
    }
}
