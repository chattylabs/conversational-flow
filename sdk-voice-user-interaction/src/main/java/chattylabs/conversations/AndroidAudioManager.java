package chattylabs.conversations;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;

import androidx.annotation.NonNull;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;

public class AndroidAudioManager {
    private static final String TAG = Tag.make("AndroidAudioHandler");

    // Log stuff
    private ILogger logger;

    // States
    private boolean requestAudioFocusMayDuck;
    private boolean requestAudioFocusExclusive;

    private int dtmfVolume;
    private int systemVolume;
    private int ringVolume;
    private int alarmVolume;
    private int notifVolume;
    private int musicVolume;
    private int callVolume;

    // Resources
    private AudioManager.OnAudioFocusChangeListener focusChangeListener;
    private AudioFocusRequest focusRequestMayDuck;
    private AudioFocusRequest focusRequestExclusive;
    private final AudioManager audioManager;
    private final ComponentConfig configuration;

    private SoundPool soundPool;
    private int priority = 1;
    private int no_loop = 0;
    private float normal_playback_rate = 1f;

    public AndroidAudioManager(AudioManager audioManager,
                               ComponentConfig configuration,
                               ILogger logger) {
        this.logger = logger;
        this.audioManager = audioManager;
        this.configuration = configuration;
    }

    public AudioManager getDefaultAudioManager() {
        return  audioManager;
    }

    public void showVolumeControls() {
        audioManager.adjustStreamVolume(getMainStreamType(), AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
    }

    public int getMainStreamType() {
        return configuration.isBluetoothScoRequired()
                && configuration.getBluetoothScoAudioMode() == AudioManager.MODE_IN_CALL ?
                AudioManager.STREAM_VOICE_CALL :
                AudioManager.STREAM_MUSIC;
    }

    public boolean requestAudioFocus(@NonNull AudioManager.OnAudioFocusChangeListener listener, boolean exclusive) {
        if (exclusive) return requestAudioFocusExclusive(listener);
        else return requestAudioFocusMayDuck(listener);
    }

    public void abandonAudioFocus(boolean exclusive) {
        if (exclusive) abandonAudioFocusExclusive();
        else abandonAudioFocusMayDuck();
    }

    private boolean requestAudioFocusMayDuck(@NonNull AudioManager.OnAudioFocusChangeListener listener) {
        if (requestAudioFocusExclusive) abandonAudioFocusExclusive();
        if (! requestAudioFocusMayDuck) {
            focusChangeListener = listener;
            requestAudioFocusExclusive = false;
            logger.v(TAG, "AUDIO - request Audio Focus May Duck");
            int res;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                res = audioManager.requestAudioFocus(listener, getMainStreamType(), AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            } else {
                AudioFocusRequest.Builder builder = new AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAcceptsDelayedFocusGain(true)
                            .setAudioAttributes(getAudioAttributes().build());
                builder.setOnAudioFocusChangeListener(listener);
                focusRequestMayDuck = builder.build();
                res = audioManager.requestAudioFocus(focusRequestMayDuck);
            }

            return requestAudioFocusMayDuck = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else return true;
    }

    private boolean requestAudioFocusExclusive(@NonNull AudioManager.OnAudioFocusChangeListener listener) {
        if (requestAudioFocusMayDuck) abandonAudioFocusMayDuck();
        if (! requestAudioFocusExclusive) {
            focusChangeListener = listener;
            requestAudioFocusMayDuck = false;
            logger.v(TAG, "AUDIO - request Audio Focus Exclusive");
            int res;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                res = audioManager.requestAudioFocus(listener, getMainStreamType(), AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
            } else {
                AudioFocusRequest.Builder builder = new AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                            .setAudioAttributes(getAudioAttributes().build());
                builder.setOnAudioFocusChangeListener(listener);
                focusRequestExclusive = builder.build();
                res = audioManager.requestAudioFocus(focusRequestExclusive);
            }

            return requestAudioFocusExclusive = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else return true;
    }

    private void abandonAudioFocusMayDuck() {
        if (requestAudioFocusMayDuck) {
            logger.v(TAG, "AUDIO - abandon Audio Focus May Duck");
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                if (focusChangeListener != null)
                    audioManager.abandonAudioFocus(focusChangeListener);
            } else {
                if (focusRequestMayDuck != null)
                    audioManager.abandonAudioFocusRequest(focusRequestMayDuck);
            }
            requestAudioFocusMayDuck = false;
        }
    }

    private void abandonAudioFocusExclusive() {
        if (requestAudioFocusExclusive) {
            logger.v(TAG, "AUDIO - abandon Audio Focus Exclusive");
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                if (focusChangeListener != null)
                    audioManager.abandonAudioFocus(focusChangeListener);
            } else {
                if (focusRequestExclusive != null)
                    audioManager.abandonAudioFocusRequest(focusRequestExclusive);
            }
            requestAudioFocusExclusive = false;
        }
    }

    public void setAudioMode(int mode) {
        //audioMode = audioManager.getMode();
        if (configuration.isBluetoothScoRequired()) {
            audioManager.setMode(mode);
        }

        // By enabling this option, the audio is not rooted to the speakers if the sco is activated
        // meaning that we can force bluetooth sco even with speakers connected
        // TODO: Nice to have feature!
        //speakerphoneOn = audioManager.isSpeakerphoneOn();
        //boolean isHeadsetConnected = peripheral.get(Peripheral.Type.HEADSET).isConnected();
        //if (!isHeadsetConnected) { audioManager.setSpeakerphoneOn(!isBluetoothScoRequired()); }
        //else { audioManager.setSpeakerphoneOn(true); }
    }

    public void unsetAudioMode() {
        //if (audioMode != audioManager.getMode()) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
        //}

        // By enabling this option, the audio is not rooted to the speakers if the sco is activated
        // meaning that we can force bluetooth sco even with speakers connected
        // TODO: Nice to have feature!
        //audioManager.setSpeakerphoneOn(speakerphoneOn);
    }

    public AudioAttributes.Builder getAudioAttributes() {
        return new AudioAttributes.Builder().setLegacyStreamType(getMainStreamType()).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
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

    private void setupSoundPool() {
        if (soundPool == null) {
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(getAudioAttributes().build())
                    .build();
        }
    }

    private void play(SoundPool soundPool, int soundId) {
        float curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float leftVolume = curVolume / maxVolume;
        float rightVolume = curVolume / maxVolume;
        soundPool.play(soundId, 1.0f, 1.0f, priority, no_loop, normal_playback_rate);
    }

    public void startBeep(Context context) {
        setupSoundPool();
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            play(sp, sampleId);
        });
        soundPool.load(context, R.raw.start_beep, 1);
    }

    public void successBeep(Context context) {
        setupSoundPool();
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            play(sp, sampleId);
        });
        soundPool.load(context, R.raw.success_beep, 1);
    }

    public void errorBeep(Context context) {
        setupSoundPool();
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            play(sp, sampleId);
        });
        soundPool.load(context, R.raw.error_beep, 1);
    }

    private void setStreamToMaxVolume() {
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

    private void resetVolumeToDefaultValues() {
        // Volume
        audioManager.setStreamVolume(AudioManager.STREAM_DTMF, dtmfVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, systemVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, ringVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notifVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, musicVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, callVolume, 0);
    }
}
