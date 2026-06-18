package com.example.tool;

import dev.langchain4j.agent.tool.Tool;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class WeatherTool {

    @Tool("获取指定城市的实时天气信息，参数city为城市名")
    public String getWeather(String city) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://wttr.in/" + encodedCity + "?format=%25C+%25t&lang=zh"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return city + "的天气：" + response.body();
        } catch (Exception e) {
            return "查询失败：" + e.getMessage();
        }
    }
}
