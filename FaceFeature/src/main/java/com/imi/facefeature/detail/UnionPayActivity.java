package com.imi.facefeature.detail;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.imi.camera.camera.CameraFrame;
import com.imi.camera.camera.CameraOrientation;
import com.imi.camera.camera.ImiCamera;
import com.imi.camera.listener.OnFrameAvailableListener;
import com.imi.facefeature.Constant;
import com.imi.facefeature.R;
import com.imi.facefeature.gl.DepthGLSurface;
import com.imi.facefeature.gl.FaceGLSurface;
import com.imi.facefeature.helper.FileHelper;
import com.imi.facefeature.helper.SessionHelper;
import com.imi.sdk.face.AuthQuery;
import com.imi.sdk.face.FaceAlgMode;
import com.imi.sdk.face.FaceInfo;
import com.imi.sdk.face.Frame;
import com.imi.sdk.face.LivenessResult;
import com.imi.sdk.face.OnSessionInitializeListener;
import com.imi.sdk.face.Session;
import com.imi.sdk.face.SessionConfig;
import com.imi.sdk.face.feature.FaceFeature;
import com.imi.sdk.face.feature.FeatureInfo;
import com.imi.sdk.face.feature.FeatureSession;
import com.imi.sdk.face.feature.InitCode;
import com.imi.sdk.facebase.base.ResultCode;
import com.imi.sdk.facebase.utils.ImageData;
import com.imi.sdk.facebase.utils.Rect;
import com.imi.sdk.facefeature.ImageUtil;
import com.imi.sdk.faceid.BuildConfig;
import com.imi.sdk.image.ImageInfo;
import com.imi.sdk.utils.NativeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static com.imi.facefeature.Constant.APP_KEY;
import static com.imi.facefeature.Constant.IMAGE_HEIGHT;
import static com.imi.facefeature.Constant.IMAGE_WIDTH;
import static com.imi.sdk.image.ImageInfo.IMGFMT_RGB;

/**
 * @author TianLong
 */
public class UnionPayActivity extends AppCompatActivity implements OnSessionInitializeListener, View.OnClickListener, OnFrameAvailableListener, CompoundButton.OnCheckedChangeListener {
    private final String TAG = "xxx";
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
    private ImageView mFeatureImage;

    private Session mSession;
    private CameraOrientation mCameraOrientation;
    private Object mObject = new Object();

    private volatile boolean isSessionInit = false;
    private volatile boolean isFace = false;
    private volatile boolean isFacePause = false;
    private LinkedBlockingQueue<CameraImage> mLinkedBlockingQueue;
    private ConcurrentLinkedQueue<CameraImage> mImageResusePool;
    private volatile Thread mFaceThread;
    //统计次数
    private ImiCamera mImiCamera;
    private int count = 0;
    private CountDownLatch mCountDownLatch = new CountDownLatch(1);
    private Bitmap mFeatureBitmap;
    private ByteBuffer mFeatureBuffer;
    private float[] mFeatureImg;
    private FaceFeature faceFeature1;
    private int mImageWidth;
    private int mImageHeight;
    private String imagePath = FileHelper.getInstance().getFaceImageFolderPath() + "face1.jpg";
    private StringBuilder mStringBuilder = new StringBuilder();
    private int netCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_union_pay);
        init();
    }

