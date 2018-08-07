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
import android.widget.Button;
import android.widget.Spinner;

import com.chattylabs.sdk.android.common.PermissionsHelper;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.ThreadUtils;
import com.chattylabs.sdk.android.voice.AndroidSpeechRecognizer;
import com.chattylabs.sdk.android.voice.AndroidSpeechSynthesizer;
import com.chattylabs.sdk.android.voice.Conversation;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent;
import com.chattylabs.sdk.android.voice.Flow;
import com.chattylabs.sdk.android.voice.GoogleSpeechRecognizer;
import com.chattylabs.sdk.android.voice.GoogleSpeechSynthesizer;
import com.chattylabs.sdk.android.voice.VoiceMatch;
import com.chattylabs.sdk.android.voice.VoiceMessage;
import com.chattylabs.sdk.android.voice.VoiceNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import javax.inject.Inject;

import dagger.android.support.DaggerAppCompatActivity;

public class MainActivity extends DaggerAppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = Tag.make(ConversationCreatorActivity.class);

    private static final String ANDROID = "Android";
    private static final String GOOGLE = "Google";

    private static LinkedHashMap<Integer, String> addonMap = new LinkedHashMap<>();
    static {
        addonMap.put(0, ANDROID);
        addonMap.put(1, GOOGLE);
    }

    private static String ADDON_TYPE = addonMap.get(0);

    @Inject ConversationalFlowComponent component;

    private Button proceed;
    private Spinner addonSpinner;
    private ArrayAdapter<String> addonAdapter;
    private ThreadUtils.SerialThread serialThread;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.demo, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.demo_conversation:
                ContextCompat.startActivity(this,
                        new Intent(this, ConversationCreatorActivity.class), null);
                return true;
            case R.id.demo_components:
                ContextCompat.startActivity(this,
                        new Intent(this, MainActivity.class), null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        serialThread = ThreadUtils.newSerialThread();
        setup();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //UpdateManager.unregister();
        serialThread.shutdownNow();
        component.shutdown();
    }

    private void initViews() {
        addonSpinner = findViewById(R.id.addon);
        proceed = findViewById(R.id.proceed);
        proceed.setOnClickListener(v -> {
            loadConversation();
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

    private void setup() {
        String[] perms = component.requiredPermissions();
        PermissionsHelper.check(this,
                perms,
                () -> onRequestPermissionsResult(
                        PermissionsHelper.REQUEST_CODE, perms,
                        new int[] {PackageManager.PERMISSION_GRANTED}));
    }

    @SuppressLint("MissingPermission")
    private void loadConversation() {
        Conversation conversation = component.create(this);
        Flow flow = conversation.prepare();
        try {
            VoiceNode firstNode = null;
            VoiceNode lastNode = null;
            JSONArray array = new JSONArray(loadJSONFromAsset());
            for (int a = 0, size = array.length(); a < size; a++) {

                JSONObject object = array.getJSONObject(a);

                JSONArray jsonArray = object.getJSONArray("results");
                String[] stringArray = new String[jsonArray.length()];
                for (int i = 0; i < jsonArray.length(); i++) {
                    stringArray[i]= jsonArray.getString(i);
                }

                VoiceMessage message = VoiceMessage.newBuilder()
                        .setText(object.getString("message")).build();
                VoiceMatch match = VoiceMatch.newBuilder()
                        .setExpectedResults(stringArray).build();

                conversation.addNode(message);
                conversation.addNode(match);
                if (lastNode != null) flow.from(lastNode).to(message);
                if (firstNode == null) firstNode = message;
                flow.from(message).to(match);
                lastNode = match;
            }

            conversation.start(firstNode);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = getAssets().open("conversation.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
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
                    component.setup(this, status -> {});
                });
            }
        }
    }
}
