package com.betelge.rvlvr.gvr;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.betelge.rvlvr.R;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * Created by koroblyaker on 1/3/18.
 */

public class GVRenderer implements GvrView.StereoRenderer, DriftRenderer {

    private Context context;

    private int skyBoxProgram;
    private int blitProgram;
    private int vertexLoc = 0;
    private int mvpLoc, mvpBlitLoc;
    private int textureUniformLoc, textureUniformBlitLoc;

    private FloatBuffer vertexBuffer;
    private ShortBuffer indexBuffer;
    private float[] mat;
    private int textureName;

    ByteBuffer rawBuffer;
    boolean hasNewFrame = false;

    float[] headView;
    float rot = 0;

    private int videoFrameCount = 0;
    private long lastFrameTime = 0;

    // GLSL based YUV -> RGB converter
    private int fbo;
    private int oldFbo;
    private int yuy2ConverterProgram;
    private int nv12ConverterProgram;
    private int yuvTextureUniformYUY2Loc;
    private int yuvTextureUniformNV12Loc;
    private int yuvTextureName;
    private FloatBuffer quadBuffer;

    // Input signal
    private final int width;
    private final int height;
    private final int colorspace;
    private int stereotype;
    private float aspect;
    private int projectionAngle;

    // Display settings
    private int projectionType;
    private boolean noWrap;

    public final static int COLORSPACE_NV12 = 0;
    public final static int COLORSPACE_YUY2 = 1;


    public GVRenderer(Context context, int width, int height, int colorspace) {
        this.context = context;
        this.colorspace = colorspace;

        mat = new float[16];
        rawBuffer = ByteBuffer.allocateDirect(4 * width * height);
        rawBuffer.order(ByteOrder.nativeOrder());
        rawBuffer.flip();

        this.width = width;
        this.height = height;

        stereotype = DriftRenderer.SIGNAL_TYPE_MONO;
        aspect = 1920f / 1080f;
        projectionAngle = 360;

        projectionType = DriftRenderer.PROJECTION_TYPE_VR;
        noWrap = false;
    }

    public void drawFrame(ByteBuffer frame) {
        rawBuffer.clear();
        rawBuffer.put(frame);
        rawBuffer.position(0);
        hasNewFrame = true;
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {

        createSkybox();

        // Texture to be projected onto sky box
        int[] texLoc = {0};
        GLES20.glGenTextures(1, texLoc, 0);
        textureName = texLoc[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureName);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, null);

        createYUVConverter();
    }

    private void createSkybox() {
        // Sky box shader
        skyBoxProgram = GLES20.glCreateProgram();
        blitProgram = GLES20.glCreateProgram();

        int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        int blitFragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);

        try {
            GLES20.glShaderSource(vertexShader, loadString(R.raw.skybox_v));
            GLES20.glShaderSource(fragmentShader, loadString(R.raw.skybox_f));
            GLES20.glShaderSource(blitFragmentShader, loadString(R.raw.blit_f));
        }
        catch(IOException e) {
            // Can't load shader
            return;
        }

        GLES20.glCompileShader(vertexShader);
        GLES20.glCompileShader(fragmentShader);
        GLES20.glCompileShader(blitFragmentShader);

        GLES20.glAttachShader(skyBoxProgram, vertexShader);
        GLES20.glAttachShader(skyBoxProgram, fragmentShader);
        GLES20.glAttachShader(blitProgram, vertexShader);
        GLES20.glAttachShader(blitProgram, blitFragmentShader);

        GLES20.glBindAttribLocation(skyBoxProgram, vertexLoc, "a_vertex");
        GLES20.glBindAttribLocation(blitProgram, vertexLoc, "a_vertex");

        GLES20.glLinkProgram(skyBoxProgram);
        GLES20.glLinkProgram(blitProgram);

        mvpLoc = GLES20.glGetUniformLocation(skyBoxProgram, "u_mvp");
        mvpBlitLoc = GLES20.glGetUniformLocation(blitProgram, "u_mvp");
        textureUniformLoc = GLES20.glGetUniformLocation(skyBoxProgram, "frame");
        textureUniformBlitLoc = GLES20.glGetUniformLocation(blitProgram, "frame");

        String vertexLog = GLES20.glGetShaderInfoLog(vertexShader);
        String fragmentLog = GLES20.glGetShaderInfoLog(fragmentShader);
        String fragmentLog1 = GLES20.glGetShaderInfoLog(blitFragmentShader);
        //String linkLog = GLES20.glGetProgramInfoLog(skyBoxProgram);

        System.out.print(vertexLog);
        System.out.print(fragmentLog);
        System.out.print(fragmentLog1);
        //System.out.print(linkLog);


