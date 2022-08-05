package site.texnopos.djakonystar.passportreader.ui

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.lds.icao.MRZInfo
import org.koin.androidx.viewmodel.ext.android.viewModel
import site.texnopos.djakonystar.passportreader.R
import site.texnopos.djakonystar.passportreader.databinding.ActivityMainBinding
import site.texnopos.djakonystar.passportreader.model.DocType
import site.texnopos.djakonystar.passportreader.model.EDocument
import site.texnopos.djakonystar.passportreader.ui.CaptureActivity.Companion.DOC_TYPE
import site.texnopos.djakonystar.passportreader.ui.CaptureActivity.Companion.MRZ_RESULT
import site.texnopos.djakonystar.passportreader.util.AppUtil
import site.texnopos.djakonystar.passportreader.util.ImageUtil
import site.texnopos.djakonystar.passportreader.util.PermissionUtil
import site.texnopos.djakonystar.passportreader.util.ResourceState
import site.texnopos.djakonystar.passportreader.viewmodel.ReadViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: NfcAdapter
    private lateinit var passportNumber: String
    private lateinit var expirationDate: String
    private lateinit var birthDate: String
    private lateinit var docType: DocType
    private val viewModel: ReadViewModel by viewModel()

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnScanIdCard.setOnClickListener {
            docType = DocType.ID_CARD
            requestPermissionForCamera()
        }

        binding.btnScanPassport.setOnClickListener {
            docType = DocType.PASSPORT
            requestPermissionForCamera()
        }
    }

    private fun setMrzData(mrzInfo: MRZInfo) {
        adapter = NfcAdapter.getDefaultAdapter(this)
        binding.mainLayout.isVisible = false
        binding.imageLayout.isVisible = true

        passportNumber = mrzInfo.documentNumber
        expirationDate = mrzInfo.dateOfExpiry
        birthDate = mrzInfo.dateOfBirth
    }

    private fun openCameraActivity() {
        val intent = Intent(this, CaptureActivity::class.java)
        intent.putExtra(DOC_TYPE, docType)
        cameraActivityLauncher.launch(intent)
    }

    private val cameraActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val mrzInfo = it.data?.getSerializableExtra(MRZ_RESULT) as MRZInfo?
                if (mrzInfo != null) {
                    setMrzData(mrzInfo)
                } else {
                    Snackbar.make(
                        binding.loadingLayout,
                        R.string.error_input,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            val intent = Intent(applicationContext, this::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            val pendingIntent =
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
            val filter = arrayOf(arrayOf("android.nfc.tech.IsoDep"))
            adapter.enableForegroundDispatch(this, pendingIntent, null, filter)
        }
    }

    override fun onPause() {
        super.onPause()

        if (::adapter.isInitialized) {
            adapter.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent?.action) {
            val tag: Tag? = intent.extras?.getParcelable(NfcAdapter.EXTRA_TAG)
            tag?.let { tag1 ->
                if (tag1.techList.toList().contains("android.nfc.tech.IsoDep")) {
                    clearViews()
                    if (::passportNumber.isInitialized && passportNumber.isNotEmpty()
                        && ::expirationDate.isInitialized && expirationDate.isNotEmpty()
                        && ::birthDate.isInitialized && birthDate.isNotEmpty()
                    ) {
                        val bacKey: BACKeySpec = BACKey(passportNumber, birthDate, expirationDate)
                        val nfcTag = IsoDep.get(tag1)
                        nfcTag.connect()
                        nfcTag.timeout = 5000
                        viewModel.doRead(this, nfcTag, bacKey)
                        binding.apply {
                            mainLayout.isVisible = false
                            imageLayout.isVisible = false
                            loadingLayout.isVisible = true
                        }
                        viewModel.eDocument.observe(this) {
                            when (it.status) {
                                ResourceState.LOADING -> {
                                    binding.apply {
                                        mainLayout.isVisible = false
                                        imageLayout.isVisible = false
                                        loadingLayout.isVisible = true
                                    }
                                }
                                ResourceState.SUCCESS -> {
                                    binding.mainLayout.isVisible = true
                                    binding.loadingLayout.isVisible = false
                                    it.data?.let { eDocument ->
                                        setResultToView(eDocument)
                                    }
                                }
                                ResourceState.ERROR -> {
                                    binding.mainLayout.isVisible = true
                                    binding.loadingLayout.isVisible = false
                                    it.message?.let { message ->
                                        Snackbar.make(binding.mainLayout, message, Snackbar.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Snackbar.make(
                        binding.loadingLayout,
                        R.string.error_input,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setResultToView(eDocument: EDocument) {
        val image = ImageUtil.scaleImage(eDocument.personDetails.faceImage)

        binding.viewPhoto.setImageBitmap(image)

        var result = "NAME: ${eDocument.personDetails.name}\n"
        result += "SURNAME: ${eDocument.personDetails.surname}\n"
        result += "PERSONAL NUMBER: ${eDocument.personDetails.personalNumber}\n"
        result += "GENDER: ${eDocument.personDetails.gender}\n"

        binding.textResult.text = result
    }

    private fun clearViews() {
        binding.apply {
            viewPhoto.setImageBitmap(null)
            textResult.text = ""
        }
    }

    private fun requestPermissionForCamera() {
        val permissions = arrayOf(Manifest.permission.CAMERA)
        val isPermissionGranted = PermissionUtil.hasPermissions(this, permissions[0])

        if (!isPermissionGranted) {
            AppUtil.showAlertDialog(
                this,
                getString(R.string.permission_title),
                getString(R.string.permission_description),
                getString(R.string.button_ok),
                false
            ) { _, _ ->
                requestPermissionForCamera.launch(permissions)
            }
        } else {
            openCameraActivity()
        }
    }

    private val requestPermissionForCamera =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var isGranted = true
            permissions.entries.forEach {
                if (!it.value) isGranted = false
            }

            if (!isGranted) {
                if (!PermissionUtil.showRationale(this, permissions.keys.first())) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } else {
                    requestPermissionForCamera()
                }
            } else if (isGranted) {
                openCameraActivity()
            }
        }
}
