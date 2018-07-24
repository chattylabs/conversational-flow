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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class AndroidAudioHandlerTest {

    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock AudioManager audioManager;
    @Mock ComponentConfig config;
    @Mock ILogger logger;

    private AndroidAudioHandler audioHandler;
    private int currentSdkLevel;

    @Before
    public void setUp() throws Exception {
        currentSdkLevel = Build.VERSION.SDK_INT;

        // Preconditions
        UnitTestUtils.setVersionSdk(Build.VERSION_CODES.O);
        when(config.isBluetoothScoRequired()).thenReturn(false);

        audioHandler = spy(new AndroidAudioHandler(audioManager, config, logger));
    }

    @After
    public void tearDown() throws Exception {
        UnitTestUtils.setVersionSdk(currentSdkLevel);
    }

    @Test
    public void getMainStreamType() {
        when(config.isBluetoothScoRequired()).thenReturn(true);

        assertEquals(audioHandler.getMainStreamType(), AudioManager.STREAM_VOICE_CALL);

        when(config.isBluetoothScoRequired()).thenReturn(false);

        assertEquals(audioHandler.getMainStreamType(), AudioManager.STREAM_MUSIC);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void requestAudioFocus_AndroidO() throws Exception {
        audioHandler.requestAudioFocus(false);

        verify(audioManager, never()).requestAudioFocus(any(), anyInt(), anyInt());

        verify(audioManager).requestAudioFocus(any(AudioFocusRequest.class));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void requestAudioFocus_AndroidN() throws Exception {
        UnitTestUtils.setVersionSdk(Build.VERSION_CODES.N);

        audioHandler.requestAudioFocus(false);

        verify(audioManager).requestAudioFocus(any(), anyInt(), anyInt());

        verify(audioManager, never()).requestAudioFocus(any(AudioFocusRequest.class));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void requestAudioFocus_NotExclusive() throws Exception {
        audioHandler.requestAudioFocus(false);

        verify(audioHandler, never()).abandonAudioFocus();

        verify(audioHandler).setAudioMode();

        verify(audioManager, never()).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE));

        verify(audioManager).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void requestAudioFocus_Exclusive() throws Exception {
        audioHandler.requestAudioFocus(true);

        verify(audioHandler, never()).abandonAudioFocus();

        verify(audioHandler).setAudioMode();

        verify(audioManager).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE));

        verify(audioManager, never()).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK));
    }

    @Test
    public void abandonAudioFocus_AndroidO() throws Exception {
        when(audioManager.requestAudioFocus(any()))
                .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        audioHandler.requestAudioFocus(false);
        audioHandler.abandonAudioFocus();

        verify(audioManager, never()).abandonAudioFocus(any());

        verify(audioManager).abandonAudioFocusRequest(any());
    }

    @Test
    public void abandonAudioFocus_AndroidN() throws Exception {
        UnitTestUtils.setVersionSdk(Build.VERSION_CODES.N);
        when(audioManager.requestAudioFocus(any(), anyInt(), anyInt()))
                .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        audioHandler.requestAudioFocus(false);
        audioHandler.abandonAudioFocus();

        verify(audioManager).abandonAudioFocus(any());

        verify(audioManager, never()).abandonAudioFocusRequest(any());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void abandonAudioFocus_NotExclusive() throws Exception {
        when(audioManager.requestAudioFocus(any()))
                .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        audioHandler.requestAudioFocus(false);
        audioHandler.abandonAudioFocus();

        verify(audioHandler).unsetAudioMode();

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

        audioHandler.requestAudioFocus(true);
        audioHandler.abandonAudioFocus();

        verify(audioHandler).unsetAudioMode();

        verify(audioManager).abandonAudioFocusRequest(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE));

        verify(audioManager, never()).abandonAudioFocusRequest(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK));
    }

    @Test
    public void setAudioMode_ScoOn() throws Exception {
        when(config.isBluetoothScoRequired()).thenReturn(true);

        audioHandler.setAudioMode();

        verify(audioManager).getMode();
        verify(audioManager).setMode(AudioManager.MODE_IN_CALL);
    }

    @Test
    public void setAudioMode_ScoOff() throws Exception {
        when(config.isBluetoothScoRequired()).thenReturn(false);

        audioHandler.setAudioMode();

        verify(audioManager).getMode();
        verify(audioManager).setMode(AudioManager.MODE_NORMAL);
    }

    @Test
    public void unsetAudioMode() throws Exception {
        audioHandler.unsetAudioMode();
        verify(audioManager).setMode(AudioManager.MODE_CURRENT);
        verify(audioHandler).unsetAudioMode();
        verifyNoMoreInteractions(audioHandler);
        verifyNoMoreInteractions(audioManager);
    }

    @Test
    public void isBluetoothScoAvailableOffCall() throws Exception {
        when(audioManager.isBluetoothScoAvailableOffCall()).thenReturn(true);
        assertTrue(audioHandler.isBluetoothScoAvailableOffCall());
    }

    @Test
    public void setBluetoothScoOn() throws Exception {
        audioHandler.setBluetoothScoOn(true);
        verify(audioManager).setBluetoothScoOn(true);
    }

    @Test
    public void bluetoothSco() throws Exception {
        audioHandler.startBluetoothSco();
        verify(audioManager).startBluetoothSco();
        audioHandler.stopBluetoothSco();
        verify(audioManager).stopBluetoothSco();
    }
}