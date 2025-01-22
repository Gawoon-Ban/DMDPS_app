package com.example.test1;


import static android.media.ExifInterface.ORIENTATION_ROTATE_270;

import static androidx.core.util.SparseBooleanArrayKt.contains;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;




public class Preview extends Thread {
    private final static String TAG = "Preview : ";
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private Size mPreviewSize;
    private Context mContext;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;
    private TextureView mTextureView;
    private Activity mActivity;
    private Switch mSwitch;
    private StreamConfigurationMap map;
    private int once = 0;
    int RAWflag = 0, Presetflag = 0;
    private String mCameraID;
    private CaptureResult mResult;
    private byte[] blackarr;
    private int photoindex;
    private byte[] dngbyte;
    private Size mSize;
    private File dngfile;
    private int mResultupdated = 0;
    private int onImageExec = 0;
    private SeekBar mBar;
    int AutoMode = 1, PresetCount = 0, PresetMax = 0;
    int lastISOval = 266, ISOval = 0, ISOMin = 0;
    int ISOAuto = 1, SpeedAuto = 1, FrameAuto = 1;
    long lastshutterspeed = 33333333, shutterspeedval = 0, shutterspeedMin = 0, previewshutterspeed = 33333333;
    long lastframerate = 33333333, framerateval = 0, framerateMin = 0;
    Range<Integer> ISORange;
    Range<Long> shutterspeedRange;
    Long framerateRange;
    long[] SPEEDArr = new long[100];
    String Basetimestamp;
    int PausePreview = 0;
    int BlockCapture = 0;



    // Orientation array to convert from screen rotation to JPEG orientation.
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray(4);