//    Handler mHandler = new Handler();

    @Override
    protected void onResume() {
        super.onResume();
        Log.w(TAG, "onResume" + isFacePause);
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
        Log.w(TAG, "onDestroy");
        //解绑相机回调
        mImiCamera.setFrameAvailableListener(null);

        //等待人脸和相机线程执行完之后，在继续执行主线程
        //因为onPause里改变了标志位，导致线程wait，所以这里要线notify
        //防止人脸库release后，线程中还持有人脸的引用，导致Assert（内存地址错误）
        isFace = false;
        if (mFaceThread != null) {
            try {
                //唤醒人脸线程
                threadNotify();
                //添加空数据，唤醒因阻塞队列阻塞的线程
                Log.w(TAG, "mFaceThread state:" + "阻塞队列添加数据");
                mLinkedBlockingQueue.add(new CameraImage(null, null, null));
                Log.w(TAG, "mFaceThread state:" + "阻塞队列唤醒线程");
                mFaceThread.join();
                Log.w(TAG, "mFaceThread state:" + mFaceThread.getState().name() + " 次数：" + count);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //release人脸库
        if (mSession != null) {
            mSession.release();
        }
        //人脸特征库 release
        FeatureSession.getInstance().release();
        if (mFeatureBitmap != null) {
            mFeatureBitmap.recycle();
        }
    }

    @Override
    public void onSessionInitialized(int i, String s) {
        mSession = SessionHelper.getInstance().getFaceSession();
        Log.i("yyy","imagePath = "+imagePath);
        mFeatureBitmap = BitmapFactory.decodeFile(imagePath);
        mImageWidth = mFeatureBitmap.getWidth();
        mImageHeight = mFeatureBitmap.getHeight();

        Log.w(TAG, mImageWidth + "  " + mImageHeight + "  " + mFeatureBitmap.getAllocationByteCount());
        mFeatureBuffer = ByteBuffer.allocateDirect(mImageWidth * mImageHeight * 3).order(ByteOrder.nativeOrder());
        ImageUtil util = new ImageUtil();
        ImageInfo imageInfo = new ImageInfo(mImageWidth, mImageHeight, IMGFMT_RGB);
        util.loadColorImage(mFeatureBuffer, imageInfo, mFeatureBitmap);
        mFeatureBuffer.position(0);

        mFeatureImage.post(new Runnable() {
            @Override
            public void run() {
                mFeatureImage.setImageBitmap(mFeatureBitmap);
            }
        });
        String msg = "";

        mCameraInfoText.post(new Runnable() {
            @Override
            public void run() {
                mStringBuilder.append(" 算法版本号:" + mSession.getSDKVersion() + "_" + BuildConfig.VERSION_NAME);
                mCameraInfoText.setText(mStringBuilder.toString());
            }
        });
        if (i == ResultCode.OK) {
            isSessionInit = true;
            long tt = System.currentTimeMillis();
            InitCode initCode = FeatureSession.getInstance().initialize(Constant.FACE_MODEL_PATH, mSession.getFeatureToken());
            Log.w(TAG, "人脸识别初始化:" + (System.currentTimeMillis() - tt));
            if (initCode.code == InitCode.OK) {
                startFaceLiveness();
            }
            msg = i + "  " + s + "  人脸识别:" + initCode.code + "  " + initCode.info;
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

        //第一帧，深度图和IR图可能为null
        if (cameraFrame.getDepthImage() == null || cameraFrame.getIrImage() == null) {
            return;
        }
        NativeUtils.copyByteBufferData(cameraFrame.getColorImage().getImageData(), cameraImage.faceImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 3);
        NativeUtils.copyByteBufferData(cameraFrame.getDepthImage().getImageData(), cameraImage.depthImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 2);
        NativeUtils.copyByteBufferData(cameraFrame.getIrImage().getImageData(), cameraImage.irImage.getImageData(), IMAGE_WIDTH * IMAGE_HEIGHT * 2);
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

    /**
     * 初始化人脸
     */
    private void initSession() {
        //Session初始化 Session的具体配置在getSessionConfig()中
        SessionConfig sessionConfig = SessionHelper.getInstance().getSessionConfig();
        sessionConfig.camera = mImiCamera;
        // 授权信息
        sessionConfig.appKey = APP_KEY;
        // 算法模式（商用模式）
        sessionConfig.faceAlgMode = FaceAlgMode.MODE_COMMERCIAL;
        if (!TextUtils.isEmpty(sessionConfig.appKey)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mCountDownLatch.await();
                        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                        SessionHelper.getInstance().initSession(getApplicationContext(), UnionPayActivity.this::onSessionInitialized, sessionConfig);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } else {
            showToast("AppKey为空，无法授权，算法未初始化，请设置AppKey");
            showContent("AppKey为空，无法授权，算法未初始化，请设置AppKey");
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.switch_union_pay_save:
                break;
        }
    }

    Bitmap bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);

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
        mFeatureImage = findViewById(R.id.iv_union_pay_feature);

        mAuthButton.setOnClickListener(this::onClick);
        mFaceButton.setOnClickListener(this::onClick);
        mModeSwitch.setOnCheckedChangeListener(this::onCheckedChanged);
        mSaveSwitch.setOnCheckedChangeListener(this::onCheckedChanged);

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
                mStringBuilder.append("Sn:" + mImiCamera.getSn() + " 硬件版本:" + mImiCamera.getHwVersion() + " 固件版本:" + mImiCamera.getFwVersion());
                mCameraInfoText.setText(mStringBuilder.toString());
            }
        });
