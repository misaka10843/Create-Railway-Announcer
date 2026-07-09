package com.misaka10843.createrailwayannouncer.pack;

import com.misaka10843.createrailwayannouncer.sequence.SequenceRegistry;

import java.util.Optional;

public final class VoicePackManager {
    private static final PhraseRegistry PHRASES = new PhraseRegistry();
    private static VoicePack activePack;
    private static final SequenceRegistry SEQUENCES = new SequenceRegistry();

    private VoicePackManager() {
    }

    public static PhraseRegistry phrases() {
        return PHRASES;
    }

    public static Optional<VoicePack> activePack() {
        return Optional.ofNullable(activePack);
    }

    public static SequenceRegistry sequences() {
        return SEQUENCES;
    }

    static void setActivePack(VoicePack pack) {
        activePack = pack;
    }

    static void clear() {
        activePack = null;
        PHRASES.clear();
        SEQUENCES.clear();
    }
}