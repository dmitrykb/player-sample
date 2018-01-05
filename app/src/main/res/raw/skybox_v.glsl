#version 100

attribute vec3 a_vertex;

varying vec3 pos;

uniform mat4 u_mvp;

void main(void) {
    pos = a_vertex;
    gl_Position = u_mvp * vec4(a_vertex, 1.);
}