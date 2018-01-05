#version 100

attribute vec3 a_vertex;

varying vec2 v_texCoord;

void main(void) {
    v_texCoord = vec2(.5) * a_vertex.xy + vec2(.5);
    gl_Position = vec4(a_vertex, 1.);
}