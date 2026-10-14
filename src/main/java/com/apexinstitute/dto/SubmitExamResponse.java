// SubmitExamResponse.java
package com.apexinstitute.dto;

public class SubmitExamResponse {

    private final int correct;
    private final int incorrect;
    private final int unanswered;
    private final int totalQuestions;
    private final double marksObtained;
    private final int totalMarks;
    private final double percentage;
    private final String grade;

    public SubmitExamResponse(int correct, int incorrect, int unanswered,
                              int totalQuestions, double marksObtained,
                              int totalMarks, double percentage, String grade) {
        this.correct = correct;
        this.incorrect = incorrect;
        this.unanswered = unanswered;
        this.totalQuestions = totalQuestions;
        this.marksObtained = marksObtained;
        this.totalMarks = totalMarks;
        this.percentage = percentage;
        this.grade = grade;
    }

    public int getCorrect() { return correct; }
    public int getIncorrect() { return incorrect; }
    public int getUnanswered() { return unanswered; }
    public int getTotalQuestions() { return totalQuestions; }
    public double getMarksObtained() { return marksObtained; }
    public int getTotalMarks() { return totalMarks; }
    public double getPercentage() { return percentage; }
    public String getGrade() { return grade; }
}