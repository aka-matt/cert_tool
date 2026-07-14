package io.github.certtool.compliance.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.certtool.domain.profile.ExpirationPolicy;
import io.github.certtool.domain.profile.JksPrivateKeyPolicy;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.domain.profile.ProfileId;
import io.github.certtool.domain.profile.Sha1Policy;
import io.github.certtool.domain.profile.Standard;
import io.github.certtool.domain.profile.UnknownAlgorithmPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads {@link Profile} definitions from JSON. Schema is versioned via {@code schemaVersion};
 * unknown enum values or missing required fields raise {@link IllegalArgumentException} (the
 * caller is expected to surface a typed load-failure).
 *
 * <p>This loader never logs password material (per spec §2). It accepts profile data only.
 */
public final class ProfileLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProfileLoader() {}

    /** Parses a JSON profile from the given input stream. */
    public static Profile fromJson(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in");
        JsonNode root = MAPPER.readTree(in);
        ProfileId id = readEnum(root, "id", ProfileId.class);
        Standard target = readEnum(root, "targetStandard", Standard.class);
        Sha1Policy sha1 = readEnum(root, "sha1Policy", Sha1Policy.class);
        ExpirationPolicy exp = readEnum(root, "expirationPolicy", ExpirationPolicy.class);
        UnknownAlgorithmPolicy unk = readEnum(root, "unknownAlgorithmPolicy", UnknownAlgorithmPolicy.class);
        JksPrivateKeyPolicy jks = readEnum(root, "jksPrivateKeyPolicy", JksPrivateKeyPolicy.class);

        String name = requiredText(root, "name");
        String schemaVersion = requiredText(root, "schemaVersion");
        String requiredProvider = optText(root, "requiredRuntimeProvider");
        boolean approvedOnlyRequired = root.path("approvedOnlyRequired").asBoolean(false);

        List<String> allowedAlgorithms = stringList(root, "allowedAlgorithms");
        List<String> disallowedAlgorithms = stringList(root, "disallowedAlgorithms");
        List<String> allowedCurves = stringList(root, "allowedCurves");
        List<String> references = stringList(root, "references");
        Map<String, Integer> minimumKeySizes = intMap(root, "minimumKeySizes");

        return new Profile(
                id,
                name,
                target,
                schemaVersion,
                allowedAlgorithms,
                disallowedAlgorithms,
                minimumKeySizes,
                allowedCurves,
                sha1,
                exp,
                unk,
                jks,
                requiredProvider,
                approvedOnlyRequired,
                references);
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull() || !n.isTextual()) {
            throw new IllegalArgumentException("Missing required string field: " + field);
        }
        return n.asText();
    }

    private static String optText(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        return n.asText();
    }

    private static List<String> stringList(JsonNode root, String field) {
        JsonNode n = root.get(field);
        List<String> out = new ArrayList<>();
        if (n == null || n.isNull()) {
            return out;
        }
        if (!n.isArray()) {
            throw new IllegalArgumentException("Field '" + field + "' must be an array");
        }
        for (Iterator<JsonNode> it = n.elements(); it.hasNext(); ) {
            out.add(it.next().asText());
        }
        return out;
    }

    private static Map<String, Integer> intMap(JsonNode root, String field) {
        JsonNode n = root.get(field);
        Map<String, Integer> out = new HashMap<>();
        if (n == null || n.isNull()) {
            return out;
        }
        if (!n.isObject()) {
            throw new IllegalArgumentException("Field '" + field + "' must be an object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = n.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            JsonNode v = e.getValue();
            if (!v.isInt()) {
                throw new IllegalArgumentException(
                        "Field '" + field + "." + e.getKey() + "' must be an integer");
            }
            out.put(e.getKey(), v.asInt());
        }
        return out;
    }

    private static <E extends Enum<E>> E readEnum(JsonNode root, String field, Class<E> type) {
        String text = requiredText(root, field);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown value for field '" + field + "': '" + text + "'");
        }
    }
}