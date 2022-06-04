package com.example.nativeopencvandroidtemplate

import android.Manifest
import android.R.attr.path
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import org.opencv.android.*
import org.opencv.core.CvType
import org.opencv.core.Mat


class MainActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private var tattooExample: Mat? = null
    private var mOpenCvCameraView: CameraBridgeViewBase? = null

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("native-lib")

                    val bMap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.tattoo_example)
                    tattooExample = Mat.zeros(bMap.height, bMap.width, CvType.CV_8UC3)
                    Utils.bitmapToMat(bMap, tattooExample)

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

        mOpenCvCameraView = findViewById<CameraBridgeViewBase>(R.id.main_surface)
        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView!!.setCvCameraViewListener(this)

        val selectFromGallery = findViewById<Button>(R.id.selectImage)
        selectFromGallery.setOnClickListener {
            val pickImageIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(pickImageIntent, 1)
        }
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
        // get current camera frame as OpenCV Mat object
        val mat = frame.rgba()

        // native call to process current camera frame

        adaptiveThresholdFromJNI(mat.nativeObjAddr, tattooExample!!.nativeObjAddr)

        // return processed frame for live preview
        return mat
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, imageReturnedIntent: Intent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent)
        when (requestCode) {
            0 -> if (resultCode == RESULT_OK) {
                val selectedImage: Uri? = imageReturnedIntent.data
                Log.d("IMAGE", selectedImage.toString())
            }
            1 -> if (resultCode == RESULT_OK) {
                val selectedImage: Uri? = imageReturnedIntent.data
                Glide.with(this)
                    .asBitmap()
                    .load(selectedImage)
                    .listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.e("ERROR", "FILE NOT LOADED")
                            return false;
                        }

                        override fun onResourceReady(
                            resource: Bitmap?,
                            model: Any?,
                            target: Target<Bitmap>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            tattooExample = Mat.zeros(resource!!.height, resource.width, CvType.CV_8UC3)
                            Utils.bitmapToMat(resource, tattooExample)
                            Log.d("IMAGE", "SUCCESS")
                            return false;
                        }

                    })
                Log.d("IMAGE", selectedImage.toString())
            }
        }
    }

    private external fun adaptiveThresholdFromJNI(matAddr: Long, tattooAddr: Long)

    companion object {

        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST = 1
    }
}
