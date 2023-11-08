package com.imi.facefeature.helper;

import android.os.Environment;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class FileHelper {
    private static final String TAG = FileHelper.class.getSimpleName();
    private static String mSDCardFolderPath;
    private static String mFaceImageFolderPath;
    private static String mFaceModelFolderPath;
    private static String mFaceImiBinFolderPath;
    private static String mFaceTestFolderPath;
    private FileHelper() {
        init();
    }

    public static FileHelper getInstance() {
        return FileHelperHolder.sFileHelper;
    }

    public void init() {
        mSDCardFolderPath = getSDCardFolderPath();
        mFaceImageFolderPath = getFaceImageFolderPath();
        mFaceModelFolderPath = getFaceModelFolderPath();
        mFaceImiBinFolderPath = getFaceImiBinFolderPath();
        mFaceTestFolderPath = getFaceTestFolderPath();
    }


    public String getSDCardFolderPath() {
        if (TextUtils.isEmpty(mSDCardFolderPath)) {
            mSDCardFolderPath = Environment.getExternalStorageDirectory().getPath() + "/IMIFace/";
        }
        mkdirs(mSDCardFolderPath);

        return mSDCardFolderPath;
    }

    public String getFaceImageFolderPath() {
        if (TextUtils.isEmpty(mFaceImageFolderPath)) {
            mFaceImageFolderPath = getSDCardFolderPath() + "FaceImage/";
        }
        mkdirs(mFaceImageFolderPath);

        return mFaceImageFolderPath;
    }

    public String getFaceModelFolderPath() {
        if (TextUtils.isEmpty(mFaceModelFolderPath)) {
            mFaceModelFolderPath = getSDCardFolderPath() + "FaceModel/faceidcom_02.01.21.2000727_1.1.9/";
        }
        mkdirs(mFaceModelFolderPath);

        return mFaceModelFolderPath;
    }

    public String getFaceImiBinFolderPath() {
        if (TextUtils.isEmpty(mFaceImiBinFolderPath)) {
            mFaceImiBinFolderPath = getSDCardFolderPath() + "FaceImiBin/";
        }
        mkdirs(mFaceImiBinFolderPath);

        return mFaceImiBinFolderPath;
    }

    public String getFaceTestFolderPath() {
        if (TextUtils.isEmpty(mFaceTestFolderPath)) {
            mFaceTestFolderPath = getSDCardFolderPath() + "FaceTest/";
        }
        mkdirs(mFaceTestFolderPath);

        return mFaceTestFolderPath;
    }

    public File mkdirs(@NonNull String path) {
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }

        return file;
    }

    //true为存在，false为不存在
    public boolean mkdir(String path) {
        File file = new File(path);

        return file.exists();
    }

    public void deleteFile(String videoPath) {
        File file = new File(videoPath);
        if (file.exists()) {
            file.delete();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // 文件复制
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 文件复制
     *
     * @param srcFile     源文件路径
     * @param desFile     目标文件路径
     * @param forceUpdate true: 即使文件已经存在，也强制复制覆盖， false：文件已存在不复制
     * @throws IOException
     */
    public void copy(String srcFile, String desFile, boolean forceUpdate) throws IOException {
        InputStream srcInputStream = new FileInputStream(srcFile);
        copy(srcInputStream, desFile, forceUpdate);
    }

    /**
     * 文件复制
     *
     * @param srcInput    源文件输入流
     * @param desFile     目标文件路径
     * @param forceUpdate true: 即使文件已经存在，也强制复制覆盖， false：文件已存在不复制
     * @throws IOException
     */
    public void copy(InputStream srcInput, String desFile, boolean forceUpdate) throws IOException {
        File file = new File(desFile);
        if (file.exists()) {
            if (!forceUpdate) {
                srcInput.close();
                return;
            } else {
                file.delete();
            }
        }

        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(desFile);
            byte[] buffer = new byte[1024];
            int length = -1;
            while ((length = srcInput.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.flush();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            srcInput.close();
        }
    }


    /**
     * @param memorySize
     * @return 是否有 @param memorySize 的可用内存
     */
    public boolean isExceedMemorySize(int memorySize) {
        if (memorySize > 0) {
            long size = getAvailableExternalMemorySize();
            Log.e(TAG, "" + size / (1024 * 1024) + "MB");
            return size / (1024 * 1024) >= memorySize;
        }
        return false;
    }


    /**
     * @return 返回可用内存
     */
    public long getAvailableExternalMemorySize() {
        boolean isExternalMemoryAvailable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (isExternalMemoryAvailable) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return availableBlocks * blockSize;
        } else {
            return -1;
        }
    }

    /**
     * 保存二进制文件至本地
     *
     * @param bytes    数据源
     * @param path     路径
     * @param fileName 文件名
     */
    public boolean saveFileWithByte(byte[] bytes, String path, String fileName) {
        File file = new File(path + fileName);
        FileOutputStream fileOutputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        boolean saveSuccess;
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
            fileOutputStream = new FileOutputStream(file);
            bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            bufferedOutputStream.write(bytes);
            bufferedOutputStream.flush();
            saveSuccess = true;
        } catch (IOException e) {
            saveSuccess = false;
            e.printStackTrace();
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
                if (bufferedOutputStream != null) {
                    bufferedOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return saveSuccess;
    }

    public ByteBuffer readLocalFileByteBuffer(@NonNull String path, int length) {
        File file = new File(path);
        ByteBuffer byteBuffer = null;
        try {
            // 拿到输入流
            FileInputStream inputStream = new FileInputStream(file);
            // 建立存储器
            byte[] buf = new byte[length];
            // 读取到存储器
            inputStream.read(buf);
            // 读取到ByteBuffer
            byteBuffer = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
            byteBuffer.put(buf);
            byteBuffer.position(0);
            // 关闭输入流
            inputStream.close();
            // 返回数据
            return byteBuffer;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.w(TAG, "FileNotFoundException:" + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "IOException:" + e.getMessage());
        }

        return byteBuffer;
    }

    public ByteBuffer readLocalFileByteBuffer(@NonNull String path, int length,ByteBuffer byteBuffer) {
        File file = new File(path);
        try {
            // 拿到输入流
            FileInputStream inputStream = new FileInputStream(file);
            // 建立存储器
            byte[] buf = new byte[length];
            // 读取到存储器
            inputStream.read(buf);
            // 读取到ByteBuffer
            byteBuffer.put(buf,0,length);
            byteBuffer.position(0);
            // 关闭输入流
            inputStream.close();
            // 返回数据
            return byteBuffer;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.w(TAG, "FileNotFoundException:" + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "IOException:" + e.getMessage());
        }

        return byteBuffer;
    }
    /**
     * @param path 获取该文件夹下的所有jpg，png文件名
     * @return
     */
    public List<String> getFiles(@NonNull String path) {
        File file = new File(path);
        File[] files = file.listFiles();
        if (files == null) {
            Log.e(TAG, "空目录");
            return null;
        }
        List<String> fileList = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            boolean isAdd = files[i].getAbsolutePath().endsWith(".jpg") || files[i].getAbsolutePath().endsWith(".JPG")
                    || files[i].getAbsolutePath().endsWith(".png") || files[i].getAbsolutePath().endsWith(".PNG");
            if (isAdd) {
                fileList.add(files[i].getAbsolutePath());
            }
        }
        return fileList;
    }

    /**
     * @param path
     * @return
     */
    public List<String> getFiles(@NonNull String path, String... names) {
        File file = new File(path);
        File[] files = file.listFiles();
        if (files == null) {
            Log.e(TAG, "空目录");
            return null;
        }
        List<String> fileList = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            for (String name:names){
                boolean isAdd = files[i].getAbsolutePath().endsWith(name);
                if (isAdd) {
                    fileList.add(files[i].getAbsolutePath());
                }
            }
        }
        return fileList;
    }

    private static class FileHelperHolder {
        private static FileHelper sFileHelper = new FileHelper();
    }
}
