package com.chattylabs.demo.voice;

import android.app.AlertDialog;
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
import android.widget.Toast;

import com.chattylabs.sdk.android.common.HtmlUtils;
import com.chattylabs.sdk.android.common.PermissionsHelper;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.voice.AndroidSpeechSynthesizer;
import com.chattylabs.sdk.android.voice.Peripheral;
import com.chattylabs.sdk.android.voice.TextFilterForUrl;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent;

import javax.inject.Inject;

import dagger.android.support.DaggerAppCompatActivity;

import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SpeechRecognizer;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SpeechSynthesizer;


public class MainActivity extends DaggerAppCompatActivity {
    public static final String TAG = Tag.make(MainActivity.class);

    // Constants
    public static final int CHECK = 3;
    public static final int LISTEN = 2;
    public static final int READ = 1;

    // Resources
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

    // Components
    @Inject VoiceInteractionComponent voiceInteractionComponent;
    private Peripheral peripheral;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        initActions();
        peripheral = new Peripheral((AudioManager) getSystemService(AUDIO_SERVICE));
        //voiceInteractionComponent = VoiceInteractionModule.provideVoiceInteractionComponent();
        voiceInteractionComponent.setup(this, status -> {
            if (status.isAvailable()) {
                voiceInteractionComponent.getSpeechSynthesizer(this)
                        .addFilter(new TextFilterForUrl());
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
                representQueue(-1);
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
            representQueue(-1);
            readAll();
        });
    }

    @Nullable
    private void representQueue(int index) {
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
            } else {
                String text = item.second;
                String label = (String) adapter.getItem(item.first);
                if (i == index) {
                    if (text != null) {
                        text = "<font color=\"#FFFFFF\">" + text + "</font>";
                    } else {
                        label = "<font color=\"#FFFFFF\">" + label + "</font>";
                    }
                }
                String action = label + (item.first == LISTEN ? "" : " \"<i>" + text + "</i>\" ");
                tx = new StringBuilder(tx == null || tx.length() == 0 ?
                        action :
                        tx.append((item.first == READ ? "<br/>" : "<br/>...then "))
                          .append(action));
                isChecking = item.first == CHECK;
            }
        }
        final StringBuilder copy = tx;

        runOnUiThread(() -> {
            if (copy != null) execution.setText(HtmlUtils.from(copy.toString()));
        });
    }

    private void play(String text, int index) {
        SpeechSynthesizer synthesizer = voiceInteractionComponent.getSpeechSynthesizer(this);
        synthesizer.playText(text, "default",
                (VoiceInteractionComponent.OnSynthesizerStart) s -> {
                    representQueue(index);
                },
                (VoiceInteractionComponent.OnSynthesizerDone) s -> {
                    Log.i(TAG, "on Done index: " + index);
                    Pair<Integer, String> next = queue.get(index + 1);
                    if (next != null && next.first == LISTEN) {
                        synthesizer.holdCurrentQueue();
                        listen(index);
                    } else {
                        synthesizer.resume();
                        if (synthesizer.isEmpty()) {
                            voiceInteractionComponent.shutdown();
                        }
                    }
                });
    }

    private void listen(int index) {
        SpeechRecognizer recognizer = voiceInteractionComponent.getSpeechRecognizer(this);
        recognizer.listen((VoiceInteractionComponent.OnRecognizerReady) bundle -> {
            representQueue(index + 1);
        }, (VoiceInteractionComponent.OnRecognizerMostConfidentResult) o -> {
            representQueue(-1);
            SparseArray<String> news = getChecks(new SparseArray<>(), index + 1);
            if (news.size() > 0) {
                for (int a = 0; a < news.size(); a++) {
                    if (VoiceInteractionComponent.matches(news.valueAt(a), o)) {
                        representQueue(news.keyAt(a));
                        break;
                    }
                }
            }
            SpeechSynthesizer synthesizer = voiceInteractionComponent.getSpeechSynthesizer(this);
            synthesizer.releaseCurrentQueue();
            synthesizer.resume();
            if (synthesizer.isEmpty()) {
                voiceInteractionComponent.shutdown();
            }
        }, (VoiceInteractionComponent.OnRecognizerError) (i, i1) -> {
            Log.e(TAG, "Error " + i);
            Log.e(TAG, "Original Error " + AndroidSpeechSynthesizer.getErrorType(i1));
            new AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(AndroidSpeechSynthesizer.getErrorType(i1))
                    .create().show();
            voiceInteractionComponent.shutdown();
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
            // TODO: Check! How it continues speaking after listening?
            } else if (i == 0 && item.first == LISTEN) {
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

        //
        scoCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !peripheral.get(Peripheral.Type.BLUETOOTH).isConnected()) {
                buttonView.setChecked(false);
                Toast.makeText(this, "Not connected to a Bluetooth device", Toast.LENGTH_LONG).show();
                return;
            }
            voiceInteractionComponent.updateVoiceConfiguration(
                    builder -> {
                        builder.setBluetoothScoRequired(() ->
                                peripheral.get(Peripheral.Type.BLUETOOTH).isConnected() && isChecked);
                        return builder.build();
                    });
        });
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
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
}
