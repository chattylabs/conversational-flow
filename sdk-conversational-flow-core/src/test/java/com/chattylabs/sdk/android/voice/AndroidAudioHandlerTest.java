package com.chattylabs.sdk.android.voice;

import android.media.AudioManager;

import com.chattylabs.sdk.android.common.internal.ILogger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AndroidAudioHandlerTest {

    @Mock AudioManager audioManager;
    @Mock VoiceConfig config;
    @Mock ILogger logger;

    private AndroidAudioHandler audioHandler;

    @Before
    public void setUp() throws Exception {
        when(config.isBluetoothScoRequired()).thenReturn(false);

        audioHandler = spy(new AndroidAudioHandler(audioManager, config, logger));
    }

    @Test
    public void getMainStreamType() {
        when(config.isBluetoothScoRequired()).thenReturn(true);

        assertEquals(audioHandler.getMainStreamType(), AudioManager.STREAM_VOICE_CALL);

        when(config.isBluetoothScoRequired()).thenReturn(false);

        assertEquals(audioHandler.getMainStreamType(), AudioManager.STREAM_MUSIC);
    }

    @Test
    public void requestAudioFocus() {
        audioHandler.requestAudioFocus(false);

        verify(audioHandler).abandonAudioFocus();

        verify(audioManager, never()).requestAudioFocus()
    }

    @Test
    public void abandonAudioFocus() {
    }

    @Test
    public void setAudioMode() {
    }

    @Test
    public void unsetAudioMode() {
    }

    @Test
    public void isBluetoothScoAvailableOffCall() {
    }

    @Test
    public void setBluetoothScoOn() {
    }

    @Test
    public void startBluetoothSco() {
    }

    @Test
    public void stopBluetoothSco() {
    }
}