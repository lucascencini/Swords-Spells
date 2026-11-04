package com.minidofus.engine;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Effets sonores. Chaque son est préchargé (en arrière-plan) dans un petit groupe de {@link Clip}
 * pour pouvoir se superposer à lui-même. Sans carte son, le jeu reste simplement silencieux.
 */
public final class SoundPlayer implements AutoCloseable {

    private static final int VOICES_PER_SOUND = 3;

    private final Map<String, Clip[]> pools = new ConcurrentHashMap<>();
    private final Map<String, Integer> nextVoice = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sons");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean muted;
    private volatile boolean disabled;

    /** Lance le préchargement sans bloquer l'ouverture de la fenêtre. */
    public void preload(String... names) {
        scheduler.execute(() -> {
            for (String name : names) {
                if (disabled) return;
                pools.computeIfAbsent(name, this::loadPool);
            }
        });
    }

    public void play(String name, double volume) {
        if (muted || disabled) return;
        scheduler.execute(() -> playNow(name, volume));
    }

    public void play(String name, double volume, double delaySeconds) {
        if (muted || disabled) return;
        scheduler.schedule(() -> playNow(name, volume), (long) (delaySeconds * 1000), TimeUnit.MILLISECONDS);
    }

    public boolean toggleMute() {
        muted = !muted;
        return muted;
    }

    public boolean isMuted() {
        return muted;
    }

    private void playNow(String name, double volume) {
        Clip[] pool = pools.computeIfAbsent(name, this::loadPool);
        if (pool.length == 0) return;
        int i = nextVoice.merge(name, 1, Integer::sum) % pool.length;
        Clip clip = pool[i];
        clip.stop();
        clip.setFramePosition(0);
        setVolume(clip, volume);
        clip.start();
    }

    private Clip[] loadPool(String name) {
        String resource = "/sounds/" + name + ".wav";
        Clip[] clips = new Clip[VOICES_PER_SOUND];
        try {
            for (int i = 0; i < clips.length; i++) {
                InputStream raw = SoundPlayer.class.getResourceAsStream(resource);
                if (raw == null) {
                    System.err.println("[son] fichier manquant : " + resource);
                    return new Clip[0];
                }
                try (AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                    clips[i] = AudioSystem.getClip();
                    clips[i].open(in);
                }
            }
            return clips;
        } catch (Exception | LinkageError e) {
            disabled = true;
            System.err.println("[son] audio indisponible, jeu silencieux (" + e.getMessage() + ")");
            return new Clip[0];
        }
    }

    private static void setVolume(Clip clip, double volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = (float) (20 * Math.log10(Math.max(0.0001, Math.min(1, volume))));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        for (Clip[] pool : pools.values()) {
            for (Clip c : pool) {
                if (c != null) c.close();
            }
        }
    }
}
