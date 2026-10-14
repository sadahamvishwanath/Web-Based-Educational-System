// SubmitExamRequest.java
package com.apexinstitute.dto;

import java.util.List;

public class SubmitExamRequest {

    private List<Entry> answers;

    public List<Entry> getAnswers() { return answers; }
    public void setAnswers(List<Entry> answers) { this.answers = answers; }

    public static class Entry {
        private Long questionId;
        private String choice;   // "A" | "B" | "C" | "D" | null

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getChoice() { return choice; }
        public void setChoice(String choice) { this.choice = choice; }
    }
}