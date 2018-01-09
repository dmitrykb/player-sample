package com.betelge.rvlvr;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import com.betelge.rvlvr.gvr.DriftRenderer;
import com.betelge.rvlvr.gvr.GVRenderer;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import java.nio.ByteBuffer;

public class ViewActivity extends GvrActivity implements GvrView.OnTouchListener {

    private Player player;
    private GVRenderer renderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_viewer);

        initGVR();

        initPlayer();
    }

    private void initGVR() {
        renderer = new GVRenderer(this, 1920, 1080, GVRenderer.COLORSPACE_YUY2);
        GvrView gvrView = findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        gvrView.setRenderer(renderer);
        gvrView.setOnTouchListener(this);
        gvrView.setTransitionViewEnabled(false);
        gvrView.enableCardboardTriggerEmulation();
        //gvrView.setStereoModeEnabled(false);

        //renderer.setProjectionType(DriftRenderer.PROJECTION_TYPE_NOVR);
        //renderer.setNoWrap(true);
        //renderer.setSignalType(DriftRenderer.SIGNAL_TYPE_STEREO_SIDE_BY_SIDE);
        //renderer.setProjectionAngle(180);

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        setGvrView(gvrView);
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
        if(motionEvent.getAction() == MotionEvent.ACTION_MOVE
                && motionEvent.getHistorySize() >= 1) {
            renderer.drag(motionEvent.getX() - motionEvent.getHistoricalX(0),
                    motionEvent.getY() - motionEvent.getHistoricalY(0));
        }
        return true;
    }
}
