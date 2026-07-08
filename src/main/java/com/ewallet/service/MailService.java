package com.ewallet.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email. When {@code app.mail.enabled=false} (the default in
 * dev/test) the message is logged instead of being sent, so flows that depend on
 * email can be exercised without an SMTP server.
 */
@Slf4j(topic = "MailService")
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@ewallet.local}")
    private String from;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void send(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("[MAIL DISABLED] Would send email to={} subject='{}' body='{}'", to, subject, body);
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Mail is enabled but no JavaMailSender is configured; skipping email to {}", to);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
        log.info("Sent email to={} subject='{}'", to, subject);
    }
}
