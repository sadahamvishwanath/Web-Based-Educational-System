package com.apexinstitute.dto;

/**
 * A question as seen by a student DURING an exam.
 * Deliberately has NO `correct` field — the answer must never leave the server.
 */
public class ExamTakeQuestionDto {

    private final Long id;
    private final String text;
    private final String optionA;
    private final String optionB;
    private final String optionC;
    private final String optionD;
    private final int orderNo;

    public ExamTakeQuestionDto(Long id, String text,
                               String optionA, String optionB,
                               String optionC, String optionD,
                               int orderNo) {
        this.id = id;
        this.text = text;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.orderNo = orderNo;
    }

    public Long getId() { return id; }
    public String getText() { return text; }
    public String getOptionA() { return optionA; }
    public String getOptionB() { return optionB; }
    public String getOptionC() { return optionC; }
    public String getOptionD() { return optionD; }
    public int getOrderNo() { return orderNo; }
}