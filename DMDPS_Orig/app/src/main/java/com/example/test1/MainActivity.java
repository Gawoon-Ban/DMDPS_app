package com.example.test1;

import static java.lang.Integer.valueOf;
import static java.lang.Math.abs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.Window;
import 	android.graphics.PixelFormat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int PICK_IMAGE_REQUEST = 1;

    private TextureView mCameraTextureView;
    private Preview mPreview;
    Activity mainActivity = this;
    private static final String TAG = "MAINACTIVITY";

    private Button btnUpload, nextopt, prevopt, plusopt, minusopt;
    private ImageView imageView;
    private View blackView;
    private TextView statusTextView;
    private List<Uri> imageUris = new ArrayList<>();
    private int currentImageIndex = 0, optmode = 0;
    private Timer m_timer;
    private Switch RAWswitch, Presetswitch;
    private SeekBar OptionBar;
    private TextView OptionVal;
    private EditText presetText;
    int Capturetimeval = 20, BarLock = 0, SpeedProgress = 0, TotalCount = 0, InitCapture = 0;



    public void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    int ISOSetting (int val)
    {
        return (val/50)*50;
    }
    double SpeedSetting (int val) { // microseconds
        if (val >= 1000) {
           return Math.round(val/1000.0);
        }
        else { // +1000 ~ -Min -> +1.0ms ~ 0.046ms로 mapping
            double ret = Math.round(1000+(val/100.0))/1000.0;
            if (ret > 1.0)
                ret = 1.0;
            return ret;
        }
    }
    int FrameSetting (int val) {return (val/1000)*1000;}

    String Printfloat(int n, long val)
    {
        return String.valueOf(val/((long)Math.pow(10,n)))+"."+String.valueOf(val%((long)Math.pow(10,n)));
    }

    boolean ParsePreset() {
        String[] Input = (presetText.getText().toString()).split(",");
        mPreview.PresetMax = Input.length;
        Log.i(TAG, "로그 PresetMax : "+mPreview.PresetMax);
        try {
            for (int i = 0; i < Input.length; i++) {
                mPreview.SPEEDArr[i] = (long) (Float.parseFloat(Input[i].trim()) * 1000000);
                Log.i(TAG, "로그 Preset "+i+" : "+mPreview.SPEEDArr[i]);

                if (mPreview.SPEEDArr[i] < mPreview.shutterspeedMin)
                {
                    Toast.makeText(mainActivity,"Vaild Range : "+mPreview.shutterspeedRange.getLower()/1000000.0+"~",Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (mPreview.SPEEDArr[i] > (long)Capturetimeval * 100000000)
                {
                    Toast.makeText(mainActivity,"Capture Period is too short : AtLeast "+mPreview.SPEEDArr[i]/1000000000.0+"s required",Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        } catch (Exception e) {
            Toast.makeText(mainActivity,"Invaild Input",Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "로그 onCreate");

        hideSystemUI();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        btnUpload = findViewById(R.id.btnUpload);
        imageView = findViewById(R.id.imageView);
        statusTextView = findViewById(R.id.statusTextView);
        blackView = findViewById(R.id.blackview);
        RAWswitch = findViewById(R.id.RAWswitch);
        OptionBar = findViewById(R.id.optionBar);
        OptionVal = findViewById(R.id.CurrentVal);
        nextopt = findViewById(R.id.nextOption);
        prevopt = findViewById(R.id.prevOption);
        plusopt = findViewById(R.id.plusOption);
        minusopt = findViewById(R.id.minusOption);
        presetText = findViewById(R.id.presetText);
        Presetswitch = findViewById(R.id.Presetswitch);
        OptionBar.setMin(1);
        OptionBar.setMax(100);
        OptionBar.setProgress(20);

        OptionBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (BarLock == 0) {
                    if (optmode == 0) {
                        if (progress == 0)
                            progress = 1;
                        Capturetimeval = progress;
                        OptionVal.setText("촬영 주기 : " +Printfloat(1,progress)+"s");
                        Log.i(TAG, "로그 OptionVal 0 " + Capturetimeval);
                        Log.i(TAG, "로그 텍스트 " + Printfloat(1,progress));
                    } else if (optmode == 1) {
                        progress = ISOSetting(progress);
                        if (progress < mPreview.ISOMin)
                            OptionVal.setText("ISO : Auto");
                        else
                            OptionVal.setText("ISO : " + String.valueOf(progress));
                        mPreview.ISOval = progress;
                        Log.i(TAG, "로그 OptionVal 1 " + mPreview.ISOval);
                    } else if (optmode == 2) {
                        Log.i(TAG, "로그 OptionVal 2 " + progress);
                        double Progress = SpeedSetting(progress);
                        if (progress < mPreview.shutterspeedMin)
                            OptionVal.setText("노출 시간 : Auto");
                        else
                            OptionVal.setText("노출 시간 : " +Progress+"ms");
                        mPreview.shutterspeedval = (long) (Progress * 1000000);
                        mPreview.previewshutterspeed = mPreview.shutterspeedval;
                        Log.i(TAG, "로그 OptionVal 2 " + mPreview.shutterspeedval);
                    } else if (optmode == 3) {
                        progress = FrameSetting(progress);
                        if (progress < mPreview.framerateMin/1000)
                            OptionVal.setText("Frame Duration : Auto");
                        else
                            OptionVal.setText("Frame Duration : " + Printfloat(3,progress)+"ms");
                        mPreview.framerateval = (long) progress * 1000;
                        Log.i(TAG, "로그 OptionVal 3 " + mPreview.framerateval);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (optmode == 0) {
                    if (progress == 0)
                        progress = 1;
                    Capturetimeval = progress;
                    OptionVal.setText("촬영 주기 : " +Printfloat(1,progress)+"s");
                    Log.i(TAG, "로그 OptionVal 0 " + Capturetimeval);
                }
                else if (optmode == 1)
                {
                    progress = ISOSetting(progress);
                    if (progress < mPreview.ISOMin)
                        OptionVal.setText("ISO : Auto");
                    else
                        OptionVal.setText("ISO : " + String.valueOf(progress));
                    mPreview.ISOval = progress;
                    Log.i(TAG, "로그 OptionVal 1 " + mPreview.ISOval);
                }
                else if (optmode == 2)
                {
                    double Progress = SpeedSetting(progress);
                    if (progress < mPreview.shutterspeedMin)
                        OptionVal.setText("노출 시간 : Auto");
                    else
                        OptionVal.setText("노출 시간 : " +Progress+"ms");
                    mPreview.shutterspeedval = (long) (Progress * 1000000);
                    mPreview.previewshutterspeed = mPreview.shutterspeedval;
                    Log.i(TAG, "로그 OptionVal 2 " + mPreview.shutterspeedval);
                }
                else if (optmode == 3)
                {
                    progress = FrameSetting(progress);
                    if (progress < mPreview.framerateMin/1000)
                        OptionVal.setText("Frame Duration : Auto");
                    else
                        OptionVal.setText("Frame Duration : " + Printfloat(3,progress)+"ms");
                    mPreview.framerateval = (long)progress*1000;
                    Log.i(TAG, "로그 OptionVal 3 " + mPreview.framerateval);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (optmode == 0) {
                    if (progress == 0)
                        progress = 1;
                    Capturetimeval = progress;
                    OptionVal.setText("촬영 주기 : " +Printfloat(1,progress)+"s");
                    Log.i(TAG, "로그 OptionVal 0 " + Capturetimeval);
                }
                else if (optmode == 1)
                {
                    progress = ISOSetting(progress);
                    if (progress < mPreview.ISOMin) {
                        mPreview.ISOAuto = 1;
                        Log.i(TAG, "로그 PreviewCapture");
                        OptionVal.setText("ISO : Auto");
                        OptionBar.setProgress(OptionBar.getMin(),true);
                        if (mPreview.ISOAuto == 1 && mPreview.SpeedAuto == 1 && mPreview.FrameAuto == 1) {
                            mPreview.AutoMode = 1;
                        }
                        else
                            mPreview.AutoMode = 0;
                        mPreview.PreviewCapture();
                    }
                    else {
                        mPreview.ISOAuto = 0;
                        Log.i(TAG, "로그 UpdatePreview");
                        OptionVal.setText("ISO : " + String.valueOf(progress));
                        mPreview.AutoMode = 0;
                        mPreview.startPreview();
                    }
                    mPreview.ISOval = progress;
                    Log.i(TAG, "로그 OptionVal 1 "+mPreview.ISOval);
                }
                else if (optmode == 2)
                {
                    double Progress = SpeedSetting(progress);
                    if (progress < mPreview.shutterspeedMin) {
                        mPreview.SpeedAuto = 1;
                        Log.i(TAG, "로그 PreviewCapture");
                        OptionVal.setText("노출 시간 : Auto");
                        OptionBar.setProgress(OptionBar.getMin(),true);
                        if (mPreview.ISOAuto == 1 && mPreview.SpeedAuto == 1 && mPreview.FrameAuto == 1) {
                            mPreview.AutoMode = 1;
                        }
                        else
                            mPreview.AutoMode = 0;
                        mPreview.PreviewCapture();
                    }
                    else {
                        mPreview.SpeedAuto = 0;
                        Log.i(TAG, "로그 UpdatePreview");
                        OptionVal.setText("노출 시간 : " +Progress+"ms");
                        mPreview.AutoMode = 0;
                        mPreview.startPreview();
                    }
                    mPreview.shutterspeedval = (long) (Progress * 1000000);
                    mPreview.previewshutterspeed = mPreview.shutterspeedval;
                    SpeedProgress = progress;
                    Log.i(TAG, "로그 OptionVal 2 "+mPreview.shutterspeedval);
                }
                else if (optmode == 3)
                {
                    FrameSetting(progress);
                    if (progress < mPreview.framerateMin/1000) {
                        mPreview.FrameAuto = 1;
                        Log.i(TAG, "로그 PreviewCapture");
                        OptionVal.setText("Frame Duration : Auto");
                        OptionBar.setProgress(OptionBar.getMin(),true);
                        if (mPreview.ISOAuto == 1 && mPreview.SpeedAuto == 1 && mPreview.FrameAuto == 1) {
                            mPreview.AutoMode = 1;
                        }
                        else
                            mPreview.AutoMode = 0;
                        mPreview.PreviewCapture();
                    }
                    else {
                        mPreview.FrameAuto = 0;
                        Log.i(TAG, "로그 UpdatePreview");
                        OptionVal.setText("Frame Duration : " + Printfloat(3,progress)+"ms");
                        mPreview.AutoMode = 0;
                        OptionBar.setProgress(FrameSetting(progress));
                        mPreview.startPreview();
                    }
                    mPreview.framerateval = (long)(progress/1000)*1000000;
                    Log.i(TAG, "로그 OptionVal 3 "+mPreview.framerateval);
                }
            }
        });

        plusopt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BarLock = 1;
                int CurVal = OptionBar.getProgress();
                Log.i(TAG, "로그 "+OptionBar.getMin()+" "+OptionBar.getMax()+" "+optmode);
                if (optmode == 0) { // capture interval
                    int NewVal = CurVal+1;
                    if (NewVal > OptionBar.getMax())
                        NewVal = OptionBar.getMax();
                    OptionBar.setProgress(NewVal);
                    Capturetimeval = NewVal;
                    OptionVal.setText("촬영 주기 : " +Printfloat(1,NewVal)+"s");
                    Log.i(TAG, "로그 plusOpt 0 " + Capturetimeval);
                }
                else if (optmode == 1) // ISO
                {
                    CurVal = ISOSetting(CurVal);
                    int NewVal = CurVal+50;
                    if (NewVal < mPreview.ISOMin) {
                        NewVal = mPreview.ISOMin;
                    }
                    if (NewVal > OptionBar.getMax())
                        NewVal = OptionBar.getMax();
                    mPreview.ISOAuto = 0;
                    OptionVal.setText("ISO : " + String.valueOf(NewVal));
                    mPreview.AutoMode = 0;
                    mPreview.startPreview();
                    OptionBar.setProgress(NewVal);
                    mPreview.ISOval = NewVal;
                    Log.i(TAG, "로그 plusOpt 1 " + mPreview.ISOval);
                }
                else if (optmode == 2) // shutter speed
                {
                    double Progress = SpeedSetting(CurVal+1000);
                    if (Progress < mPreview.shutterspeedRange.getUpper()/1000000.0) {
                        int NewVal = CurVal + 1000;
                        if (NewVal < mPreview.shutterspeedMin) {
                            NewVal = (int) mPreview.shutterspeedMin;
                        }
                        if (NewVal > OptionBar.getMax())
                            NewVal = OptionBar.getMax();

                        mPreview.SpeedAuto = 0;
                        OptionVal.setText("노출 시간 : " + Progress + "ms");
                        mPreview.AutoMode = 0;
                        mPreview.startPreview();
                        OptionBar.setProgress(NewVal);
                        mPreview.shutterspeedval = (long) (Progress * 1000000);
                        mPreview.previewshutterspeed = mPreview.shutterspeedval;
                        SpeedProgress = NewVal;
                        Log.i(TAG, "로그 plusOpt 2 " + mPreview.shutterspeedval);
                    }
                }
                else if (optmode == 3) // frame duration
                {
                    CurVal = FrameSetting(CurVal);
                    int NewVal = CurVal+1000;
                    if (NewVal < mPreview.framerateMin/1000) {
                        NewVal = (int)mPreview.framerateMin/1000;
                    }
                    if (NewVal > OptionBar.getMax())
                        NewVal = OptionBar.getMax();

                    mPreview.FrameAuto = 0;
                    mPreview.AutoMode = 0;
                    mPreview.startPreview();
                    OptionBar.setProgress(FrameSetting(NewVal));
                    mPreview.framerateval = (long)(NewVal/1000)*1000000;
                    OptionVal.setText("Frame Duration : " + Printfloat(3,mPreview.framerateval/1000)+"ms");
                    Log.i(TAG, "로그 plusOpt 3 " + mPreview.framerateval);
                }
                BarLock = 0;
            }
        });

        minusopt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BarLock = 1;
                int CurVal = OptionBar.getProgress();
                Log.i(TAG, "로그 "+OptionBar.getMin()+" "+OptionBar.getMax()+" "+optmode);
                if (optmode == 0) { // capture interval
                    int NewVal = CurVal-1;
                    if (NewVal == 0)
                        NewVal = 1;
                    OptionBar.setProgress(NewVal);
                    Capturetimeval = NewVal;
                    OptionVal.setText("촬영 주기 : " +Printfloat(1,NewVal)+"s");
                    Log.i(TAG, "로그 minusOpt 0 " + Capturetimeval);
                }
                else if (optmode == 1) // ISO
                {
                    CurVal = ISOSetting(CurVal);
                    int NewVal = CurVal-50;
                    if (NewVal < mPreview.ISOMin) {
                        NewVal = 0;
                        mPreview.ISOAuto = 1;
                        OptionVal.setText("ISO : Auto");
                        OptionBar.setProgress(OptionBar.getMin());
                        if (mPreview.ISOAuto == 1 && mPreview.SpeedAuto == 1 && mPreview.FrameAuto == 1) {
                            mPreview.AutoMode = 1;
                        }
                        else
                            mPreview.AutoMode = 0;
                        mPreview.PreviewCapture();
                    }
                    else {
                        mPreview.ISOAuto = 0;
                        OptionVal.setText("ISO : " + String.valueOf(NewVal));
                        mPreview.AutoMode = 0;
                        mPreview.startPreview();
                        OptionBar.setProgress(NewVal);
                    }
                    mPreview.ISOval = NewVal;
                    Log.i(TAG, "로그 minusOpt 1 " + mPreview.ISOval);
                }
                else if (optmode == 2) // shutter speed
                {
                    double Progress = SpeedSetting(CurVal-1000);
                    if (Progress > 0) {
                        int NewVal = CurVal - 1000;
                        if (NewVal < mPreview.shutterspeedMin) {
                            NewVal = (int) mPreview.shutterspeedMin;
                        }
                        if (NewVal > OptionBar.getMax())
                            NewVal = OptionBar.getMax();

                        if (Progress < mPreview.shutterspeedRange.getLower()/1000000.0) {
                            mPreview.SpeedAuto = 1;
                            OptionVal.setText("노출 시간 : Auto");
                            OptionBar.setProgress(OptionBar.getMin());
                            if (mPreview.ISOAuto == 1 && mPreview.SpeedAuto == 1 && mPreview.FrameAuto == 1) {
                                mPreview.AutoMode = 1;
                            } else
                                mPreview.AutoMode = 0;
                            mPreview.PreviewCapture();
                        } else {
                            mPreview.SpeedAuto = 0;
                            OptionVal.setText("노출 시간 : " + Progress + "ms");
                            mPreview.AutoMode = 0;
                            mPreview.startPreview();
                            OptionBar.setProgress(NewVal);
                        }
                        mPreview.shutterspeedval = (long) (Progress * 1000000);
                        mPreview.previewshutterspeed = mPreview.shutterspeedval;
                        SpeedProgress = NewVal;
                        Log.i(TAG, "로그 minusOpt 2 " + mPreview.shutterspeedval);
                    }
                }
                else if (optmode == 3) // frame duration
                {
                    CurVal = FrameSetting(CurVal);
                    int NewVal = CurVal-1000;
                    if (NewVal < mPreview.framerateMin/1000) {
                        NewVal = 1;
                        mPreview.FrameAuto = 1;
                        OptionVal.setText("Frame Duration : Auto");
                        OptionBar.setProgress(OptionBar.getMin(),true);
                        if (mPreview.ISOAuto == 1 && mPreview.SpeedAuto == 1 && mPreview.FrameAuto == 1) {
                            mPreview.AutoMode = 1;
                        }
                        else
                            mPreview.AutoMode = 0;
                        mPreview.PreviewCapture();
                    }
                    else {
                        mPreview.FrameAuto = 0;
                        OptionVal.setText("Frame Duration : " + Printfloat(3,NewVal)+"ms");
                        mPreview.AutoMode = 0;
                        mPreview.startPreview();
                        OptionBar.setProgress(FrameSetting(NewVal));
                    }
                    mPreview.framerateval = (long)(NewVal/1000)*1000000;
                    Log.i(TAG, "로그 minusOpt 3 " + mPreview.framerateval);
                }
                BarLock = 0;
            }
        });


        nextopt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BarLock = 1;
                Log.i(TAG, "로그 "+OptionBar.getMin()+" "+OptionBar.getMax()+" "+optmode);
                if (optmode == 0) { // ISO
                    optmode = 1;
                    OptionBar.setMin(-mPreview.ISORange.getUpper()/10);
                    OptionBar.setMax(mPreview.ISORange.getUpper());
                    OptionBar.setProgress(mPreview.ISOval);
                    OptionVal.setText("ISO : " + String.valueOf(mPreview.ISOval));
                    if (mPreview.ISOval < mPreview.ISOMin) {
                        OptionVal.setText("ISO : Auto");
                        OptionBar.setProgress(OptionBar.getMin());
                    }
                    Presetswitch.setVisibility(View.INVISIBLE);
                    OptionBar.setVisibility(View.VISIBLE);
                    presetText.setVisibility(View.INVISIBLE);
                }
                else if (optmode == 1) // shutter speed
                {
                    optmode = 2;
                    OptionBar.setMin(-100000);
                    OptionBar.setMax((int)(mPreview.shutterspeedRange.getUpper()/1000));
                    OptionBar.setProgress(SpeedProgress);
                    double Progress = SpeedSetting(SpeedProgress);
                    mPreview.shutterspeedval = (long) (Progress * 1000000);
                    OptionVal.setText("노출 시간 : " +Progress+"ms");
                    if (mPreview.SpeedAuto == 1) {
                        OptionVal.setText("노출 시간 : Auto");
                        OptionBar.setProgress(OptionBar.getMin());
                    }
                    Presetswitch.setVisibility(View.VISIBLE);
                    if (mPreview.Presetflag == 1) {
                        OptionBar.setVisibility(View.INVISIBLE);
                        presetText.setVisibility(View.VISIBLE);
                    }
                    else
                    {
                        OptionBar.setVisibility(View.VISIBLE);
                        presetText.setVisibility(View.INVISIBLE);
                    }
                }
                else if (optmode == 2) // frame duration
                {
                    optmode = 3;
                    OptionBar.setMin(0);
                    OptionBar.setMax((int)(mPreview.framerateRange/1000));
                    OptionBar.setProgress((int)(mPreview.framerateval/1000));
                    OptionVal.setText("Frame Duration : " + Printfloat(3,(int)(mPreview.framerateval/1000))+"ms");
                    if (mPreview.framerateval <= 0) {
                        OptionVal.setText("Frame Duration : Auto");
                        OptionBar.setProgress(OptionBar.getMin());
                    }
                    Presetswitch.setVisibility(View.INVISIBLE);
                    OptionBar.setVisibility(View.VISIBLE);
                    presetText.setVisibility(View.INVISIBLE);
                }
                else if (optmode == 3) // capture interval
                {
                    optmode = 0;
                    OptionBar.setMin(1);
                    OptionBar.setMax(100);
                    OptionBar.setProgress(Capturetimeval);
                    OptionVal.setText("촬영 주기 : " +Printfloat(1,Capturetimeval)+"s");
                    Presetswitch.setVisibility(View.INVISIBLE);
                    OptionBar.setVisibility(View.VISIBLE);
                    presetText.setVisibility(View.INVISIBLE);
                }
                BarLock = 0;
            }
        });

        prevopt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BarLock = 1;
                Log.i(TAG, "로그 "+OptionBar.getMin()+" "+OptionBar.getMax()+" "+optmode);
                if (optmode == 2) { // ISO
                    optmode = 1;
                    OptionBar.setMin(-mPreview.ISORange.getUpper()/10);
                    OptionBar.setMax(mPreview.ISORange.getUpper());
                    OptionBar.setProgress(mPreview.ISOval);
                    OptionVal.setText("ISO : " + String.valueOf(mPreview.ISOval));
                    if (mPreview.ISOval < mPreview.ISOMin) {
                        OptionVal.setText("ISO : Auto");
                        OptionBar.setProgress(OptionBar.getMin());
                    }
                    Presetswitch.setVisibility(View.INVISIBLE);
                    OptionBar.setVisibility(View.VISIBLE);
                    presetText.setVisibility(View.INVISIBLE);
                }
                else if (optmode == 3) // shutter speed
                {
                    optmode = 2;
                    OptionBar.setMin(-100000);
                    OptionBar.setMax((int)(mPreview.shutterspeedRange.getUpper()/1000));
                    OptionBar.setProgress(SpeedProgress);
                    double Progress = SpeedSetting(SpeedProgress);
                    mPreview.shutterspeedval = (long) (Progress * 1000000);
                    OptionVal.setText("노출 시간 : " +Progress+"ms");
                    if (mPreview.SpeedAuto == 1) {
                        OptionVal.setText("노출 시간 : Auto");
                        OptionBar.setProgress(OptionBar.getMin());
                    }
                    Presetswitch.setVisibility(View.VISIBLE);
                    if (mPreview.Presetflag == 1) {
                        OptionBar.setVisibility(View.INVISIBLE);
                        presetText.setVisibility(View.VISIBLE);
                    }
                    else
                    {
                        OptionBar.setVisibility(View.VISIBLE);
                        presetText.setVisibility(View.INVISIBLE);
                    }
                }
                else if (optmode == 0) // frame duration
                {
                    optmode = 3;
                    OptionBar.setMin(0);
                    OptionBar.setMax((int)(mPreview.framerateRange/1000));
                    OptionBar.setProgress((int)(mPreview.framerateval/1000));
                    OptionVal.setText("Frame Duration : " + Printfloat(3,(int)(mPreview.framerateval/1000))+"ms");
                    if (mPreview.framerateval <= 0) {
                        OptionVal.setText("Frame Duration : Auto");
                        OptionBar.setProgress(OptionBar.getMin());
                    }
                    Presetswitch.setVisibility(View.INVISIBLE);
                    OptionBar.setVisibility(View.VISIBLE);
                    presetText.setVisibility(View.INVISIBLE);
                }
                else if (optmode == 1) // capture interval
                {
                    optmode = 0;
                    OptionBar.setMin(1);
                    OptionBar.setMax(100);
                    OptionBar.setProgress(Capturetimeval);
                    OptionVal.setText("촬영 주기 : " +Printfloat(1,Capturetimeval)+"s");
                    Presetswitch.setVisibility(View.INVISIBLE);
                    OptionBar.setVisibility(View.VISIBLE);
                    presetText.setVisibility(View.INVISIBLE);
                }
                BarLock = 0;
            }
        });

        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPreview.Presetflag == 0) {
                    openImageChooser();
                }
                else if (ParsePreset())
                {
                    openImageChooser();
                }
            }
        });


        if (Build.VERSION.SDK_INT >= 33) { // Android OS 13 이상
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_MEDIA_IMAGES,
                }, REQUEST_CAMERA_PERMISSION);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_CAMERA_PERMISSION);
            }
        }

        mCameraTextureView = (TextureView) findViewById(R.id.cameraTextureView);
        mPreview = new Preview(this, mCameraTextureView, mainActivity, RAWswitch, OptionBar);

        RAWswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b) {
                    mPreview.RAWflag = 1;
                    Log.i(TAG, "로그 RAWswitch On");
                }
                else {
                    mPreview.RAWflag = 0;
                    Log.i(TAG, "로그 RAWswitch Off");
                }
            }
        });

        Presetswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b) {
                    mPreview.Presetflag = 1;
                    OptionBar.setVisibility(View.INVISIBLE);
                    presetText.setVisibility(View.VISIBLE);
                    Log.i(TAG, "로그 Presetswitch On");
                }
                else {
                    mPreview.Presetflag = 0;
                    OptionBar.setVisibility(View.VISIBLE);
                    presetText.setVisibility(View.INVISIBLE);
                    Log.i(TAG, "로그 Presetswitch Off");
                }
            }
        });
    }

    private void openImageChooser() {
        Log.d(TAG, "로그 openImageChooser");
        imageUris.clear();
        currentImageIndex = 0;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT); // ACTION_PICK 으로 바꾸면 기존 APP에서 Uri만 복사됨 (실제 파일명 얻기 가능)
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select Pictures"), PICK_IMAGE_REQUEST);
        Toast.makeText(this,"Select Images",Toast.LENGTH_SHORT).show();
    }

    private void PresetLooper() {
        if (mPreview.Presetflag == 1) {
            mPreview.PresetCount++;
            if (mPreview.PresetCount < mPreview.PresetMax) {
                currentImageIndex--;
            } else {
                mPreview.PresetCount = 0;
            }
        }
    }

   public String getFileName(Uri uri) {
        String[] result = null;
        /*
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToNext()) {
            String path = cursor.getString(abs(cursor.getColumnIndex("_data")));
            cursor.close();
            return path != null ? path : "";
        }
        return "";
        */
       Cursor cursor = getContentResolver().query(uri, null, null, null, null);
       cursor.moveToNext();
       String path = cursor.getString(abs(cursor.getColumnIndex("_data")));
       File file = new File(path);
       result = file.getName().split("[.]");

       return result[0];
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "로그 onActivityResult");
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                if (data.getClipData() != null) {
                    Log.d(TAG, "로그 Select many");
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri imageUri = data.getClipData().getItemAt(i).getUri();
                        imageUris.add(imageUri);
                        Log.d(TAG, "로그 Select : " + imageUri);
                    }
                } else if (data.getData() != null) {
                    Uri imageUri = data.getData();
                    imageUris.add(imageUri);
                    Log.d(TAG, "로그 Select one : " + imageUri);
                }
                Toast.makeText(this,"Capture Starts in 5 seconds",Toast.LENGTH_SHORT).show();
                InitCapture = 1;
                mPreview.BlockCapture = 1;
                mPreview.PausePreview = 1;
                statusTextView.setText(getString(R.string.status_text));
                mPreview.PresetCount = 0;
                mPreview.Basetimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                TotalCount = 0;
                m_timer = new Timer();
                m_timer.schedule( new TimerTask()  { // TimerTask, Timer 매번 생성해야함
                    @Override
                    public void run() {
                        Log.d(TAG, "로그 displayNextImage " + imageUris.size());
                        if (currentImageIndex == 0) {
                            Log.d(TAG, "로그 first image");
                            runOnUiThread(new Runnable() { // UIThread 미사용시 오류발생
                                @Override
                                public void run() {
                                    Presetswitch.setVisibility(View.INVISIBLE);
                                    presetText.setVisibility(View.INVISIBLE);
                                    OptionBar.setVisibility(View.INVISIBLE);
                                    prevopt.setVisibility(View.INVISIBLE);
                                    nextopt.setVisibility(View.INVISIBLE);
                                    plusopt.setVisibility(View.INVISIBLE);
                                    minusopt.setVisibility(View.INVISIBLE);
                                    btnUpload.setVisibility(View.INVISIBLE);
                                    statusTextView.setVisibility(View.INVISIBLE);
                                    mCameraTextureView.setVisibility(View.INVISIBLE);
                                    blackView.setVisibility(View.VISIBLE);
                                    mPreview.takePicture(currentImageIndex,"Base");
                                    if (InitCapture == 1) {
                                        InitCapture = 0;
                                    }
                                    else {
                                        TotalCount++;
                                        currentImageIndex++; // race condition 주의
                                        PresetLooper(); // (thread 내부에 있어야함)
                                    }
                                }
                            });
                        }
                        else if (currentImageIndex < imageUris.size() + 1) {
                            Log.d(TAG, "로그 next image");
                            runOnUiThread(new Runnable() { // UIThread 미사용시 오류발생
                                @Override
                                public void run() {
                                    imageView.setImageURI(imageUris.get(currentImageIndex-1));
                                    Log.d(TAG, "로그 이미지 URL : "+imageUris.get(currentImageIndex-1));
                                    Presetswitch.setVisibility(View.INVISIBLE);
                                    presetText.setVisibility(View.INVISIBLE);
                                    OptionBar.setVisibility(View.INVISIBLE);
                                    blackView.setVisibility(View.INVISIBLE);
                                    prevopt.setVisibility(View.INVISIBLE);
                                    nextopt.setVisibility(View.INVISIBLE);
                                    plusopt.setVisibility(View.INVISIBLE);
                                    minusopt.setVisibility(View.INVISIBLE);
                                    btnUpload.setVisibility(View.INVISIBLE);
                                    statusTextView.setVisibility(View.INVISIBLE);
                                    mCameraTextureView.setVisibility(View.INVISIBLE);
                                    imageView.setVisibility(View.VISIBLE);
                                    mPreview.takePicture(currentImageIndex,getFileName(imageUris.get(currentImageIndex-1)));
                                    TotalCount++;
                                    currentImageIndex++; // race condition 주의
                                    PresetLooper(); // (thread 내부에 있어야함)
                                }
                            });
                        } else {
                            Log.d(TAG, "로그 All Images are processed");
                            runOnUiThread(new Runnable() { // UIThread 미사용시 오류발생
                                @Override
                                public void run() {
                                    Log.d(TAG, "로그 UiThread else");
                                    mPreview.PausePreview = 0;
                                    if (mPreview.RAWflag == 1) {
                                        statusTextView.setText("Status: " + TotalCount + " RAW Images are processed");
                                    }
                                    else {
                                        statusTextView.setText("Status: " + TotalCount + " JPEG Images are processed");
                                    }
                                    if (mPreview.Presetflag == 1)
                                    {
                                        OptionBar.setVisibility(View.INVISIBLE);
                                        Presetswitch.setVisibility(View.VISIBLE);
                                        presetText.setVisibility(View.VISIBLE);
                                    }
                                    else {
                                        OptionBar.setVisibility(View.VISIBLE);
                                        Presetswitch.setVisibility(View.INVISIBLE);
                                        presetText.setVisibility(View.INVISIBLE);
                                    }
                                    prevopt.setVisibility(View.VISIBLE);
                                    nextopt.setVisibility(View.VISIBLE);
                                    plusopt.setVisibility(View.VISIBLE);
                                    minusopt.setVisibility(View.VISIBLE);
                                    btnUpload.setVisibility(View.VISIBLE);
                                    statusTextView.setVisibility(View.VISIBLE);
                                    mCameraTextureView.setVisibility(View.VISIBLE);
                                    imageView.setVisibility(View.INVISIBLE);
                                    mPreview.openCamera();
                                }
                            });
                            m_timer.cancel();
                        }
                    }
                },5000,Capturetimeval*100); // 시간 조절 (초기 대기시간, 촬영 주기)
            }
            else
            {
                Log.e(TAG, "로그 No Select");
                Toast.makeText(this,"Selected No Image",Toast.LENGTH_SHORT).show();
            }
        }
    }
    // Test Code (복붙용)
//                try {
//                    statusTextView.setText(getString(R.string.status_text2));
//                } catch (Exception e) {
//                    Log.e(TAG, "로그 "+e);
//                    throw new RuntimeException(e);
//                }

    @Override
    protected void onResume() {
        Log.e(TAG, "로그 onResume");
        super.onResume();
        mPreview.onResume();
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "로그 onPause");
        mPreview.onPause();
        super.onPause();
    }
}
