# SDK集成方式
    用户需要将算法模型文件存放到人脸算法初始化时指定的模型路径下，Demo中已经完成模型文件的自
    动拷贝，用户在实际工程中需要注意算法模型文件的拷贝
    华捷艾米人脸算法需要在线授权，用户需要确定拿到的模组已经向销售申请过对应算法版本的授权，并
    且开发设备可以访问外网
    用户运行Demo前需要将算法配置参数的AppKey替换为自己申请的AppKey，以用于算法在线授权

### 注意事项
    1. 需联网授权
    2. 模型文件需拷贝到本地。Demo中已实现自动拷贝
    3. 算法初始化未成功时，Session.upDate() 返回为null
    4. FaceQuality计算失败时可能返回null
    5. targetSDK 在Android10 上，需要小于28，否则没有USB权限

### SDK集成方式
    用户需要将算法模型文件存放到人脸算法初始化时指定的模型路径下，Demo中已经完成模型文件的自
    动拷贝，用户在实际工程中需要注意算法模型文件的拷贝
    华捷艾米人脸算法需要在线授权，用户需要确定拿到的模组已经向销售申请过对应算法版本的授权，并
    且开发设备可以访问外网
    用户运行Demo前需要将算法配置参数的AppKey替换为自己申请的AppKey，以用于算法在线授权

##### 将ImiFaceSdk库aar存放到工程项目的libs文件夹下，项目工程gradle配置文件中引入依赖，配置相应Gradle
```
android {
    compileSdkVersion 28
    buildToolsVersion "29.0.2"
    defaultConfig {
        .....省略.....
        ndk {
//            abiFilters 'armeabi-v7a'//32位库
//            abiFilters 'arm64-v8a'  //64位库
            abiFilters 'armeabi-v7a','arm64-v8a'//32位 + 64位库  可自行选则配置
            stl 'gnustl_static'
        }
    }

    repositories {
        flatDir {
            dirs 'libs'
        }
    }
}

dependencies {
    implementation fileTree(include: ['*.jar'], dir: 'libs')
    implementation 'com.android.support:appcompat-v7:28.0.0'
    implementation 'com.android.support.constraint:constraint-layout:1.1.3'

    implementation(name: 'ImiFaceSdk_faceidcom_02.00.14.200604_1.1.6', ext: 'aar')
    implementation files('libs/core-3.4.0.jar')
}
```
##### 申请授权：项目工程的Manifest文件中配置华捷艾米人脸算法所需权限
```xml
  <!--算法联网授权-->
  <uses-permission android:name="android.permission.INTERNET"/>
  <!--算法读取模型文件-->
  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
  <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
  <!--适配Android9.0-->
  <uses-permission android:name="android.permission.CAMERA"/>
```
##### 打开华捷艾米相机模组 (必须使用支持红外的相机模组)
```java
   public void openCamera(){
        //相机初始化（具体逻辑在ImiCameraHelper里，改类只是一个简单的封装类，用户可自行抽取）
        ImiCameraHelper.getInstance().init(this, this, true);
        mImiCamera = ImiCameraHelper.getInstance().getImiCamera();
   }
   
    @Override
    public void onOpenCameraError(String s) {
        isCameraInit = false;
    }

    @Override
    public void onOpenCameraSuccess() {
        Log.w(TAG, "onOpenCameraSuccess");

        //二维码相关类初始化
        multiFormatReader = new MultiFormatReader();
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
                    constraintSet.connect(R.id.gl_union_pay_face, ConstraintSet.END, R.id.gl_union_pay_depth_dectect, ConstraintSet.END);
                    constraintSet.setDimensionRatio(mFaceGLSurface.getId(), "3:4");
                    constraintSet.setDimensionRatio(mFaceDetectGLSurface.getId(), "3:4");
                    constraintSet.setDimensionRatio(mDepthGLSurface.getId(), "3:4");
                    constraintSet.setDimensionRatio(mIrGLSurface.getId(), "3:4");
                    constraintSet.applyTo(mConstraintLayout);
                }
            });
        }
        isCameraInit = true;
        //设置相机分辨率
        ImiCameraHelper.getInstance().setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        //配置相机 -> 打开相机流
        mImiCamera.configure(ImiCameraHelper.getInstance().getCameraConfig());
        //设置数据回调
        mImiCamera.setFrameAvailableListener(this::onFrameAvailable);
        //相机开流
        mImiCamera.startStream();
        //拷贝模型数据
        copyImiData();

        //Session初始化 Session的具体配置在getSessionConfig()中
        SessionConfig sessionConfig = SessionHelper.getInstance().getSessionConfig();
        sessionConfig.camera = mImiCamera;
        // 授权信息
        sessionConfig.appKey = APP_KEY;
        // 算法模式（商用模式）
        sessionConfig.faceAlgMode = FaceAlgMode.MODE_COMMERCIAL;

        if (!TextUtils.isEmpty(sessionConfig.appKey)) {
             SessionHelper.getInstance().initSession(getApplicationContext(), UnionPayActivity.this::onSessionInitialized, sessionConfig);
        } else {
            showToast("AppKey为空，无法授权，算法未初始化，请设置AppKey");
            showContent("AppKey为空，无法授权，算法未初始化，请设置AppKey");
        }
    }
```

