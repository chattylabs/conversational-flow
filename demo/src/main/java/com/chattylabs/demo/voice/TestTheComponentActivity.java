package com.chattylabs.demo.voice;

import android.media.AudioManager;
import android.os.Bundle;
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

import com.chattylabs.android.commons.HtmlUtils;
import com.chattylabs.android.commons.Tag;
import com.chattylabs.sdk.android.voice.Peripheral;
import com.chattylabs.sdk.android.voice.RecognizerListener;
import com.chattylabs.sdk.android.voice.SynthesizerListener;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.matches;


public class TestTheComponentActivity extends BaseActivity {

    private static final String TAG = Tag.make(TestTheComponentActivity.class);

    // Constants
    private static final int CHECK = 3;
    private static final int LISTEN = 2;
    private static final int READ = 1;
    // ...

    // Resources
    private TextView execution;
    private Spinner actionSpinner;
    private EditText text;
    private Button add;
    private Button clear;
    private Button proceed;
    private SparseArray<Pair<Integer, String>> queue = new SparseArray<>();
    private ArrayAdapter<CharSequence> actionAdapter;

    // Components
    private Peripheral peripheral;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_the_component);
        peripheral = new Peripheral((AudioManager) getSystemService(AUDIO_SERVICE));
        initCommonViews();
        initViews();
        initActions();
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
                if (tx == null) {
                    tx = new StringBuilder();
                }
                tx.append("<br/><br/> > ").append(action);
                isChecking = item.first == CHECK;
            }
        }
        final StringBuilder copy = tx;

        runOnUiThread(() -> {
            if (copy != null) execution.setText(HtmlUtils.from(copy.toString()));
        });
    }

    private void playByIndex(String text, int index) {
        synthesizer.playText(text, "default",
                (SynthesizerListener.OnStart) utteranceId -> {
                    representQueue(index);
                },
                (SynthesizerListener.OnDone) utteranceId -> {
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
                (SynthesizerListener.OnError) (utteranceId, errorCode) -> {
                    if (errorCode == SynthesizerListener.Status.UNKNOWN_ERROR) {
                        if (synthesizer.isEmpty()) {
                            component.shutdown();
                        } else synthesizer.resume();
                    } else {
                        component.shutdown();
                    }
                });
    }

    private void listen(int index) {
        recognizer.listen((RecognizerListener.OnReady) bundle -> {
            representQueue(index + 1);
        }, (RecognizerListener.OnMostConfidentResult) o -> {
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
            synthesizer.freeCurrentQueue();
            if (synthesizer.isEmpty()) {
                component.shutdown();
            } else synthesizer.resume();
        }, (RecognizerListener.OnError) (i, i1) -> {
            Log.e(TAG, "Error " + i);

            synthesizer.freeCurrentQueue();
            if (synthesizer.isEmpty()) {
                component.shutdown();
            } else synthesizer.resume();

            //runOnUiThread(() -> new AlertDialog.Builder(this)
            //        .setTitle("Error")
            //        .setMessage(getErrorString(i1))
            //        .create().show());
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
        serialThread.addTask(() -> {
            Log.i(TAG, "Start processing the queue");
            for (int i = 0; i < queue.size(); i++) {
                Log.i(TAG, "queue index: " + i);
                Pair<Integer, String> item = queue.get(i);
                if (item.first == READ) {
                    playByIndex(item.second, i);
                } else if (i == 0 && item.first == LISTEN) {
                    // FIXME: Check why it continues speaking sometimes after listening
                    listen(-1);
                }
            }
        });
    }

    private void initViews() {
        execution = findViewById(R.id.execution);
        actionSpinner = findViewById(R.id.spinner);
        text = findViewById(R.id.text);
        add = findViewById(R.id.add);
        clear = findViewById(R.id.clear);
        proceed = findViewById(R.id.proceed);
        CheckBox scoCheck = findViewById(R.id.bluetooth_sco);

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
    }
}
