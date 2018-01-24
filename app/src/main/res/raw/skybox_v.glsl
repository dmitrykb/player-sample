#version 100

attribute vec3 a_vertex;
attribute vec2 a_uvCoord;

varying vec2 uvCoord;
varying vec3 pos;

uniform vec4 u_map; // (xpos, ypos, xscale, yscale)
uniform vec2 u_angles; // (hori angle (1=360), vert angle (1=180) or verticalCrop)
uniform mat4 u_mvp;

void main(void) {
    vec2 uv = a_uvCoord - vec2(.5);
    uv *= u_angles.xy; // Stereo/mono projection and crop
    uv += vec2(.5);

	uv = u_map.zw * uv + u_map.xy;
	uvCoord = uv;

    pos = a_vertex;
    gl_Position = u_mvp * vec4(a_vertex, 1.);
}