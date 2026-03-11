package com.trangherb.ecotech;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DocxContextProvider {
    private static final int MAX_PARAGRAPHS = 3;
    private static final int MIN_TERM_LENGTH = 3;
    private static final int FALLBACK_PARAGRAPHS = 2;
    private static final int MAX_CONTEXT_CHARS = 2000;
    private static final int MAX_ANSWER_CHARS = 600;

    private final List<String> paragraphs;

    public DocxContextProvider(String docxPaths) {
        this.paragraphs = loadParagraphs(docxPaths);
    }

    public String getContextForQuery(String query) {
        if (paragraphs.isEmpty()) {
            return "";
        }

        List<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return fallbackContext();
        }

        List<ScoredParagraph> scored = new ArrayList<>();
        for (String paragraph : paragraphs) {
            int score = scoreParagraph(paragraph, terms);
            if (score > 0) {
                scored.add(new ScoredParagraph(paragraph, score));
            }
        }

        if (scored.isEmpty()) {
            return fallbackContext();
        }

        scored.sort(Comparator.comparingInt(ScoredParagraph::score).reversed());
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(MAX_PARAGRAPHS, scored.size());
        for (int i = 0; i < limit; i++) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(scored.get(i).paragraph());
        }
        return trimContext(builder.toString().trim());
    }

    public String getAnswerForQuery(String query) {
        if (paragraphs.isEmpty()) {
            return "";
        }

        String normalizedQuery = normalize(query == null ? "" : query);
        List<String> scored = topParagraphs(normalizedQuery);
        if (scored.isEmpty()) {
            return "";
        }

        String answer;
        if (normalizedQuery.contains("san pham") || normalizedQuery.contains("dich vu")) {
            answer = extractProducts(scored);
        } else if (normalizedQuery.contains("tra hoa trang") || (normalizedQuery.contains("hoa trang") && normalizedQuery.contains("tra"))) {
            answer = extractByKeywords(scored, List.of("hoa trang", "duoc lieu", "tra"));
        } else if (normalizedQuery.contains("la gi") || (normalizedQuery.contains("trangherb") && normalizedQuery.contains("la"))) {
            answer = extractDefinition(scored);
        } else {
            answer = extractByKeywords(scored, List.of());
        }

        if (answer.isBlank()) {
            answer = firstSentences(scored.get(0), 2);
        }

        return trimAnswer(cleanHeading(answer));
    }

    private List<String> loadParagraphs(String docxPaths) {
        String rawPaths = docxPaths == null ? "" : docxPaths.trim();
        if (rawPaths.isEmpty()) {
            return List.of();
        }
        String[] pathValues = rawPaths.split(";");
        List<String> all = new ArrayList<>();

        for (String pathValue : pathValues) {
            String trimmed = pathValue == null ? "" : pathValue.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path path = Path.of(trimmed).toAbsolutePath();
            if (!Files.exists(path)) {
                continue;
            }
            all.addAll(loadParagraphsFromDocx(path));
        }

        return all;
    }

    private List<String> loadParagraphsFromDocx(Path path) {
        try (ZipFile zipFile = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) {
                return List.of();
            }

            try (InputStream stream = zipFile.getInputStream(entry)) {
                String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return extractParagraphs(xml);
            }
        } catch (IOException ex) {
            return List.of();
        }
    }

    private List<String> extractParagraphs(String xml) {
        List<String> results = new ArrayList<>();
        Pattern paragraphPattern = Pattern.compile("<w:p[^>]*>(.*?)</w:p>", Pattern.DOTALL);
        Matcher paragraphMatcher = paragraphPattern.matcher(xml);
        Pattern textPattern = Pattern.compile("<w:t[^>]*>(.*?)</w:t>", Pattern.DOTALL);

        while (paragraphMatcher.find()) {
            String paragraphXml = paragraphMatcher.group(1);
            Matcher textMatcher = textPattern.matcher(paragraphXml);
            StringBuilder builder = new StringBuilder();
            while (textMatcher.find()) {
                builder.append(decodeXml(textMatcher.group(1)));
            }
            String paragraph = normalizeWhitespace(builder.toString());
            if (!paragraph.isBlank()) {
                results.add(paragraph);
            }
        }

        return results;
    }

    private String decodeXml(String value) {
        return value.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private List<String> tokenize(String value) {
        if (value == null) {
            return List.of();
        }
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] raw = normalized.split(" ");
        List<String> terms = new ArrayList<>();
        for (String token : raw) {
            if (token.length() >= MIN_TERM_LENGTH) {
                terms.add(token);
            }
        }
        return terms;
    }

    private int scoreParagraph(String paragraph, List<String> terms) {
        String normalized = normalize(paragraph);
        int score = 0;
        for (String term : terms) {
            if (normalized.contains(term)) {
                score += 1;
            }
        }
        return score;
    }

    private String fallbackContext() {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(FALLBACK_PARAGRAPHS, paragraphs.size());
        for (int i = 0; i < limit; i++) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(paragraphs.get(i));
        }
        return trimContext(builder.toString().trim());
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String trimContext(String context) {
        if (context.length() <= MAX_CONTEXT_CHARS) {
            return context;
        }
        return context.substring(0, MAX_CONTEXT_CHARS).trim();
    }

    private String trimAnswer(String answer) {
        if (answer.length() <= MAX_ANSWER_CHARS) {
            return answer.trim();
        }
        return answer.substring(0, MAX_ANSWER_CHARS).trim();
    }

    private List<String> topParagraphs(String normalizedQuery) {
        List<String> terms = tokenize(normalizedQuery);
        List<ScoredParagraph> scored = new ArrayList<>();
        for (String paragraph : paragraphs) {
            int score = terms.isEmpty() ? 0 : scoreParagraph(paragraph, terms);
            if (score > 0 || terms.isEmpty()) {
                scored.add(new ScoredParagraph(paragraph, score));
            }
        }
        if (scored.isEmpty()) {
            return List.of();
        }
        scored.sort(Comparator.comparingInt(ScoredParagraph::score).reversed());
        List<String> result = new ArrayList<>();
        int limit = Math.min(MAX_PARAGRAPHS, scored.size());
        for (int i = 0; i < limit; i++) {
            result.add(scored.get(i).paragraph());
        }
        return result;
    }

    private String extractProducts(List<String> paragraphs) {
        String[] keys = new String[] {
                "san pham duoc lieu",
                "mo hinh nuoi",
                "thiet bi ho tro",
                "giai phap xu ly nuoc thai",
                "tra hoa trang",
                "nen thom",
                "phan huu co",
                "chau sinh hoc"
        };

        List<String> items = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String normalized = normalize(paragraph);
            for (String key : keys) {
                if (normalized.contains(key)) {
                    String sentence = firstSentences(paragraph, 1);
                    if (!sentence.isBlank()) {
                        items.add(sentence);
                    }
                    break;
                }
            }
        }

        if (items.isEmpty()) {
            return firstSentences(paragraphs.get(0), 2);
        }

        StringBuilder builder = new StringBuilder("Các sản phẩm/dịch vụ nổi bật gồm: ");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(items.get(i));
        }
        return builder.toString().trim();
    }

    private String extractByKeywords(List<String> paragraphs, List<String> keywords) {
        for (String paragraph : paragraphs) {
            if (keywords.isEmpty()) {
                String sentence = firstSentences(paragraph, 2);
                if (!sentence.isBlank()) {
                    return sentence;
                }
                continue;
            }
            String normalized = normalize(paragraph);
            boolean hit = false;
            for (String keyword : keywords) {
                if (normalized.contains(keyword)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                String sentence = firstSentences(paragraph, 2);
                if (!sentence.isBlank()) {
                    return sentence;
                }
            }
        }
        return "";
    }

    private String extractDefinition(List<String> paragraphs) {
        for (String paragraph : paragraphs) {
            String normalized = normalize(paragraph);
            if (normalized.contains("he sinh thai") && normalized.contains("tuan hoan")) {
                String sentence = firstSentences(paragraph, 2);
                if (!sentence.isBlank()) {
                    if (!sentence.toLowerCase(Locale.ROOT).contains("trangherb")) {
                        return "TrangHerb EcoTech là " + sentence;
                    }
                    return sentence;
                }
            }
        }
        return firstSentences(paragraphs.get(0), 2);
    }

    private String firstSentences(String paragraph, int count) {
        String cleaned = normalizeWhitespace(paragraph);
        String[] parts = cleaned.split("(?<=[.!?…])\\s+");
        StringBuilder builder = new StringBuilder();
        int added = 0;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(trimmed);
            added += 1;
            if (added >= count) {
                break;
            }
        }
        return builder.toString().trim();
    }

    private String cleanHeading(String text) {
        String cleaned = text.replaceAll("^\\s*\\d+\\.?\\s*", "").trim();
        return cleaned.replace("TrangHerb EcoTech – Hành Trình Xanh Cộng Hưởng Với Sức Khỏe Cộng Đồng & Nông Nghiệp Tuần Hoàn", "TrangHerb EcoTech");
    }

    private record ScoredParagraph(String paragraph, int score) {}
}
