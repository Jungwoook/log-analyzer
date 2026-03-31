package com.jw.log_analyzer.parser.implementations;

import java.util.Set;

public class KokoaServiceIdPolicy implements ServiceIdPolicy {

    private static final Set<String> ALLOWED_SERVICE_IDS = Set.of(
            "blog",
            "book",
            "image",
            "knowledge",
            "news",
            "vclip"
    );

    @Override
    public String normalize(String rawServiceId) {
        if (rawServiceId == null) {
            return null;
        }

        String candidate = rawServiceId.trim().toLowerCase();
        return ALLOWED_SERVICE_IDS.contains(candidate) ? candidate : null;
    }
}
