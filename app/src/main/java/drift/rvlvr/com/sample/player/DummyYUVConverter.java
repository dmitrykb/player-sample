package drift.rvlvr.com.sample.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Nick on 2017-12-21.
 */

public class DummyYUVConverter {

    private ByteBuffer mBytebuffer = null;
    private Bitmap bmp = null;

    public Bitmap convert(ByteBuffer yuvSample) {

        if (bmp != null) {
            bmp.recycle();
            bmp = null;
        }

        if (mBytebuffer == null || mBytebuffer.capacity() < yuvSample.limit())
            mBytebuffer = ByteBuffer.allocate(yuvSample.limit());

        mBytebuffer.clear();
        mBytebuffer.put(yuvSample);
        mBytebuffer.flip();
        YuvImage yuvImage = new YuvImage(mBytebuffer.array(), ImageFormat.YUY2, 1920, 1080, null);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, 1920, 1080), 100, stream);

        bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

        return bmp;
    }
}
