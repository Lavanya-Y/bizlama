package com.bizlama.api.experiment;

public record ExperimentResponse(
    String dish,
    String theme,
    int themeCount,
    int feedbackCount,
    String metricName,
    double currentValue,
    double proposedValue,
    int testDurationDays,
    ExperimentStatus status
) {
}