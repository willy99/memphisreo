package com.memphisreo.common.mail;

/**
 * Відправка листів. Контракт — тут, реалізація (SMTP) — у platform-app:
 * модулі не залежать від конкретного поштового провайдера, той самий
 * принцип, що й {@link com.memphisreo.common.storage.ObjectStorage}.
 */
public interface EmailSender {

    void send(Email email);

    record Email(String to, String subject, String textBody, String htmlBody) {
    }
}
