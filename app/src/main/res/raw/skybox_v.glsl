#version 100

attribute vec3 a_vertex;
attribute vec2 a_uvCoord;

varying vec2 uvCoord;

uniform mat4 u_mvp;

void main(void) {
    uvCoord = a_uvCoord;
    gl_Position = u_mvp * vec4(a_vertex, 1.);
}