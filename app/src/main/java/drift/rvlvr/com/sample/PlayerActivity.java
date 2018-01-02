package drift.rvlvr.com.sample;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;


import com.google.vr.sdk.widgets.pano.VrPanoramaView;

import java.nio.ByteBuffer;

import drift.rvlvr.com.sample.player.DummyYUVConverter;
import drift.rvlvr.com.sample.player.Player;


public class PlayerActivity extends Activity {

    private VrPanoramaView panoramaView;
    private DummyYUVConverter converter;

    private Player player;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_player);

        panoramaView = findViewById(R.id.pano_view);

        converter = new DummyYUVConverter();

        player = new Player(this, new Player.OnFrameListener() {
            @Override
            public void onFrame(ByteBuffer frame) {
                Bitmap bmp = converter.convert(frame);
                panoramaView.loadImageFromBitmap(bmp, null);
            }
        });

        player.play();

        super.onCreate(savedInstanceState);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        panoramaView.resumeRendering();
        super.onResume();
    }

    @Override
    protected void onPause() {
        panoramaView.pauseRendering();
        super.onPause();
    }

}
