package com.imi.faceunionpayset.gl;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;

import com.imi.sdk.facebase.utils.Rect;

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * FaceSurfaceView
 *
 * @author jtl
 * @date 2019/9/26
 */
public class FaceGLSurface extends BaseGLSurfaceView {
    private static final String TAG = "FaceGLSurface";
    private ByteBuffer rgbImage;
    private Rect faceRect;

    private RgbRender rgbRender;
    private RectRender rectRender;

    private int width;
    private int height;

    public FaceGLSurface(Context context) {
        super(context);
    }

    public FaceGLSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void updateColorImage(ByteBuffer rgbImage) {
        this.rgbImage = rgbImage;
    }

    public void updateFaceRect(Rect rect) {
        this.faceRect = rect;
    }

    public void updateColorImage(ByteBuffer rgbImage, int width, int height) {
        this.rgbImage = rgbImage;
        this.width = width;
        this.height = height;
    }

    public void updateFaceRect(Rect rect, int width, int height) {
        this.faceRect = rect;
        this.width = width;
        this.height = height;
    }

    public void updateFaceRect(@Nullable int[] data) {
        if (data != null) {
            this.faceRect = new Rect(data);
        } else {
            faceRect = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        super.onSurfaceCreated(gl, config);

        rgbRender = new RgbRender(getContext());
        rgbRender.createGlThread();

        rectRender = new RectRender(getContext());
        rectRender.createGlThread();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        super.onSurfaceChanged(gl, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        super.onDrawFrame(gl);

        if (rgbImage == null || width == 0 || height == 0) {
            Log.w(TAG, "渲染参数异常");
            return;
        }
        rgbRender.draw(rgbImage, width, height);

        if (faceRect != null) {
            rectRender.draw(faceRect, width, height);
        }
    }
}
