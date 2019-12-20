package chattylabs.conversations.demo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import javax.inject.Inject;

import chattylabs.android.commons.PermissionsHelper;
import chattylabs.android.commons.ThreadUtils;
import chattylabs.conversations.AmazonSpeechSynthesizer;
import chattylabs.conversations.AndroidSpeechRecognizer;
import chattylabs.conversations.AndroidSpeechSynthesizer;
import chattylabs.conversations.ConversationalFlow;
import chattylabs.conversations.FilterForUrl;
import chattylabs.conversations.GoogleSpeechRecognizer;
import chattylabs.conversations.GoogleSpeechSynthesizer;
import chattylabs.conversations.RecognizerListener;
import chattylabs.conversations.SpeechRecognizer;
import chattylabs.conversations.SpeechSynthesizer;
import chattylabs.conversations.SynthesizerListener;
import dagger.android.support.DaggerAppCompatActivity;

abstract class BaseActivity extends DaggerAppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String ANDROID = "Android";
    private static final String GOOGLE = "Google";
    private static final String AMAZON = "Amazon";

    private String addonType = ANDROID;
    private static LinkedHashMap<Integer, String> addonMap = new LinkedHashMap<>();

    static {
        addonMap.put(0, ANDROID);
        addonMap.put(1, GOOGLE);
        addonMap.put(2, AMAZON);
    }

    @Inject ConversationalFlow component;
    ThreadUtils.SerialThread serialThread;
    SpeechSynthesizer synthesizer;
    SpeechRecognizer recognizer;

    View proceed;
    boolean proceedEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        serialThread = ThreadUtils.newSerialThread();
        setup();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        serialThread.shutdownNow();
        component.resetConfiguration(this);
    }

    void initCommonViews() {
        proceed = findViewById(R.id.proceed);
        Spinner addonSpinner = findViewById(R.id.addon);
        if (addonSpinner != null) {
            // Create an ArrayAdapter of the addons
            List<String> addonList = Arrays.asList(addonMap.values().toArray(new String[0]));
            ArrayAdapter<String> addonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, addonList);
            addonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            addonSpinner.setAdapter(addonAdapter);
            addonSpinner.setSelection(0, false);
            addonSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    addonType = addonMap.get(position);
                    setup();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
    }

    private void setup() {
        if (proceed != null) proceedEnabled = proceed.isEnabled();
        runOnUiThread(() -> {
            if (proceed != null && proceed.isEnabled()) proceed.setEnabled(false);
        });

        component.updateConfiguration(builder ->
                builder.setGoogleCredentialsResourceFile(() -> R.raw.google_credentials)
                        .setRecognizerServiceType(() -> {
                            if (GOOGLE.equals(addonType)) {
                                return GoogleSpeechRecognizer.class;
                            } else {
                                return AndroidSpeechRecognizer.class;
                            }
                        })
                        .setSynthesizerServiceType(() -> {
                            if (AMAZON.equals(addonType)) {
                                return AmazonSpeechSynthesizer.class;
                            } else if (GOOGLE.equals(addonType)) {
                                return GoogleSpeechSynthesizer.class;
                            } else {
                                return AndroidSpeechSynthesizer.class;
                            }
                        }).build());

        String[] perms = component.requiredPermissions();
        PermissionsHelper.check(this,
                perms,
                () -> onRequestPermissionsResult(
                        202, perms,
                        new int[]{PackageManager.PERMISSION_GRANTED}), 202);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 202 && PermissionsHelper.allGranted(grantResults)) {
            serialThread.addTask(() -> {
                component.checkSpeechSynthesizerStatus(this, synthesizerStatus -> {

                    if (synthesizerStatus == SynthesizerListener.Status.AVAILABLE) {
                        synthesizer = component.getSpeechSynthesizer(this);
                        synthesizer.addFilter(new FilterForUrl());

                        component.checkSpeechRecognizerStatus(this, recognizerStatus -> {

                            if (recognizerStatus == RecognizerListener.Status.AVAILABLE) {

                                recognizer = component.getSpeechRecognizer(this);
                                runOnUiThread(() -> {
                                    if (proceed != null) proceed.setEnabled(proceedEnabled);
                                });
                            } else throw new UnsupportedOperationException("SpeechRecognizer not available. Cannot proceed.");
                        });
                    } else throw new UnsupportedOperationException("SpeechSynthetizer not available. Cannot proceed.");
                });
            });
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
                        new Intent(this, TestingAddonsActivity.class), null);
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
