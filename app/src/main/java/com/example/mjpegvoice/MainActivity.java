package com.example.mjpegvoice;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private AudioRecord recorder;  // 오디오 녹음을 위한 객체
    private boolean isRecording = false;  // 녹음 상태를 나타내는 변수
    private Socket socket;  // 서버와 연결할 소켓
    private OutputStream outputStream;  // 서버로 데이터를 전송할 출력 스트림

    int g_sampleRate = 44100;  // 샘플링 레이트
    int g_channel = AudioFormat.CHANNEL_IN_MONO;  // 모노 채널
    int g_format = AudioFormat.ENCODING_PCM_16BIT;  // 16비트 오디오 포맷


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI 요소 초기화
        Button connectButton = findViewById(R.id.connectButton);
        WebView webView = findViewById(R.id.webView);
        Button micButton = findViewById(R.id.micButton);
        AutoCompleteTextView ipInput = findViewById(R.id.ipInput);

        loadRecentIps(ipInput); //이전의 접속 아이피를 읽어온다.

        // WebView 설정 (JavaScript 활성화, 캐시 비활성화)
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.setWebViewClient(new WebViewClient());

        // 서버와 연결 버튼 클릭 시 : 서버는 미리 실행되어 접속 대기되어 있어야 함.
        connectButton.setOnClickListener(v -> {
            // 입력된 IP를 가져와서 MJPEG 스트리밍 URL로 설정
            String ip = ipInput.getText().toString().trim();
            if (!ip.isEmpty()) {
                saveRecentIp(ip);         // IP 저장
                String url = "http://" + ip + ":8080/video";
                webView.loadUrl(url);  // WebView에 URL 로드
            }
        });

        // 마이크 버튼에 터치 이벤트 리스너 설정 -----------------------------------
        micButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:  // 버튼을 누를 때
                    micButton.setText("Recording...");
                    micButton.setBackgroundColor(Color.RED);  // 버튼 색상 변경
                    startRecording(ipInput.getText().toString().trim());  // 녹음 시작
                    return true;
                case MotionEvent.ACTION_UP:  // 버튼에서 손을 뗄 때
                case MotionEvent.ACTION_CANCEL:  // 취소 시
                    micButton.setText("Hold to Record");
                    micButton.setBackgroundColor(Color.DKGRAY);  // 버튼 색상 복원
                    stopRecording();  // 녹음 중지
                    return true;
            }
            return false;
        });



        // 테스트 오디오 파일 전송 버튼 클릭 시 ---------------------------------
        findViewById(R.id.micTestButton).setOnClickListener(v -> {
            String ip = ipInput.getText().toString().trim();
            if (!ip.isEmpty()) {
                sendTestAudioFile(ip);  // 테스트 오디오 파일 전송
            }
        });
    }

    //최근의 IP를 SharedPreferences에 저장한다.

    private void saveRecentIp(String newIp) {
        SharedPreferences prefs = getSharedPreferences("IP_PREFS", MODE_PRIVATE);
        String json = prefs.getString("recent_ips", "[]");

        try {
            JSONArray jsonArray = new JSONArray(json);
            List<String> ipList = new ArrayList<>();

            // 기존 IP 가져오기
            for (int i = 0; i < jsonArray.length(); i++) {
                String ip = jsonArray.getString(i);
                if (!ip.equals(newIp)) {
                    ipList.add(ip);  // 중복 제거
                }
            }

            // 새 IP를 맨 위에 추가
            ipList.add(0, newIp);

            // 최대 5개로 자르기
            if (ipList.size() > 5) {
                ipList = ipList.subList(0, 5);
            }

            // 다시 JSON으로 저장
            JSONArray newJsonArray = new JSONArray();
            for (String ip : ipList) {
                newJsonArray.put(ip);
            }

            prefs.edit().putString("recent_ips", newJsonArray.toString()).apply();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 녹음 시작 메소드
    private void startRecording(String ip) {
        int BUFFER_SIZE = AudioRecord.getMinBufferSize(g_sampleRate, g_channel, g_format);  // 최소 버퍼 크기
        Log.d("AudioRecord", "Buffer size: " + BUFFER_SIZE);

        // 오디오 녹음 객체 생성
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 0);
            return;
        }

        // 기존의 recorder가 있으면 먼저 종료하고, 새로운 객체 생성
        if (recorder != null) {
            recorder.stop();
            recorder.release();
        }

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, g_sampleRate, g_channel, g_format, BUFFER_SIZE);
        recorder.startRecording();  // 녹음 시작
        isRecording = true;  // 녹음 상태 업데이트

        new Thread(() -> {
            try {
                // 서버와 연결
                socket = new Socket(ip, 50005);
                Log.d("Socket", "Connected to server at " + ip + ":50005");
                outputStream = socket.getOutputStream();


                while (isRecording) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        // 데이터 읽은 크기 로그 출력
                        Log.d("AudioRecord", "read: " + read);

                        // 첫 10바이트 출력 (디버깅용)
                        Log.d("AudioRecord", "first 10 bytes: " + Arrays.toString(Arrays.copyOf(buffer, 10)));

                        // 서버로 데이터 전송
                        outputStream.write(buffer, 0, read);
                    }else {
                        // 데이터를 읽지 못한 경우 디버깅
                        Log.d("AudioRecord", "No data to send (read = 0).");
                    }
                }

                // 녹음 종료 후 소켓 종료
                socket.close();
            } catch (IOException e) {
                Log.e("AudioRecord", "Socket error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // 녹음 중지 메소드
    private void stopRecording() {
        isRecording = false;
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        try {
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendTestAudioFile(String ip) {
        int BUFFER_SIZE = AudioRecord.getMinBufferSize(g_sampleRate, g_channel, g_format);  // 최소 버퍼 크기

        new Thread(() -> {
            try {
                // 서버와 연결
                Socket socket = new Socket(ip, 50005);
                OutputStream outputStream = socket.getOutputStream();

                // 리소스에서 오디오 파일 열기
                InputStream inputStream = getResources().openRawResource(R.raw.test_audio_mono);

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                // 오디오 파일 데이터를 서버로 전송
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    // 데이터 읽은 크기 로그 출력
                    Log.d("sendTestAudioFile", "read: " + bytesRead);

                    // 첫 10바이트 출력 (디버깅용)
                    Log.d("sendTestAudioFile", "first 10 bytes: " + Arrays.toString(Arrays.copyOf(buffer, 10)));

                    // 서버로 데이터 전송

                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
                outputStream.close();
                inputStream.close();
                socket.close();

                Log.d("AudioTest", "오디오 파일 전송 완료");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadRecentIps(AutoCompleteTextView ipInput) {

        SharedPreferences prefs = getSharedPreferences("IP_PREFS", MODE_PRIVATE);
        String json = prefs.getString("recent_ips", "[]");

        try {
            JSONArray jsonArray = new JSONArray(json);
            List<String> ipList = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                ipList.add(jsonArray.getString(i));
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, ipList);
            ipInput.setAdapter(adapter);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
