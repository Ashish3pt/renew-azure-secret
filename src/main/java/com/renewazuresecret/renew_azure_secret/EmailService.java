package com.renewazuresecret.renew_azure_secret;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {
    private static final Logger log = LogManager.getLogger(EmailService.class);
    @Autowired
    private Configuration configuration;

    @Autowired
    private JavaMailSender mailSender;

    private String getTemplate(Map<String, Object> model)
    {
        StringWriter writer = new StringWriter();
        try {
            Template template = configuration.getTemplate("email-template.ftl");
            template.process(model, writer);
        }
        catch(TemplateException | IOException ex)
        {
            log.error("Error getting template", ex);
        }
        return writer.toString();
    }

    public void sendEmail(List<String> recipients, String subject, String applicationEnvironment, String trouxId, String existingSecretExpiry,
                          String displayName, String clientId, String secretText, String secretExpirationDate)  {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            Map<String, Object> model = getObjectMap(applicationEnvironment, trouxId, existingSecretExpiry, displayName, clientId, secretText, secretExpirationDate);
            helper.setText(getTemplate(model), true);
            helper.setFrom("no-reply@cat.com");
            mailSender.send(msg);
        }
        catch(MessagingException me)
        {
            log.error("Error sending email", me);
        }
    }

    private static Map<String, Object> getObjectMap(String applicationEnvironment, String trouxId, String existingSecretExpiry, String displayName, String clientId, String secretText, String secretExpirationDate) {
        Map<String, Object> model = new HashMap<>();
        model.put("applicationEnvironment", applicationEnvironment);
        model.put("trouxId", trouxId);
        model.put("existingSecretExpiry", existingSecretExpiry);
        model.put("displayName", displayName);
        model.put("clientId", clientId);
        model.put("secretText", secretText);
        model.put("secretExpirationDate", secretExpirationDate);
        return model;
    }
}
