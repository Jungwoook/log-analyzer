package com.jw.log_analyzer.parser.implementations;

public class MaverServiceIdPolicy implements ServiceIdPolicy {

    @Override
    public String normalize(String rawServiceId) {
        if (rawServiceId == null) {
            return null;
        }

        String candidate = rawServiceId.trim().toLowerCase();
        return candidate.isEmpty() ? null : candidate;
    }
}
