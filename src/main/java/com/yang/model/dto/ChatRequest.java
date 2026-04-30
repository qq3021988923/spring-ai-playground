package com.yang.model.dto;

import lombok.Data;

@Data
public  class ChatRequest {
    private String message;      // 用户消息
    private String mode;         // 模式：normal / agent / love
    private String userId;       // 用户ID（可选）
}