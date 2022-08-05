package site.texnopos.djakonystar.passportreader.mlkit.text

import android.graphics.Color
import android.os.Handler
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import net.sf.scuba.data.Gender
import org.jmrtd.lds.icao.MRZInfo
import site.texnopos.djakonystar.passportreader.mlkit.other.FrameMetadata
import site.texnopos.djakonystar.passportreader.mlkit.other.GraphicOverlay
import site.texnopos.djakonystar.passportreader.model.DocType
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

open class TextRecognitionProcessor(docType: DocType, resultListener: ResultListener) {
    private val textRecognizer: TextRecognizer
    private val resultListener: ResultListener
    private var scannedTextBuffer: String? = null
    private val docType: DocType

    // Whether we should ignore process(). This is usually caused by feeding input data faster than
    // the model can handle.
    private val shouldThrottle = AtomicBoolean(false)

    //region ----- Exposed Methods -----
    fun stop() {
        textRecognizer.close()
    }

    @Throws(MlKitException::class)
    fun process(data: ByteBuffer?, frameMetadata: FrameMetadata, graphicOverlay: GraphicOverlay) {
        if (shouldThrottle.get()) {
            return
        }
        val inputImage = InputImage.fromByteBuffer(
            data!!,
            frameMetadata.width,
            frameMetadata.height,
            frameMetadata.rotation,
            InputImage.IMAGE_FORMAT_NV21
        )
        detectInVisionImage(inputImage, frameMetadata, graphicOverlay)
    }

    //endregion
    //region ----- Helper Methods -----
    private fun detectInImage(image: InputImage?): Task<Text> {
        return textRecognizer.process(image!!)
    }

