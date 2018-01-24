#version 100

attribute vec3 a_vertex;
attribute vec2 a_uvCoord;

varying vec2 uvCoord;
varying vec3 pos;

uniform vec4 u_map; // (xpos, ypos, xscale, yscale)
uniform vec3 u_angles; // (hori angle (1=360), vert angle (1=180), verticalCrop)
uniform mat4 u_mvp;

void main(void) {
    uvCoord = a_uvCoord - vec2(.5);
    uvCoord *= u_angles.xy;
    uvCoord += vec2(.5);

    // Crop
    uvCoord.y -= .5;
    uvCoord.y *= u_angles.z;
    uvCoord.y += .5;

	uvCoord = u_map.zw * uvCoord + u_map.xy;

    pos = a_vertex;
    gl_Position = u_mvp * vec4(a_vertex, 1.);
}