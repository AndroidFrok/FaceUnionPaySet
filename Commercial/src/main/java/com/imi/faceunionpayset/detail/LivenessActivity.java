package com.imi.faceunionpayset.detail;

import android.content.Intent;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.imi.camera.camera.CameraFrame;
import com.imi.camera.camera.CameraOrientation;
import com.imi.camera.camera.ImiCamera;
import com.imi.camera.listener.OnFrameAvailableListener;
import com.imi.faceunionpayset.Constant;
import com.imi.faceunionpayset.R;
import com.imi.faceunionpayset.gl.DepthGLSurface;
import com.imi.faceunionpayset.gl.FaceGLSurface;
import com.imi.faceunionpayset.helper.FileHelper;
import com.imi.faceunionpayset.helper.SessionHelper;
import com.imi.sdk.face.AuthQuery;
import com.imi.sdk.face.BctcFaceQuality;
import com.imi.sdk.face.FaceInfo;
import com.imi.sdk.face.Frame;
import com.imi.sdk.face.LivenessResult;
import com.imi.sdk.face.OnSessionInitializeListener;
import com.imi.sdk.face.Quality;
import com.imi.sdk.face.Session;
import com.imi.sdk.face.SessionConfig;
import com.imi.sdk.facebase.base.ResultCode;
import com.imi.sdk.facebase.utils.ImageData;
import com.imi.sdk.facebase.utils.Rect;
import com.imi.sdk.utils.NativeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.imi.sdk.face.FaceAlgMode.MODE_BCTC;

/**
 * @author TianLong
 */
public class LivenessActivity extends AppCompatActivity implements OnFrameAvailableListener, OnSessionInitializeListener, View.OnClickListener {
    private static final String TAG = LivenessActivity.class.getSimpleName();
    private int IMAGE_WIDTH = 640;
    private int IMAGE_HEIGHT = 480;
    private ConstraintLayout mConstraintLayout;
    private FaceGLSurface mFaceGLSurface;
    private FaceGLSurface mFaceDetectGLSurface;
    private DepthGLSurface mDepthGLSurface;
    private FaceGLSurface mIrGLSurface;
    private TextView mFaceText;
    private Button mFaceButton;
    private Button mAuthButton;
    private ImiCamera mImiCamera;
    private CameraOrientation mCameraOrientation;
    private Session mSession;
    private Object mObject = new Object();

    private volatile boolean isSessionInit = false;
    private volatile boolean isFace = false;
    private volatile boolean isFacePause = false;

