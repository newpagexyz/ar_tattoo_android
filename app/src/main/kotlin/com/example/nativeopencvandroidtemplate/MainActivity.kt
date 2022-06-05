package com.example.nativeopencvandroidtemplate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.opencv.android.*
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc


class MainActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private var tattooExample: Mat? = null
    private var mOpenCvCameraView: CameraBridgeViewBase? = null

    private var toggleColor = false

    private var hSensitivity = 100
    private var sSensitivity = 100
    private var vSensitivity = 100

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("native-lib")

                    if (tattooExample == null) {
                        val bMap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.tattoo_example)
                        tattooExample = Mat.zeros(bMap.height, bMap.width, CvType.CV_8UC3)
                        Utils.bitmapToMat(bMap, tattooExample)
                    }

                    mOpenCvCameraView!!.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "called onCreate")
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )

        setContentView(R.layout.activity_main)

        mOpenCvCameraView = findViewById(R.id.main_surface)
        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView!!.setCvCameraViewListener(this)

        val selectFromGallery = findViewById<ImageButton>(R.id.imageButton2)
        selectFromGallery.setOnClickListener {
            val pickImageIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(pickImageIntent, 1)
        }
        val toggleColorButton = findViewById<ImageButton>(R.id.toggleColor)
        toggleColorButton.setOnClickListener {
            toggleColor = !toggleColor
        }

        val hSensitivityTrackBack = findViewById<SeekBar>(R.id.hsens)
        hSensitivityTrackBack.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                hSensitivity = p1
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        val sSensitivityTrackBack = findViewById<SeekBar>(R.id.ssens)
        hSensitivityTrackBack.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                sSensitivity = p1
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        val vSensitivityTrackBack = findViewById<SeekBar>(R.id.vsens)
        hSensitivityTrackBack.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                vSensitivity = p1
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mOpenCvCameraView!!.setCameraPermissionGranted()
                } else {
                    val message = "Camera permission was not granted"
                    Log.e(TAG, message)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                Log.e(TAG, "Unexpected permission request")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mOpenCvCameraView != null)
            mOpenCvCameraView!!.disableView()
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mOpenCvCameraView != null)
            mOpenCvCameraView!!.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {}

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(frame: CameraBridgeViewBase.CvCameraViewFrame): Mat {

        val mRgba = frame.rgba()
        val mRgbaT: Mat = mRgba.t()
        Core.flip(mRgba.t(), mRgbaT, 1)
        Imgproc.resize(mRgbaT, mRgbaT, mRgba.size())

        adaptiveThresholdFromJNI(mRgbaT.nativeObjAddr, tattooExample!!.nativeObjAddr, toggleColor, hSensitivity, sSensitivity, vSensitivity)
        mRgba.release()
        return mRgbaT
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, imageReturnedIntent: Intent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent)

        if (requestCode == 1 && resultCode == RESULT_OK) {
            mOpenCvCameraView!!.disableView()
            val inputStream = contentResolver.openInputStream(imageReturnedIntent.data!!)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            tattooExample = Mat.zeros(bitmap.height, bitmap.width, CvType.CV_8UC3)
            Utils.bitmapToMat(bitmap, tattooExample)
            mOpenCvCameraView!!.enableView()
        }
    }

    private external fun adaptiveThresholdFromJNI(matAddr: Long, tattooAddr: Long, toggleColor: Boolean, hSens: Int, sSens: Int, vSens: Int)

    companion object {

        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST = 1
    }
}
