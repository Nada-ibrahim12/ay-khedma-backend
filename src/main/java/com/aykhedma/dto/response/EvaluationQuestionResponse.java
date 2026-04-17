package com.aykhedma.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationQuestionResponse {
    private String dimension;
    private String question;
    private Integer minValue;
    private Integer maxValue;
}
