package com.betelge.rvlvr.gvr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.betelge.rvlvr.R;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
    private int uvCoordLoc = 1;
    private int primitiveType;
    private int mvpLoc, mvpBlitLoc;
    private int textureUniformLoc, textureUniformBlitLoc;
    private int mapLoc;
    private int angleLoc;

    private FloatBuffer vertexBuffer;
    private FloatBuffer uvCoordsBuffer;
    private ShortBuffer indexBuffer;
    private float[] mat;
    private int textureName;

    ByteBuffer rawBuffer;
    ByteBuffer debugBuffer;
    boolean hasNewFrame = false;
    boolean dumpFrame = false;

    float[] headView;
    float rotx, roty = 0;

    private int videoFrameCount = 0;
    private long lastFrameTime = 0;

    // GLSL based YUV -> RGB converter
    private int fbo;
    private int oldFbo;
    private int yuy2ConverterProgram;
    private int nv12ConverterProgram;
    private int cropRatioYUY2Loc;
    private int cropRatioNV12Loc;
    private int yuvTextureUniformYUY2Loc;
    private int yuvTextureUniformNV12Loc;
    private int uvTextureUniformNV12Loc;
    private int yuvTextureName;
    private int uvTextureName;
    private boolean texturesAreDirty; // Textures need resizing
    private FloatBuffer quadBuffer;

    // Input signal
    private int width;
    private int height;
    private int colorspace;
    private int stereotype;
    private float aspectCorrection;
    private int projectionAngle;

    // Display settings
    private int projectionType;
    private boolean noWrap; // Cropping and projection are skipped when noWrap is enabled
    private int viewWidth;
    private int viewHeight;

    private float MONOSCOPIC_FOVY = 60;

    final int MAX_WIDTH = 4096;
    final int MAX_HEIGHT = 4096;

    private float noWrapZoom = 1;
    private float noWrapX = 0;
    private float noWrapY = 0;

    public GVRenderer(Context context) {
        this.context = context;

        mat = new float[16];
        rawBuffer = ByteBuffer.allocateDirect(4 * MAX_WIDTH * MAX_HEIGHT);
        rawBuffer.order(ByteOrder.nativeOrder());
        rawBuffer.flip();

        // Used for dumping frames to file
        debugBuffer = ByteBuffer.allocateDirect(4*MAX_WIDTH * MAX_HEIGHT);
        debugBuffer.order(ByteOrder.nativeOrder());

        // The default values
        setResolution(1920, 1080);
        setColorspace(DriftRenderer.COLORSPACE_NV12);
        setSignalType(DriftRenderer.SIGNAL_TYPE_MONO);
        setSignalAspectRatio(16, 9);
        setProjectionAngle(360);
        setProjectionType(DriftRenderer.PROJECTION_TYPE_VR);
        setNoWrap(false);
    }

    private void setupTexturesAndFbos() {

        // RGB texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureName);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // YUV texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextureName);
        if(colorspace == COLORSPACE_YUY2)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width / 2, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        else if(colorspace == COLORSPACE_NV12)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0,
                    GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        if(colorspace == COLORSPACE_NV12) {
            // UV texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, uvTextureName);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, width / 2, height / 2, 0,
                    GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }


        // Recreate FBO
        int[] fbos = {fbo};
        GLES20.glDeleteFramebuffers(1, fbos, 0);
        GLES20.glGenFramebuffers(1, fbos, 0);
        fbo = fbos[0];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, fbos, 0);
        oldFbo = fbos[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, textureName, 0);
        int fbStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        assert(fbStatus == GLES20.GL_FRAMEBUFFER_COMPLETE);
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

        createYUVConverter();

        setupTexturesAndFbos();
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
        GLES20.glBindAttribLocation(skyBoxProgram, uvCoordLoc, "a_uvCoord");
        GLES20.glBindAttribLocation(blitProgram, vertexLoc, "a_vertex");

        GLES20.glLinkProgram(skyBoxProgram);
        GLES20.glLinkProgram(blitProgram);

        mvpLoc = GLES20.glGetUniformLocation(skyBoxProgram, "u_mvp");
        mvpBlitLoc = GLES20.glGetUniformLocation(blitProgram, "u_mvp");
        textureUniformLoc = GLES20.glGetUniformLocation(skyBoxProgram, "frame");
        textureUniformBlitLoc = GLES20.glGetUniformLocation(blitProgram, "frame");
        mapLoc = GLES20.glGetUniformLocation(skyBoxProgram, "u_map");
        angleLoc = GLES20.glGetUniformLocation(skyBoxProgram, "u_angles");

        String vertexLog = GLES20.glGetShaderInfoLog(vertexShader);
        String fragmentLog = GLES20.glGetShaderInfoLog(fragmentShader);
        String fragmentLog1 = GLES20.glGetShaderInfoLog(blitFragmentShader);
        //String linkLog = GLES20.glGetProgramInfoLog(skyBoxProgram);

        System.out.print(vertexLog);
        System.out.print(fragmentLog);
        System.out.print(fragmentLog1);
        //System.out.print(linkLog);


        // Sky sphere mesh
        Sphere sphere = new Sphere();
        float[] vertices = sphere.vertices;
        float[] uvCoords = sphere.uvCoords;
        short[] elements = sphere.elements;
        primitiveType = sphere.primitive_type;

        ByteBuffer bbFloats = ByteBuffer.allocateDirect(4 * vertices.length);
        bbFloats.order(ByteOrder.nativeOrder());
        vertexBuffer = bbFloats.asFloatBuffer();

        ByteBuffer bbUVFloats = ByteBuffer.allocateDirect(4 * uvCoords.length);
        bbUVFloats.order(ByteOrder.nativeOrder());
        uvCoordsBuffer = bbUVFloats.asFloatBuffer();

        ByteBuffer bbShorts = ByteBuffer.allocateDirect(2 * elements.length);
        bbShorts.order(ByteOrder.nativeOrder());
        indexBuffer = bbShorts.asShortBuffer();

        indexBuffer.put(elements);
        indexBuffer.flip();
        vertexBuffer.put(vertices);
        vertexBuffer.flip();
        uvCoordsBuffer.put(uvCoords);
        uvCoordsBuffer.flip();

        GLES20.glEnableVertexAttribArray(vertexLoc);
        GLES20.glVertexAttribPointer(vertexLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(uvCoordLoc);
        GLES20.glVertexAttribPointer(uvCoordLoc, 2, GLES20.GL_FLOAT, false, 0, uvCoordsBuffer);
    }

    private void createYUVConverter() {

        // YUV texture
        int[] texLoc = {0, 0};
        GLES20.glGenTextures(2, texLoc, 0);
        yuvTextureName = texLoc[0];
        uvTextureName = texLoc[1];

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
        uvTextureUniformNV12Loc = GLES20.glGetUniformLocation(nv12ConverterProgram, "uvTex");

        cropRatioYUY2Loc = GLES20.glGetUniformLocation(yuy2ConverterProgram, "u_cropRatio");
        cropRatioNV12Loc = GLES20.glGetUniformLocation(nv12ConverterProgram, "u_cropRatio");

        String vertexLog = GLES20.glGetShaderInfoLog(vertexShader);
        String fragmentLog = GLES20.glGetShaderInfoLog(yuy2FragmentShader);
        String fragmentLog1 = GLES20.glGetShaderInfoLog(nv12FragmentShader);
        //String linkLog = GLES20.glGetProgramInfoLog(skyBoxProgram);

        System.out.print(vertexLog);
        System.out.print(fragmentLog);
        System.out.print(fragmentLog1);
        //System.out.print(linkLog);

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
    public void onSurfaceChanged(int w, int h) {
        viewWidth = w;
        viewHeight = h;
    }

    private void convertYUV() {
        int[] fbos = {0};
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, fbos, 0);
        oldFbo = fbos[0];

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);

        if(colorspace == COLORSPACE_NV12) {
            GLES20.glUseProgram(nv12ConverterProgram);
            GLES20.glUniform1i(yuvTextureUniformNV12Loc, 0);
            GLES20.glUniform1i(uvTextureUniformNV12Loc, 1);
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

        // Dumps the currently bound FBO to file
        if(dumpFrame) {
            dumpFrame = false;

            try {
                dumpFrameToFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, oldFbo);
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        if(texturesAreDirty) {
            setupTexturesAndFbos();
            texturesAreDirty = false;
        }

        // Upload texture to OpenGL when needed
        synchronized (rawBuffer) {
            if(hasNewFrame) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextureName);

                if(colorspace == COLORSPACE_YUY2)
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width / 2, height, 0, GLES20.GL_RGBA,
                        GLES20.GL_UNSIGNED_BYTE, rawBuffer);
                else if(colorspace == COLORSPACE_NV12) {
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0, GLES20.GL_LUMINANCE,
                            GLES20.GL_UNSIGNED_BYTE, rawBuffer);

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, uvTextureName);

                    rawBuffer.position(width*height);
                    ByteBuffer uvBuffer = rawBuffer.slice();
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, width / 2, height / 2, 0, GLES20.GL_LUMINANCE_ALPHA,
                            GLES20.GL_UNSIGNED_BYTE, uvBuffer);

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                }

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

        GLES20.glClearColor(.0f, .0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if(noWrap) {
            GLES20.glUseProgram(blitProgram);
            GLES20.glUniform1i(textureUniformBlitLoc, 0);
        }
        else {
            GLES20.glUseProgram(skyBoxProgram);
            GLES20.glUniform1i(textureUniformLoc, 0);
        }

        GLES20.glEnableVertexAttribArray(vertexLoc);

        if(noWrap) {
            GLES20.glDisableVertexAttribArray(uvCoordLoc);
            GLES20.glVertexAttribPointer(vertexLoc, 3, GLES20.GL_FLOAT, false, 0, quadBuffer);
        }
        else {
            GLES20.glEnableVertexAttribArray(uvCoordLoc);
            GLES20.glVertexAttribPointer(vertexLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glVertexAttribPointer(uvCoordLoc, 2, GLES20.GL_FLOAT, false, 0, uvCoordsBuffer);
        }


        if(noWrap && projectionType == DriftRenderer.PROJECTION_TYPE_NOVR) {
            Matrix.setIdentityM(mat, 0);
            float yScale = height / viewHeight * viewWidth / width;
            Matrix.scaleM(mat, 0, noWrapZoom, noWrapZoom * yScale, 1);
            Matrix.translateM(mat, 0, noWrapX, noWrapY, 0);
            GLES20.glUniformMatrix4fv(mvpBlitLoc, 1, false, mat, 0);
        }
        else {
            if(eye.getType() == Eye.Type.MONOCULAR)
                Matrix.perspectiveM(mat, 0, MONOSCOPIC_FOVY, viewWidth/(float)viewHeight, .1f, 100f);
            else
                eye.getFov().toPerspectiveMatrix(.1f, 100f, mat, 0);
            Matrix.multiplyMM(mat, 0, mat, 0, eye.getEyeView(), 0);

            Matrix.rotateM(mat, 0, roty, 1, 0, 0);
            Matrix.rotateM(mat, 0, rotx, 0, 1, 0);
            if(noWrap) {
                Matrix.translateM(mat, 0, 0, 0, -2);
                Matrix.scaleM(mat, 0, 1, height/(float)width, 1);
            }
            GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mat, 0);
        }

        // Map a region of the texture onto this eye
        float rightEye = eye.getType() == Eye.Type.LEFT || eye.getType() == Eye.Type.MONOCULAR ?
                0 : 1;
        if(!noWrap) {
            switch (stereotype) {
                default:
                case SIGNAL_TYPE_MONO:
                    GLES20.glUniform4f(mapLoc, 1, 0, 1, 1);
                    break;
                case SIGNAL_TYPE_STEREO_SIDE_BY_SIDE:
                    GLES20.glUniform4f(mapLoc, .5f * rightEye, 0, .5f, 1);
                    break;
                case SIGNAL_TYPE_STEREO_OVER_UNDER:
                    GLES20.glUniform4f(mapLoc, 1, .5f * rightEye, 1, .5f);
                    break;
            }

            float angle = 360f / projectionAngle;
            GLES20.glUniform2f(angleLoc, angle, aspectCorrection);
        }



        if(noWrap)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        else
            GLES20.glDrawElements(primitiveType, indexBuffer.limit(),
                    GLES20.GL_UNSIGNED_SHORT, indexBuffer);
    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }

    @Override
    public void onRendererShutdown() {
        int textures[] = {textureName, yuvTextureName, uvTextureName};
        int fbos[] = {fbo};
        int programs[] = {blitProgram, skyBoxProgram, nv12ConverterProgram, yuy2ConverterProgram};
        GLES20.glDeleteTextures(textures.length, textures, 0);
        GLES20.glDeleteFramebuffers(fbos.length, fbos, 0);
        for(int prog : programs)
            GLES20.glDeleteProgram(prog);
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

    public void drag(float dx, float dy) {
        if(noWrap && projectionType == PROJECTION_TYPE_NOVR && noWrapZoom != 1f) {
            noWrapX += .0015 * dx / noWrapZoom;
            noWrapY -= .0015 * dy / noWrapZoom;

            float L = viewWidth / (float) width;
            if(noWrapX < -L)
                noWrapX = -L;
            else if(noWrapX > L)
                noWrapX = L;
            if(noWrapY < -L)
                noWrapY = -L;
            else if(noWrapY > L)
                noWrapY = L;
        }
        else if (!noWrap) {
            float speed = -.1f;

            if(projectionType == PROJECTION_TYPE_NOVR)
                speed *= MONOSCOPIC_FOVY / 60f;

            rotx += speed * dx;
            roty += speed * dy;

            rotx %= 360;
            roty = Math.max(roty, -90);
            roty = Math.min(roty, 90);
        }
    }

    public void doubleTap() {
        if(noWrap && projectionType == PROJECTION_TYPE_NOVR) {
            if(noWrapZoom != 1f) {
                noWrapZoom = 1f;
                noWrapX = 0;
                noWrapY = 0;
            }
            else {
                noWrapZoom = height / (float) viewHeight;
            }
        }
        /*else if(projectionType == PROJECTION_TYPE_NOVR) {
            MONOSCOPIC_FOVY *= .5f;
            if(MONOSCOPIC_FOVY < 3f)
                MONOSCOPIC_FOVY = 60f;
        }*/
    }

    public void longPress() {
        /*setNoWrap(!noWrap);*/

        if(noWrap) {
            Toast.makeText(context, "Dumping frame...", Toast.LENGTH_SHORT).show();
            dumpFrame = true;
        }
    }

    public void dumpFrameToFile() throws IOException {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, debugBuffer);
        int r = debugBuffer.remaining();
        bmp.copyPixelsFromBuffer(debugBuffer);
        debugBuffer.clear();
        debugBuffer.rewind();

        /*Bitmap yuvBmp = Bitmap.createBitmap(width, height/2*3, Bitmap.Config.ARGB_8888);

        rawBuffer.rewind();
        for(int i = 0; i < width * height/2*3; i++) {
            byte b = rawBuffer.get();
            debugBuffer.put((byte)255);
            debugBuffer.put(b);
            debugBuffer.put(b);
            debugBuffer.put(b);
        }
        rawBuffer.rewind();
        debugBuffer.rewind();
        yuvBmp.copyPixelsFromBuffer(debugBuffer);*/

        int i = 0;
        File file, yuvFile;
        String filename, yuvFilename;
        do {
            filename = "frame" + i + ".png";
            yuvFilename = "frame_yuv" + i + ".png";
            file = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename);
            yuvFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), yuvFilename);

            i++;
        } while(file.exists() || yuvFile.exists());
        file.createNewFile();
        //yuvFile.createNewFile();
        FileOutputStream out = new FileOutputStream(file);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.close();

        /*out = new FileOutputStream(yuvFile);
        yuvBmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.close();*/

        debugBuffer.clear();
        bmp.recycle();
        //yuvBmp.recycle();
    }

    @Override
    public void setResolution(int w, int h) {
        if(width == w && height == h)
            return;

        aspectCorrection *= h / (float) height * width / (float)  w;

        width = w;
        height = h;

        texturesAreDirty = true;
    }

    @Override
    public void setColorspace(int format) {
        if(colorspace == format)
            return;

        colorspace = format;

        texturesAreDirty = true;
    }

    @Override
    public void setSignalAspectRatio(int w, int h) {
        aspectCorrection = h / (float) height * width / (float) w;
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
