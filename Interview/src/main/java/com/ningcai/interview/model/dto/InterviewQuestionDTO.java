package com.ningcai.interview.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class InterviewQuestionDTO {
    private String questionId;
    private String question;
    private String category;
    private String difficulty;
    private List<String> expectedKeywords;
    private String timestamp;
    
    public InterviewQuestionDTO() {
        this.timestamp = java.time.LocalDateTime.now().toString();
    }
}