package com.chattylabs.sdk.android.voice;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.nio.ShortBuffer;

public class PlaybackThread {
    private static final String LOG_TAG = PlaybackThread.class.getSimpleName();

    public PlaybackThread(short[] samples, int sampleRate, PlaybackListener listener) {
        mSamples = ShortBuffer.wrap(samples);
        mNumSamples = samples.length;
        mSampleRate = sampleRate;
        mListener = listener;
    }

    private Thread mThread;
    private boolean mShouldContinue;
    private ShortBuffer mSamples;
    private int mNumSamples;
    private int mSampleRate;
    private PlaybackListener mListener;

    public boolean playing() {
        return mThread != null;
    }

    public void startPlayback() {
        if (mThread != null)
            return;

        // Start streaming in a thread
        mShouldContinue = true;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                play();
            }
        });
        mThread.start();
    }

    public void stopPlayback() {
        if (mThread == null)
            return;

        mShouldContinue = false;
        mThread = null;
    }

    private void play() {
        int bufferSize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioTrack.ERROR || bufferSize == AudioTrack.ERROR_BAD_VALUE) {
            bufferSize = mSampleRate * 2;
        }

        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                mSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);

        audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onPeriodicNotification(AudioTrack track) {
                if (mListener != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    mListener.onProgress((track.getPlaybackHeadPosition() * 1000) / mSampleRate);
                }
            }
            @Override
            public void onMarkerReached(AudioTrack track) {
                Log.v(LOG_TAG, "Audio file end reached");
                track.release();
                if (mListener != null) {
                    mListener.onCompletion();
                }
            }
        });
        audioTrack.setPositionNotificationPeriod(mSampleRate / 30); // 30 times per second
        audioTrack.setNotificationMarkerPosition(mNumSamples);

        audioTrack.play();

        Log.v(LOG_TAG, "Audio streaming started");

        short[] buffer = new short[bufferSize];
        mSamples.rewind();
        int limit = mNumSamples;
        int totalWritten = 0;
        while (mSamples.position() < limit && mShouldContinue) {
            int numSamplesLeft = limit - mSamples.position();
            int samplesToWrite;
            if (numSamplesLeft >= buffer.length) {
                mSamples.get(buffer);
                samplesToWrite = buffer.length;
            } else {
                for(int i = numSamplesLeft; i < buffer.length; i++) {
                    buffer[i] = 0;
                }
                mSamples.get(buffer, 0, numSamplesLeft);
                samplesToWrite = numSamplesLeft;
            }
            totalWritten += samplesToWrite;
            audioTrack.write(buffer, 0, samplesToWrite);
        }

        if (!mShouldContinue) {
            audioTrack.release();
        }

        Log.v(LOG_TAG, "Audio streaming finished. Samples written: " + totalWritten);
    }
}