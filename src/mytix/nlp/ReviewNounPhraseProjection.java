package mytix.nlp;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Stores noun-phrase occurrences; counting and ranking belong to SQL reports. */
public final class ReviewNounPhraseProjection {
    private static NounPhraseExtractor extractor;

    private ReviewNounPhraseProjection() {}

    /** Returns normalized occurrences in extraction order, deliberately retaining duplicates. */
    public static List<String> extract(String comment) {
        if (comment == null || comment.isBlank()) {
            return List.of();
        }
        return normalizeOccurrences(extractor().extractNounPhrases(comment));
    }

    static List<String> normalizeOccurrences(List<String> phrases) {
        List<String> occurrences = new ArrayList<>();
        for (String phrase : phrases) {
            if (phrase == null) {
                continue;
            }
            String normalized = Normalizer.normalize(
                    phrase.replaceAll("(?U)\\s+", " ").strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.getBytes(StandardCharsets.UTF_8).length > 65535) {
                throw new IllegalStateException("A normalized noun phrase exceeds the database TEXT limit.");
            }
            occurrences.add(normalized);
        }
        return List.copyOf(occurrences);
    }

    /**
     * Rebuild inside the same transaction as review creation/update or dataset loading.
     * Never commits. The caller must roll back the whole operation on any failure.
     * Model absence is an explicit write-time error; reading reports needs no models.
     */
    public static void replace(Connection connection, int reviewID, String comment) throws SQLException {
        if (connection.getAutoCommit()) {
            throw new IllegalStateException("Review and noun-phrase projection require one explicit transaction.");
        }
        if (reviewID <= 0) {
            throw new IllegalStateException("A positive reviewID is required.");
        }
        List<String> occurrences = extract(comment);
        try (PreparedStatement ps = connection.prepareStatement("SELECT commentText FROM reviews WHERE reviewID=? FOR UPDATE")) {
            ps.setInt(1, reviewID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !Objects.equals(rs.getString(1), comment)) {
                    throw new IllegalStateException("Review text must be stored before its matching noun-phrase projection.");
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM review_noun_phrases WHERE reviewID=?")) {
            ps.setInt(1, reviewID);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO review_noun_phrases(reviewID,phraseIndex,nounPhrase) VALUES(?,?,?)")) {
            int index = 1;
            for (String phrase : occurrences) {
                ps.setInt(1, reviewID);
                ps.setInt(2, index++);
                ps.setString(3, phrase);
                ps.addBatch();
            }
            if (!occurrences.isEmpty()) {
                ps.executeBatch();
            }
        }
    }

    private static synchronized NounPhraseExtractor extractor() {
        if (extractor == null) {
            extractor = new OpenNlpNounPhraseExtractor();
        }
        return extractor;
    }
}
