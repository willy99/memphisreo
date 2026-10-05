package com.memphisreo.inquiry;

public record CreateInquiryRequest(
        String contactName,
        String contactEmail,
        String contactPhone,
        String message
) {
}