//        mHandler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                finish();
//            }
//        }, 4000);
    }

    /**
     * 人脸活体检测
     */
    private void startFaceLiveness() {

        isFace = true;
        mFaceThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isFace) {
                    if (isFacePause) {
                        //改变线程为wait状态
                        Log.w(TAG, "测试：线程暂停");
                        threadWait("Face");
                        continue;
                    }

                    try {
                        //阻塞队列
                        CameraImage cameraImage = mLinkedBlockingQueue.take();
                        if (cameraImage == null || cameraImage.faceImage == null || cameraImage.depthImage == null || cameraImage.irImage == null) {
                            Log.d(TAG, "测试：人脸线程数据为null");
                            continue;
                        }
                        Log.w(TAG, "mFaceThread :" + "新增数据");
                        Frame frame = mSession.update(cameraImage.faceImage, cameraImage.depthImage, cameraImage.irImage);
                        Log.w(TAG, "mFaceThread :" + "检测前");
                        if (frame == null) {
                            Log.w(TAG, "frame :" + "为NULL");
                            continue;
                        }
                        //检测人脸
                        FaceInfo[] faceInfos = frame.detectFaces();
                        Log.w(TAG, "mFaceThread :" + "检测后");
                        if (faceInfos == null || faceInfos.length == 0) {
                            showContent("未检测到人脸");
                            Log.d(TAG, "未检测到人脸");
                            mImageResusePool.add(cameraImage);
                            continue;
                        }
                        //Demo中只实现，最大人脸。多人脸时，人脸个数为FaceInfo[]数组长度
                        FaceInfo faceInfo = faceInfos[0];
                        //获取人脸矩形框信息
                        Rect rect = faceInfo.getFaceRect();
//                        //渲染
                        mFaceDetectGLSurface.updateColorImage(cameraImage.faceImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                        mFaceDetectGLSurface.updateFaceRect(rect, IMAGE_WIDTH, IMAGE_HEIGHT);
                        mFaceDetectGLSurface.requestRender();
                        mDepthGLSurface.updateDepthImage(cameraImage.depthImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                        mDepthGLSurface.requestRender();
                        ByteBuffer irBuffer = NativeUtils.ir2Rgb(cameraImage.irImage.getImageData(), IMAGE_WIDTH, IMAGE_HEIGHT);
                        mIrGLSurface.updateColorImage(irBuffer, IMAGE_WIDTH, IMAGE_HEIGHT);
                        mIrGLSurface.requestRender();

                        ImageInfo imageInfo = new ImageInfo(IMAGE_WIDTH, IMAGE_HEIGHT, IMGFMT_RGB);
                        FeatureInfo featureInfo = new FeatureInfo(rect.getValueArray(), faceInfo.getLandMark());
                        FaceFeature faceFeature = FeatureSession.getInstance().faceFeature(cameraImage.faceImage.getImageData(), imageInfo, featureInfo);

                        //为0 OK
                        if (faceFeature.getState() != 0) {
                            Log.w(TAG, "人脸识别状态不可用:" + faceFeature.getState());
                            continue;
                        }
                        float[] features = faceFeature.getFeatures();

                        //第一次时，计算图片的人脸特征值
                        if (faceFeature1 == null) {
                            //获取图片的人脸特征值
                            ImageData imageData = new ImageData(mFeatureBuffer, mImageWidth, mImageHeight);
                            //这里由于人脸识别用的是彩色图。所以Session.upDate第一个参数是彩色图。后两个参数只要非null即可。
                            Frame featureFrame = mSession.update(imageData, imageData, imageData);
                            FaceInfo[] featureFaceInfo = featureFrame.detectFaces();
                            Log.w(TAG, "featureFaceInfo:" + featureFaceInfo.length);
                            Rect featureRect = featureFaceInfo[0].getFaceRect();
                            int[] featureLandMark = featureFaceInfo[0].getLandMark();
                            ImageInfo featureImageInfo = new ImageInfo(mImageWidth, mImageHeight, IMGFMT_RGB);
                            FeatureInfo featureInfo1 = new FeatureInfo(featureRect.getValueArray(), featureLandMark);
                            faceFeature1 = FeatureSession.getInstance().faceFeature(mFeatureBuffer, featureImageInfo, featureInfo1);
                        }

                        if (faceFeature1.getState() == 0) {
                            mFeatureImg = faceFeature1.getFeatures();
                            float featureScore = FeatureSession.getInstance().matchFeatures(features, mFeatureImg);
                            Log.w(TAG, "相似度:" + featureScore);
                            showContent("相似度:" + featureScore);

//                            //退出该页面
//                            isFace = false;
//                            finish();
                        }

                        mImageResusePool.add(cameraImage);
                    } catch (InterruptedException | IllegalStateException e) {
                        Log.d(TAG, e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }, "FaceLiveness");
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
    private void getAlgorithmAuthInfo(@NonNull long handle) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在获取授权信息");
        progressDialog.setCancelable(false);
        progressDialog.show();
        AuthQuery authQuery = new AuthQuery();
        String authResult = authQuery.getAlgorithmAuthInfo(handle);
        Log.d("Auth:", authResult);
        progressDialog.cancel();
        Intent intent = new Intent(UnionPayActivity.this, AuthActivity.class);
        intent.putExtra("Auth", getJson(authResult));
        startActivity(intent);
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
    private boolean saveData(ByteBuffer dataBuffer, String dataName) {
        boolean save = false;
        if (dataBuffer != null) {
            if (FileHelper.getInstance().isExceedMemorySize(100)) {
                byte[] data = new byte[dataBuffer.remaining()];
                dataBuffer.get(data);
                dataBuffer.position(0);
                save = FileHelper.getInstance().saveFileWithByte(data, FileHelper.getInstance().getFaceImageFolderPath(), dataName);
            }
        }
        return save;
    }

    public void copyImiData() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i("xxx","正在复制模型文件到：" + FileHelper.getInstance().getFaceModelFolderPath());
                    showContent("正在复制模型文件到：" + FileHelper.getInstance().getFaceModelFolderPath());
                    String[] paths = getAssets().list("model");
                    for (String path : paths) {
                        FileHelper.getInstance().copy(getAssets().open("model/" + path), FileHelper.getInstance().getFaceModelFolderPath() + path, false);
                        Log.d(TAG, path);
                    }
                    //复制识别图片到本地
                    String[] imgPaths = getAssets().list("image");
                    for (String path : imgPaths) {
                        FileHelper.getInstance().copy(getAssets().open("image/" + path), FileHelper.getInstance().getFaceImageFolderPath() + path, false);
                        Log.d(TAG, path);
                    }
                    mCountDownLatch.countDown();
                    showContent("复制模型文件完毕，初始化算法");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "copyImiData");
        thread.start();
    }

    private void showToast(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(UnionPayActivity.this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showContent(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFaceText.setText(msg);
            }
        });
    }

    private void threadWait(String a) {
        synchronized (mObject) {
            try {
                Log.w(TAG, "threadWait" + a);
                mObject.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

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

    private byte[] rgb2RGBA(byte[] data, int width, int height) {
        if (data == null) {
            throw new IllegalArgumentException("数组为空");
        }

        byte[] rgbaData = new byte[width * height * 4];
        Log.d("rgb", "" + rgbaData.length + "  " + data.length);
        for (int i = 0; i < data.length / 3; i++) {
            rgbaData[i * 4] = data[i * 3];
            rgbaData[i * 4 + 1] = data[i * 3 + 1];
            rgbaData[i * 4 + 2] = data[i * 3 + 2];
            rgbaData[i * 4 + 3] = (byte) 255;
        }

        return rgbaData;
    }
}
