package com.chattylabs.sdk.android.voice;

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;

public class AndroidAudioManager {
    private static final String TAG = Tag.make("AndroidAudioHandler");

    // States
    private boolean requestAudioFocusMayDuck; // released
    private boolean requestAudioFocusExclusive; // released
    private boolean requestAudioExclusive; // released
    private int audioMode = AudioManager.MODE_CURRENT; // released

    private int dtmfVolume;
    private int systemVolume;
    private int ringVolume;
    private int alarmVolume;
    private int notifVolume;
    private int musicVolume;
    private int callVolume;

    // Resources
    private AudioFocusRequest focusRequestMayDuck;
    private AudioFocusRequest focusRequestExclusive;
    private final AudioManager audioManager;
    private final ComponentConfig configuration;
    private final AudioAttributes.Builder audioAttributes;

    // Log stuff
    private ILogger logger;

    public AndroidAudioManager(AudioManager audioManager,
                               ComponentConfig configuration,
                               ILogger logger) {
        this.logger = logger;
        this.audioManager = audioManager;
        this.configuration = configuration;
        this.audioAttributes = new AudioAttributes.Builder();
        audioAttributes.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
        audioAttributes.setUsage(configuration.isBluetoothScoRequired() ?
                        // We put this mode because when over Sco we're never going to gain focus!
                        AudioAttributes.USAGE_VOICE_COMMUNICATION :
                        AudioAttributes.USAGE_MEDIA);
        audioAttributes.setLegacyStreamType(getMainStreamType());
    }

    public int getMainStreamType() {
        return configuration.isBluetoothScoRequired() ?
                AudioManager.STREAM_VOICE_CALL :
                AudioManager.STREAM_MUSIC;
    }

    public void requestAudioFocus(boolean exclusive) {
        if (requestAudioExclusive && !exclusive) {
            abandonAudioFocus();
        }
        requestAudioExclusive = exclusive;
        if (requestAudioExclusive) requestAudioFocusExclusive();
        else requestAudioFocusMayDuck();
    }

    public void abandonAudioFocus() {
        abandonAudioFocusMayDuck();
        abandonAudioFocusExclusive();
    }

    private void requestAudioFocusMayDuck() {
        if (!requestAudioFocusMayDuck) {
            logger.v(TAG, "AUDIO - request Audio Focus May Duck");
            setAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                requestAudioFocusMayDuck = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(
                        null, getMainStreamType(),
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            } else {
                focusRequestMayDuck = new AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes.build()).build();
                requestAudioFocusMayDuck = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(focusRequestMayDuck);
            }
        }
    }

    private void abandonAudioFocusMayDuck() {
        if (requestAudioFocusMayDuck) {
            logger.v(TAG, "AUDIO - abandon Audio Focus May Duck");
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                audioManager.abandonAudioFocus(null);
            } else {
                audioManager.abandonAudioFocusRequest(focusRequestMayDuck);
            }
            unsetAudioMode();
            requestAudioFocusMayDuck = false;
        }
    }

    private void requestAudioFocusExclusive() {
        if (!requestAudioFocusExclusive) {
            logger.v(TAG, "AUDIO - request Audio Focus Exclusive");
            setAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                requestAudioFocusExclusive = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(
                        null, getMainStreamType(),
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);

            } else {
                focusRequestExclusive = new AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(audioAttributes.build()).build();
                requestAudioFocusExclusive = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(focusRequestExclusive);
            }
        }
    }

    private void abandonAudioFocusExclusive() {
        if (requestAudioFocusExclusive) {
            logger.v(TAG, "AUDIO - abandon Audio Focus Exclusive");
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                audioManager.abandonAudioFocus(null);
            } else {
                audioManager.abandonAudioFocusRequest(focusRequestExclusive);
            }
            unsetAudioMode();
            requestAudioFocusExclusive = false;
        }
    }

    public void setAudioMode() {
        audioMode = audioManager.getMode();
        audioManager.setMode(configuration.isBluetoothScoRequired() ?
                AudioManager.MODE_IN_CALL :
                AudioManager.MODE_NORMAL);

        // Enabling this option, the audio is not rooted to the speakers if the sco is activated
        // Meaning that we can force bluetooth sco even with speakers connected
        // Nice to have feature!
        //speakerphoneOn = audioManager.isSpeakerphoneOn();
        //boolean isHeadsetConnected = peripheral.get(Peripheral.Type.HEADSET).isConnected();
        //if (!isHeadsetConnected) { audioManager.setSpeakerphoneOn(!isBluetoothScoRequired()); }
        //else { audioManager.setSpeakerphoneOn(true); }
    }

    public void unsetAudioMode() {
        audioManager.setMode(audioMode);

        // Enabling this option, the audio is not rooted to the speakers if the sco is activated
        // Meaning that we can force bluetooth sco even with speakers connected
        // Nice to have feature!
        //audioManager.setSpeakerphoneOn(speakerphoneOn);
    }

    private void adjustVolumeForBeep() {
        // Volume
        dtmfVolume = audioManager.getStreamVolume(AudioManager.STREAM_DTMF);
        audioManager.setStreamVolume(AudioManager.STREAM_DTMF, audioManager.getStreamMaxVolume(AudioManager.STREAM_DTMF), 0);
        systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM), 0);
        ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, audioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
        alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        notifVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0);
        musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        callVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0);
    }

    private void resetVolumeForBeep() {
        // Volume
        audioManager.setStreamVolume(AudioManager.STREAM_DTMF, dtmfVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, systemVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, ringVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notifVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, musicVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, callVolume, 0);
    }

    public boolean isBluetoothScoAvailableOffCall() {
        return audioManager.isBluetoothScoAvailableOffCall();
    }

    public void setBluetoothScoOn(boolean on) {
        audioManager.setBluetoothScoOn(on);
    }

    public void startBluetoothSco() {
        audioManager.startBluetoothSco();
    }

    public void stopBluetoothSco() {
        audioManager.stopBluetoothSco();
    }
}
