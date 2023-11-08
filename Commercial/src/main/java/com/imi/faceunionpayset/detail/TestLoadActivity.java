package com.imi.faceunionpayset.detail;

import android.content.Intent;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
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
import com.imi.sdk.face.FaceAlgMode;
import com.imi.sdk.face.FaceHelper;
import com.imi.sdk.face.FaceInfo;
import com.imi.sdk.face.FaceQuality;
import com.imi.sdk.face.Frame;
import com.imi.sdk.face.LivenessResult;
import com.imi.sdk.face.OnSessionInitializeListener;
import com.imi.sdk.face.Session;
import com.imi.sdk.face.SessionConfig;
import com.imi.sdk.facebase.base.ResultCode;
import com.imi.sdk.facebase.utils.ImageData;
import com.imi.sdk.facebase.utils.Rect;
import com.imi.sdk.faceid.BuildConfig;
import com.imi.sdk.utils.NativeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author TianLong
 * 测试代码
 */
public class TestLoadActivity extends AppCompatActivity implements OnSessionInitializeListener, View.OnClickListener, OnFrameAvailableListener, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = TestLoadActivity.class.getSimpleName();
    private int IMAGE_WIDTH = 640;
    private int IMAGE_HEIGHT = 480;
    private ConstraintLayout mConstraintLayout;
    private FaceGLSurface mFaceGLSurface;
    private FaceGLSurface mFaceDetectGLSurface;
    private DepthGLSurface mDepthGLSurface;
    private FaceGLSurface mIrGLSurface;
    private SwitchCompat mModeSwitch;
    private SwitchCompat mSaveSwitch;
    private TextView mCameraInfoText;
    private TextView mFaceText;
    private Button mFaceButton;
    private Button mAuthButton;
    private ImiCamera mImiCamera;
    private Session mSession;
    private CameraOrientation mCameraOrientation;
    private Object mObject = new Object();
    private volatile boolean isCameraInit = false;
    private volatile boolean isQCodeMode = false; //相机是否二维码模式
    private volatile boolean isSessionInit = false;
    private volatile boolean isFace = false;
    private volatile boolean isFacePause = false;
    private volatile boolean isSaveData = false;
    private LinkedBlockingQueue<CameraImage> mLinkedBlockingQueue;
    private ConcurrentLinkedQueue<CameraImage> mImageResusePool;
    private Thread mFaceThread;
    //统计次数
    private AtomicInteger mAtomicInteger = new AtomicInteger(0);
    private MultiFormatReader multiFormatReader;
    private ByteBuffer mQRCodeBuffer;
    private int count = 0;
    private StringBuilder mStringBuilder = new StringBuilder();

    private int netCount = 0;
    private List<String> pathAll = new ArrayList<>();
    private CountDownLatch mCountDownLatch = new CountDownLatch(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_union_pay);
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.w(TAG, "onResume:" + isSessionInit + isFacePause);

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
            threadNotify();
//                mSemaphore1.release();
            //添加空数据，唤醒因阻塞队列阻塞的线程
            mLinkedBlockingQueue.add(new CameraImage(null, null, null));
//                mCountDownLatch.await();
            Log.w(TAG, "mFaceThread state:" + mFaceThread.getState().name() + " 次数：" + count);
        }

        //release人脸库
        if (mSession != null) {
            mSession.release();
            Log.w(TAG, "Session.release" + " db ：" + count);
        }
    }


    @Override
    public void onSessionInitialized(int i, String s) {
        Log.d("RenderMode", "========> FaceAlg>" + Thread.currentThread().getPriority());
        String msg = "";
        mSession = SessionHelper.getInstance().getFaceSession();
        mCameraInfoText.post(new Runnable() {
            @Override
            public void run() {
                mStringBuilder.append(" 算法版本号:" + mSession.getSDKVersion() + "_" + BuildConfig.VERSION_NAME);
                mCameraInfoText.setText(mStringBuilder.toString());
            }
        });
        if (i == ResultCode.OK) {
            isSessionInit = true;

            startFaceLiveness();
            msg = i + "  " + s;
        } else if (i == ResultCode.NETWORK_CONNECTION_ERROR && netCount < 3) {
            msg = "重试次数：" + netCount++;
            showContent("人脸算法：" + msg);
            initSession();
        } else {
            msg = i + "  " + s + "\n 算法模型文件目录：" + Constant.FACE_MODEL_PATH;
        }
        showToast("人脸算法：" + msg);
        showContent("人脸算法：" + msg);
    }

    private int testCount = 0;
    private long totalDetectFace = 0;
    private long totalLivenessFace = 0;
    private long totalQualityFace = 0;
