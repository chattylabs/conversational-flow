package com.chattylabs.sdk.android.voice;

import android.speech.tts.TextToSpeech;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class AndroidEngines {

    static TextToSpeech.EngineInfo getEngineByName(TextToSpeech tts, String name) {
        List<TextToSpeech.EngineInfo> engines = tts.getEngines();
        for (TextToSpeech.EngineInfo engineInfo : engines) {
            if (engineInfo.name.contains(name)) {
                return engineInfo;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "TryWithIdenticalCatches"})
    static String getCurrentEngine(TextToSpeech tts) {
        Class c;
        String result = null;
        try {
            c = Class.forName("android.speech.tts.TextToSpeech");
            // TODO: this need a proguard to skip obfuscating reflection method
            Method m = c.getMethod("getCurrentEngine");
            result = (String) m.invoke(tts);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return result;
    }
}
