package com.ningcai.interview.model.dto;

import lombok.Data;

@Data
public class UserAnswerDTO {
    private String sessionId;
    private String questionId;
    private String userAnswer;
    private String position;
    private String round;
}