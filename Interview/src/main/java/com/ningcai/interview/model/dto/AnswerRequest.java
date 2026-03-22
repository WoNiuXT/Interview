package com.ningcai.interview.model.dto;

import lombok.Data;

/**
 * 回答请求DTO
 * 用于 /next 接口，提交当前问题的答案
 */
@Data
public class AnswerRequest {
    /**
     * 当前问题的答案
     */
    private String answer;
    
    /**
     * 可选：问题ID（用于追踪）
     */
    private String questionId;
}