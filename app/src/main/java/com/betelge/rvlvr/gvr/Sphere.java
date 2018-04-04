package com.betelge.rvlvr.gvr;

import android.opengl.GLES20;

/**
 * Created by jasminko on 10/01/18.
 */

public class Sphere {
    final int primitive_type;
    final public float vertices[];
    final public short elements[];
    final public float uvCoords[];

    Sphere() {
        primitive_type = GLES20.GL_TRIANGLE_STRIP;

        final int SEGS = 64;

        vertices = new float[3*SEGS*SEGS];
        elements = new short[2*(SEGS+1)*(SEGS-1)];
        uvCoords = new float[2*SEGS*SEGS];

        int v = 0;
        int u = 0;
        for(int y = 0; y < SEGS; y++) {
            for(int x = 0; x < SEGS; x++) {
                float uc = x / (float) (SEGS-1);
                float vc = y / (float) (SEGS-1);

                uvCoords[u++] = uc;
                uvCoords[u++] = vc;

                double theta = 2 * Math.PI * uc;
                double phi = Math.PI * vc;

                vertices[v++] = (float) (-Math.sin(theta) * Math.sin(phi));
                vertices[v++] = (float) Math.cos(phi);
                vertices[v++] = (float) (Math.cos(theta) * Math.sin(phi));
            }
        }

        int e = 0;
        for(int y = 0; y < SEGS - 1; y++) {
            for(int x = 0; x < SEGS; x++) {
                elements[e++] = (short) (y*SEGS+x);
                elements[e++] = (short) ((y+1)*SEGS+x);
            }
            elements[e++] = (short) ((y+1)*SEGS+SEGS-1);
            elements[e++] = (short) ((y+1)*SEGS);
        }
    }
}