//    private Semaphore mSemaphore = new Semaphore(1);
//    private Semaphore mSemaphore1 = new Semaphore(1);
    ByteBuffer color = ByteBuffer.allocateDirect(640*480*3).order(ByteOrder.nativeOrder());
    ByteBuffer depth = ByteBuffer.allocateDirect(640*480*2).order(ByteOrder.nativeOrder());
    ByteBuffer ir = ByteBuffer.allocateDirect(640*480*2).order(ByteOrder.nativeOrder());
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

        //二维码和刷脸模式互斥，二维码模式，深度图和IR图返回为null
        if (isQCodeMode) {
            NativeUtils.copyByteBufferData(cameraFrame.getColorImage().getImageData(), cameraImage.faceImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 3);
        } else {
            //第一帧，深度图和IR图可能为null
            if (cameraFrame.getDepthImage() == null || cameraFrame.getIrImage() == null) {
                return;
            }
            NativeUtils.copyByteBufferData(cameraFrame.getColorImage().getImageData(), cameraImage.faceImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 3);
            NativeUtils.copyByteBufferData(cameraFrame.getDepthImage().getImageData(), cameraImage.depthImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 2);
            NativeUtils.copyByteBufferData(cameraFrame.getIrImage().getImageData(), cameraImage.irImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 2);
        }

        //传输数据
        if (mLinkedBlockingQueue.size() > 0) {
            CameraImage cameraImageData = mLinkedBlockingQueue.poll();
            if (cameraImageData != null) {
                mImageResusePool.add(cameraImageData);
            }
        }

        mLinkedBlockingQueue.offer(cameraImage);
        mFaceGLSurface.updateColorImage(cameraFrame.getColorImage().getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
        mFaceGLSurface.requestRender();
    }

    private volatile int mode = 1;

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.switch_union_pay_save:
                isSaveData = isChecked;
                break;
        }
    }

    /*------------------------------------------- 私有方法 --------------------------------------------*/
    private void init() {
        count = getIntent().getIntExtra("count", -1);
        mConstraintLayout = findViewById(R.id.layout_union_pay_constraint);
        mFaceGLSurface = findViewById(R.id.gl_union_pay_face);
        mFaceDetectGLSurface = findViewById(R.id.gl_union_pay_face_dectect);
        mDepthGLSurface = findViewById(R.id.gl_union_pay_depth_detect);
        mIrGLSurface = findViewById(R.id.gl_union_pay_ir_dectect);
        mModeSwitch = findViewById(R.id.switch_union_pay_mode);
        mSaveSwitch = findViewById(R.id.switch_union_pay_save);
        mCameraInfoText = findViewById(R.id.tv_union_pay_info);
        mFaceText = findViewById(R.id.tv_union_pay_face);
        mFaceButton = findViewById(R.id.btn_union_pay_face);
        mAuthButton = findViewById(R.id.btn_union_pay_auth);
        mAuthButton.setOnClickListener(this::onClick);
        mFaceButton.setOnClickListener(this::onClick);
        mModeSwitch.setOnCheckedChangeListener(this::onCheckedChanged);
        mSaveSwitch.setOnCheckedChangeListener(this::onCheckedChanged);
        //数据初始化
        mLinkedBlockingQueue = new LinkedBlockingQueue<>(2);

        //复用队列
        mImageResusePool = new ConcurrentLinkedQueue<>();

        pathAll = FileHelper.getInstance().getFiles(FileHelper.getInstance().getFaceTestFolderPath(), "_color.jpg");
        for (String path : pathAll) {
            Log.w(TAG, path);
        }
        //二维码相关类初始化
        multiFormatReader = new MultiFormatReader();
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
                    constraintSet.connect(R.id.gl_union_pay_face, ConstraintSet.END, R.id.gl_union_pay_depth_detect, ConstraintSet.END);
                    constraintSet.setDimensionRatio(mFaceGLSurface.getId(), "3:4");
                    constraintSet.setDimensionRatio(mFaceDetectGLSurface.getId(), "3:4");
                    constraintSet.setDimensionRatio(mDepthGLSurface.getId(), "3:4");
                    constraintSet.setDimensionRatio(mIrGLSurface.getId(), "3:4");
                    constraintSet.applyTo(mConstraintLayout);
                }
            });
        }
        IMAGE_WIDTH = 640;
        IMAGE_HEIGHT = 480;
        //设置数据回调
        mImiCamera.setFrameAvailableListener(this::onFrameAvailable);

        //复制数据
        copyImiData();
        //初始化人脸数据
        initSession();
        mCameraInfoText.post(new Runnable() {
            @Override
            public void run() {
                mStringBuilder.append("Sn:" + mImiCamera.getSn() + " 硬件版本:" + mImiCamera.getHwVersion() + " 固件版本:" + mImiCamera.getFwVersion());
                mCameraInfoText.setText(mStringBuilder.toString());
            }
        });
    }

    /**
     * 初始化人脸
     */
    private void initSession() {
        //Session初始化 Session的具体配置在getSessionConfig()中
        SessionConfig sessionConfig = SessionHelper.getInstance().getSessionConfig();
        // 算法模式（商用模式）
        sessionConfig.faceAlgMode = FaceAlgMode.MODE_COMMERCIAL;
        if (!TextUtils.isEmpty(sessionConfig.appKey)) {
            SessionHelper.getInstance().initSession(getApplicationContext(), TestLoadActivity.this::onSessionInitialized, sessionConfig);
        } else {
            showContent("AppKey为空，无法授权，算法未初始化，请设置AppKey");
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_union_pay_face:
                if (isSessionInit && isFace) {
                    if (!isQCodeMode) {
                        if (isFacePause) {
                            isFacePause = false;
                            threadNotify();
                        } else {
                            isFacePause = true;
                        }
                    } else {
                        showToast("当前为二维码模式，请切换为人脸模式");
                    }
                } else {
                    showToast("算法未初始化，或者人脸算法线程尚未执行");
                }
                break;
            case R.id.btn_union_pay_auth:
                if (isCameraInit && mImiCamera != null) {
//                    getAlgorithmAuthInfo(mImiCamera.getCameraHandle());
                    mode++;
                    mode = mode % 4;

                } else {
                    showToast("请先初始化相机");
                }
                break;
            default:
                break;
        }
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
                    if (isFacePause) {
                        Log.d(TAG, "isFacePause:" + isFacePause);
                        //改变线程为wait状态
                        Log.w(TAG, "测试：线程暂停");
                        threadWait("Face");
                    }
                    try {

                        //阻塞队列
//                        CameraImage cameraImage1 = mLinkedBlockingQueue.take();
//                        if (cameraImage1.faceImage == null) {
//                            Log.d(TAG, "测试：人脸线程数据为null");
//                            continue;
//                        }
                        if (!isQCodeMode) {
                            boolean aa= testCount>=pathAll.size();
                            if (aa){
//                                long avaDetect = totalDetectFace/mAtomicInteger.get();
//                                long avaLiveness = totalLivenessFace/mAtomicInteger.get();
//                                long avaQuality = totalQualityFace/mAtomicInteger.get();
//                                Log.e(TAG,"平均结果:"+" 人脸检测："+avaDetect+" 活体平均"+avaLiveness+" 质检平均："+avaQuality);
                                return;
                            }
                            String colorPath = pathAll.get(testCount);
                            String depthPath = pathAll.get(testCount).replace("color","depth").replace("jpg","png");
                            String irPath = pathAll.get(testCount).replace("color","infrared").replace("jpg","png");
//
                            color.position(0);
                            depth.position(0);
                            ir.position(0);
                            mFaceGLSurface.updateColorImage(color,IMAGE_WIDTH,IMAGE_HEIGHT);
                            mFaceGLSurface.requestRender();
                            ImageData faceImage = new ImageData(IMAGE_WIDTH, IMAGE_HEIGHT, ImageData.Type.RGB, color);
                            ImageData depthImage = new ImageData(IMAGE_WIDTH, IMAGE_HEIGHT, ImageData.Type.DEPTH, depth);
                            ImageData irImage = new ImageData(IMAGE_WIDTH, IMAGE_HEIGHT, ImageData.Type.IR, ir);
                            CameraImage cameraImage = new CameraImage(faceImage, depthImage, irImage,colorPath);
                            cameraImage.name = colorPath;
                            testCount++;
                            Frame frame = mSession.update(cameraImage.faceImage, cameraImage.depthImage, cameraImage.irImage);

                            //检测人脸
                            long tt = System.currentTimeMillis();
                            FaceInfo[] faceInfos = frame.detectFaces();
                            long detect = System.currentTimeMillis() - tt;

                            if (faceInfos.length == 0) {
                                showContent("未检测到人脸"+"name:"+cameraImage.name+"\n  ");
                                Log.w(TAG,"未检测到人脸 "+mode+" name:"+cameraImage.name+"\n  ");
//                                mImageResusePool.add(cameraImage1);
//                                mSemaphore.release();
                                continue;
                            }
//                            Demo中只实现，最大人脸。多人脸时，人脸个数为FaceInfo[]数组长度
                            FaceInfo faceInfo = faceInfos[0];
//                            获取人脸矩形框信息
                            Rect rect = faceInfo.getFaceRect();

//                            渲染
                            mFaceDetectGLSurface.updateColorImage(cameraImage.faceImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                            mFaceDetectGLSurface.updateFaceRect(rect, IMAGE_WIDTH, IMAGE_HEIGHT);
                            mFaceDetectGLSurface.requestRender();
                            mDepthGLSurface.updateDepthImage(cameraImage.depthImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                            mDepthGLSurface.requestRender();
                            ByteBuffer irBuffer = NativeUtils.ir2Rgb(cameraImage.irImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                            mIrGLSurface.updateColorImage(irBuffer, IMAGE_WIDTH, IMAGE_HEIGHT);
                            mIrGLSurface.requestRender();

                            //人脸质量评估
                            long tt0 = System.currentTimeMillis();
                            FaceQuality faceQuality = frame.detectFaceQuality();

                            long quality = System.currentTimeMillis() - tt0;
                            if (faceQuality == null) {
                                if (isSaveData) {
                                    String name = System.currentTimeMillis() + "_qualityfailed_";
                                    saveData(cameraImage.faceImage.getImageData(), name + "rgb.raw");
                                    saveData(cameraImage.depthImage.getImageData(), name + "depth.raw");
                                    saveData(cameraImage.irImage.getImageData(), name + "ir.raw");
                                }
                                Log.w(TAG,"人脸质量评估失败 "+mode+" name:"+cameraImage.name+"\n  ");
                                showContent("人脸质量评估失败 "+" name:"+cameraImage.name+"\n  ");
//                                mImageResusePool.add(cameraImage1);
//                                mSemaphore.release();
                                continue;
                            }

                            //人脸活体检测
                            long tt1 = System.currentTimeMillis();
                            LivenessResult liveness = frame.detectLiveness(faceInfo);
                            long live = System.currentTimeMillis() - tt1;
                            if (liveness == null) {
                                if (isSaveData) {
                                    String name = System.currentTimeMillis() + "_livenessfailed_";
                                    saveData(cameraImage.faceImage.getImageData(), name + "rgb.raw");
                                    saveData(cameraImage.depthImage.getImageData(), name + "depth.raw");
                                    saveData(cameraImage.irImage.getImageData(), name + "ir.raw");
                                }
                                Log.w(TAG,"活体检测失败 "+mode+" name:"+cameraImage.name+"\n  ");
                                showContent("活体检测失败"+"name:"+cameraImage.name+"\n  ");
//                                mImageResusePool.add(cameraImage1);
//                                mSemaphore.release();
                                continue;
                            }
                            if (isSaveData) {
                                String name = System.currentTimeMillis() + "_" + faceQuality.getQuality().toInfo() + "_" + liveness.getLivenessScore() + "_";
                                saveData(cameraImage.faceImage.getImageData(), name + "rgb.raw");
                                saveData(cameraImage.depthImage.getImageData(), name + "depth.raw");
                                saveData(cameraImage.irImage.getImageData(), name + "ir.raw");
                            }

                            Log.d("LivenessScore", ">>>>>>>>>>>>>>" + liveness);
                            Log.d(TAG, ">>>>>>>>>>>>>>检测, "+mode+" name:"+cameraImage.name+"  "+ detect + "    质量评估," + quality + "    活体," + live);
                            showContent("name:" + cameraImage.name + "\n  " + getLivenessMsg(liveness) + "\n人脸质量：" + faceQuality.getQuality().toInfo() + "\n人脸检测：" + detect + "ms  " + "\n质检耗时：" + quality + "ms\n活体耗时：" + live + "ms\n" + "检测次数：" + mAtomicInteger.getAndIncrement() + "  光照：" + FaceHelper.getExposureTime(mSession));
                            Log.w(TAG, "模式:" + mode + " name:" + cameraImage.name + "\n  " + getLivenessMsg(liveness) + "\n人脸质量：" + faceQuality.getQuality().toInfo() + "\n人脸检测：" + detect + "ms  " + "\n质检耗时：" + quality + "ms\n活体耗时：" + live + "ms\n" + "检测次数：" + mAtomicInteger.get() + "  光照：" + FaceHelper.getExposureTime(mSession));
                            totalDetectFace+=detect;
                            totalLivenessFace+=live;
                            totalQualityFace+=quality;

//                            mImageResusePool.add(cameraImage1);
//                            mSemaphore.release();
                        } else {
                        }
                    } catch (IllegalStateException e) {
                        Log.d(TAG, e.getMessage());
                        e.printStackTrace();
                    }
                }
                Log.d(TAG, ">>>>>>>>>>>>>>人脸线程执行完毕" );
                mCountDownLatch.countDown();
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


    //获取相机的授权信息，耗时操作需另开线程
    private void getAlgorithmAuthInfo(long handle) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AuthQuery authQuery = new AuthQuery();
                String authResult = authQuery.getAlgorithmAuthInfo(handle);
                Log.d("Auth:", authResult);

                Intent intent = new Intent(TestLoadActivity.this, AuthActivity.class);
                intent.putExtra("Auth", getJson(authResult));
                startActivity(intent);
            }
        }, "AlgorithmAuth").start();
    }

    private String getJson(String json) {
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

    private void saveData(ByteBuffer dataBuffer, String dataName) {
        boolean save = false;
        if (dataBuffer != null) {
            if (FileHelper.getInstance().isExceedMemorySize(100)) {
                byte[] data = new byte[dataBuffer.remaining()];
                dataBuffer.get(data);
                dataBuffer.position(0);
                save = FileHelper.getInstance().saveFileWithByte(data, FileHelper.getInstance().getFaceImageFolderPath(), dataName);
            }
        }

        Log.d(TAG, save ? "保存成功" : "保存失败" + " " + dataName);
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
            //主线程wait，等待该线程。防止模型没有拷贝完，就开使初始化算法。
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 二维码解析
     *
     * @param bytes
     */
    public void convertCode(byte[] bytes) {
        long a = System.currentTimeMillis();
        int[] pixel = rgb24ToPixel(bytes, IMAGE_WIDTH, IMAGE_HEIGHT);
        RGBLuminanceSource rgbLuminanceSource = new RGBLuminanceSource(IMAGE_WIDTH, IMAGE_HEIGHT, pixel);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(rgbLuminanceSource));
        String s = null;
        try {
            Result result = multiFormatReader.decode(binaryBitmap);
            Log.i(">>>", result.toString());
            s = result.toString();
        } catch (Exception | NoSuchMethodError e) {
            e.printStackTrace();
            Log.i(">>>", "detect failed NotFound:" + e.getMessage());
            s = "detect qr code failed NotFound:" + e.getMessage();
        } finally {
            Log.e(">>>", "detect qr code time:" + (System.currentTimeMillis() - a));
        }
        showContent(s != null ? s : "");
    }

    /**
     * rgb转成int值
     *
     * @param rgb24
     * @param width
     * @param height
     * @return
     */
    private int[] rgb24ToPixel(byte[] rgb24, int width, int height) {
        int[] pix = new int[rgb24.length / 3];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int idx = width * i + j;
                int rgbIdx = idx * 3;
                int red = rgb24[rgbIdx];
                int green = rgb24[rgbIdx + 1];
                int blue = rgb24[rgbIdx + 2];
                int color = (blue & 0x000000FF) | (green << 8 & 0x0000FF00) | (red << 16 & 0x00FF0000);
                pix[idx] = color;
            }
        }
        return pix;
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
                Toast.makeText(TestLoadActivity.this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
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
     */
    private void threadWait(String a) {
        synchronized (mObject) {
            Log.w(TAG, "threadWait" + a);
            try {
                mObject.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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

    private class CameraImage {
        private ImageData faceImage;
        private ImageData depthImage;
        private ImageData irImage;
        private String name = "";

        public CameraImage(ImageData faceImage, ImageData depthImage, ImageData irImage) {
            this.faceImage = faceImage;
            this.depthImage = depthImage;
            this.irImage = irImage;
        }

        public CameraImage(ImageData faceImage, ImageData depthImage, ImageData irImage,String name) {
            this.faceImage = faceImage;
            this.depthImage = depthImage;
            this.irImage = irImage;
            this.name = name;
        }
    }
}
