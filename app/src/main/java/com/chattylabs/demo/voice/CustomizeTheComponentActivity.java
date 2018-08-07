package com.chattylabs.demo.voice;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import com.chattylabs.sdk.android.common.ThreadUtils;
import com.chattylabs.sdk.android.voice.AndroidSpeechRecognizer;
import com.chattylabs.sdk.android.voice.AndroidSpeechSynthesizer;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent;
import com.chattylabs.sdk.android.voice.GoogleSpeechRecognizer;
import com.chattylabs.sdk.android.voice.GoogleSpeechSynthesizer;
import com.chattylabs.sdk.android.voice.Peripheral;
import com.chattylabs.sdk.android.voice.TextFilterForUrl;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import javax.inject.Inject;

import dagger.android.support.DaggerAppCompatActivity;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerError;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerMostConfidentResult;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerReady;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerDone;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerError;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerStart;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SpeechRecognizer;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SpeechSynthesizer;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SynthesizerListener;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.matches;


public class CustomizeTheComponentActivity extends DaggerAppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = Tag.make(CustomizeTheComponentActivity.class);

    // Constants
    private static final int CHECK = 3;
    private static final int LISTEN = 2;
    private static final int READ = 1;

    private static final String ANDROID = "Android";
    private static final String GOOGLE = "Google";
    // ...

    private static LinkedHashMap<Integer, String> addonMap = new LinkedHashMap<>();
    static {
        addonMap.put(0, ANDROID);
        addonMap.put(1, GOOGLE);
    }

    private static String ADDON_TYPE = addonMap.get(0);

    // Resources
    private TextView execution;
    private Spinner actionSpinner;
    private Spinner addonSpinner;
    private EditText text;
    private Button add;
    private Button clear;
    private Button proceed;
    private SparseArray<Pair<Integer, String>> queue = new SparseArray<>();
    private ArrayAdapter<CharSequence> actionAdapter;
    private ArrayAdapter<String> addonAdapter;
    private CheckBox scoCheck;
    private ThreadUtils.SerialThread serialThread;

    // Components
    @Inject ConversationalFlowComponent component;
    private SpeechSynthesizer synthesizer;
    private SpeechRecognizer recognizer;
    private Peripheral peripheral;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.demos, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.demo_conversation:
                ContextCompat.startActivity(this,
                        new Intent(this, CustomizeTheComponentActivity.class), null);
                return true;
            case R.id.demo_components:
                ContextCompat.startActivity(this,
                        new Intent(this, BuildFromJsonActivity.class), null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customize_the_component);
        initViews();
        initActions();
        peripheral = new Peripheral((AudioManager) getSystemService(AUDIO_SERVICE));
        serialThread = ThreadUtils.newSerialThread();

        setup();

        //UpdateManager.register(this);
    }

    private void setup() {
        String[] perms = component.requiredPermissions();
        PermissionsHelper.check(this,
                perms,
                () -> onRequestPermissionsResult(
                        PermissionsHelper.REQUEST_CODE, perms,
                        new int[] {PackageManager.PERMISSION_GRANTED}));
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (PermissionsHelper.isPermissionRequest(requestCode)) {
            if (PermissionsHelper.isPermissionGranted(grantResults)) {
                serialThread.addTask(() -> {
                    component.updateConfiguration(builder ->
                            builder .setGoogleCredentialsResourceFile(() -> R.raw.credential)
                                    .setRecognizerServiceType(() -> {
                                        switch (ADDON_TYPE) {
                                            case GOOGLE:
                                                return GoogleSpeechRecognizer.class;
                                            default:
                                                return AndroidSpeechRecognizer.class;
                                        }
                                    })
                                    .setSynthesizerServiceType(() -> {
                                        switch (ADDON_TYPE) {
                                            case GOOGLE:
                                                return GoogleSpeechSynthesizer.class;
                                            default:
                                                return AndroidSpeechSynthesizer.class;
                                        }
                                    })
                                    .build());
                    component.setup(this, status -> {
                        if (status.isAvailable()) {
                            recognizer = component.getSpeechRecognizer(this);
                            synthesizer = component.getSpeechSynthesizer(this);
                            synthesizer.addFilter(new TextFilterForUrl());
                        }
                    });
                });
            }
        }
    }

    private void initActions() {
        add.setOnClickListener(v -> {
            String msg = text.getText().toString().trim();
            int itemPosition = actionSpinner.getSelectedItemPosition();
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
            component.shutdown();
        });
        proceed.setOnClickListener((View v) -> {
            representQueue(-1);
            readAll();
        });
    }

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
                String label = (String) actionAdapter.getItem(item.first);
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
        synthesizer.playText(text, "default",
                (OnSynthesizerStart) s -> {
                    representQueue(index);
                },
                (OnSynthesizerDone) s -> {
                    Log.i(TAG, "on Done index: " + index);
                    Pair<Integer, String> next = queue.get(index + 1);
                    if (next != null && next.first == LISTEN) {
                        synthesizer.holdCurrentQueue();
                        listen(index);
                    } else {
                        if (synthesizer.isEmpty()) {
                            component.shutdown();
                        } else synthesizer.resume();
                    }
                },
                (OnSynthesizerError) (utteranceId, errorCode) -> {
                    if (errorCode == SynthesizerListener.UNKNOWN_ERROR) {
                        if (synthesizer.isEmpty()) {
                            component.shutdown();
                        } else synthesizer.resume();
                    } else {
                        component.shutdown();
                    }
                });
    }

    private void listen(int index) {
        recognizer.listen((OnRecognizerReady) bundle -> {
            representQueue(index + 1);
        }, (OnRecognizerMostConfidentResult) o -> {
            representQueue(-1);
            SparseArray<String> news = getChecks(new SparseArray<>(), index + 1);
            if (news.size() > 0) {
                for (int a = 0; a < news.size(); a++) {
                    if (matches(news.valueAt(a), o)) {
                        representQueue(news.keyAt(a));
                        break;
                    }
                }
            }
            synthesizer.releaseCurrentQueue();
            if (synthesizer.isEmpty()) {
                component.shutdown();
            } else synthesizer.resume();
        }, (OnRecognizerError) (i, i1) -> {
            Log.e(TAG, "Error " + i);
            Log.e(TAG, "Original Error " + getErrorString(i1));

            synthesizer.releaseCurrentQueue();
            if (synthesizer.isEmpty()) {
                component.shutdown();
            } else synthesizer.resume();

//            runOnUiThread(() -> new AlertDialog.Builder(this)
//                    .setTitle("Error")
//                    .setMessage(getErrorString(i1))
//                    .create().show());
        });
    }

    @NonNull
    private String getErrorString(int i1) {
        switch (ADDON_TYPE) {
            case GOOGLE:
                return GoogleSpeechSynthesizer.getErrorType(i1);
            default:
                return AndroidSpeechSynthesizer.getErrorType(i1);
        }
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
        serialThread.addTask(() -> {
            for (int i = 0; i < queue.size(); i++) {
                Log.i(TAG, "readAll index: " + i);
                Pair<Integer, String> item = queue.get(i);
                if (item.first == READ) {
                    play(item.second, i);
                } else if (i == 0 && item.first == LISTEN) {
                    // FIXME: Check why it continues speaking sometimes after listening
                    listen(-1);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //UpdateManager.unregister();
        serialThread.shutdownNow();
        component.shutdown();
    }

    @Override
    public void onResume() {
        super.onResume();
        //CrashManager.register(this);
    }

    private void initViews() {
        execution = findViewById(R.id.execution);
        actionSpinner = findViewById(R.id.spinner);
        addonSpinner = findViewById(R.id.addon);
        text = findViewById(R.id.text);
        add = findViewById(R.id.add);
        clear = findViewById(R.id.clear);
        proceed = findViewById(R.id.proceed);
        scoCheck = findViewById(R.id.bluetooth_sco);

        // Check if there is a Bluetooth device connected and setup the config for a Sco connection
        scoCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !peripheral.get(Peripheral.Type.BLUETOOTH).isConnected()) {
                buttonView.setChecked(false);
                Toast.makeText(this, "Not connected to a Bluetooth device", Toast.LENGTH_LONG).show();
                return;
            }
            component.updateConfiguration(
                    builder -> {
                        builder.setBluetoothScoRequired(() ->
                                peripheral.get(Peripheral.Type.BLUETOOTH).isConnected() && isChecked);
                        return builder.build();
                    });
        });
        proceed.setEnabled(false);

        // Create an ArrayAdapter of the actions
        actionAdapter = ArrayAdapter.createFromResource(this, R.array.actions, android.R.layout.simple_spinner_item);
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(actionAdapter);
        actionSpinner.setSelection(0);
        actionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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

        // Create an ArrayAdapter of the addons
        List<String> addonList = Arrays.asList(addonMap.values().toArray(new String[addonMap.size()]));
        addonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                addonList);
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