    static {
        // Mapping from screen rotation to JPEG orientation.
        ORIENTATIONS.append(Surface.ROTATION_0, 270);
        ORIENTATIONS.append(Surface.ROTATION_90, 180);
        ORIENTATIONS.append(Surface.ROTATION_180, 90);
        ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    public Preview(Context context, TextureView textureView, Activity activity, Switch RAWswitch, SeekBar OptionBar) {
        Log.d(TAG, "로그 Preview Constructor");
        mContext = context;
        mTextureView = textureView;
        mActivity = activity;
        mSwitch = RAWswitch;
        mBar = OptionBar;
    }

    private static boolean arrayContains(int[] arr, int needle) {
        Log.d(TAG, "로그 arrayContains");
        if (arr == null) {
            return false;
        }
        for (int elem : arr) {
            if (elem == needle) {
                return true;
            }
        }
        return false;
    }

    private String getFrontFacingCameraId(CameraManager cManager) {
        try {
            Log.d(TAG, "로그 getFrontFacingCameraId");
            for (final String cameraId : cManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cManager.getCameraCharacteristics(cameraId);
                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation == CameraCharacteristics.LENS_FACING_FRONT) {

                    // Check Camera Sensor ability
                    if (once == 0) {
                        once = 1;
                        ISORange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                        ISOMin = ISORange.getLower();

                        shutterspeedRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                        Log.i(TAG, "로그 SpeedMin "+ shutterspeedRange.getLower() + " SpeedMax : " +shutterspeedRange.getUpper());

                        shutterspeedMin = -(int)(1000000 - shutterspeedRange.getLower())/10;
                        framerateRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
                        framerateMin = shutterspeedRange.getLower();

                        Log.i(TAG, "로그 SpeedMin "+ shutterspeedMin
                                + " speedMax : " +shutterspeedRange.getUpper());

                        int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                        if (arrayContains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                            Log.d(TAG, "로그 카메라 Raw 타입 지원");
                            Toast.makeText(mContext,"전면 카메라 RAW 출력 지원",Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "로그 카메라 Raw 타입 미지원");
                            Toast.makeText(mContext,"전면 카메라 RAW 출력 미지원",Toast.LENGTH_SHORT).show();
                            mSwitch.setClickable(false);
                        }
                    }
                    mCameraID = cameraId;
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void openCamera() {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG, "로그 openCamera");
        try {
            String cameraId = getFrontFacingCameraId(manager);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[0];

            if (Build.VERSION.SDK_INT >= 33) { // Android OS 13 이상
                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(mActivity, new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.READ_MEDIA_IMAGES
                    }, REQUEST_CAMERA_PERMISSION);
                    return;
                }
            } else {
                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(mActivity, new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, REQUEST_CAMERA_PERMISSION);
                    return;
                }
            }
            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e(TAG, "로그 openCamera CameraAccessException");
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener(){
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface,
                                              int width, int height) {
            Log.d(TAG, "로그 onSurfaceTextureAvailable, width="+width+", height="+height);
            Matrix matrix = new Matrix();
            matrix.setScale(-1, 1);
            matrix.postTranslate(width, 0);
            mTextureView.setTransform(matrix);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface,
                                                int width, int height) {
            Log.d(TAG, "로그 onSurfaceTextureSizeChanged, width="+width+", height="+height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.e(TAG, "로그 onSurfaceTextureDestroyed");
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.d(TAG, "로그 onOpened");
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.e(TAG, "로그 onDisconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "로그 onError");
        }
    };

    protected void startPreview() {
        Log.d(TAG, "로그 startPreview");
        if (PausePreview == 1){
            Log.e(TAG,"로그 takecapture is processing, return");
            return;
        }

        if(null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            Log.e(TAG, "로그 startPreview fail, return");
        }

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if(null == texture) {
            Log.e(TAG,"로그 texture is null, return");
            return;
        }

        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mPreviewBuilder.addTarget(surface);

        try {
            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.d(TAG,"로그 onConfigured");
                    mPreviewSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Toast.makeText(mContext, "onConfigureFailed", Toast.LENGTH_LONG).show();
                    Log.e(TAG,"로그 onConfigureFailed");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureCommonCaptureSettings(CaptureRequest.Builder requestBuilder) {
        // ISO : 259 speed : 33244662 frame : 33428347
        if (ISOAuto == 1) {
            Log.i(TAG, "로그  ISO Auto: " + lastISOval);
            requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, lastISOval);
        }
        else {
            Log.i(TAG, "로그  ISO : " + ISOval);
            requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, ISOval);
        }
        if (Presetflag == 0 && SpeedAuto == 1) {
            Log.i(TAG, "로그  SPEED Auto: " + lastshutterspeed);
            requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, lastshutterspeed);
        }
        else if (Presetflag == 0) {
            Log.i(TAG, "로그  SPEED : " + shutterspeedval);
            requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterspeedval);
        }
        else {
            Log.i(TAG, "로그  SPEED : " + SPEEDArr[PresetCount]);
            requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, SPEEDArr[PresetCount]);
        }
        if (FrameAuto == 1) {
            Log.i(TAG, "로그  FRAME Auto: " + lastframerate);
            requestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, lastframerate);
        }
        else {
            Log.i(TAG, "로그  FRAME : " + framerateval);
            requestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, framerateval);
        }
    }

    void updatePreview() {
        Log.i(TAG, "로그 updatePreview");
        if(null == mCameraDevice) {
            Log.e(TAG, "로그 updatePreview error, return");
        }

        if (AutoMode == 1) {
            Log.i(TAG, "로그 AutoMode");
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        }
        else {
            Log.i(TAG, "로그 Manual Mode");
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            configureCommonCaptureSettings(mPreviewBuilder);
            mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewshutterspeed); // for Preview Only (186ms 초과시 미리보기 방지)
        }
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());
        try {
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.i(TAG, "로그 UpdatePreview CameraAccessException " + e);
        }
        catch (RuntimeException e) {
            Log.i(TAG, "로그 UpdatePreview RuntimeException " + e);
        }
    }

    public void setSurfaceTextureListener()
    {
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    public void onResume() {
        Log.d(TAG, "로그 onResume");
        setSurfaceTextureListener();

        if (mTextureView.isAvailable()) {
            openCamera();
        }
    }

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    public void onPause() {
        Log.e(TAG, "로그 onPause");
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
                Log.e(TAG, "로그 CameraDevice Close");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    protected void takePicture(int index, String iname) {
        Log.d(TAG, "로그 takePicture "+ index + " ["+RAWflag+"]");

        photoindex = index;
        mResultupdated = 0; // reset
        onImageExec = 0;

        if (null == mCameraDevice) {
            Log.e(TAG, "로그 mCameraDevice is null, return");
            return;
        }

        try {
            Size[] jpegSizes = null;
            if (map != null) {
                if (RAWflag == 1) {
                    jpegSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR);
                }
                else {
                    jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
                }
            }

            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            Log.d(TAG, "로그 takePicture " + width + " "+ height);
            ImageReader reader;
            if (RAWflag == 1) {
                reader = ImageReader.newInstance(width, height, ImageFormat.RAW_SENSOR, 1);
            }
            else {
                reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            }

            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            if (Presetflag == 0 && AutoMode == 1) {
                Log.i(TAG, "로그 TakeCapture Auto Mode");
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            }
            else {
                Log.i(TAG, "로그 TakeCapture Manual Mode");
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                configureCommonCaptureSettings(captureBuilder);
            }
            if (RAWflag == 0) {
                int rotation = (mActivity).getWindowManager().getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            }

            String timeStamp = Basetimestamp;
            String imageFileName;
            if (RAWflag == 1) {
                if (Presetflag == 0) {
                    if (SpeedAuto == 1)
                        imageFileName = timeStamp + "_"+iname+"_" + index + "_Auto.dng";
                    else
                        imageFileName = timeStamp + "_"+iname+"_" + index +"_" +(float)shutterspeedval/1000000.0+ ".dng";
                }
                else
                {
                    imageFileName = timeStamp + "_"+iname+"_" + index +"_" +(float)SPEEDArr[PresetCount]/1000000.0+ ".dng";
                }
            }
            else {
                if (Presetflag == 0) {
                    if (SpeedAuto == 1)
                        imageFileName = timeStamp + "_"+iname+"_" + index + "_Auto.jpg";
                    else
                        imageFileName = timeStamp + "_"+iname+"_" + index +"_" +(float)shutterspeedval/1000000.0+ ".jpg";
                }
                else
                {
                    imageFileName = timeStamp + "_"+iname+"_" + index +"_" +(float)SPEEDArr[PresetCount]/1000000.0+ ".jpg";
                }
            }

            File dir = new File(Environment.getExternalStorageDirectory()+"/DCIM/DDPS");
            if (!dir.exists()) {
                Log.e(TAG, "mkdir " + imageFileName);
                dir.mkdirs();
            }

            File subdir = new File(Environment.getExternalStorageDirectory()+"/DCIM/DDPS/"+timeStamp);
            if (!subdir.exists()) {
                Log.e(TAG, "mkdir " + timeStamp);
                subdir.mkdirs();
            }

            Log.d(TAG, "로그 imageFileName " + imageFileName);

            final File file = new File(Environment.getExternalStorageDirectory()+"/DCIM/DDPS/"+timeStamp, imageFileName);
            dngfile = file;
            mSize = jpegSizes[0];

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Log.d(TAG, "로그 onImageAvailable");
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        dngbyte = bytes;
                        onImageExec = 1;
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, "로그 FileNotFoundException "+e);
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.e(TAG, "로그 IOException +e");
                        e.printStackTrace();
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "로그 CameraAccessException +e");
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            Log.d(TAG, "로그 finally");
                            image.close();
                            reader.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException, CameraAccessException {
                    Log.d(TAG, "로그 save "+bytes.length);
                    FileOutputStream output = null;
                    try {
                        Log.d(TAG, "로그 " + file.getAbsolutePath());
                        output = new FileOutputStream(file);
                        if (RAWflag == 1 &&  mResultupdated == 1)
                        {
                            Log.d(TAG, "로그 save RAW");
                            ByteBuffer buffer = ByteBuffer.wrap(bytes);
                            CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
                            DngCreator dngCreator = new DngCreator(manager.getCameraCharacteristics(mCameraID), mResult);
                            dngCreator.setOrientation(ORIENTATION_ROTATE_270);
                            dngCreator.writeByteBuffer(output, mSize, buffer, 0);
                        }
                        else if (RAWflag == 0)
                        {
                            Log.d(TAG, "로그 save jpg");
                            output.write(bytes);
                        }
                    }
                    catch (Exception e) {
                        Log.e(TAG, "로그 save error "+e);
                        throw new RuntimeException(e);
                    }
                    finally {
                        if (null != output) {
                            output.close();
                            Log.d(TAG, "로그 save finally");
                        }
                    }
                }
            };
            HandlerThread thread = new HandlerThread("CameraPicture");
            thread.start();
            final Handler backgroudHandler = new Handler(thread.getLooper());

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.d(TAG, "로그 onCaptureCompleted");

                    mResult = result;
                    mResultupdated = 1;

                    if (RAWflag == 1 && onImageExec == 1) {
                        mResultupdated = 2;
                        Log.d(TAG, "로그 RAW save");
                        FileOutputStream output = null;
                        try {
                            if (BlockCapture == 1) {
                                BlockCapture = 0;
                            }
                            else {
                                output = new FileOutputStream(dngfile);
                                ByteBuffer buffer = ByteBuffer.wrap(dngbyte);
                                CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
                                DngCreator dngCreator = new DngCreator(manager.getCameraCharacteristics(mCameraID), mResult);
                                dngCreator.setOrientation(ORIENTATION_ROTATE_270);
                                //dngCreator.writeImage(output, image);
                                dngCreator.writeByteBuffer(output, mSize, buffer, 0);
                            }
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "로그 RAW save CameraAccessException");
                            throw new RuntimeException(e);
                        } catch (FileNotFoundException e) {
                            Log.e(TAG, "로그 RAW save FileNotFoundException");
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            Log.e(TAG, "로그 RAW save IOException");
                            throw new RuntimeException(e);
                        }
                        finally {
                            if (null != output) {
                                try {
                                    Log.d(TAG, "로그 RAW save finally");
                                    output.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "로그 RAW save finally IOException");
                                }
                            }
                        }
                    }
                    //Toast.makeText(mContext, "Saved:"+file, Toast.LENGTH_SHORT).show();
                }
            };

            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "로그 onConfigured createCaptureSession");
                    try {
                        session.capture(captureBuilder.build(), captureListener, backgroudHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "로그 CameraAccessException createCaptureSession");
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "로그 onConfigureFailed createCaptureSession");
                }
            }, backgroudHandler);

            reader.setOnImageAvailableListener(readerListener, backgroudHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "로그 CameraAccessException 외부");
            e.printStackTrace();
        }
    }

    protected void PreviewCapture() {
        Log.d(TAG, "로그 PreviewCapture");

        if (null == mCameraDevice) {
            Log.e(TAG, "로그 mCameraDevice is null, return");
            return;
        }

        try {
            Size[] jpegSizes = null;
            if (map != null) {
                jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
            }

            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            ImageReader reader;
            reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));
            final CaptureRequest.Builder PcaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            PcaptureBuilder.addTarget(reader.getSurface());


            PcaptureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);


            HandlerThread thread = new HandlerThread("PreviewPicture");
            thread.start();
            final Handler PbackgroudHandler = new Handler(thread.getLooper());

            final CameraCaptureSession.CaptureCallback PcaptureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.d(TAG, "로그 Preview onCaptureCompleted");

                    lastISOval= result.get(TotalCaptureResult.SENSOR_SENSITIVITY);
                    lastshutterspeed = result.get(TotalCaptureResult.SENSOR_EXPOSURE_TIME);
                    lastframerate = result.get(TotalCaptureResult.SENSOR_FRAME_DURATION);
                    Log.i(TAG, "로그 onCaptureCompleted ISO : "+lastISOval+" speed : "+lastshutterspeed + " frame : " +lastframerate);
                    startPreview();
                }
            };

            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "로그 Preview onConfigured createCaptureSession");
                    try {
                        session.capture(PcaptureBuilder.build(), PcaptureListener, PbackgroudHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "로그 Preview CameraAccessException createCaptureSession");
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "로그 Preview onConfigureFailed createCaptureSession");
                }
            }, PbackgroudHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "로그 Preview CameraAccessException");
            e.printStackTrace();
        }
    }
}
