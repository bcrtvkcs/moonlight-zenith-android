package com.limelight.binding.audio;

import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;

/**
 * No-op AudioRenderer used when Client (this device) audio output is disabled.
 * Keeps the RTSP/audio stream handshake healthy without allocating an AudioTrack or taking audio focus.
 */
public class SilentAudioRenderer implements AudioRenderer {
    @Override
    public int setup(MoonBridge.AudioConfiguration audioConfiguration, int sampleRate, int samplesPerFrame) {
        return 0;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void playDecodedAudio(short[] audioData) {
    }

    @Override
    public void cleanup() {
    }
}
