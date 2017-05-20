#version 430

layout(location = 0) in vec4 in_position;
layout(location = 1) in vec4 in_color;

layout(location = 0) out vec4 out_color;

void main() {
	gl_Position = in_position;
	out_color = in_color;
}