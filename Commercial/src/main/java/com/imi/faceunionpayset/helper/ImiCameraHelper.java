package com.imi.faceunionpayset.helper;

import android.content.Context;

import com.imi.camera.camera.CameraConfig;
import com.imi.camera.camera.ImiCamera;
import com.imi.camera.listener.OnOpenCameraListener;
import com.imi.sdk.facebase.utils.Size;

import static com.imi.camera.camera.CameraConfig.DepthType.DEPTH_IR;

/**
 * @author：TianLong
 * @date：2019/12/1 19:56
 */
public class ImiCameraHelper {
    private int IMAGE_WIDTH = 640;
    private int IMAGE_HEIGHT = 480;
    private ImiCamera mImiCamera;

    private ImiCameraHelper() {
    }

    public static ImiCameraHelper getInstance() {
        return ImiCameraHelperHolder.IMI_CAMERA_HELPER;
    }

    public void init(Context context, OnOpenCameraListener onOpenCameraListener, boolean isSupporIr) {
        mImiCamera = ImiCamera.getInstance();

        mImiCamera.open(context, FileHelper.getInstance().getFaceImiBinFolderPath(), isSupporIr, onOpenCameraListener);
    }

    public ImiCamera getImiCamera() {
        return mImiCamera;
    }

    public void setSize(int width, int height) {
        IMAGE_WIDTH = width;
        IMAGE_HEIGHT = height;
    }

    public CameraConfig getCameraConfig() {
        // 注意640 * 480分辨率在以上支持列表中，目前算法仅支持该分辨率
        CameraConfig cameraConfig = new CameraConfig();
        // 彩色图分辨率
        cameraConfig.colorSize = new Size(IMAGE_WIDTH, IMAGE_HEIGHT);
        // 深度图分辨率
        cameraConfig.depthSize = new Size(IMAGE_WIDTH, IMAGE_HEIGHT);
        // 摄像头模式
        cameraConfig.cameraMode = CameraConfig.MODE_FRONT_CAM;
        // 数据流的类别
        cameraConfig.depthType = DEPTH_IR;
        // ir图是否为8位单通道单色图
        cameraConfig.isOutput8BitIr = false;

        return cameraConfig;
    }

    private static class ImiCameraHelperHolder {
        private static final ImiCameraHelper IMI_CAMERA_HELPER = new ImiCameraHelper();
    }
}
