package com.imi.faceunionpayset;

import com.imi.faceunionpayset.helper.FileHelper;

/**
 * @author：TianLong
 * @date：2019/12/1 18:50
 */
public class Constant {
    public static String FACE_MODEL_PATH = FileHelper.getInstance().getFaceModelFolderPath();
    public static String FACE_IMAGE_PATH = FileHelper.getInstance().getFaceImageFolderPath();
    public static boolean TEST_MODE = false;
    //请填写申请的APP_KEY
    public static String APP_KEY = "face-dl-test";
//    public static String APP_KEY = "face_imi_test";
    public static int IMAGE_WIDTH = 640;
    public static int IMAGE_HEIGHT = 480;
}
