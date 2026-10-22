package com.hyped.app.policy.application;

import com.hyped.app.account.application.AccountException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PolicyCatalog {
    public record Versions(String termsVersion, String privacyVersion, String contentRulesVersion) { }
    public record Documents(String termsUrl, String privacyUrl) { }
    public record Document(String title, String version, String content, boolean published) { }
    private final Versions required;
    private final boolean published;
    private final String operator;
    private final String contact;
    public PolicyCatalog(@Value("${hyped.policy.terms-version:2026-10-22}") String terms,
            @Value("${hyped.policy.privacy-version:2026-10-22}") String privacy,
            @Value("${hyped.policy.content-rules-version:2026-10-22}") String rules,
            @Value("${hyped.policy.publication-ready:false}") boolean published,
            @Value("${hyped.policy.operator:}") String operator,
            @Value("${hyped.policy.contact-email:}") String contact) {
        required = new Versions(version(terms), version(privacy), version(rules));
        this.operator = operator;
        this.contact = contact;
        this.published = published && !operator.isBlank() && contact.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
        if (published && !available()) {
            throw new IllegalArgumentException(
                    "Published legal documents require bundled versions and operator contact");
        }
    }
    public Versions required() { return required; }
    public Documents documents() {
        return new Documents("/public/legal/terms/" + required.termsVersion(),
                "/public/legal/privacy/" + required.privacyVersion());
    }
    public boolean available() {
        return published && required.termsVersion().equals(required.contentRulesVersion())
                && resource("terms", required.termsVersion()).exists()
                && resource("privacy", required.privacyVersion()).exists();
    }
    public Document document(String kind, String version) {
        if (!("terms".equals(kind) || "privacy".equals(kind)) || !validVersion(version)) { throw missing(); }
        var resource = resource(kind, version);
        if (!resource.exists()) { throw missing(); }
        try {
            String text = resource.getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{operator}}", operator.isBlank() ? "Operator details pending publication" : operator)
                    .replace("{{contact}}", contact.isBlank() ? "Contact details pending publication" : contact);
            return new Document("terms".equals(kind) ? "Terms of Service and Content Rules" : "Privacy Policy",
                    version, text, available());
        } catch (IOException failure) {
            throw new AccountException(503, "LEGAL_DOCUMENTS_UNAVAILABLE",
                    "Legal documents are temporarily unavailable.");
        }
    }
    private static ClassPathResource resource(String kind, String version) {
        return new ClassPathResource("legal/" + kind + "/" + version + ".txt");
    }
    private static boolean validVersion(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,31}");
    }
    private static String version(String value) {
        if (!validVersion(value)) {
            throw new IllegalArgumentException("Invalid legal document version configuration");
        }
        return value;
    }
    private static AccountException missing() {
        return new AccountException(404, "LEGAL_DOCUMENT_NOT_FOUND", "This legal document is unavailable.");
    }
}
