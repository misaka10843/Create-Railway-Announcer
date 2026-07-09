package com.misaka10843.createrailwayannouncer.sequence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SequenceRegistry {
    private final Map<String, SequenceTemplate> templates = new LinkedHashMap<>();

    public void clear() {
        templates.clear();
    }

    public void put(SequenceTemplate template) {
        templates.put(template.id(), template);
    }

    public Optional<SequenceTemplate> get(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Map<String, SequenceTemplate> all() {
        return Collections.unmodifiableMap(templates);
    }

    public int size() {
        return templates.size();
    }
}