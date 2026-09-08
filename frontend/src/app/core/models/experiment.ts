export type ExperimentStatus = 'PROPOSED' | 'ACTIVE';

export interface Experiment {
    dish: string;
    theme: string;
    themeCount: number;
    feedbackCount: number;
    currentButterGrams: number;
    proposedButterGrams: number;
    testDurationDays: number;
    status: ExperimentStatus;
}