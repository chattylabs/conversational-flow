package com.chattylabs.sdk.android.voice;

import android.media.AudioManager;
import android.os.Build;

import com.chattylabs.sdk.android.common.UnitTestUtils;
import com.chattylabs.sdk.android.common.internal.ILogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
//@RunWith(MockitoJUnitRunner.class)
public class AndroidAudioHandlerTest {

    @Mock AudioManager audioManager;
    @Mock VoiceConfig config;
    @Mock ILogger logger;

    private AndroidAudioHandler audioHandler;
    private int currentSdkLevel;

    @Before
    public void setUp() throws Exception {
        currentSdkLevel = Build.VERSION.SDK_INT;
        MockitoAnnotations.initMocks(this);

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

    @Test
    public void requestAudioFocus() throws Exception {
        UnitTestUtils.setVersionSdk(Build.VERSION_CODES.O);

        audioHandler.requestAudioFocus(false);

        verify(audioHandler, never()).abandonAudioFocus();

        verify(audioHandler).setAudioMode();

        //noinspection ConstantConditions
        verify(audioManager, never()).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE));

        //noinspection ConstantConditions
        verify(audioManager).requestAudioFocus(argThat(argument ->
                argument.getFocusGain() == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK));
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
    public void bluetoothSco() {
    }
}