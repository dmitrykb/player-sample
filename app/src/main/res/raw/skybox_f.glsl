#version 100

precision mediump float;

varying vec3 pos;

uniform vec4 u_map; // (xpos, ypos, xscale, yscale)
uniform float u_angle;
uniform sampler2D frame;

vec4 equirectangular(sampler2D sampler, vec3 dir)
{
    // TODO: Check if this can be done without trigonometric functions
	vec2 uv;
	dir = normalize(dir);
	uv.x = atan( dir.z, dir.x );
	uv.y = acos( dir.y );
	uv /= vec2( 2. * 3.14159, 3.14159 );
	uv.x += .25;
	uv.x /= u_angle;
	uv.x += .5;

	if(uv.x < 0. || uv.x > 1.)
	    discard;

	return texture2D(sampler, u_map.zw * uv + u_map.xy);
}

void main(void) {
    //gl_FragColor = texture2D(frame, vec2(.5) + .5 * pos.xy);
    gl_FragColor = equirectangular(frame, pos.xyz);
}