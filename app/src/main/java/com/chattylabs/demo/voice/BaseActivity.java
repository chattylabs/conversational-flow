package com.chattylabs.demo.voice;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.chattylabs.sdk.android.common.PermissionsHelper;
import com.chattylabs.sdk.android.common.ThreadUtils;
import com.chattylabs.sdk.android.voice.AndroidSpeechRecognizer;
import com.chattylabs.sdk.android.voice.AndroidSpeechSynthesizer;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent;
import com.chattylabs.sdk.android.voice.GoogleSpeechRecognizer;
import com.chattylabs.sdk.android.voice.GoogleSpeechSynthesizer;
import com.chattylabs.sdk.android.voice.TextFilterForUrl;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import javax.inject.Inject;

import dagger.android.support.DaggerAppCompatActivity;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;

public class BaseActivity extends DaggerAppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String ANDROID = "Android";
    private static final String GOOGLE = "Google";

    private static LinkedHashMap<Integer, String> addonMap = new LinkedHashMap<>();
    static {
        addonMap.put(0, ANDROID);
        addonMap.put(1, GOOGLE);
    }

    private static String ADDON_TYPE = ANDROID;

    @Inject ConversationalFlowComponent component;
    ThreadUtils.SerialThread serialThread;
    SpeechSynthesizer synthesizer;
    SpeechRecognizer recognizer;

    private Spinner addonSpinner;
    private ArrayAdapter<String> addonAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        serialThread = ThreadUtils.newSerialThread();
        setup();
        initCommonViews();
        //UpdateManager.register(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        //CrashManager.register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //UpdateManager.unregister();
        serialThread.shutdownNow();
        component.shutdown();
    }

    private void initCommonViews() {
        addonSpinner = findViewById(R.id.addon);
        if (addonSpinner != null) {
            // Create an ArrayAdapter of the addons
            List<String> addonList = Arrays.asList(addonMap.values().toArray(new String[addonMap.size()]));
            addonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, addonList);
            addonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            addonSpinner.setAdapter(addonAdapter);
            addonSpinner.setSelection(0);
            addonSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    ADDON_TYPE = addonMap.get(position);
                    setup();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
    }

    private void setup() {
        component.updateConfiguration(builder ->
                builder .setGoogleCredentialsResourceFile(() -> R.raw.credential)
                        .setRecognizerServiceType(() -> {
                            if (GOOGLE.equals(ADDON_TYPE)) {
                                return GoogleSpeechRecognizer.class;
                            } else {
                                return AndroidSpeechRecognizer.class;
                            }
                        })
                        .setSynthesizerServiceType(() -> {
                            if (GOOGLE.equals(ADDON_TYPE)) {
                                return GoogleSpeechSynthesizer.class;
                            } else {
                                return AndroidSpeechSynthesizer.class;
                            }
                        }).build());

        String[] perms = component.requiredPermissions();
        PermissionsHelper.check(this,
                perms,
                () -> onRequestPermissionsResult(
                        PermissionsHelper.REQUEST_CODE, perms,
                        new int[] {PackageManager.PERMISSION_GRANTED}));
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (PermissionsHelper.isPermissionRequest(requestCode) &&
            PermissionsHelper.isPermissionGranted(grantResults)) {
            serialThread.addTask(() -> component.setup(this, status -> {
                if (status.isAvailable()) {
                    recognizer = component.getSpeechRecognizer(this);
                    synthesizer = component.getSpeechSynthesizer(this);
                    synthesizer.addFilter(new TextFilterForUrl());
                }
            }));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.demos, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.demo_custom_conversation:
                ContextCompat.startActivity(this,
                        new Intent(this, CustomConversationActivity.class), null);
                return true;
            case R.id.demo_build_from_json:
                ContextCompat.startActivity(this,
                        new Intent(this, BuildFromJsonActivity.class), null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


}
