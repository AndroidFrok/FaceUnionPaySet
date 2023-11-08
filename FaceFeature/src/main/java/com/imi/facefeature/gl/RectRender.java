package com.imi.facefeature.gl;

import android.content.Context;
import android.opengl.GLES20;

import com.imi.facefeature.helper.ShaderHelper;
import com.imi.sdk.facebase.utils.Rect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * RectRender
 *
 * @author jtl
 * @date 2019/9/26
 */
public class RectRender {
    private static final String TAG = RectRender.class.getSimpleName();
    private static final String VERTEX_SHADER_NAME = "shader/RectShader.vert";
    private static final String FRAGMENT_SHADER_NAME = "shader/RectShader.frag";

    private static final int VERTEX_NUM = 4;
    private static final int COMPONENTS_PER_VERTEX = 4;
    private static final int BYTES_PER_FLOAT = 4;
    private final float[] rectColor = {1.0f, 0.0f, 0.0f, 1.0f};
    private final float[] greenColor = {0.0f, 1.0f, 0.0f, 1.0f};
    private final float[] blueColor = {0.0f, 0.0f, 1.0f, 1.0f};
    private final float[] fixColor = {1.0f, 2.0f, 0.0f, 1.0f};
    private Context context;
    private int programHandle;
    private int vertexHandle;
    private int colorHandle;
    private FloatBuffer vertexBuffer;
    private float[] rectData = new float[16];

    public RectRender(Context context) {
        this.context = context;

        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_NUM * COMPONENTS_PER_VERTEX * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public void createGlThread() {
        int vertexShader = ShaderHelper.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int fragmentShader = ShaderHelper.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        programHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(programHandle, vertexShader);
        GLES20.glAttachShader(programHandle, fragmentShader);

        GLES20.glLinkProgram(programHandle);
        GLES20.glUseProgram(programHandle);

        vertexHandle = GLES20.glGetAttribLocation(programHandle, "rectPosition");
        colorHandle = GLES20.glGetAttribLocation(programHandle, "rectColor");

        GLES20.glDetachShader(programHandle, vertexShader);
        GLES20.glDetachShader(programHandle, fragmentShader);

        ShaderHelper.checkGLError("createGlThread");
    }


    public void draw(Rect rect, float imageWidth, float imageHeight) {
        float halfWidth = imageWidth / 2;
        float halfHeight = imageHeight / 2;

        //第一个点(左上)
        rectData[0] = rect.getX();
        rectData[1] = rect.getY();
        rectData[2] = 0;
        rectData[3] = 1;
        //第二个点（右上）
        rectData[4] = rect.getX() + rect.getWidth();
        rectData[5] = rect.getY();
        rectData[6] = 0;
        rectData[7] = 1;
        //第三个点（左下）
        rectData[8] = rect.getX() + rect.getWidth();
        rectData[9] = rect.getY() + rect.getHeight();
        rectData[10] = 0;
        rectData[11] = 1;
        //第四个点（右下）
        rectData[12] = rect.getX();
        rectData[13] = rect.getY() + rect.getHeight();
        rectData[14] = 0;
        rectData[15] = 1;

        for (int i = 0; i < rectData.length; i += 4) {
            rectData[i + 0] = (rectData[i + 0] - halfWidth) / halfWidth;
            rectData[i + 1] = -(rectData[i + 1] - halfHeight) / halfHeight;
            rectData[2] = 0;
            rectData[3] = 1;
        }

        vertexBuffer.put(rectData);
        vertexBuffer.position(0);

        GLES20.glUseProgram(programHandle);
        GLES20.glVertexAttribPointer(vertexHandle, COMPONENTS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(vertexHandle);

        GLES20.glVertexAttrib4fv(colorHandle, fixColor, 0);
        GLES20.glLineWidth(5);
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);
        GLES20.glVertexAttrib4fv(colorHandle, greenColor, 0);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 1, 1);
        GLES20.glVertexAttrib4fv(colorHandle, blueColor, 0);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 2, 1);
        GLES20.glVertexAttrib4fv(colorHandle, fixColor, 0);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 3, 1);
        GLES20.glUseProgram(0);
        ShaderHelper.checkGLError("draw");
    }
}
