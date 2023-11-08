attribute vec4 rectPosition;
attribute vec4 rectColor;
varying vec4 rectFragColor;

void main() {
    gl_Position = rectPosition;
    rectFragColor = rectColor;
}