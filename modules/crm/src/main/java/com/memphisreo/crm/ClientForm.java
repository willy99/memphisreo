package com.memphisreo.crm;

public record ClientForm(String firstName, String lastName, String email, String phone, Client.Source source, String notes) {
}
