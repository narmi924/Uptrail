package com.uptrail.service;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.uptrail.model.MailTemplate;

/**
 * Renders plain-text emails from {@code classpath:mail/<template>.txt}. The first line of each template is
 * {@code Subject: ...}; the rest is the body. A separate template engine in TEXT mode keeps mail templates
 * away from the page templates and their HTML escaping.
 */
@Component
public class MailRenderer {

    public record RenderedMail(String subject, String body) {
    }

    private static final String SUBJECT_PREFIX = "Subject:";

    private final SpringTemplateEngine engine;

    public MailRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("mail/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        this.engine = new SpringTemplateEngine();
        this.engine.setTemplateResolver(resolver);
    }

    public RenderedMail render(MailTemplate template, Map<String, String> values) {
        Context context = new Context(Locale.ENGLISH);
        values.forEach(context::setVariable);
        String text = engine.process(template.name().toLowerCase(Locale.ROOT), context).replace("\r\n", "\n");
        int lineEnd = text.indexOf('\n');
        String first = lineEnd < 0 ? text : text.substring(0, lineEnd);
        if (!first.startsWith(SUBJECT_PREFIX)) {
            throw new IllegalStateException("Mail template " + template + " has no subject line");
        }
        // Subjects are single-line by construction; line breaks are removed so that no header can be injected.
        String subject = first.substring(SUBJECT_PREFIX.length()).replaceAll("[\\r\\n]+", " ").strip();
        String body = lineEnd < 0 ? "" : text.substring(lineEnd + 1).strip() + "\n";
        return new RenderedMail(subject, body);
    }
}
