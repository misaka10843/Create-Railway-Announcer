package com.misaka10843.createrailwayannouncer.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PhraseRegistry {
    private final Map<String, PhraseEntry> phrases = new LinkedHashMap<>();

    public void clear() {
        phrases.clear();
    }

    public void put(PhraseEntry entry) {
        phrases.put(entry.id(), entry);
    }

    public Optional<PhraseEntry> get(String id) {
        return Optional.ofNullable(phrases.get(id));
    }

    public Map<String, PhraseEntry> all() {
        return Collections.unmodifiableMap(phrases);
    }

    public int size() {
        return phrases.size();
    }
}