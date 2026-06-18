package com.example.config;

import com.example.service.AiChatService;
import com.example.tool.HttpTool;
import com.example.tool.TimeTool;
import com.example.tool.WeatherTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public AiChatService aiChatService(RestTemplate restTemplate) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();

        return AiServices.builder(AiChatService.class)
                .chatModel(model)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.withMaxMessages(10))
                .tools(new TimeTool(), new WeatherTool(), new HttpTool(restTemplate))
                .build();
    }
}
