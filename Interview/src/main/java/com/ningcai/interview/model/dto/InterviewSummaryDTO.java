package com.ningcai.interview.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class InterviewSummaryDTO {
    private int totalScore;
    private int totalQuestions;
    private String correctRate;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> suggestions;
    private String nextRound;
    private int duration;
}