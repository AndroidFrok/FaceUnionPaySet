package com.imi.faceunionpayset.helper;

import android.content.Context;

import com.imi.camera.camera.ImiCamera;
import com.imi.sdk.face.OnSessionInitializeListener;
import com.imi.sdk.face.Session;
import com.imi.sdk.face.SessionConfig;

import static com.imi.faceunionpayset.Constant.APP_KEY;
import static com.imi.faceunionpayset.Constant.FACE_MODEL_PATH;

/**
 * @author：TianLong
 * @date：2019/12/1 14:51
 * 人脸SessionHelper类 简单的封装
 */
public class SessionHelper {
    private Session mFaceSession;

    private SessionHelper() {
    }

    public static SessionHelper getInstance() {
        return SessionHelperHolder.SESSION_HELPER;
    }

    public void initSession(Context context, OnSessionInitializeListener listener, SessionConfig sessionConfig) {
        mFaceSession = new Session(context);
        mFaceSession.configure(sessionConfig);

        mFaceSession.initializeAsync(listener);
    }

    public SessionConfig getSessionConfig() {
        SessionConfig sessionConfig = new SessionConfig();
        sessionConfig.camera = ImiCamera.getInstance();
        // 授权信息
        sessionConfig.appKey = APP_KEY;
        // 设置算法模型文件路径`
        sessionConfig.modelPath = FACE_MODEL_PATH;

        return sessionConfig;
    }

    public Session getFaceSession() {
        return mFaceSession;
    }


    private static class SessionHelperHolder {
        private static final SessionHelper SESSION_HELPER = new SessionHelper();
    }
}
