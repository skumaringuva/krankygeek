package es.no2.rtcapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.URISyntaxException;
import java.util.ArrayList;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {

    private static final String SIGNALING_URI = "http://192.168.0.13:5000";
    private static final String VIDEO_TRACK_ID = "video1";
    private static final String AUDIO_TRACK_ID = "audio1";
    private static final String LOCAL_STREAM_ID = "stream1";
    private static final String SDP_MID = "sdpMid";
    private static final String SDP_M_LINE_INDEX = "sdpMLineIndex";
    private static final String SDP = "sdp";
    private static final String CREATEOFFER = "createoffer";
    private static final String OFFER = "offer";
    private static final String ANSWER = "answer";
    private static final String CANDIDATE = "candidate";

    private PeerConnectionFactory peerConnectionFactory;
    private VideoSource localVideoSource;
    private PeerConnection peerConnection;
    private MediaStream localMediaStream;
    private VideoRenderer otherPeerRenderer;
    private Socket socket;
    private boolean createOffer = false;
    private GLSurfaceView videoView;
    FrameLayout surfaceHolder;

    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 1101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        surfaceHolder = (FrameLayout) findViewById(R.id.surface_holder);
        requestPermission();


    }

    private void requestPermission(){

        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        else {
            initWebRTC();
        }

    }



    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    initWebRTC();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }




    private void initWebRTC(){

        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);

        PeerConnectionFactory.initializeAndroidGlobals(
                this,  // Context
                true,  // Audio Enabled
                true,  // Video Enabled
                true,  // Hardware Acceleration Enabled
                null); // Render EGL Context

        peerConnectionFactory = new PeerConnectionFactory();

        VideoCapturerAndroid vc = VideoCapturerAndroid.create(VideoCapturerAndroid.getNameOfFrontFacingDevice(), null);

        localVideoSource = peerConnectionFactory.createVideoSource(vc, new MediaConstraints());
        VideoTrack localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource);
        localVideoTrack.setEnabled(true);

        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        AudioTrack localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(true);

        localMediaStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
        localMediaStream.addTrack(localVideoTrack);
        localMediaStream.addTrack(localAudioTrack);

        videoView =  new GLSurfaceView(this);//findViewById(R.id.glview_call);

        surfaceHolder.addView(videoView);

        VideoRendererGui.setView(videoView, null);
        try {
            otherPeerRenderer = VideoRendererGui.createGui(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);
            VideoRenderer renderer = VideoRendererGui.createGui(50, 50, 50, 50, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);
            localVideoTrack.addRenderer(renderer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void onConnect(View button) {
        if (peerConnection != null)
            return;

        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        peerConnection = peerConnectionFactory.createPeerConnection(
                iceServers,
                new MediaConstraints(),
                peerConnectionObserver);

        peerConnection.addStream(localMediaStream);

        try {
            socket = IO.socket(SIGNALING_URI);
            socket.on(CREATEOFFER, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    createOffer = true;
                    peerConnection.createOffer(sdpObserver, new MediaConstraints());
                }

            }).on(OFFER, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    try {
                        JSONObject obj = (JSONObject) args[0];
                        SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER,
                                obj.getString(SDP));
                        peerConnection.setRemoteDescription(sdpObserver, sdp);
                        peerConnection.createAnswer(sdpObserver, new MediaConstraints());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }).on(ANSWER, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    try {
                        JSONObject obj = (JSONObject) args[0];
                        SessionDescription sdp = new SessionDescription(SessionDescription.Type.ANSWER,
                                obj.getString(SDP));
                        peerConnection.setRemoteDescription(sdpObserver, sdp);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }).on(CANDIDATE, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    try {
                        JSONObject obj = (JSONObject) args[0];
                        peerConnection.addIceCandidate(new IceCandidate(obj.getString(SDP_MID),
                                obj.getInt(SDP_M_LINE_INDEX),
                                obj.getString(SDP)));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            });

            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    SdpObserver sdpObserver = new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            peerConnection.setLocalDescription(sdpObserver, sessionDescription);
            try {
                JSONObject obj = new JSONObject();
                obj.put(SDP, sessionDescription.description);
                if (createOffer) {
                    socket.emit(OFFER, obj);
                } else {
                    socket.emit(ANSWER, obj);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSetSuccess() {

        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
    };

    PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d("RTCAPP", "onSignalingChange:" + signalingState.toString());
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d("RTCAPP", "onIceConnectionChange:" + iceConnectionState.toString());
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            try {
                JSONObject obj = new JSONObject();
                obj.put(SDP_MID, iceCandidate.sdpMid);
                obj.put(SDP_M_LINE_INDEX, iceCandidate.sdpMLineIndex);
                obj.put(SDP, iceCandidate.sdp);
                socket.emit(CANDIDATE, obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            mediaStream.videoTracks.getFirst().addRenderer(otherPeerRenderer);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }
    };
}
