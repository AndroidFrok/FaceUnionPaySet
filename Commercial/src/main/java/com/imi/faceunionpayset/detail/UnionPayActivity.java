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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.imi.faceunionpayset.Constant.IMAGE_HEIGHT;
import static com.imi.faceunionpayset.Constant.IMAGE_WIDTH;

/**
 * @author TianLong
 */
public class UnionPayActivity extends AppCompatActivity implements OnSessionInitializeListener, View.OnClickListener, OnFrameAvailableListener, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "xxx";
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

    private Session mSession;
    private CameraOrientation mCameraOrientation;
    private Object mObject = new Object();

    private volatile boolean isQCodeMode = false; //相机是否二维码模式

    private volatile boolean isSessionInit = false;
    private volatile boolean isFace = false;
    private volatile boolean isFacePause = false;
    private volatile boolean isSaveData = false;

    private ConcurrentLinkedQueue<CameraImage> mLinkedBlockingQueue;
    private ConcurrentLinkedQueue<CameraImage> mImageResusePool;
    private Thread mFaceThread;
    //统计次数
    private AtomicInteger mAtomicInteger = new AtomicInteger(0);
    private MultiFormatReader multiFormatReader;
    private ByteBuffer mQRCodeBuffer;
    private ByteBuffer mQRRenderBuffer;
    private ImiCamera mImiCamera;
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_union_pay);
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();

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

        if (mImiCamera!=null){
            //解绑相机回调
            mImiCamera.setFrameAvailableListener(null);
            //设置回人脸模式
            mImiCamera.setColorAutoExposureMode(true);
            mImiCamera.setColorBackLightCompensationMode(1);
        }

        //等待人脸和相机线程执行完之后，在继续执行主线程
        //因为onPause里改变了标志位，导致线程wait，所以这里要线notify
        //防止人脸库release后，线程中还持有人脸的引用，导致Assert（内存地址错误）
        isFace = false;
        if (mFaceThread != null) {
            //唤醒人脸线程
            threadNotify();
            Log.w("ImiFaceSdk", "mFaceThread state:" + mFaceThread.getState().name() + " 次数：" + count);
        }
        //release人脸库
        if (mSession != null) {
            mSession.release();
        }
        Log.i("ImiFaceSdk", "onDestroy ------------------------  ");
    }

    private StringBuilder mStringBuilder = new StringBuilder();

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
        Log.i("xxx","i = "+i);
        if (i == ResultCode.OK) {
            long curTime = System.currentTimeMillis();
            long costtime = curTime - startTime;
            Log.i("xxxxx","cost time = "+costtime);
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

    private AtomicInteger mQR2Face = new AtomicInteger(0);


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_union_pay_face:
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
            case R.id.btn_union_pay_auth:
                getAlgorithmAuthInfo(mImiCamera.getCameraHandle());
                break;
            default:
                break;
        }
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

        mFaceGLSurface.updateColorImage(cameraImage.faceImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
        mFaceGLSurface.requestRender();
    }

    private int netCount = 0;

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
        mLinkedBlockingQueue = new ConcurrentLinkedQueue<>();

        //复用队列
        mImageResusePool = new ConcurrentLinkedQueue<>();

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

        //设置数据回调
        mImiCamera.setFrameAvailableListener(this::onFrameAvailable);

        //复制数据
        copyImiData();
        //初始化人脸数据
        initSession();
        mCameraInfoText.post(new Runnable() {
            @Override
            public void run() {
                Log.i("xxx","sn = "+mImiCamera.getSn());
                mStringBuilder.append("Sn:" + mImiCamera.getSn() + " 硬件版本:" + mImiCamera.getHwVersion() + " 固件版本:" + mImiCamera.getFwVersion());
                mCameraInfoText.setText(mStringBuilder.toString());
            }
        });
    }

    private long startTime = 0;

    /**
     * 初始化人脸
     */
    private void initSession() {
        //Session初始化 Session的具体配置在getSessionConfig()中
        SessionConfig sessionConfig = SessionHelper.getInstance().getSessionConfig();
        // 算法模式（商用模式）
        sessionConfig.faceAlgMode = FaceAlgMode.MODE_COMMERCIAL;

        if (!TextUtils.isEmpty(sessionConfig.appKey)) {
            startTime = System.currentTimeMillis();
            SessionHelper.getInstance().initSession(getApplicationContext(), UnionPayActivity.this::onSessionInitialized, sessionConfig);
        } else {
            showToast("AppKey为空，无法授权，算法未初始化，请设置AppKey");
            showContent("AppKey为空，无法授权，算法未初始化，请设置AppKey");
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.switch_union_pay_mode:
                if (isSessionInit) {
                    String msg = "";
                    if (!isQCodeMode) {
                        mQR2Face.set(0);
                        long tt = System.currentTimeMillis();
                        ImiCamera.getInstance().setColorAutoExposureMode(true);
                        ImiCamera.getInstance().setColorBackLightCompensationMode(2);
                        long tt1 = System.currentTimeMillis() - tt;
                        String mode = ImiCamera.getInstance().getColorBacklightCompensationMode() == 1 ? "刷脸模式" : "二维码模式";
                        mModeSwitch.setText(mode);
                        msg = "当前模式：" + mode + ":切换时间:" + tt1;
                        Log.w(TAG, msg);
                    } else {
                        long tt = System.currentTimeMillis();
                        ImiCamera.getInstance().setColorAutoExposureMode(true);
                        ImiCamera.getInstance().setColorBackLightCompensationMode(1);
                        long tt1 = System.currentTimeMillis() - tt;
                        String mode = ImiCamera.getInstance().getColorBacklightCompensationMode() == 1 ? "刷脸模式" : "二维码模式";
                        mModeSwitch.setText(mode);
                        msg = "当前模式：" + mode + ":切换时间:" + tt1;
                        Log.w(TAG, msg);
                    }
                    isQCodeMode = isChecked;
                    Log.w(TAG, "测试：二维码模式设置完毕");

                } else {
                    mModeSwitch.setChecked(false);
                    showToast("算法尚未初始化");
                }
                break;
            case R.id.switch_union_pay_save:
                isSaveData = isChecked;
                break;
        }

    }

    /**
     * 人脸活体检测
     */
    private long totalDetectFace = 0;
    private long totalLivenessFace = 0;
    private long totalQualityFace = 0;
    private void startFaceLiveness() {
        isFace = true;
        mFaceThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.e("ImiFaceSdk", "Face Thread is start :" + mSession.getDebugHashCode());
                while (isFace) {
//                    if (isFacePause) {
//                        //改变线程为wait状态
//                        Log.w(TAG, "测试：线程暂停");
//                        threadWait("Face");
//                        continue;
//                    }

                    try {
                        //阻塞队列
                        CameraImage cameraImage = mLinkedBlockingQueue.poll();
                        if (cameraImage == null || cameraImage.faceImage == null) {
                            //数据为null，直接返回，进行下次循环
                            continue;
                        }
                        if (!isQCodeMode) {
//                            //二维码切换回人脸模式时。有概率会返回二维码模式下较暗的彩色图，导致提高曝光。这里丢弃前5帧
                            if (mQR2Face.get() < 5) {
                                // TODO: 2020/5/13  这个保存数据仅用于测试
//                                saveData(cameraImage.faceImage.getImageData(), "二维码丢帧"+mQR2Face.get() + "rgb.raw");
//                                saveData(cameraImage.depthImage.getImageData(), "二维码丢帧"+mQR2Face.get() + "depth.raw");
//                                saveData(cameraImage.irImage.getImageData(), "二维码丢帧"+mQR2Face.get() + "ir.raw");

                                mQR2Face.incrementAndGet();
                                continue;
                            }
                            if (mAtomicInteger.get()>0&&mAtomicInteger.get()%100==0){
                                long avaDetect = totalDetectFace/mAtomicInteger.get();
                                long avaLiveness = totalLivenessFace/mAtomicInteger.get();
                                long avaQuality = totalQualityFace/mAtomicInteger.get();
                                Log.e(TAG,"平均结果:"+" 人脸检测："+avaDetect+" 活体平均"+avaLiveness+" 质检平均："+avaQuality);
                            }
                            Log.d(TAG, "测试：人脸线程运行ing1");
                            Frame frame = mSession.update(cameraImage.faceImage, cameraImage.depthImage, cameraImage.irImage);
                            if (frame == null) {
                                Log.d(TAG, "测试：frame == null");
                                return;
                            }
                            Log.d(TAG, "测试：人脸线程运行ing2");
                            //检测人脸
                            long tt = System.currentTimeMillis();
                            Log.d(TAG, "测试：detectFaces前");
                            FaceInfo[] faceInfos = frame.detectFaces();
                            if (faceInfos == null) {
                                Log.d(TAG, "测试：faceInfos == null");
                                return;
                            }
                            Log.d(TAG, "测试：detectFaces后：" + faceInfos.length);
                            long detect = System.currentTimeMillis() - tt;

                            if (faceInfos.length == 0) {
                                showContent("未检测到人脸");
                                Log.d(TAG, "未检测到人脸");
                                mImageResusePool.add(cameraImage);
                                continue;
                            }
                            //Demo中只实现，最大人脸。多人脸时，人脸个数为FaceInfo[]数组长度
                            FaceInfo faceInfo = faceInfos[0];
                            //获取人脸矩形框信息
                            Rect rect = faceInfo.getFaceRect();

                            //渲染
                            mFaceDetectGLSurface.updateColorImage(cameraImage.faceImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                            mFaceDetectGLSurface.updateFaceRect(rect, IMAGE_WIDTH, IMAGE_HEIGHT);
                            mFaceDetectGLSurface.requestRender();
                            mDepthGLSurface.updateDepthImage(cameraImage.depthImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                            mDepthGLSurface.requestRender();
                            ByteBuffer irBuffer = NativeUtils.ir2Rgb(cameraImage.irImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                            mIrGLSurface.updateColorImage(irBuffer, IMAGE_WIDTH, IMAGE_HEIGHT);
                            mIrGLSurface.requestRender();

                            //人脸活体检测
                            long tt1 = System.currentTimeMillis();
                            LivenessResult liveness = frame.detectLiveness(faceInfo);
                            if (liveness == null) {
                                Log.e(TAG, "liveness == null");
                                return;
                            }
                            long live = System.currentTimeMillis() - tt1;
                            if (liveness == null) {
                                if (isSaveData) {
                                    String name = System.currentTimeMillis() + "_livenessfailed_";
                                    saveData(cameraImage.faceImage.getImageData(), name + "rgb.raw");
                                    saveData(cameraImage.depthImage.getImageData(), name + "depth.raw");
                                    saveData(cameraImage.irImage.getImageData(), name + "ir.raw");
                                }
                                Log.d(TAG, "活体检测失败" );
                                showContent("活体检测失败" );
                                mImageResusePool.add(cameraImage);
                                continue;
                            }
                            //人脸质量评估
                            long tt0 = System.currentTimeMillis();
                            FaceQuality faceQuality = frame.detectFaceQuality();
                            if (faceQuality == null) {
                                Log.e(TAG, "faceQuality == null");
                                return;
                            }
                            long quality = System.currentTimeMillis() - tt0;
                            if (faceQuality == null) {
                                if (isSaveData) {
                                    String name = System.currentTimeMillis() + "_qualityfailed_";
                                    saveData(cameraImage.faceImage.getImageData(), name + "rgb.raw");
                                    saveData(cameraImage.depthImage.getImageData(), name + "depth.raw");
                                    saveData(cameraImage.irImage.getImageData(), name + "ir.raw");
                                }
                                Log.d(TAG, "人脸质量评估失败" );
                                showContent("人脸质量评估失败" );
                                mImageResusePool.add(cameraImage);
                                continue;
                            }
                            if (isSaveData) {
                                String name = System.currentTimeMillis() + "_" + faceQuality.getQuality().toInfo() + "_" + liveness.getLivenessScore() + "_";
                                saveData(cameraImage.faceImage.getImageData(), name + "rgb.raw");
                                saveData(cameraImage.depthImage.getImageData(), name + "depth.raw");
                                saveData(cameraImage.irImage.getImageData(), name + "ir.raw");
                            }

                            Log.i("xxxxx","live score = "+liveness.getLivenessScore());

                            showContent(getLivenessMsg(liveness)+"\n人脸相机距离："+liveness.getFaceDistance()+"cm" + "\n人脸质量：" + faceQuality.getQuality().toInfo() + "\n人脸检测：" + detect + "ms  " + "\n质检耗时：" + quality + "ms\n活体耗时：" + live + "ms\n" + "检测次数：" + mAtomicInteger.getAndIncrement() );
                            Log.w(TAG, getLivenessMsg(liveness)+"\n人脸相机距离："+liveness.getFaceDistance() + " 人脸质量：" + faceQuality.getQuality().toInfo() + " 人脸检测：" + detect + "ms  " + " 质检耗时：" + quality + "ms 活体耗时：" + live + "ms " + "检测次数：" + mAtomicInteger.get() );
                            totalDetectFace+=detect;
                            totalLivenessFace+=live;
                            totalQualityFace+=quality;
                            mImageResusePool.add(cameraImage);
                        } else {
                            if (mQRCodeBuffer == null) {
                                mQRCodeBuffer = ByteBuffer.allocateDirect(IMAGE_WIDTH * IMAGE_HEIGHT * 3).order(ByteOrder.nativeOrder());
                                mQRCodeBuffer.position(0);
                            }
                            if (mQRRenderBuffer == null) {
                                mQRRenderBuffer = ByteBuffer.allocateDirect(IMAGE_WIDTH * IMAGE_HEIGHT * 3).order(ByteOrder.nativeOrder());
                                mQRRenderBuffer.position(0);
                            }
                            NativeUtils.copyByteBufferData(cameraImage.faceImage.getImageData(), mQRRenderBuffer, IMAGE_WIDTH * IMAGE_HEIGHT * 3);
                            NativeUtils.copyByteBufferData(cameraImage.faceImage.getImageData(), mQRCodeBuffer, IMAGE_WIDTH * IMAGE_HEIGHT * 3);
                            mFaceDetectGLSurface.updateColorImage(mQRRenderBuffer, IMAGE_WIDTH, IMAGE_HEIGHT);
                            mFaceDetectGLSurface.requestRender();
                            //二维码模式
                            byte[] rgbBytes = new byte[IMAGE_WIDTH * IMAGE_HEIGHT * 3];
                            mQRCodeBuffer.get(rgbBytes);
                            convertCode(rgbBytes);
                            mQRCodeBuffer.position(0);
                            mImageResusePool.add(cameraImage);
                        }
                    } catch (IllegalStateException e) {
                        Log.d(TAG, e.getMessage());
                        e.printStackTrace();
                    }
                }

                Log.e("ImiFaceSdk", "Face Thread is exit :" + mSession.getDebugHashCode());
            }
        }, "alg:" + mSession.getDebugHashCode());
        mFaceThread.start();
    }

    /**
     * @detail[cn] 活体检测。活体值超过0.5即可认为是活体。
     * @return[cn] 图像帧中人的活体置信度。
     * 0.0--1.0：活体置信度。
     * 0-> 活体检测成功
     * -101-> 深度图错误，如图像数据为空、图像格式错误等
     * -102-> 人脸距离相机模组过远
     * -103-> 人脸距离深度图边缘过近
     * -104-> 人脸距离相机模组过近
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
                msg = "活体检测：人脸距离相机模组过远";
            } else if (errorCode == -103) {
                msg = "活体检测：人脸距离深度图边缘过近";
            } else if (errorCode == -104) {
                msg = "活体检测：图像中人脸距离相机过近";
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

                Intent intent = new Intent(UnionPayActivity.this, AuthActivity.class);
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
        String msg = stringBuffer.toString();
        Log.w(TAG, "Auth:" + msg);
        return msg;
    }

    /**
     * @param dataBuffer 保存的数据
     * @param dataName   文件名称
     */
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
                    Log.i("xxx","正在复制模型文件到：" + FileHelper.getInstance().getFaceModelFolderPath());
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
                Toast.makeText(UnionPayActivity.this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
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

        public CameraImage(ImageData faceImage, ImageData depthImage, ImageData irImage) {
            this.faceImage = faceImage;
            this.depthImage = depthImage;
            this.irImage = irImage;
        }
    }
}
