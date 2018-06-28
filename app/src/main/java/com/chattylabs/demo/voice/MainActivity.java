package com.chattylabs.demo.voice;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.chattylabs.sdk.android.common.HtmlUtils;
import com.chattylabs.sdk.android.common.PermissionsHelper;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.voice.UrlMessageFilter;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent;
import com.chattylabs.sdk.android.voice.VoiceInteractionModule;

import java.util.Objects;

import javax.inject.Inject;

import dagger.android.support.DaggerAppCompatActivity;

import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnTextToSpeechDoneListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnTextToSpeechStartedListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnVoiceRecognitionErrorListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnVoiceRecognitionMostConfidentResultListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnVoiceRecognitionReadyListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SpeechRecognizer;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SpeechSynthesizer;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.getVoiceRecognitionErrorType;


public class MainActivity extends DaggerAppCompatActivity {
    public static final int CHECK = 3;
    public static final int LISTEN = 2;
    public static final int READ = 1;
    public static final String TAG = Tag.make(MainActivity.class);
    private ConstraintLayout root;
    private TextView execution;
    private Spinner spinner;
    private EditText text;
    private Button add;
    private Button clear;
    private Button proceed;
    private SparseArray<Pair<Integer, String>> queue = new SparseArray<>();
    private ArrayAdapter<CharSequence> adapter;
    private CheckBox scoCheck;
    private boolean isScoRequired;
    private Button startStopSco;

