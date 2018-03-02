package com.betelge.rvlvr;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.betelge.rvlvr.gvr.DriftRenderer;
import com.betelge.rvlvr.gvr.GVRenderer;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import java.nio.ByteBuffer;

public class ViewActivity extends GvrActivity implements View.OnTouchListener {

    private Player player;
    private GVRenderer renderer;

    private static String VR_PREF_KEY = "vr_enabled";
    private boolean backButtonPressed = false;

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gestureDetector =
                new GestureDetector(this, new GestureDetector.SimpleOnGestureListener(){
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        renderer.doubleTap();
                        return true;
                    }
                    @Override
                    public void onLongPress(MotionEvent e) {
                        renderer.longPress();
                    }
                });

        setContentView(R.layout.activity_viewer);

        initGVR();

        initPlayer();
    }

    private void initGVR() {
        renderer = new GVRenderer(this);
        final GvrView gvrView = findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        renderer.setSignalAspectRatio(2, 1);

        gvrView.setRenderer(renderer);

        gvrView.setOnTouchListener(this);
        gvrView.setOnCloseButtonListener(new Runnable() {
            public void run() {
                if(!backButtonPressed)
                    setVR(false);
                backButtonPressed = false;
            }
        });
        gvrView.setTransitionViewEnabled(false);
        gvrView.enableCardboardTriggerEmulation();
        //gvrView.setStereoModeEnabled(false);

        gvrView.setNeckModelEnabled(false);

        renderer.setResolution(3840, 2160);
        //renderer.setColorspace(DriftRenderer.COLORSPACE_NV12);
        //renderer.setNoWrap(true);
        renderer.setSignalType(DriftRenderer.SIGNAL_TYPE_MONO);
        renderer.setProjectionAngle(360);

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        setGvrView(gvrView);

        // Get and apply VR mode. Default to non-VR on first run.
        setVR(getPreferences(MODE_PRIVATE).getBoolean(VR_PREF_KEY, false));
    }

    void setVR(boolean vrEnabled) {
        getGvrView().setStereoModeEnabled(vrEnabled);
        renderer.setProjectionType( vrEnabled ?
                DriftRenderer.PROJECTION_TYPE_VR : DriftRenderer.PROJECTION_TYPE_NOVR );
        findViewById(R.id.vrButton).setVisibility( vrEnabled ? View.GONE : View.VISIBLE );
    }

    private void initPlayer() {
        player = new Player(this, new Player.OnFrameListener() {
            @Override
            public void onFrame(ByteBuffer frame) {
                renderer.drawFrame(frame);
            }
        });
        player.play();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if(gestureDetector.onTouchEvent(motionEvent))
            return true;

        if(motionEvent.getAction() == MotionEvent.ACTION_MOVE
                && motionEvent.getHistorySize() >= 1) {
            renderer.drag(motionEvent.getX() - motionEvent.getHistoricalX(0),
                    motionEvent.getY() - motionEvent.getHistoricalY(0));
            return true;
        }

        return false;
    }

    @Override
    public void onPause() {
        getPreferences(MODE_PRIVATE).edit().putBoolean(VR_PREF_KEY,
                getGvrView().getStereoModeEnabled()).commit();
        super.onPause();
    }

    public void onVRButtonClick(View v) {
        setVR(true);
    }

    // Override back button so it doesn't change VR mode before exiting
    @Override
    public void onBackPressed() {
        backButtonPressed = true;
        super.onBackPressed();
    }
}
