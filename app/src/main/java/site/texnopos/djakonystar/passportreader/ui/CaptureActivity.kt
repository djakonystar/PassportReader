package site.texnopos.djakonystar.passportreader.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.jmrtd.lds.icao.MRZInfo
import site.texnopos.djakonystar.passportreader.databinding.ActivityCaptureBinding
import site.texnopos.djakonystar.passportreader.mlkit.camera.CameraSource
import site.texnopos.djakonystar.passportreader.mlkit.text.TextRecognitionProcessor
import site.texnopos.djakonystar.passportreader.model.DocType
import java.io.IOException

class CaptureActivity : AppCompatActivity(), TextRecognitionProcessor.ResultListener {
    companion object {
        const val MRZ_RESULT = "MRZ_RESULT"
        const val DOC_TYPE = "DOC_TYPE"
    }

    private lateinit var cameraSource: CameraSource
    private var docType = DocType.OTHER
    private val TAG = this.javaClass.simpleName
    private val binding by lazy {
        ActivityCaptureBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)


        if (intent.hasExtra(DOC_TYPE)) {
            docType = intent.getSerializableExtra(DOC_TYPE) as DocType

            if (docType == DocType.PASSPORT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }

        createCameraSource()
        startCameraSource()
    }

    override fun onResume() {
        super.onResume()
        startCameraSource()
    }

    override fun onPause() {
        super.onPause()
        binding.cameraSourcePreview.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource.release()
    }

    private fun createCameraSource() {
        cameraSource = CameraSource(this, binding.graphicsOverlay)
        cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK)
        cameraSource.setMachineLearningFrameProcessor(TextRecognitionProcessor(docType, this))
    }

    private fun startCameraSource() {
        try {
            binding.cameraSourcePreview.start(cameraSource, binding.graphicsOverlay)
        } catch (e: IOException) {
            Log.e(TAG, "Unable to start camera source.", e)
            cameraSource.release()
        }
    }

    override fun onSuccess(mrzInfo: MRZInfo?) {
        val returnIntent = Intent()
        returnIntent.putExtra(MRZ_RESULT, mrzInfo)
        setResult(Activity.RESULT_OK, returnIntent)
        finish()
    }

    override fun onError(exp: Exception?) {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