    private LinkedBlockingQueue<CameraImage> mLinkedBlockingQueue;
    private ConcurrentLinkedQueue<CameraImage> mImageResusePool;
    private Thread mFaceThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_liveness);
        Log.w(TAG, "onCreate");
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.w(TAG, "onResume:" + isSessionInit + isFacePause);

        if (isSessionInit && isFacePause) {
            isFacePause = false;
            threadNotify();
        }

        if (mFaceGLSurface != null) {
            mFaceGLSurface.onResume();
        }

        if (mFaceDetectGLSurface != null) {
            mFaceDetectGLSurface.onResume();
        }

        if (mDepthGLSurface != null) {
            mDepthGLSurface.onResume();
        }

        if (mIrGLSurface != null) {
            mIrGLSurface.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.w(TAG, "onPause:" + isFacePause);
        if (mFaceGLSurface != null) {
            mFaceGLSurface.onPause();
        }

        if (mFaceDetectGLSurface != null) {
            mFaceDetectGLSurface.onPause();
        }

        if (mDepthGLSurface != null) {
            mDepthGLSurface.onPause();
        }

        if (mIrGLSurface != null) {
            mIrGLSurface.onPause();
        }

        if (isSessionInit && !isFacePause) {
            isFacePause = true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //解绑相机回调
        mImiCamera.setFrameAvailableListener(null);

        //等待人脸和相机线程执行完之后，在继续执行主线程
        //因为onPause里改变了标志位，导致线程wait，所以这里要线notify
        //防止人脸库release后，线程中还持有人脸的引用，导致Assert（内存地址错误）
        isFace = false;

        if (mFaceThread != null) {
            try {
                threadNotify();
                //添加空数据，唤醒因阻塞队列阻塞的线程
                mLinkedBlockingQueue.add(new CameraImage(null, null, null));
                //等待人脸线程退出
                mFaceThread.join();
                Log.w(TAG, "mFaceThread state:" + mFaceThread.getState().name());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //release人脸库
        if (mSession != null) {
            mSession.release();
            Log.w(TAG, "Session.release");
        }
    }

    private void init() {
        mConstraintLayout = findViewById(R.id.layout_liveness_constraint);
        mFaceGLSurface = findViewById(R.id.gl_liveness_face);
        mFaceDetectGLSurface = findViewById(R.id.gl_liveness_face_dectect);
        mDepthGLSurface = findViewById(R.id.gl_liveness_depth_dectect);
        mIrGLSurface = findViewById(R.id.gl_liveness_ir_dectect);

        mFaceText = findViewById(R.id.tv_liveness_face);
        mFaceButton = findViewById(R.id.btn_liveness_face);
        mAuthButton = findViewById(R.id.btn_liveness_auth);
        mAuthButton.setOnClickListener(this::onClick);
        mFaceButton.setOnClickListener(this::onClick);

        //数据初始化
        mLinkedBlockingQueue = new LinkedBlockingQueue<>(2);

        //复用队列
        mImageResusePool = new ConcurrentLinkedQueue<>();

        mImiCamera = ImiCamera.getInstance();
        //判断相机横竖版
        mCameraOrientation = mImiCamera.getCameraOrientation();
        if (mCameraOrientation == CameraOrientation.PORTRAIT) {
            //竖版摄像头，分辨率为480 * 640
            IMAGE_WIDTH = 480;
            IMAGE_HEIGHT = 640;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //动态修改宽高比例
                    ConstraintSet constraintSet = new ConstraintSet();
                    constraintSet.clone(mConstraintLayout);
                    constraintSet.connect(R.id.gl_liveness_face, ConstraintSet.END, R.id.gl_liveness_depth_dectect, ConstraintSet.END);
                    constraintSet.setDimensionRatio(mFaceGLSurface.getId(), "3:4");
                    constraintSet.setDimensionRatio(mFaceDetectGLSurface.getId(), "3:4");
                    constraintSet.setDimensionRatio(mDepthGLSurface.getId(), "3:4");
                    constraintSet.setDimensionRatio(mIrGLSurface.getId(), "3:4");
                    constraintSet.applyTo(mConstraintLayout);
                }
            });
        }

        mImiCamera.setFrameAvailableListener(this::onFrameAvailable);
        //复制模型
        copyImiData();

        //Session初始化 Session的具体配置在getSessionConfig()中
        SessionConfig sessionConfig = SessionHelper.getInstance().getSessionConfig();
        // 算法类型（过检版）
        sessionConfig.faceAlgMode = MODE_BCTC;
        if (!TextUtils.isEmpty(sessionConfig.appKey)) {
            SessionHelper.getInstance().initSession(getApplicationContext(), LivenessActivity.this::onSessionInitialized, sessionConfig);
        } else {
            showToast("AppKey为空，无法授权，算法未初始化，请设置AppKey");
            showContent("AppKey为空，无法授权，算法未初始化，请设置AppKey");
        }


    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_liveness_face:
                if (isSessionInit && isFace) {
                    if (isFacePause) {
                        isFacePause = false;
                        threadNotify();
                    } else {
                        isFacePause = true;
                    }
                } else {
                    showToast("算法未初始化，或者人脸算法线程尚未执行");
                }
                break;
            case R.id.btn_liveness_auth:
                getAlgorithmAuthInfo(mImiCamera.getCameraHandle());
                break;
            default:
                break;
        }
    }


    @Override
    public void onSessionInitialized(int i, String s) {
        Log.d("RenderMode", "========> FaceAlg>" + Thread.currentThread().getPriority());
        String msg;
        mSession = SessionHelper.getInstance().getFaceSession();
        if (i == ResultCode.OK) {
            isSessionInit = true;

            startFaceLiveness();
            msg = i + "  " + s;
        } else {
            msg = i + "  " + s + "\n 算法模型文件目录：" + Constant.FACE_MODEL_PATH;
        }

        showContent("人脸算法：" + msg);
        showToast("人脸算法：" + msg);
    }


    @Override
    public void onFrameAvailable(CameraFrame cameraFrame) {
        CameraImage cameraImage = mImageResusePool.poll();
        if (cameraImage == null) {
            ByteBuffer mFaceByteBuffer = ByteBuffer.allocateDirect(IMAGE_WIDTH * IMAGE_HEIGHT * 3).order(ByteOrder.nativeOrder());
            ByteBuffer mDepthByteBuffer = ByteBuffer.allocateDirect(IMAGE_WIDTH * IMAGE_HEIGHT * 2).order(ByteOrder.nativeOrder());
            ByteBuffer mIrByteBuffer = ByteBuffer.allocateDirect(IMAGE_WIDTH * IMAGE_HEIGHT * 2).order(ByteOrder.nativeOrder());
            ImageData faceImage = new ImageData(IMAGE_WIDTH, IMAGE_HEIGHT, ImageData.Type.RGB, mFaceByteBuffer);
            ImageData depthImage = new ImageData(IMAGE_WIDTH, IMAGE_HEIGHT, ImageData.Type.DEPTH, mDepthByteBuffer);
            ImageData irImage = new ImageData(IMAGE_WIDTH, IMAGE_HEIGHT, ImageData.Type.IR, mIrByteBuffer);
            cameraImage = new CameraImage(faceImage, depthImage, irImage);
        }
        //第一帧，深度图和IR图可能为null
        if (cameraFrame.getDepthImage() == null || cameraFrame.getIrImage() == null) {
            return;
        }
        NativeUtils.copyByteBufferData(cameraFrame.getColorImage().getImageData(), cameraImage.faceImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 3);
        NativeUtils.copyByteBufferData(cameraFrame.getDepthImage().getImageData(), cameraImage.depthImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 2);
        NativeUtils.copyByteBufferData(cameraFrame.getIrImage().getImageData(), cameraImage.irImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 2);

        if (mLinkedBlockingQueue.size() > 0) {
            CameraImage cameraImageData = mLinkedBlockingQueue.poll();
            if (cameraImageData != null) {
                mImageResusePool.add(cameraImageData);
            }
        }
        mLinkedBlockingQueue.offer(cameraImage);

        mFaceGLSurface.updateColorImage(cameraImage.faceImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
        mFaceGLSurface.requestRender();
    }

    /**
     * 人脸活体检测
     */
    private void startFaceLiveness() {
        isFace = true;
        mFaceThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("RenderMode", "========> FaceAlg>" + Thread.currentThread().getPriority());
                while (isFace) {
                    try {
                        if (isFacePause) {
                            Log.d(TAG, "isFacePause:" + isFacePause);
                            //改变线程为wait状态
                            threadWait("Face");
                            continue;
                        }
                        Log.d(TAG, "Face");
                        //阻塞队列
                        CameraImage cameraImage = mLinkedBlockingQueue.take();
                        if (cameraImage == null || cameraImage.faceImage == null) {
                            Log.d(TAG, "测试：人脸线程数据为null");
                            continue;
                        }
                        //封装数据
                        Frame frame = mSession.update(cameraImage.faceImage, cameraImage.depthImage, cameraImage.irImage);

                        //检测人脸
                        long tt = System.currentTimeMillis();
                        FaceInfo[] faceInfos = frame.detectFaces();
                        long detectTime = System.currentTimeMillis() - tt;

                        if (faceInfos.length == 0) {
                            showContent("未检测到人脸");
                            mImageResusePool.add(cameraImage);
                            continue;
                        }

                        //Demo中只实现，最大人脸。多人脸时，人脸个数为FaceInfo[]数组长度
                        FaceInfo faceInfo = faceInfos[0];
                        //获取人脸矩形框信息
                        Rect rect = faceInfo.getFaceRect();

                        //数据渲染
                        mFaceDetectGLSurface.updateColorImage(cameraImage.faceImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                        mFaceDetectGLSurface.updateFaceRect(rect, IMAGE_WIDTH, IMAGE_HEIGHT);
                        mFaceDetectGLSurface.requestRender();
                        mDepthGLSurface.updateDepthImage(cameraImage.depthImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                        mDepthGLSurface.requestRender();
                        ByteBuffer byteBuffer = NativeUtils.ir2Rgb(cameraImage.irImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                        mIrGLSurface.updateColorImage(byteBuffer, IMAGE_WIDTH, IMAGE_HEIGHT);
                        mIrGLSurface.requestRender();

                        //活体检测
                        long tt1 = System.currentTimeMillis();
                        LivenessResult liveness = frame.detectLiveness(faceInfo);
                        long livenessTime = System.currentTimeMillis() - tt1;

                        //质量检测
                        long tt2 = System.currentTimeMillis();
                        BctcFaceQuality bctcFaceQuality = frame.detectBctcFaceQuality();
                        long qualityTime = System.currentTimeMillis() - tt2;

                        if (bctcFaceQuality == null) {
                            showContent("人脸质量评估失败");
                            mImageResusePool.add(cameraImage);
                            continue;
                        }

                        Log.d(TAG, getLivenessMsg(liveness) + "\n" + compareFaceQuality(bctcFaceQuality) + "\n人脸检测时间：" + detectTime + "ms" + "\n活体检测时间：" + livenessTime + "ms" + "\n质检检测时间：" + qualityTime + "ms");
                        showContent(getLivenessMsg(liveness) + "\n" + compareFaceQuality(bctcFaceQuality) + "\n人脸检测时间：" + detectTime + "ms" + "\n活体检测时间：" + livenessTime + "ms" + "\n质检检测时间：" + qualityTime + "ms");

                        //回收复用CameraImage
                        mImageResusePool.add(cameraImage);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, "DemoFaceLiveness");
        mFaceThread.start();
    }

    /**
     * @detail[cn] 活体检测。活体值超过0.5即可认为是活体。
     * @return[cn] 图像帧中人的活体置信度。
     * 0.0--1.0：活体置信度。
     * 0-> 活体检测成功
     * -101-> 深度图错误，如图像数据为空、图像格式错误等
     * -102-> 人脸距离相机模组过近或者过远
     * -103-> 人脸距离深度图边缘过近
     * -201-> 彩色图错误，如图像数据为空、图像格式错误等
     * -202-> 人脸过小
     * -203-> 人脸距离彩色图边缘过近
     * -99-> 人脸算法初始化失败
     * -1-> 未知错误
     */
    private String getLivenessMsg(LivenessResult livenessResult) {
        String msg = "";
        if (livenessResult.getErrorCode() != 0) {
            int errorCode = livenessResult.getErrorCode();
            if (errorCode == -1) {
                msg = "活体检测：未知错误";
            } else if (errorCode == -99) {
                msg = "活体检测：人脸算法初始化失败";
            } else if (errorCode == -101) {
                msg = "活体检测：深度图错误，如图像数据为空、图像格式错误等";
            } else if (errorCode == -102) {
                msg = "活体检测：人脸距离相机模组过近或者过远";
            } else if (errorCode == -103) {
                msg = "活体检测：人脸距离深度图边缘过近";
            } else if (errorCode == -201) {
                msg = "活体检测：彩色图错误，比如图像为空、格式错误等";
            } else if (errorCode == -202) {
                msg = "活体检测：图像中人脸过小";
            } else if (errorCode == -203) {
                msg = "活体检测：人脸距离彩色图边缘过近";
            }
        } else {
            float liveness = livenessResult.getLivenessScore();
            if (livenessResult.getLivenessScore() >= 0.5) {
                msg = "活体值：" + liveness + " 活体";
            } else if (liveness < 0.5 && liveness >= 0) {
                msg = "活体值：" + liveness + " 非活体";
            }
        }


        return msg;
    }

    private String compareFaceQuality(BctcFaceQuality faceQuality) {
        String faceDetail = "";

        if (faceQuality != null) {
            if (faceQuality.matchQuality(Quality.GOOD)) {
                faceDetail = faceDetail + "人脸质量OK：" + Quality.GOOD + "\n";
            }
            if (faceQuality.matchQuality(Quality.FACE_ANGLE)) {
                faceDetail = faceDetail + "人脸有角度，非人脸正面：" + Quality.FACE_ANGLE + "\n";
            }
            if (faceQuality.matchQuality(Quality.FACE_BLUR)) {
                faceDetail = faceDetail + "人脸模糊：" + Quality.FACE_BLUR + "\n";
            }
            if (faceQuality.matchQuality(Quality.LIGHT_UNSATISFIED)) {
                faceDetail = faceDetail + "光照不满足：" + Quality.LIGHT_UNSATISFIED + "\n";
            }
            if (faceQuality.matchQuality(Quality.FACE_EXPRESSION)) {
                faceDetail = faceDetail + "人脸面部有表情：" + Quality.FACE_EXPRESSION + "\n";
            }
            if (faceQuality.matchQuality(Quality.FACE_MASK)) {
                faceDetail = faceDetail + "人脸遮罩不完整：" + Quality.FACE_MASK + "\n";
            }
            if (faceQuality.matchQuality(Quality.FACE_TOO_MANY)) {
                faceDetail = faceDetail + "画面中人脸数量超过一个：" + Quality.FACE_TOO_MANY + "\n";
            }
            if (faceQuality.matchQuality(Quality.FACE_DISTANCE)) {
                faceDetail = faceDetail + "图像中人脸双眼间距小于60像素：" + Quality.FACE_DISTANCE + "\n";
            }
            if (faceQuality.matchQuality(Quality.IMAGE_RESOLUTION_UNSATISFIED)) {
                faceDetail = faceDetail + "图像分辨率不满足算法要求：" + Quality.IMAGE_RESOLUTION_UNSATISFIED + "\n";
            }
        } else if (faceDetail == null) {
            faceDetail = faceDetail + "faceQuality == null 检测失败\n";
        } else if (faceQuality.isDetectQualityFailed()) {
            faceDetail = faceQuality.getErrorMessage() + "\n";
        }

        return faceDetail;
    }

    /**
     * 吐司内容
     *
     * @param msg
     */
    private void showToast(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LivenessActivity.this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 显示内容
     *
     * @param msg
     */
    private void showContent(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFaceText.setText(msg);
            }
        });
    }


    /**
     * 线程等待
     *
     * @throws InterruptedException
     */
    private void threadWait(String a) throws InterruptedException {
        synchronized (mObject) {
            Log.w(TAG, "threadWait" + a);
            mObject.wait();
        }
    }

    /**
     * 线程唤醒
     */
    private void threadNotify() {
        synchronized (mObject) {
            mObject.notifyAll();
            Log.w(TAG, "threadNotify");
        }
    }

    //获取相机的授权信息，耗时操作需另开线程
    public void getAlgorithmAuthInfo(long handle) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AuthQuery authQuery = new AuthQuery();
                String authResult = authQuery.getAlgorithmAuthInfo(handle);
                Log.d("Auth:", authResult);

                Intent intent = new Intent(LivenessActivity.this, AuthActivity.class);
                intent.putExtra("Auth", getJson(authResult));
                startActivity(intent);
            }
        }, "AlgorithmAuth").start();
    }

    public String getJson(String json) {
        StringBuffer stringBuffer = new StringBuffer(64);
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                stringBuffer.append("\n\nAppKey:" + jsonObject.getString("appKey"));
                stringBuffer.append("\n平台:" + jsonObject.getString("platform"));
                stringBuffer.append("\n算法版本:" + jsonObject.getString("algorithmVersion"));
                stringBuffer.append("\n开始时间:" + jsonObject.getString("beginDate"));
                stringBuffer.append("\n截至时间:" + jsonObject.getString("endDate"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return stringBuffer.toString();
    }

    /**
     * 复制文件至文件夹
     */
    public void copyImiData() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                // 复制Forest和license文件到手机存储
                try {
                    showContent("正在复制模型文件到：" + FileHelper.getInstance().getFaceModelFolderPath());
                    String[] paths = getAssets().list("model");
                    for (String path : paths) {
                        FileHelper.getInstance().copy(getAssets().open("model/" + path), FileHelper.getInstance().getFaceModelFolderPath() + path, false);
                        Log.d(TAG, path);
                    }
                    showContent("复制模型文件完毕，初始化算法");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "copyImiData");
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class CameraImage {
        private ImageData faceImage;
        private ImageData depthImage;
        private ImageData irImage;

        public CameraImage(ImageData faceImage, ImageData depthImage, ImageData irImage) {
            this.faceImage = faceImage;
            this.depthImage = depthImage;
            this.irImage = irImage;
        }
    }
}
