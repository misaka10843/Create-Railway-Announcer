package com.misaka10843.create_railway_announcer.playback;

import java.util.function.LongSupplier;

public final class PlaybackClock {
    public static final PlaybackClock GAME=new PlaybackClock(System::nanoTime);
    private final LongSupplier time;
    private boolean paused;
    private long pausedAt, skipped;
    public PlaybackClock(LongSupplier time){this.time=time;}
    public synchronized void paused(boolean value){
        if(paused==value)return;
        long now=time.getAsLong();if(value)pausedAt=now;else skipped+=now-pausedAt;
        paused=value;notifyAll();
    }
    public synchronized boolean paused(){return paused;}
    public synchronized long now(){return (paused?pausedAt:time.getAsLong())-skipped;}
    public synchronized void awaitPlaying() throws InterruptedException {while(paused)wait();}
    public void sleep(long millis) throws InterruptedException {
        long end=now()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0,millis));
        while(true){awaitPlaying();long left=end-now();if(left<=0)return;Thread.sleep(Math.max(1,Math.min(20,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(left))));}
    }
}
