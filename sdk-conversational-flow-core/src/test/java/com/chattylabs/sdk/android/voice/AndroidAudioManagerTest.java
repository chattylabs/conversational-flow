package com.chattylabs.sdk.android.voice;

import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import com.chattylabs.sdk.android.common.UnitTestUtils;
import com.chattylabs.sdk.android.common.internal.ILogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class AndroidAudioManagerTest {

    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock AudioManager audioManager;
    @Mock ComponentConfig config;
    @Mock ILogger logger;

    private AndroidAudioManager androidAudioManager;
    private int currentSdkLevel;

    @Before
    public void setUp() throws Exception {
        currentSdkLevel = Build.VERSION.SDK_INT;

        UnitTestUtils.setVersionSdk(Build.VERSION_CODES.O);
        when(config.isBluetoothScoRequired()).thenReturn(false);

        androidAudioManager = spy(new AndroidAudioManager(audioManager, config, logger));
    }

    @After
    public void tearDown() throws Exception {
        UnitTestUtils.setVersionSdk(currentSdkLevel);
    }

    @Test
    public void getMainStreamType() {
        when(config.isBluetoothScoRequired()).thenReturn(true);

        assertEquals(androidAudioManager.getMainStreamType(), AudioManager.STREAM_VOICE_CALL);

        when(config.isBluetoothScoRequired()).thenReturn(false);

        assertEquals(androidAudioManager.getMainStreamType(), AudioManager.STREAM_MUSIC);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void requestAudioFocus_AndroidO() throws Exception {
        androidAudioManager.requestAudioFocus(false);

        verify(audioManager, never()).requestAudioFocus(any(), anyInt(), anyInt());
        verify(audioManager).requestAudioFocus(any(AudioFocusRequest.class));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void requestAudioFocus_AndroidN() throws Exception {
        UnitTestUtils.setVersionSdk(Build.VERSION_CODES.N);

        androidAudioManager.requestAudioFocus(false);

        verify(audioManager).requestAudioFocus(any(), anyInt(), anyInt());
        verify(audioManager, never()).requestAudioFocus(any(AudioFocusRequest.class));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void requestAudioFocus_NotExclusive() throws Exception {
        androidAudioManager.requestAudioFocus(false);

        verify(androidAudioManager).setAudioMode();
        verify(audioManager, never()).abandonAudioFocus(any());
        verify(audioManager, never()).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE));
        verify(audioManager).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void requestAudioFocus_Exclusive() throws Exception {
        androidAudioManager.requestAudioFocus(true);

        verify(androidAudioManager).setAudioMode();
        verify(audioManager, never()).abandonAudioFocus(any());
        verify(audioManager).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE));
        verify(audioManager, never()).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK));
    }

    @Test
    public void abandonAudioFocus_AndroidO() throws Exception {
        when(audioManager.requestAudioFocus(any()))
                .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        androidAudioManager.requestAudioFocus(false);
        androidAudioManager.abandonAudioFocus();

        verify(audioManager, never()).abandonAudioFocus(any());
        verify(audioManager).abandonAudioFocusRequest(any());
    }

    @Test
    public void abandonAudioFocus_AndroidN() throws Exception {
        UnitTestUtils.setVersionSdk(Build.VERSION_CODES.N);
        when(audioManager.requestAudioFocus(any(), anyInt(), anyInt()))
                .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        androidAudioManager.requestAudioFocus(false);
        androidAudioManager.abandonAudioFocus();

        verify(audioManager).abandonAudioFocus(any());
        verify(audioManager, never()).abandonAudioFocusRequest(any());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void abandonAudioFocus_NotExclusive() throws Exception {
        when(audioManager.requestAudioFocus(any()))
                .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        androidAudioManager.requestAudioFocus(false);
        androidAudioManager.abandonAudioFocus();

        verify(androidAudioManager).unsetAudioMode();
        verify(audioManager, never()).abandonAudioFocusRequest(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE));
        verify(audioManager).abandonAudioFocusRequest(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void abandonAudioFocus_Exclusive() throws Exception {
        when(audioManager.requestAudioFocus(any()))
                .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        androidAudioManager.requestAudioFocus(true);
        androidAudioManager.abandonAudioFocus();

        verify(androidAudioManager).unsetAudioMode();
        verify(audioManager).abandonAudioFocusRequest(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE));
        verify(audioManager, never()).abandonAudioFocusRequest(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK));
    }

    @Test
    public void setAudioMode_ScoOn() throws Exception {
        when(config.isBluetoothScoRequired()).thenReturn(true);

        androidAudioManager.setAudioMode();

        verify(audioManager).getMode();
        verify(audioManager).setMode(AudioManager.MODE_IN_CALL);
    }

    @Test
    public void setAudioMode_ScoOff() throws Exception {
        when(config.isBluetoothScoRequired()).thenReturn(false);

        androidAudioManager.setAudioMode();

        verify(audioManager).getMode();
        verify(audioManager).setMode(AudioManager.MODE_NORMAL);
    }

    @Test
    public void unsetAudioMode() throws Exception {
        androidAudioManager.unsetAudioMode();

        verify(audioManager).setMode(AudioManager.MODE_CURRENT);
    }

    @Test
    public void bluetoothSco() throws Exception {
        androidAudioManager.startBluetoothSco();

        verify(audioManager).startBluetoothSco();

        androidAudioManager.stopBluetoothSco();

        verify(audioManager).stopBluetoothSco();
    }
}