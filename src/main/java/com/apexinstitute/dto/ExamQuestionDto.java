package com.apexinstitute.dto;

public class ExamQuestionDto {

    private final Long id;
    private final String text;
    private final String optionA;
    private final String optionB;
    private final String optionC;
    private final String optionD;
    private final String correct;
    private final int orderNo;

    public ExamQuestionDto(Long id, String text,
                           String optionA, String optionB, String optionC, String optionD,
                           String correct, int orderNo) {
        this.id = id;
        this.text = text;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correct = correct;
        this.orderNo = orderNo;
    }

    public Long getId() { return id; }
    public String getText() { return text; }
    public String getOptionA() { return optionA; }
    public String getOptionB() { return optionB; }
    public String getOptionC() { return optionC; }
    public String getOptionD() { return optionD; }
    public String getCorrect() { return correct; }
    public int getOrderNo() { return orderNo; }
}