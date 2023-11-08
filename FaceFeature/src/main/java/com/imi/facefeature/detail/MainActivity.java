package com.imi.facefeature.detail;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.imi.camera.camera.ImiCamera;
import com.imi.camera.listener.OnOpenCameraListener;
import com.imi.facefeature.R;
import com.imi.facefeature.helper.ImiCameraHelper;
import com.imi.facefeature.helper.PermissionHelper;

import static com.imi.facefeature.Constant.IMAGE_HEIGHT;
import static com.imi.facefeature.Constant.IMAGE_WIDTH;
import static com.imi.facefeature.Constant.TEST_MODE;

/**
 * @author TianLong
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener, OnOpenCameraListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    public volatile boolean isCameraInit = false;
    private Button mCommercialBtn;
    private Button mTestBtn;
    private ImiCamera mImiCamera;
    private ProgressDialog mProgressDialog;
    private int i = 0;
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        mCommercialBtn = findViewById(R.id.btn_main_facefature);
        mCommercialBtn.setOnClickListener(this);

        mTestBtn = findViewById(R.id.btn_main_test_load);
        mTestBtn.setOnClickListener(this);

        if (!TEST_MODE) {
            mTestBtn.setVisibility(View.GONE);
        }

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("相机初始化中...");
        mProgressDialog.setCancelable(false);
//        mProgressDialog.show();
        //相机初始化
        ImiCameraHelper.getInstance().init(this, this, true);
        mImiCamera = ImiCameraHelper.getInstance().getImiCamera();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_main_facefature:
                if (!PermissionHelper.hasPermission(this, Manifest.permission.CAMERA) || !PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    PermissionHelper.requestPermission(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PermissionHelper.PERMISSION_CODE);
                } else {
                    if (isCameraInit) {
                        i++;
                        Intent intent = new Intent(this, UnionPayActivity.class);
                        intent.putExtra("count", i);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this.getApplicationContext(), "相机未打开，请退出APP重进", Toast.LENGTH_SHORT).show();
                    }

                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // TODO: 2020/3/27  仅用于测试
//        mHandler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                if (i > 0 && i <= 300) {
//                    Intent intent = new Intent(MainActivity.this, UnionPayActivity.class);
//                    intent.putExtra("count", i);
//                    startActivity(intent);
//                    Log.w(TAG, "测试结果：" + i);
//                    i++;
//                }
//            }
//        }, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //相机打开成功，关闭相机
        if (mImiCamera != null && isCameraInit) {
            //恢复相机默认曝光值
            mImiCamera.setColorAutoExposureMode(true);
            mImiCamera.closeCamera();
            isCameraInit = false;
            Log.w(TAG, "ImiCamera.closeCamera");
        }
    }

    @Override
    public void onOpenCameraError(String s) {
        Log.e(TAG, "打开相机失败:" + s);

        isCameraInit = false;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this.getApplicationContext(), "打开相机失败:" + s, Toast.LENGTH_SHORT).show();
                mProgressDialog.cancel();
            }
        });
    }

    @Override
    public void onOpenCameraSuccess() {
        isCameraInit = true;
        //设置相机分辨率
        ImiCameraHelper.getInstance().setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        //配置相机 -> 打开相机流
        mImiCamera.configure(ImiCameraHelper.getInstance().getCameraConfig());
        //相机开流
        mImiCamera.startStream();

        Log.e(TAG, "打开相机成功");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this.getApplicationContext(), "打开相机成功", Toast.LENGTH_SHORT).show();
                mProgressDialog.cancel();
            }
        });
    }
}
