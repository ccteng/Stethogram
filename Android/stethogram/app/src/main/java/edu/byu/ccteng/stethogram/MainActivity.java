package edu.byu.ccteng.stethogram;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.MediaRouter;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.visualizer.amplitude.AudioRecordView;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    final String LOG_TAG = "stethogram";
    private static final int RECORD_AUDIO_PERMISSION_CODE = 100;

    TextView txtMain;
    TextView txtDevice;
    Timer timer;
    Switch swDevice;
    Switch swFile;
    Random random = new Random();

    MediaRecorder mediaRecorder;
    AudioRecordView audioRecordView;
    int amp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // check microphone permission
        checkPermission(Manifest.permission.RECORD_AUDIO, RECORD_AUDIO_PERMISSION_CODE);

        txtMain = (TextView) findViewById(R.id.textView);
        txtDevice = (TextView) findViewById(R.id.txtDevice);

        swDevice = (Switch) findViewById(R.id.swDevice);
        swDevice.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    m_isRun = true;
                } else {
                    m_isRun = false;
                    m_count = 0;
                }
            }
        });

        // start backgound thread
        do_loopback();

        swFile = (Switch) findViewById(R.id.swFile);
        swFile.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // TBD: begin record to file
                } else {
                    // TBD: end record to file
                }
            }
        });

        audioRecordView = findViewById(R.id.audioRecordView);
        timer = new Timer();
        TimerTask timerTask = new TimerTask() {

            @Override
            public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int currentMaxAmplitude = 10 * amp;
                    audioRecordView.update(currentMaxAmplitude);
                }
            });
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 100);

        Button btnRestart = (Button) findViewById(R.id.button3);
        btnRestart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            // start app
            Intent mStartActivity = new Intent(MainActivity.this, MainActivity.class);
            int mPendingIntentId = 123456;
            PendingIntent mPendingIntent = PendingIntent.getActivity(MainActivity.this, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager mgr = (AlarmManager)MainActivity.this.getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
            System.exit(0);
            }
        });

        // get selected route
        router = (MediaRouter)getSystemService(Context.MEDIA_ROUTER_SERVICE);
        routeSelected = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
        CharSequence name = routeSelected.getDescription();
        if (name == null)
            name = routeSelected.getName();
        txtDevice.setText(name);
        Log.i(LOG_TAG, "Selected:" + name.toString() + ":" + routeSelected.getDeviceType());

        for (int i = 0; i < router.getRouteCount(); i++) {
            // read available routes
            MediaRouter.RouteInfo routeInfo = router.getRouteAt(i);
            name = routeInfo.getDescription();
            if (name == null)
                name = routeInfo.getName();
            Log.i(LOG_TAG, name.toString() + ":" + routeInfo.getDeviceType());

            if (routeInfo.getDeviceType() == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH) {
                // save Bluetooth route
                routeBT = routeInfo;
            }
        }

        if (routeBT != null) {
            // found a Bluetooth device
            if (routeSelected.getDeviceType() != MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH) {
                // select Bluetooth route
                router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, routeBT);

                // confirm slected route
                routeSelected = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
                name = routeSelected.getDescription();
                if (name == null)
                    name = routeSelected.getName();
                Log.i(LOG_TAG, "Selected:" + name.toString() + ":" + routeSelected.getDeviceType());
            }

            startTimer();
        } else {
            // no Bluetooth device
            txtMain.setText("No Bluetooth audio device.\nPlease connect and restart app.");
        }
    }

    // Function to check and request permission.
    public void checkPermission(String permission, int requestCode)
    {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission)
                == PackageManager.PERMISSION_DENIED) {

            // Requesting the permission
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[] { permission },
                    requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super
                .onRequestPermissionsResult(requestCode,
                        permissions,
                        grantResults);

        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // microphone permission granted
            }
            else {
                Toast.makeText(MainActivity.this,
                        "Microphone Permission Denied",
                        Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    public void startTimer() {
        timer = new Timer();
        TimerTask timerTask = new TimerTask() {

            @Override
            public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // TBD: update UI
                }
            });
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 1000);
    }

    MediaRouter router;
    MediaRouter.RouteInfo routeSelected;
    MediaRouter.RouteInfo routeBT = null;
    boolean m_isRun = false;
    int m_count = 0;
    int SAMPLE_RATE = 44100;
    int BUF_SIZE = 256;
    short[] buffer = new short[BUF_SIZE];
    AudioRecord m_record;
    AudioTrack m_track;
    NoiseSuppressor m_suppressor;
    AcousticEchoCanceler m_canceler;
    Thread m_thread;

    private void do_loopback() {
        m_thread = new Thread() {
            public void run() {
                CharSequence name;

                // check microphone permission
                while (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_DENIED) {
                    try {
                        // thread to sleep for 1000 milliseconds
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

                router = (MediaRouter)getSystemService(Context.MEDIA_ROUTER_SERVICE);
                routeSelected = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);

                for (int i = 0; i < router.getRouteCount(); i++) {
                    MediaRouter.RouteInfo routeInfo = router.getRouteAt(i);
                    name = routeInfo.getDescription();
                    if (name == null)
                        name = routeInfo.getName();
                    Log.i(LOG_TAG, name.toString() + ":" + routeInfo.getDeviceType());

                    if (routeInfo.getDeviceType() == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH)
                        routeBT = routeInfo;
                }

                if (routeBT != null) {
                    // select Bluetooth route
                    router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, routeBT);

                    // confirm slected route
                    routeSelected = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
                    name = routeSelected.getDescription();
                    if (name == null)
                        name = routeSelected.getName();
                    Log.i(LOG_TAG, "Selected:" + name.toString() + ":" + routeSelected.getDeviceType());

                    // exit thread if failed?
                }

                // stream audio
                int buffersize = BUF_SIZE;
                try {
                    buffersize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);

                    if (buffersize <= BUF_SIZE) {
                        buffersize = BUF_SIZE;
                    }
                    Log.i(LOG_TAG,"Initializing Audio Record and Audio Playing objects");

                    m_record = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, buffersize * 1);
                    if (NoiseSuppressor.isAvailable()) {
                        m_suppressor = NoiseSuppressor.create(m_record.getAudioSessionId());
                    }
                    if (AcousticEchoCanceler.isAvailable()) {
                        m_canceler = AcousticEchoCanceler.create(m_record.getAudioSessionId());
                    }

                    m_track = new AudioTrack(AudioManager.STREAM_MUSIC,
                            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, buffersize * 1,
                            AudioTrack.MODE_STREAM);

                    m_track.setPlaybackRate(SAMPLE_RATE);
                } catch (Throwable t) {
                    Log.e("Error", "Initializing Audio Record and Play objects Failed "+t.getLocalizedMessage());
                    return;
                }

                //m_isRun = true;

                m_record.startRecording();
                Log.i(LOG_TAG,"Audio Recording started");
                m_track.play();
                Log.i(LOG_TAG,"Audio Playing started");

                while (true) { //m_isRun) {
                    int samplesRead = m_record.read(buffer, 0, buffer.length);
                    //Log.i(LOG_TAG,"Samples Read: " + samplesRead);

                    if (m_isRun)
                        m_track.write(buffer, 0, samplesRead);

                    //amp = (int)averageAmp(buffer, samplesRead);
                    amp = Math.abs(buffer[0]);

                    yield();
                }

                //m_record.stop();
                //m_track.stop();
                //Log.i(LOG_TAG, "loopback exit");
            }
        };

        m_thread.start();
    }

    double averageAmp(short[] data, int size) {
        double sum = 0;
        for (int i = 0; i < size; i++)
            sum += Math.abs(data[i]);

        return sum / size;
    }
}
