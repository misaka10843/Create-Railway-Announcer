package com.misaka10843.create_railway_announcer.compat.create;

public final class DoorChimeGate {
    private long opened = Long.MIN_VALUE, closed = Long.MIN_VALUE;
    private Batch openingBatch, closingBatch;
    public Batch batch(boolean opening, long tick) {
        if (accept(opening,tick)) {
            if (opening) openingBatch = new Batch(); else closingBatch = new Batch();
        }
        return opening ? openingBatch : closingBatch;
    }
    public static final class Batch {
        private final java.util.UUID id = java.util.UUID.randomUUID();
        private final java.util.Set<java.util.UUID> listeners = new java.util.HashSet<>();
        private boolean announced;
        public boolean claimAnnouncement(){if(announced)return false;announced=true;return true;}
        public java.util.UUID id() { return id; }
        public boolean claim(java.util.UUID listener) { return listeners.add(listener); }
    }
    public boolean accept(boolean opening, long tick) {
        long previous = opening ? opened : closed;
        if (previous != Long.MIN_VALUE && tick >= previous && tick - previous < 5) return false;
        if (opening) opened = tick; else closed = tick;
        return true;
    }
}
