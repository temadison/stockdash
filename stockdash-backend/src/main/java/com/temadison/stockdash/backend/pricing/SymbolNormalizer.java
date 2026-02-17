package com.temadison.stockdash.backend.pricing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SymbolNormalizer {

    private static final Map<String, String> ALIAS_TO_CANONICAL = Map.of(
            "KLA", "KLAC"
    );

    private static final Map<String, Set<String>> CANONICAL_TO_ALIASES = buildCanonicalAliases();

    private SymbolNormalizer() {
    }

    public static String normalize(String symbol) {
        String upper = symbol.trim().toUpperCase(Locale.US);
        return ALIAS_TO_CANONICAL.getOrDefault(upper, upper);
    }

    public static Set<String> lookupCandidatesForCanonical(String canonicalSymbol) {
        String canonical = normalize(canonicalSymbol);
        Set<String> candidates = new HashSet<>();
        candidates.add(canonical);
        candidates.addAll(CANONICAL_TO_ALIASES.getOrDefault(canonical, Set.of()));
        return candidates;
    }

    private static Map<String, Set<String>> buildCanonicalAliases() {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, String> entry : ALIAS_TO_CANONICAL.entrySet()) {
            result.computeIfAbsent(entry.getValue(), ignored -> new HashSet<>()).add(entry.getKey());
        }
        return result;
    }
}