##### 渲染相机数据
```java
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
```

##### Session初始化回调
```java
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
    }
```
##### 运行人脸算法
```java
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
                         if (cameraImage == null || cameraImage.faceImage == null) {
                             Log.d(TAG, "测试：人脸线程数据为null");
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
                             Log.d(TAG, "测试：人脸线程运行ing");
                             Frame frame = mSession.update(cameraImage.faceImage, cameraImage.depthImage, cameraImage.irImage);

                             //检测人脸
                             long tt = System.currentTimeMillis();
                             FaceInfo[] faceInfos = frame.detectFaces();
                             long detect = System.currentTimeMillis() - tt;

                             if (faceInfos.length == 0) {
                                 showContent("未检测到人脸");
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
                                 showContent("人脸质量评估失败");
                                 mImageResusePool.add(cameraImage);
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
                                 showContent("活体检测失败");
                                 mImageResusePool.add(cameraImage);
                                 continue;
                             }

                             if (isSaveData) {
                                 String name = System.currentTimeMillis() + "_" + faceQuality.getQuality().toInfo() + "_" + liveness.getLivenessScore() + "_";
                                 saveData(cameraImage.faceImage.getImageData(), name + "rgb.raw");
                                 saveData(cameraImage.depthImage.getImageData(), name + "depth.raw");
                                 saveData(cameraImage.irImage.getImageData(), name + "ir.raw");
                             }

                             showContent(getLivenessMsg(liveness) + "\n人脸质量：" + faceQuality.getQuality().toInfo() + "\n人脸检测：" + detect + "ms  " + "\n质检耗时：" + quality + "ms\n活体耗时：" + live + "ms\n" + "检测次数：" + mAtomicInteger.getAndIncrement());
                             Log.w(TAG, getLivenessMsg(liveness) + " 人脸质量：" + faceQuality.getQuality().toInfo() + " 人脸检测：" + detect + "ms  " + " 质检耗时：" + quality + "ms 活体耗时：" + live + "ms " + "检测次数：" + mAtomicInteger.get());

                             mImageResusePool.add(cameraImage);
                         } else {
                             if (mQRCodeBuffer == null) {
                                 mQRCodeBuffer = ByteBuffer.allocateDirect(IMAGE_WIDTH * IMAGE_HEIGHT * 3).order(ByteOrder.nativeOrder());
                                 mQRCodeBuffer.position(0);
                             }
                             NativeUtils.copyByteBufferData(cameraImage.faceImage.getImageData(), mQRCodeBuffer, IMAGE_WIDTH * IMAGE_HEIGHT * 3);
                             mFaceDetectGLSurface.updateColorImage(mQRCodeBuffer, IMAGE_WIDTH, IMAGE_HEIGHT);
                             mFaceDetectGLSurface.requestRender();
                             //二维码模式
                             byte[] rgbBytes = new byte[IMAGE_WIDTH * IMAGE_HEIGHT * 3];
                             cameraImage.faceImage.getImageData().position(0);
                             cameraImage.faceImage.getImageData().get(rgbBytes);
                             convertCode(rgbBytes);
                             cameraImage.faceImage.getImageData().position(0);
                             mImageResusePool.add(cameraImage);

                         }
                     } catch (InterruptedException | IllegalStateException e) {
                         Log.d(TAG, e.getMessage());
                         e.printStackTrace();
                     }
                 }
             }
         }, "DemoFaceLiveness");
         mFaceThread.start();
     }
```

##### 释放资源
```java
     @Override
     protected void onDestroy() {
         super.onDestroy();
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

         //关闭相机
         if (mImiCamera != null) {
             //恢复相机默认曝光值
             mImiCamera.setColorAutoExposureMode(true);
             mImiCamera.closeCamera();
             Log.w(TAG, "ImiCamera.closeCamera");
         }
     }
```