        // Sky box mesh
        float[] vertices = {-1,-1,-1, -1,-1,1, -1,1,-1, -1,1,1, 1,-1,-1, 1,-1,1, 1,1,-1, 1,1,1};
        short[] elements = {0, 1, 2, 3, 6, 7, 4, 5, 0, 1, 1, 0, 0, 2, 4, 6, 6, 1, 1, 3, 5, 7};

        ByteBuffer bbFloats = ByteBuffer.allocateDirect(4 * vertices.length);
        bbFloats.order(ByteOrder.nativeOrder());
        vertexBuffer = bbFloats.asFloatBuffer();

        ByteBuffer bbShorts = ByteBuffer.allocateDirect(2 * elements.length);
        bbShorts.order(ByteOrder.nativeOrder());
        indexBuffer = bbShorts.asShortBuffer();

        indexBuffer.put(elements);
        indexBuffer.flip();
        vertexBuffer.put(vertices);
        vertexBuffer.flip();

        GLES20.glEnableVertexAttribArray(vertexLoc);
        GLES20.glVertexAttribPointer(vertexLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
    }

    private void createYUVConverter() {

        // Initialize GLSL based YUV converter
        int[] fbos = {0};
        GLES20.glGenFramebuffers(1, fbos, 0);
        fbo = fbos[0];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, fbos, 0);
        oldFbo = fbos[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, textureName, 0);
        int fbStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, oldFbo);
        assert(fbStatus == GLES20.GL_FRAMEBUFFER_COMPLETE);

        // Shaders for YUV converter
        yuy2ConverterProgram = GLES20.glCreateProgram();
        nv12ConverterProgram = GLES20.glCreateProgram();

        int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        int yuy2FragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        int nv12FragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);

        try {
            GLES20.glShaderSource(vertexShader, loadString(R.raw.converter_v));
            GLES20.glShaderSource(yuy2FragmentShader, loadString(R.raw.converter_yuy2_f));
            GLES20.glShaderSource(nv12FragmentShader, loadString(R.raw.converter_nv12_f));
        }
        catch(IOException e) {
            // Can't load shader
            return;
        }

        GLES20.glCompileShader(vertexShader);
        GLES20.glCompileShader(yuy2FragmentShader);
        GLES20.glCompileShader(nv12FragmentShader);

        GLES20.glAttachShader(yuy2ConverterProgram, vertexShader);
        GLES20.glAttachShader(yuy2ConverterProgram, yuy2FragmentShader);
        GLES20.glAttachShader(nv12ConverterProgram, vertexShader);
        GLES20.glAttachShader(nv12ConverterProgram, nv12FragmentShader);

        GLES20.glBindAttribLocation(yuy2ConverterProgram, vertexLoc, "a_vertex");
        GLES20.glBindAttribLocation(nv12ConverterProgram, vertexLoc, "a_vertex");

        GLES20.glLinkProgram(yuy2ConverterProgram);
        GLES20.glLinkProgram(nv12ConverterProgram);

        yuvTextureUniformYUY2Loc = GLES20.glGetUniformLocation(yuy2ConverterProgram, "tex");
        yuvTextureUniformNV12Loc = GLES20.glGetUniformLocation(nv12ConverterProgram, "tex");

        String vertexLog = GLES20.glGetShaderInfoLog(vertexShader);
        String fragmentLog = GLES20.glGetShaderInfoLog(yuy2FragmentShader);
        String fragmentLog1 = GLES20.glGetShaderInfoLog(nv12FragmentShader);
        //String linkLog = GLES20.glGetProgramInfoLog(skyBoxProgram);

        System.out.print(vertexLog);
        System.out.print(fragmentLog);
        System.out.print(fragmentLog1);
        //System.out.print(linkLog);

        // YUV texture
        int[] texLoc = {0};
        GLES20.glGenTextures(1, texLoc, 0);
        yuvTextureName = texLoc[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextureName);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        if(colorspace == COLORSPACE_YUY2)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width / 2, height, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, null);
        else if(colorspace == COLORSPACE_NV12)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width / 4, height * 2, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, null);

        // Quad geometry
        float[] vertices = {-1, -1, .5f,  1, -1, .5f,  -1, 1, .5f, 1, 1, .5f};

        ByteBuffer bbFloats = ByteBuffer.allocateDirect(4 * vertices.length);
        bbFloats.order(ByteOrder.nativeOrder());
        quadBuffer = bbFloats.asFloatBuffer();
        quadBuffer.put(vertices);
        quadBuffer.flip();

        GLES20.glEnableVertexAttribArray(vertexLoc);
        GLES20.glVertexAttribPointer(vertexLoc, 3, GLES20.GL_FLOAT, false, 0, quadBuffer);
    }

    @Override
    public void onSurfaceChanged(int i, int i1) {

    }

    private void convertYUV() {
        int[] fbos = {0};
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, fbos, 0);
        oldFbo = fbos[0];

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);

        if(colorspace == COLORSPACE_NV12) {
            GLES20.glUseProgram(nv12ConverterProgram);
            GLES20.glUniform1i(yuvTextureUniformNV12Loc, 0);
        }
        else if(colorspace == COLORSPACE_YUY2) {
            GLES20.glUseProgram(yuy2ConverterProgram);
            GLES20.glUniform1i(yuvTextureUniformYUY2Loc, 0);

        }
        //else
            // Invalid colorspace

        GLES20.glEnableVertexAttribArray(vertexLoc);
        GLES20.glVertexAttribPointer(vertexLoc, 3, GLES20.GL_FLOAT, false, 0, quadBuffer);

        GLES20.glViewport(0, 0, width, height);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, oldFbo);
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Upload texture to OpenGL when needed
        synchronized (rawBuffer) {
            if(hasNewFrame) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextureName);

                if(colorspace == COLORSPACE_YUY2)
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width / 2, height, 0, GLES20.GL_RGBA,
                        GLES20.GL_UNSIGNED_BYTE, rawBuffer);
                else if(colorspace == COLORSPACE_NV12)
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width / 4, height * 2, 0, GLES20.GL_RGBA,
                            GLES20.GL_UNSIGNED_BYTE, rawBuffer);

                convertYUV();

                hasNewFrame = false;

                // Frame counter
                videoFrameCount++;
                long newTime = System.currentTimeMillis();
                if(lastFrameTime == 0) {
                    lastFrameTime = newTime;
                }

                if (newTime - lastFrameTime >= 1000) {
                    Log.d("rvlvr", "Video frame rate: " + videoFrameCount);
                    videoFrameCount = 0;
                    lastFrameTime = newTime;
                }

            }
        }

        if(false/*showYUV*/)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextureName);
        else
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureName);
    }

    @Override
    public void onDrawEye(Eye eye) {

        GLES20.glClearColor(.0f, .0f, 2f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if(noWrap && projectionType == DriftRenderer.PROJECTION_TYPE_NOVR) {
            GLES20.glUseProgram(blitProgram);
            GLES20.glUniform1i(textureUniformBlitLoc, 0);
        }
        else {
            GLES20.glUseProgram(skyBoxProgram);
            GLES20.glUniform1i(textureUniformLoc, 0);
        }

        GLES20.glEnableVertexAttribArray(vertexLoc);

        if(noWrap)
            GLES20.glVertexAttribPointer(vertexLoc, 3, GLES20.GL_FLOAT, false, 0, quadBuffer);
        else
            GLES20.glVertexAttribPointer(vertexLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);


        if(noWrap && projectionType == DriftRenderer.PROJECTION_TYPE_NOVR) {
            Matrix.setIdentityM(mat, 0);
            GLES20.glUniformMatrix4fv(mvpBlitLoc, 1, false, mat, 0);
        }
        else {
            eye.getFov().toPerspectiveMatrix(.1f, 1000f, mat, 0);
            Matrix.multiplyMM(mat, 0, mat, 0, eye.getEyeView(), 0);
            //Matrix.rotateM(mat, 0, rot, 1, 0, 0);
            //rot += 1;
            //rot = rot % 360;
            GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mat, 0);
        }


        if(noWrap)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        else
            GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, indexBuffer.limit(),
                    GLES20.GL_UNSIGNED_SHORT, indexBuffer);
    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }

    @Override
    public void onRendererShutdown() {

    }

    private String loadString(int resId) throws IOException {
        InputStream is = context.getResources().openRawResource(resId);
        if(is == null) {
            // Can't load shader file
            return null;
        }
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader reader = new BufferedReader(isr);
        StringBuilder builder = new StringBuilder();
        String line = null;

        while ((line = reader.readLine()) != null) {
            builder.append(line + "\n");
        }
        reader.close();

        return builder.toString();
    }

    /*@Override
    public void setResolution(int w, int h) {
        this.width = w;
        this.height = h;
    }*/

    @Override
    public void setSignalAspectRatio(int w, int h) {
        this.aspect = (float) w / (float) h;
    }

    @Override
    public void setSignalType(int stereotype) {
        this.stereotype = stereotype;
    }

    @Override
    public void setProjectionAngle(int projectionAngle) {
        this.projectionAngle = projectionAngle;
    }

    @Override
    public void setProjectionType(int projectionType) {
        this.projectionType = projectionType;
    }

    @Override
    public void setNoWrap(boolean noWrap) {
        this.noWrap = noWrap;
    }
}