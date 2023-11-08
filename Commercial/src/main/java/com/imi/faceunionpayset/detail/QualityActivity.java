package com.imi.faceunionpayset.detail;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.imi.camera.camera.ImiCamera;
import com.imi.faceunionpayset.Constant;
import com.imi.faceunionpayset.R;
import com.imi.faceunionpayset.helper.FileHelper;
import com.imi.faceunionpayset.helper.SessionHelper;
import com.imi.sdk.face.AuthQuery;
import com.imi.sdk.face.BctcFaceQuality;
import com.imi.sdk.face.FaceAlgMode;
import com.imi.sdk.face.Frame;
import com.imi.sdk.face.OnSessionInitializeListener;
import com.imi.sdk.face.Quality;
import com.imi.sdk.face.Session;
import com.imi.sdk.face.SessionConfig;
import com.imi.sdk.facebase.base.ResultCode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.widget.Toast.LENGTH_SHORT;
import static com.imi.faceunionpayset.Constant.FACE_IMAGE_PATH;

/**
 * @author TianLong
 */
public class QualityActivity extends AppCompatActivity implements OnSessionInitializeListener, View.OnClickListener {
    private static final String TAG = QualityActivity.class.getSimpleName();
    private ImageView mFaceImage;
    private TextView mFaceText;
    private Button mFaceButton;
    private Button mAuthButton;
    private Session mSession;
    private volatile boolean isSessionInit = false;
    private volatile boolean isFace = false;
    private volatile boolean isFacePause = false;
    private Thread mThread;
    private List<String> mFileNames;
    private Bitmap mBitmap;
    private Object mObject = new Object();
    /**
     * 人脸质量评估线程
     */
    private volatile int num = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quality);

        initView();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isSessionInit && isFacePause) {
            isFacePause = false;
            threadNotify();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (!isFacePause) {
            isFacePause = true;
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //立刻关闭线程（防止人脸库release后，线程中还持有人脸的引用，导致Assert（内存地址错误））
        isFace = false;
        if (mThread != null) {
            try {
                threadNotify();
                mThread.join();
                Log.w(TAG, "mFaceThread state:" + mThread.getState().name());
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

    private void initView() {
        mFaceImage = findViewById(R.id.image_quality_face);
        mFaceText = findViewById(R.id.tv_quality_face);
        mFaceButton = findViewById(R.id.btn_quality_face);
        mAuthButton = findViewById(R.id.btn_quality_auth);
        mAuthButton.setOnClickListener(this::onClick);
        mFaceButton.setOnClickListener(this::onClick);

        mFileNames = new ArrayList<>();

        //复制模型
        copyImiData();
        //Session初始化 Session的具体配置在getSessionConfig()中
        SessionConfig sessionConfig = SessionHelper.getInstance().getSessionConfig();
        // 算法类型（过检版）
        sessionConfig.faceAlgMode = FaceAlgMode.MODE_BCTC;
        if (!TextUtils.isEmpty(sessionConfig.appKey)) {
            //Session初始化 Session的具体配置在getSessionConfig()中
            SessionHelper.getInstance().initSession(getApplicationContext(), this, sessionConfig);
        } else {
            showContent("AppKey为空，无法授权，算法未初始化，请设置AppKey");
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_quality_face:
                if (isSessionInit && isFace) {
                    if (isFacePause) {
                        isFacePause = false;
                        threadNotify();
                    } else {
                        isFacePause = true;
                    }
                } else {
                    Toast.makeText(this.getApplicationContext(), "算法未初始化，或者人脸算法线程尚未执行", Toast.LENGTH_SHORT).show();
                }

                break;
            case R.id.btn_quality_auth:
                getAlgorithmAuthInfo(ImiCamera.getInstance().getCameraHandle());
                break;
            default:
                break;
        }
    }

    @Override
    public void onSessionInitialized(int i, String s) {
        String msg = "";
        if (i == ResultCode.OK) {
            isSessionInit = true;

            //从指定路径加载Bitmap，用于人脸质量评估
            initFile(FACE_IMAGE_PATH);

            mSession = SessionHelper.getInstance().getFaceSession();
            startFaceQuality();
            msg = i + "  " + s;
        } else {
            msg = i + "  " + s + "\n 算法模型文件目录：" + Constant.FACE_MODEL_PATH;
        }

        String finalMsg = msg;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(QualityActivity.this.getApplicationContext(), finalMsg, LENGTH_SHORT).show();
                mFaceText.setText(finalMsg);
            }
        });
    }

    private void initFile(String bitmapPath) {
        mFileNames = FileHelper.getInstance().getFiles(bitmapPath);
        Log.w(TAG, "File number is " + mFileNames.size());
    }

    private void startFaceQuality() {
        isFace = true;

        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("FaceDemoThread");
                while (isFace) {
                    try {
                        if (isFacePause) {
                            //改变线程为wait状态
                            threadWait();
                            continue;
                        }

                        if (mFileNames.isEmpty()) {
                            Log.e(TAG, "没有用于质量评估的jpg或者png文件");
                            mFaceText.post(new Runnable() {
                                @Override
                                public void run() {
                                    mFaceText.setText("没有用于质量评估的jpg或者png文件\n请将文件放于：" + FACE_IMAGE_PATH + "  路径下");
                                }
                            });
                            Thread.sleep(3);
                            return;
                        }

                        //获取文件路径
                        String path = mFileNames.get(num);
                        //加载Bitmap。这里的Bitmap会自动触发垃圾回收机制。
                        mBitmap = BitmapFactory.decodeFile(path);

                        //质量评估算法，耗时
                        long tt = System.currentTimeMillis();
                        BctcFaceQuality faceQuality = Frame.detectBctcFaceQuality(path);
                        long qualityTime = System.currentTimeMillis() - tt;

                        //结果比对
                        String faceDetail = compareFaceQuality(faceQuality);
                        Log.d(TAG, faceDetail + "\n" + "文件路径：" + path + "\n\n" + "人脸评估耗时：" + qualityTime + "ms");
                        //更新数据到UI
                        mFaceImage.post(new Runnable() {
                            @Override
                            public void run() {
                                mFaceImage.setImageBitmap(mBitmap);
                                mFaceText.setText(faceDetail + "\n" + "文件路径：" + path + "\n\n" + "人脸评估耗时：" + qualityTime + "ms");
                            }
                        });
                        Log.w(TAG, "次数：" + num + ",路径Path:" + path + ",结果：" + faceDetail);
                        if (num < mFileNames.size() - 1) {
                            num++;
                        } else {
                            num = 0;
                        }
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.w(TAG, "Exception");
                    }
                }
            }
        }, "faceQuality");
        mThread.start();
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

    private void threadWait() throws InterruptedException {
        synchronized (mObject) {
            Log.w(TAG, "threadWait");
            mObject.wait();
        }
    }

    private void threadNotify() {
        synchronized (mObject) {
            mObject.notify();
            Log.w(TAG, "threadNotify");
        }
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

    //获取相机的授权信息，耗时操作需另开线程
    public void getAlgorithmAuthInfo(long handle) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AuthQuery authQuery = new AuthQuery();
                String authResult = authQuery.getAlgorithmAuthInfo(handle);
                Log.d("Auth:", authResult);

                Intent intent = new Intent(QualityActivity.this, AuthActivity.class);
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
     * 复制模型文件至文件夹
     */
    public void copyImiData() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                // 复制Forest和license文件到手机存储
                try {
                    showContent("正在复制图片文件到：" + FileHelper.getInstance().getFaceImageFolderPath());
                    String[] imgPaths = getAssets().list("image");
                    for (String path : imgPaths) {
                        FileHelper.getInstance().copy(getAssets().open("image/" + path), FileHelper.getInstance().getFaceImageFolderPath() + path, false);
                        Log.d(TAG, path);
                    }
                    showContent("正在复制模型文件到：" + FileHelper.getInstance().getFaceModelFolderPath());
                    String[] paths = getAssets().list("model");
                    for (String path : paths) {
                        FileHelper.getInstance().copy(getAssets().open("model/" + path), FileHelper.getInstance().getFaceModelFolderPath() + path, false);
                        Log.d(TAG, path);
                    }
                    showContent("复制文件完毕，初始化算法");
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
}
