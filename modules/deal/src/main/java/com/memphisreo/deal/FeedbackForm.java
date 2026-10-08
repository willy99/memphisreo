package com.memphisreo.deal;

public record FeedbackForm(Integer interest, Showing.Objection objection, String comment, Showing.NextStep nextStep) {
}
