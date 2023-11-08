# Change_Log

#### 2020-04-03
    1. 更新ImiFaceSdk_faceidcom_02.00.14.200317_1.1.1
    2. 新增ImiDataBinPath路径，方便service版本独立进程获取目录
    3. 更新保存本地模型路径地址

#### 2020-04-24
    1. 更新SDKImiFaceSdk_faceidcom_02.00.14.200422_1.1.2.
    2. 算法：新增质量评估枚举值张嘴，闭眼。授权逻辑更新，
    3. 算法可以重复初始化。
    4. 相机：更新为ImiCamera1.3.8对应ImiSDK为1.8.8
    5. Demo新增保存人脸数据逻辑。小于100M时不在保存。保存路径为sdcard/IMIFace/FaceImage/
    6. 更新模型库，模型路径修改为sdcard/IMIFace/FaceModel/faceidcom_02.00.14.200422_1.1.2/
    7. 自动生成app名称

#### 2020-05-06
    1. 更新SDKImiFaceSdk_faceidcom_02.00.14.200506_1.1.3.
    2. SDK：新增UVC版本0618时才进行检测FaceAE修改的逻辑
    3. SDK：10009时打印Log日志
    4. SDK:修复正常情况下网络返回10009的问题
    5. SDK:修复最开始无网10009，退出联网再次初始化时没有回调的逻辑。
    6. SDK:新增红外彩色人脸检测的修改逻辑
    7. 修复0.9-1m左右活体值分低的问题
    8. SDK:解决获取固件版本列表和查询授权信息为空的问题。
    9. Demo层把过检模式和商用模式修改至一个Module中
    10. 更新模型库，模型路径修改为sdcard/IMIFace/FaceModel/faceidcom_02.00.14.200506_1.1.3/
    11. 新增横板布局

#### 2020-05-13
    1. 更新SDKImiFaceSdk_faceidcom_02.00.14.200513_1.1.4.
    2. 相机SDK：增益改为2.5
    3. 质量评估新增墨镜遮挡，口罩遮挡，手势遮挡
    4. 更新模型Net1.mnn,Net2.mnn,Q3.mnn
    5. Demo更新版本1.0.4
    6. Demo更新模型位置 FaceModel/faceidcom_02.00.14.200513_1.1.4/
    7. Demo新增二维码模式切回人脸模式时，丢弃5帧的逻辑。以防止二维码模式下的较暗的彩色图传给人脸算法导致过曝
    
### ImiFaceSdk_faceidcom_02.00.14.200522_1.1.5（2020/5/22）
    1. 更新SDKImiFaceSdk_faceidcom_02.00.14.200522_1.1.5.
    2. 新增livenessResult值，距离过近.错误码位-104
    3. 去除手部遮挡
    4. 更新模型Q3

### ImiFaceSdk_faceidcom_02.00.14.200604_1.1.6（2020/6/04）
    1. 更新SDKImiFaceSdk_faceidcom_02.00.14.200604_1.1.6.
    2. 深度图渲染逻辑修改
    3. 第一次打开时开启相机
    4. 新增多个FAE值
    5. 新增人脸特征值识别（1：1）
    6. 新增人脸识别Module

### ImiFaceSdk_faceidcom_02.00.14.200622_1.1.6（2020/6/22）
    1. ImiFaceSdk_faceidcom_02.00.14.200622_1.1.6（2020/6/22）
    2. 识别库更新64位算法
    3. 更新ImiCameraCipher_v1_4_4(IMISdk1.8.11)
    4. 人脸库接口新增同步，防止多线程调用崩溃问题

### ImiFaceSdk_faceidcom_02.00.14.200703_1.1.7（2020/7/03）
    1. ImiFaceSdk_faceidcom_02.00.14.200703_1.1.7（2020/7/03）
    2. 识别库更新64位算法
    3. 更新ImiCameraCipher_v1_4_5 解决原生相机问题
	4. 更新人脸距相机距离
	6. 人脸识别 多人脸

### ImiFaceSdk_faceidcom_02.00.14.2000720_1.1.8（2020/7/20）
    1. ImiFaceSdk_faceidcom_02.00.14.2000720_1.1.8（2020/7/20）
    2. 更新ImiCameraCipher_v1_4_9(IMISdk1.8.11)
    3. 优化 冷热启动时间（得刷相应的固件）
    4. 增益时间放到算法初始化中。
    5. getVersion从相机初始化中移除，第一次调用时赋值。
    6. 新增LivenessMode枚举
    7. 新增活体检测接口(用于深度，红外，深度+红外)的人脸检测
            LivenessResult detectLiveness(@NonNull FaceInfo faceInfo,LivenessMode livenessMode)

### ImiFaceSdk_faceidcom_02.01.21.200727_1.1.9（2020/7/27）
    1. 算法更新至ImiFaceSdk_faceidcom_02.01.21.200727_1.1.9，性能优化版本。底层开启多线程，人脸识别大幅度优化。
    2. 更新模型为0727版本
    3. 新增配置文件ImiAlg.xml 方便配置质量评时人脸角度值，范围为0-30度。放置位置为 **算法模型路径**下。
    4.ImiAlg.xml文件格式为：
    ```xml
       <ImiAlg>
       <ImiQuality>
       <Pitch>10</Pitch>
       <Yaw>30 </Yaw>
       <Roll>20</Roll>
       </ImiQuality>
       </ImiAlg>
     ```
     5.模型路径更新/sdacrd/IMIFace/FaceModel/faceidcom_02.01.21.2000727_1.1.9/
	 
### ImiFaceSdk_faceidcom_02.00.14.200730_1.1.10（2020/7/30）
    1. ImiCamera1.5.0修复竖版增益导致的栈溢出问题
    2. 32位在某些平台上运行崩溃问题