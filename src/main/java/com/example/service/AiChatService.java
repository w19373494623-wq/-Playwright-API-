package com.example.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

public interface AiChatService {
    String chat(@MemoryId String memoryId, @UserMessage String message);
}
