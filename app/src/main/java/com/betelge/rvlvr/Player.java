package com.betelge.rvlvr;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by koroblyaker on 1/1/18.
 */

public class Player {

    int currentFrame = 0;

    private int UPDATE_INTERVAL = 1000 / 30;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    private OnFrameListener listener;

    private ByteBuffer[] data;
    private Context context;


    public Player(Context context, OnFrameListener listener) {
        this.context = context;
        this.listener = listener;
        load();
    }

    public synchronized void play() {

        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                currentFrame++;
                if(currentFrame >= data.length) {
                    currentFrame = 0;
                }
                if(listener != null) {
                    listener.onFrame(getCurrentFrame());
                }
            }
        }, 0L, UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        currentFrame = 0;
        executor.shutdown();
    }

    public ByteBuffer getCurrentFrame() {
        return  data[currentFrame];
    }

    private void load() {

        AssetManager assetManager = context.getAssets();

        try {
            InputStream inputStream = assetManager.open("windows.stereo.NV12.3840x2160.@.30.fps.serialized");
            ObjectInputStream ois = new ObjectInputStream(inputStream);
            byte[][] loaded = (byte[][]) ois.readObject();
            this.data = new ByteBuffer[loaded.length];
            for(int i = 0; i < data.length; i++) {
                data[i] = ByteBuffer.wrap(loaded[i]);
            }
            System.out.println(data);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }


    }


   public interface OnFrameListener {
        void onFrame(ByteBuffer frame);
    }

}
