precision mediump float;
varying vec2 v_TexCoord;
uniform sampler2D sTexture;

void main() {
    gl_FragColor = texture2D(sTexture, v_TexCoord);
}