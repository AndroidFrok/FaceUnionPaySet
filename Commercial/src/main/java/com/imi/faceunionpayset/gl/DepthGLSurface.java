package com.imi.faceunionpayset.gl;

import android.content.Context;
import android.util.AttributeSet;

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * DepthGLSurface
 *
 * @author jtl
 * @date 2019/9/26
 */
public class DepthGLSurface extends BaseGLSurfaceView {
    private DepthRender mDepthRender;
    private ByteBuffer depthImage;
    private int width;
    private int height;

    public DepthGLSurface(Context context) {
        super(context);
    }

    public DepthGLSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        super.onSurfaceCreated(gl, config);
        mDepthRender = new DepthRender(getContext());
        mDepthRender.createGlThread();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        super.onSurfaceChanged(gl, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        super.onDrawFrame(gl);

        if (mDepthRender != null) {
            mDepthRender.draw(depthImage, width, height);
        }
    }

    public void updateDepthImage(ByteBuffer depthImage, int width, int height) {
        this.depthImage = depthImage;
        this.width = width;
        this.height = height;
    }
}
