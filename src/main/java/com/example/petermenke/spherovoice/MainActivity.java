package com.example.petermenke.spherovoice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import android.view.View.OnClickListener;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.util.ArrayList;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;
import com.orbotix.ConvenienceRobot;
import com.orbotix.DualStackDiscoveryAgent;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;
import com.orbotix.le.RobotLE;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// Sphero code from HelloWorld example on the beta branch
// Speach code from http://stackoverflow.com/questions/4975443/is-there-a-way-to-use-the-speechrecognizer-api-directly-for-speech-input


public class MainActivity extends AppCompatActivity implements RobotChangedStateListener {

    private ConvenienceRobot mRobot;
    private DualStackDiscoveryAgent mDiscoveryAgent;
    private float direction;

    private static final int REQUEST_CODE_LOCATION_PERMISSION = 42;

    private TextView mText;
    private SpeechRecognizer sr;
    private FloatingActionButton fab;
    private ProgressBar progress;
    private static final String TAG = "MyStt3Activity";

    private ArrayList<Pattern> commands;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // GENERAL DEFINITIONS
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // DEFINE SPEECH STUFF
        mText = (TextView) findViewById(R.id.textView1);
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new listener());

        direction = 0.0f;

        // DEFINE FAB
        fab = (FloatingActionButton) findViewById(R.id.fab2);
        fab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                //fab.setImageResource(R.drawable.ic_mic_black);

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");

                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                sr.startListening(intent);
                Log.i("111111", "11111111");
            }
        });

        //CUSTOM SCRIPT
        commands = new ArrayList<Pattern>();
        commands.add(Pattern.compile("(turn)\\s(right|left|around)"));
        commands.add(Pattern.compile("(forward|backward)\\s(\\d+)"));

        progress = (ProgressBar) findViewById(R.id.progressBar2);
        progress.setVisibility(View.VISIBLE);

        /*
            Associate a listener for robot state changes with the DualStackDiscoveryAgent.
            DualStackDiscoveryAgent checks for both Bluetooth Classic and Bluetooth LE.
            DiscoveryAgentClassic checks only for Bluetooth Classic robots.
            DiscoveryAgentLE checks only for Bluetooth LE robots.
       */
        mDiscoveryAgent = new DualStackDiscoveryAgent();
        mDiscoveryAgent.addRobotStateListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int hasLocationPermission = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (hasLocationPermission != PackageManager.PERMISSION_GRANTED) {
                Log.e("Sphero", "Location permission has not already been granted");
                List<String> permissions = new ArrayList<String>();
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                requestPermissions(permissions.toArray(new String[permissions.size()]), REQUEST_CODE_LOCATION_PERMISSION);
            } else {
                Log.d("Sphero", "Location permission already granted");
            }
        }
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOCATION_PERMISSION: {
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        startDiscovery();
                        Log.d("Permissions", "Permission Granted: " + permissions[i]);
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d("Permissions", "Permission Denied: " + permissions[i]);
                    }
                }
            }
            break;
            default: {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    //Turn the robot LED on or off every two seconds
    private void blink(final boolean lit) {
        if (mRobot == null)
            return;

        if (lit) {
            mRobot.setLed(0.0f, 0.0f, 0.0f);
        } else {
            mRobot.setLed(0.0f, 0.0f, 1.0f);
        }

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                blink(!lit);
            }
        }, 2000);
    }

    @Override
    protected void onStart() {
        super.onStart();// ATTENTION: This was auto-generated to implement the App Indexing API.
// See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        startDiscovery();

    }

    private void startDiscovery() {
        //If the DiscoveryAgent is not already looking for robots, start discovery.
        if (!mDiscoveryAgent.isDiscovering()) {
            try {
                mDiscoveryAgent.startDiscovery(getApplicationContext());
            } catch (DiscoveryException e) {
                Log.e("Sphero", "DiscoveryException: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onStop() {
        //If the DiscoveryAgent is in discovery mode, stop it.
        if (mDiscoveryAgent.isDiscovering()) {
            mDiscoveryAgent.stopDiscovery();
        }

        //If a robot is connected to the device, disconnect it
        if (mRobot != null) {
            mRobot.disconnect();
            mRobot = null;
        }

        super.onStop();// ATTENTION: This was auto-generated to implement the App Indexing API.

        client.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDiscoveryAgent.addRobotStateListener(null);
    }

    @Override
    public void handleRobotChangedState(Robot robot, RobotChangedStateNotificationType type) {
        Log.d(TAG, "CHANGED STATE");

        // Offline, Connecting, Connected, Online, Disconnected, FailedConnect;

        switch (type) {
            case Online: {
                Log.d(TAG, "Status: Sphero Online");
                logMessage("Status: Sphero Online");

                //If robot uses Bluetooth LE, Developer Mode can be turned on.
                //This turns off DOS protection. This generally isn't required.
                if (robot instanceof RobotLE) {
                    ((RobotLE) robot).setDeveloperMode(true);
                }

                //Save the robot as a ConvenienceRobot for additional utility methods
                mRobot = new ConvenienceRobot(robot);

                //Start blinking the robot's LED
                blink(false);

                // This is where the interesting code goes??? //
                progress.setVisibility(View.GONE);
                fab.setImageResource(R.drawable.ic_mic_black);

                break;
            }
            case Offline: {
                logMessage("Status: Sphero Offline");
                break;
            }
            case Connecting: {
                logMessage("Status: Sphero Connecting");
                break;
            }
            case Disconnected: {
                logMessage("Status: Sphero Disconnected");
                break;
            }
            case FailedConnect: {
                logMessage("Status: Sphero Failed to Connect");
                break;
            }
        }
    }

    public void logMessage(String msg) {
        String prevText = mText.getText().toString();

        ArrayList<String> arr = new ArrayList<>(Arrays.asList(prevText.split("\n")));
        arr.add(msg);
        if (arr.size() > 8) {
            arr.remove(0);
        }

        mText.setText(TextUtils.join("\n", arr));
    }


    class listener implements RecognitionListener {
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
        }

        public void onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech");
        }

        public void onRmsChanged(float rmsdB) {
            Log.d(TAG, "onRmsChanged");
        }

        public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "onBufferReceived");
        }

        public void onEndOfSpeech() {
            Log.d(TAG, "onEndofSpeech");
        }

        public void onError(int error) {
            Log.d(TAG, "error " + error);
            logMessage("Command: Unknown");
        }

        public void onResults(Bundle results) {
            String str = new String();
            Log.d(TAG, "onResults " + results);
            ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            fab.setImageResource(R.drawable.ic_mic_none_black);


            for (int i = 0; i < data.size(); i++) {
                Log.d(TAG, "result " + data.get(i));
                str += data.get(i);


                // CUSTOM LOGIC
                for (Pattern p : commands) {
                    Matcher m = p.matcher("" + data.get(i));
                    if (m.find()) {

                        String cmd = "";

                        if (m.group(1).equals("turn")) {
                            Log.d(TAG, "TURNING: " + m.group(2));
                            cmd = m.group(0);

                            if (m.group(2).equalsIgnoreCase("right")) {
                                direction += 90f;
                            } else if (m.group(2).equalsIgnoreCase("left")){
                                direction -= 90f;
                            } else {
                                direction += 180f;
                            }
                            mRobot.rotate(direction);

                        } else { // forward / back
                            Log.d(TAG, "MOVING: " + m.group(1) + " by " + m.group(2));
                            cmd = m.group(0);

                            Log.d(TAG, "group 1: " + m.group(1));

                            float speed = Float.parseFloat(m.group(2));


                            if (m.group(1).equalsIgnoreCase("backward")) {

                                mRobot.drive(direction + 180, speed / 10f);

                                Log.d(TAG, "GOUING BACK");
                            } else {
                                mRobot.drive(direction, speed / 10f);
                            }

                            //mRobot.drive(0, 0.5f, reverse);

                        }

                        String prevText = mText.getText().toString();

                        /** ArrayList<String> arr = new ArrayList<>(Arrays.asList(prevText.split("\n")));
                        arr.add(cmd);
                        if (arr.size() > 8) {
                            arr.remove(0);
                        }

                        mText.setText(TextUtils.join("\n", arr));
                        **/

                        logMessage("Command: " + cmd);

                        break; // stops from mathing multiple times
                    }
                }
            }
        }

        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "onPartialResults");
        }

        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "onEvent " + eventType);
        }
    }
}
