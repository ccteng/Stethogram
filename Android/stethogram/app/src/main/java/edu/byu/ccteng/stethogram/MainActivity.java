package edu.byu.ccteng.stethogram;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.MediaRouter;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO;

public class MainActivity extends AppCompatActivity {

    final String LOG_TAG = "stethogram";
    TextView txtMain;
    Timer timer;
    Button btnStart;
    Button btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtMain = (TextView) findViewById(R.id.textView);

        btnStart = (Button) findViewById(R.id.button1);
        btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            // Do something in response to button click
            if (!m_isRun) {
                do_loopback();
            }
            }
        });

        btnStop = (Button) findViewById(R.id.button2);
        btnStop.setEnabled(false);
        btnStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            // Do something in response to button click
            m_isRun = false;
            m_count = 0;
            }
        });

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

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.MODIFY_AUDIO_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            txtMain.setText("Need Audio Permission ...");
        }

        // get selected route
        router = (MediaRouter)getSystemService(Context.MEDIA_ROUTER_SERVICE);
        routeSelected = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
        CharSequence name = routeSelected.getDescription();
        if (name == null)
            name = routeSelected.getName();
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
            btnStart.setEnabled(false);
            txtMain.setText("No Bluetooth device. Please connect and restart app.");
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
                    if (m_isRun) {
                        switch (m_count % 3) {
                            case 0:
                                txtMain.setText("---");
                                break;
                            case 1:
                                txtMain.setText("\\");
                                break;
                            case 2:
                                txtMain.setText("/");
                                break;
                        }
                        m_count++;
                    } else
                        txtMain.setText("Click Start to Transmit");

                    btnStart.setEnabled(!m_isRun);
                    btnStop.setEnabled(m_isRun);
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
    int BUF_SIZE = 1*1024;
    byte[] buffer = new byte[BUF_SIZE];
    AudioRecord m_record;
    AudioTrack m_track;
    NoiseSuppressor m_suppressor;
    AcousticEchoCanceler m_canceler;
    Thread m_thread;

    private void do_loopback() {
        m_thread = new Thread() {
            public void run() {
                CharSequence name;
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

                m_isRun = true;

                m_record.startRecording();
                Log.i(LOG_TAG,"Audio Recording started");
                m_track.play();
                Log.i(LOG_TAG,"Audio Playing started");

                while (m_isRun) {
                    int bytesRead = m_record.read(buffer, 0, buffer.length);
                    Log.i(LOG_TAG,"Audio bytes: " + bytesRead);

                    m_track.write(buffer, 0, bytesRead);

                    yield();
                }

                m_record.stop();
                m_track.stop();

                Log.i(LOG_TAG, "loopback exit");
            }
        };

        m_thread.start();
    }
}