    private fun onSuccess(
        results: Text,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {
        graphicOverlay.clear()
        scannedTextBuffer = ""
        val blocks = results.textBlocks
        for (i in blocks.indices) {
            val lines = blocks[i].lines
            for (j in lines.indices) {
                val elements = lines[j].elements
                for (k in elements.indices) {
                    filterScannedText(graphicOverlay, elements[k])
                }
            }
        }
    }

    private fun filterScannedText(graphicOverlay: GraphicOverlay, element: Text.Element) {
        val textGraphic: GraphicOverlay.Graphic = TextGraphic(graphicOverlay, element, Color.GREEN)
        scannedTextBuffer += element.text
        if (docType === DocType.ID_CARD) {
            val patternIDCardTD1Line1 = Pattern.compile(ID_CARD_TD_1_LINE_1_REGEX)
            val matcherIDCardTD1Line1 = patternIDCardTD1Line1.matcher(scannedTextBuffer)
            val patternIDCardTD1Line2 = Pattern.compile(ID_CARD_TD_1_LINE_2_REGEX)
            val matcherIDCardTD1Line2 = patternIDCardTD1Line2.matcher(scannedTextBuffer)
            val patternIDCardTD1Line3 = Pattern.compile(ID_CARD_TD_1_LINE_3_REGEX)
            val matcherIDCardTD1Line3 = patternIDCardTD1Line3.matcher(scannedTextBuffer)
            if (matcherIDCardTD1Line1.find() && matcherIDCardTD1Line2.find() && matcherIDCardTD1Line3.find()) {
                graphicOverlay.add(textGraphic)
                var line1 = matcherIDCardTD1Line1.group(0)
                val line2 = matcherIDCardTD1Line2.group(0)
                val line3 = matcherIDCardTD1Line3.group(0)
                if (line1.indexOf(TYPE_ID_CARD) > 0) {
                    line1 = line1.substring(line1.indexOf(TYPE_ID_CARD))
                    var documentNumber = line1.substring(5, 14)
                    documentNumber = documentNumber.replace("O", "0")
                    val dateOfBirthDay = line2.substring(0, 6)
                    val expiryDate = line2.substring(8, 14)
                    Log.d(
                        TAG,
                        "Scanned Text Buffer ID Card ->>>> Doc Number: $documentNumber DateOfBirth: $dateOfBirthDay ExpiryDate: $expiryDate"
                    )
                    val mrzInfo = buildTempMrz(documentNumber, dateOfBirthDay, expiryDate)
                    mrzInfo?.let { finishScanning(it) }
                }
            }
        } else if (docType === DocType.PASSPORT) {
            val patternPassportTD3Line1 = Pattern.compile(
                PASSPORT_TD_3_LINE_1_REGEX
            )
            val matcherPassportTD3Line1 = patternPassportTD3Line1.matcher(scannedTextBuffer)
            val patternPassportTD3Line2 = Pattern.compile(
                PASSPORT_TD_3_LINE_2_REGEX
            )
            val matcherPassportTD3Line2 = patternPassportTD3Line2.matcher(scannedTextBuffer)
            if (matcherPassportTD3Line1.find() && matcherPassportTD3Line2.find()) {
                graphicOverlay.add(textGraphic)
                val line2 = matcherPassportTD3Line2.group(0)
                var documentNumber = line2.substring(0, 9)
                documentNumber = documentNumber.replace("O", "0")
                val dateOfBirthDay = line2.substring(13, 19)
                val expiryDate = line2.substring(21, 27)
                Log.d(
                    TAG,
                    "Scanned Text Buffer Passport ->>>> Doc Number: $documentNumber DateOfBirth: $dateOfBirthDay ExpiryDate: $expiryDate"
                )
                val mrzInfo = buildTempMrz(documentNumber, dateOfBirthDay, expiryDate)
                mrzInfo?.let { finishScanning(it) }
            }
        }
    }

    protected fun onFailure(e: Exception) {
        Log.w(TAG, "Text detection failed.$e")
        resultListener.onError(e)
    }

    private fun detectInVisionImage(
        image: InputImage,
        metadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {
        detectInImage(image)
            .addOnSuccessListener { results ->
                shouldThrottle.set(false)
                this@TextRecognitionProcessor.onSuccess(results, metadata, graphicOverlay)
            }
            .addOnFailureListener { e ->
                shouldThrottle.set(false)
                this@TextRecognitionProcessor.onFailure(e)
            }
        // Begin throttling until this frame of input has been processed, either in onSuccess or
        // onFailure.
        shouldThrottle.set(true)
    }

    private fun finishScanning(mrzInfo: MRZInfo) {
        try {
            if (isMrzValid(mrzInfo)) {
                // Delay returning result 1 sec. in order to make mrz text become visible on graphicOverlay by user
                // You want to call 'resultListener.onSuccess(mrzInfo)' without no delay
                Handler().postDelayed({ resultListener.onSuccess(mrzInfo) }, 1000)
            }
        } catch (exp: Exception) {
            Log.d(TAG, "MRZ DATA is not valid")
        }
    }

    private fun buildTempMrz(
        documentNumber: String,
        dateOfBirth: String,
        expiryDate: String
    ): MRZInfo? {
        var mrzInfo: MRZInfo? = null
        try {
            mrzInfo = MRZInfo(
                "P",
                "NNN",
                "",
                "",
                documentNumber,
                "NNN",
                dateOfBirth,
                Gender.UNSPECIFIED,
                expiryDate,
                ""
            )
        } catch (e: Exception) {
            Log.d(TAG, "MRZInfo error : " + e.localizedMessage)
        }
        return mrzInfo
    }

    private fun isMrzValid(mrzInfo: MRZInfo): Boolean {
        return mrzInfo.documentNumber != null && mrzInfo.documentNumber.length >= 8 && mrzInfo.dateOfBirth != null && mrzInfo.dateOfBirth.length == 6 && mrzInfo.dateOfExpiry != null && mrzInfo.dateOfExpiry.length == 6
    }

    interface ResultListener {
        fun onSuccess(mrzInfo: MRZInfo?)
        fun onError(exp: Exception?)
    }

    companion object {
        private val TAG = TextRecognitionProcessor::class.java.name
        const val TYPE_PASSPORT = "P<"
        const val TYPE_ID_CARD = "I<"
        const val ID_CARD_TD_1_LINE_1_REGEX = "([A|C|I][A-Z0-9<]{1})([A-Z]{3})([A-Z0-9<]{31})"
        const val ID_CARD_TD_1_LINE_2_REGEX =
            "([0-9]{6})([0-9]{1})([M|F|X|<]{1})([0-9]{6})([0-9]{1})([A-Z]{3})([A-Z0-9<]{11})([0-9]{1})"
        const val ID_CARD_TD_1_LINE_3_REGEX = "([A-Z0-9<]{30})"
        const val PASSPORT_TD_3_LINE_1_REGEX = "(P[A-Z0-9<]{1})([A-Z]{3})([A-Z0-9<]{39})"
        const val PASSPORT_TD_3_LINE_2_REGEX =
            "([A-Z0-9<]{9})([0-9]{1})([A-Z]{3})([0-9]{6})([0-9]{1})([M|F|X|<]{1})([0-9]{6})([0-9]{1})([A-Z0-9<]{14})([0-9<]{1})([0-9]{1})"
    }

    init {
        this.docType = docType
        this.resultListener = resultListener
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
}