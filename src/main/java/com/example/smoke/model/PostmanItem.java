package com.example.smoke.model;

import java.util.List;
import java.util.Map;

/**
 * Postman Collection Item（条目或文件夹）。
 * 如果 getItem() 不为空，则为文件夹；否则为接口条目。
 */
public class PostmanItem {

    private String name;
    private PostmanRequest request;
    private List<PostmanItem> item;
    private List<Map<String, Object>> response;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PostmanRequest getRequest() { return request; }
    public void setRequest(PostmanRequest request) { this.request = request; }

    public List<PostmanItem> getItem() { return item; }
    public void setItem(List<PostmanItem> item) { this.item = item; }

    public List<Map<String, Object>> getResponse() { return response; }
    public void setResponse(List<Map<String, Object>> response) { this.response = response; }

    public boolean isFolder() {
        return item != null && !item.isEmpty();
    }
}
