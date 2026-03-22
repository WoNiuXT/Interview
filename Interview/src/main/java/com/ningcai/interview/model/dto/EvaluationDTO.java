package com.ningcai.interview.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class EvaluationDTO {
    private int score;           // 分数（内部用）
    private String feedback;      // 简要评价（内部用）
    private List<String> strengths;   // 优点（用于总结）
    private List<String> weaknesses;  // 不足（用于总结）
    private List<String> suggestions; // 建议（用于总结）
}