    @Inject VoiceInteractionComponent voiceInteractionComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        initActions();
        //voiceInteractionComponent = VoiceInteractionModule.provideVoiceInteractionComponent();
        voiceInteractionComponent.setup(this, voiceInteractionStatus -> {
            if (voiceInteractionStatus.isAvailable()) {
                voiceInteractionComponent.getSpeechSynthesizer(this).addFilter(new UrlMessageFilter());
            }
        });
        PermissionsHelper.check(this, voiceInteractionComponent.requiredPermissions());
        //UpdateManager.register(this);
    }

    private void initActions() {
        add.setOnClickListener(v -> {
            String msg = text.getText().toString().trim();
            int itemPosition = spinner.getSelectedItemPosition();
            if (msg.length() > 0 || itemPosition == LISTEN) {
                queue.put(queue.size(), Pair.create(itemPosition, itemPosition == LISTEN ? null : msg));
                execution.setText(HtmlUtils.from(representQueue(-1)));
                proceed.setEnabled(true);
                text.setText(null);
            }
        });
        clear.setOnClickListener(v -> {
            queue.clear();
            text.setText(null);
            execution.setText(null);
            voiceInteractionComponent.shutdown();
        });
        proceed.setOnClickListener((View v) -> {
            voiceInteractionComponent.shutdown();
            execution.setText(HtmlUtils.from(representQueue(-1)));
            readAll();
        });

    }

    @Nullable
    private String representQueue(int index) {
        StringBuilder tx = null;
        boolean isChecking = false;
        for (int i = 0; i < queue.size(); i++) {
            Pair<Integer, String> item = queue.get(i);
            if (isChecking && item.first == CHECK) {
                String text = item.second;
                if (i == index && text != null) {
                    text = "<font color=\"#FFFFFF\">" + text + "</font>";
                }
                tx.append("or \"<i>").append(text).append("</i>\" ");
            }
            else {
                String text = item.second;
                String label = (String) adapter.getItem(item.first);
                if (i == index) {
                    if (text != null) { text = "<font color=\"#FFFFFF\">" + text + "</font>"; }
                    else { label = "<font color=\"#FFFFFF\">" + label + "</font>"; }
                }
                String action = label + (item.first == LISTEN ? "" : " \"<i>" + text + "</i>\" ");
                tx = new StringBuilder(tx == null || tx.length() == 0 ? action : tx + (item.first == READ ? "<br/>" : "<br/>...then ") + action);
                isChecking = item.first == CHECK;
            }
        }
        return tx != null ? tx.toString() : null;
    }

    private void play(String text, int index) {
        SpeechSynthesizer synthesizer = voiceInteractionComponent.getSpeechSynthesizer(this);
        synthesizer.setBluetoothScoRequired(isScoRequired);
        synthesizer.play(text, "default",
                         (OnTextToSpeechStartedListener) s -> {
                             execution.setText(HtmlUtils.from(representQueue(index)));
                         },
                         (OnTextToSpeechDoneListener) s -> {
                             Log.i(TAG, "on Done index: " + index);
                             Pair<Integer, String> next = queue.get(index + 1);
                             if (next.first == LISTEN) {
                                 listen(index);
                             } else {
                                 synthesizer.resume();
                             }
                         });
    }

    private void listen(int index) {
        SpeechSynthesizer synthesizer = voiceInteractionComponent.getSpeechSynthesizer(this);
        SpeechRecognizer recognizer = voiceInteractionComponent.getSpeechRecognizer(this);
        recognizer.setBluetoothScoRequired(isScoRequired);
        recognizer.listen((OnVoiceRecognitionReadyListener) bundle -> {
            execution.setText(HtmlUtils.from(representQueue(index + 1)));
        }, (OnVoiceRecognitionMostConfidentResultListener) o -> {
            execution.setText(HtmlUtils.from(representQueue(-1)));
            SparseArray<String> news = getChecks(new SparseArray<>(), index + 1);
            if (news.size() > 0) {
                for (int a = 0; a < news.size(); a++) {
                    if (Objects.equals(news.valueAt(a), o)) {
                        execution.setText(HtmlUtils.from(representQueue(news.keyAt(a))));
                        break;
                    }
                }
            }
            synthesizer.resume();
        }, (OnVoiceRecognitionErrorListener) (i, i1) -> {
            Log.e(TAG, "Error " + i);
            Log.e(TAG, "Original Error " + getVoiceRecognitionErrorType(i1));
            new AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(getVoiceRecognitionErrorType(i1))
                    .create().show();
            synthesizer.shutdown();
        });
    }

    private SparseArray<String> getChecks(SparseArray<String> news, int index) {
        Pair<Integer, String> checks = queue.get(index + 1);
        if (checks != null && checks.first == CHECK) {
            news.append(index + 1, checks.second);
            news = getChecks(news, index + 1);
        }
        return news;
    }

    private void readAll() {
        for (int i = 0; i < queue.size(); i++) {
            Log.i(TAG, "readAll index: " + i);
            Pair<Integer, String> item = queue.get(i);
            if (item.first == READ) {
                play(item.second, i);
            }
            else if (i == 0 && item.first == LISTEN) { // TODO: Check! How it continues speaking after listening?
                listen(i);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //UpdateManager.unregister();
        voiceInteractionComponent.shutdown();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    @Override
    public void onResume() {
        super.onResume();
        //CrashManager.register(this);
    }

    private void initViews() {
        root = findViewById(R.id.root);
        execution = findViewById(R.id.execution);
        spinner = findViewById(R.id.spinner);
        text = findViewById(R.id.text);
        add = findViewById(R.id.add);
        clear = findViewById(R.id.clear);
        proceed = findViewById(R.id.proceed);
        scoCheck = findViewById(R.id.bluetooth_sco);
        startStopSco = findViewById(R.id.start_stop_sco);


        //
        scoCheck.setOnCheckedChangeListener((buttonView, isChecked) -> isScoRequired = isChecked);
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            startStopSco.setText(audioManager.isBluetoothScoOn() ? "Stop Sco" : "Start Sco");
            startStopSco.setOnClickListener(v -> {
                if (audioManager.isBluetoothScoOn()) {
                    audioManager.setBluetoothScoOn(false);
                    audioManager.stopBluetoothSco();
                }
                else {
                    audioManager.setBluetoothScoOn(true);
                    audioManager.startBluetoothSco();
                }
                startStopSco.setText(audioManager.isBluetoothScoOn() ? "Stop Sco" : "Start Sco");
            });
        }
        proceed.setEnabled(false);
        // Create an ArrayAdapter using the string array and a default spinner layout
        adapter = ArrayAdapter.createFromResource(this, R.array.actions, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean isDefault = position > 0;
                text.setEnabled(isDefault);
                add.setEnabled(isDefault);
                text.setVisibility(View.VISIBLE);
                if (position == LISTEN) text.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
