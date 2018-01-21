#version 100

precision mediump float;

varying vec2 uvCoord;

uniform vec4 u_map; // (xpos, ypos, xscale, yscale)
uniform vec2 u_angles; // (hori angle (1=360), vert angle (1=180))
uniform sampler2D frame;

vec4 equirectangular(sampler2D sampler, vec2 uv)
{
	uv -= vec2(.5);
	uv /= u_angles;
	uv += vec2(.5);

	if(uv.x < 0. || uv.x > 1. || uv.y < 0. || uv.y > 1.)
	    discard;

	return texture2D(sampler, u_map.zw * uv + u_map.xy);
}

void main(void) {
    gl_FragColor = equirectangular(frame, uvCoord);
}