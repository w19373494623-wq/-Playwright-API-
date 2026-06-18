package com.example.tool;

import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDateTime;

public class TimeTool {
    @Tool
    public String getcurrntTime(){
        return LocalDateTime.now().toString();
    }
}
