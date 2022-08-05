package site.texnopos.djakonystar.passportreader.data

import android.content.Context
import android.nfc.tech.IsoDep
import android.util.Log
import io.reactivex.rxjava3.core.Observable
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.PassportService.DEFAULT_MAX_BLOCKSIZE
import org.jmrtd.PassportService.NORMAL_MAX_TRANCEIVE_LENGTH
import org.jmrtd.lds.CardSecurityFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.icao.*
import org.jmrtd.lds.iso19794.FaceImageInfo
import site.texnopos.djakonystar.passportreader.model.DocType
import site.texnopos.djakonystar.passportreader.model.EDocument
import site.texnopos.djakonystar.passportreader.model.PersonDetails
import site.texnopos.djakonystar.passportreader.util.DateUtil
import site.texnopos.djakonystar.passportreader.util.ImageUtil
import java.lang.Exception

class ReadTask {
    fun doTask(context: Context, isoDep: IsoDep, bacKey: BACKeySpec): Observable<EDocument> {
        var docType = DocType.OTHER

        val cardService = CardService.getInstance(isoDep)
        cardService.open()

        val service = PassportService(
            cardService,
            NORMAL_MAX_TRANCEIVE_LENGTH,
            DEFAULT_MAX_BLOCKSIZE,
            true,
            false
        )
        service.open()

        var paceSucceeded = false
        try {
            val cardSecurityFile =
                CardSecurityFile(service.getInputStream(PassportService.EF_CARD_SECURITY))
            val securityInfoCollection = cardSecurityFile.securityInfos

            for (securityInfo in securityInfoCollection) {
                if (securityInfo is PACEInfo) {
                    val paceInfo: PACEInfo = securityInfo
                    service.doPACE(
                        bacKey,
                        paceInfo.objectIdentifier,
                        PACEInfo.toParameterSpec(paceInfo.parameterId),
                        null
                    )
                    paceSucceeded = true
                }
            }
        } catch (e: Exception) {
            Log.w(this::class.java.simpleName, e)
        }

        service.sendSelectApplet(paceSucceeded)

        if (!paceSucceeded) {
            try {
                service.getInputStream(PassportService.EF_COM).read()
            } catch (e: Exception) {
                service.doBAC(bacKey)
            }
        }

        val dg1In = service.getInputStream(PassportService.EF_DG1)
        val dg1File = DG1File(dg1In)
        val dg2In = service.getInputStream(PassportService.EF_DG2)
        val dg2File = DG2File(dg2In)

        val mrzInfo = dg1File.mrzInfo

        val faceInfos = dg2File.faceInfos
        val allFaceImageInfos = mutableListOf<FaceImageInfo>()
        for (faceInfo in faceInfos) {
            allFaceImageInfos.addAll(faceInfo.faceImageInfos)
        }

        val faceImageInfo = allFaceImageInfos.iterator().next()
        val image = ImageUtil.getImage(context, faceImageInfo)

        val personDetails = PersonDetails(
            name = mrzInfo.secondaryIdentifier.replace("<", " ").trim(),
            surname = mrzInfo.primaryIdentifier.replace("<", " ").trim(),
            personalNumber = mrzInfo.personalNumber,
            gender = mrzInfo.gender.toString(),
            birthDate = DateUtil.convertFromMrzDate(mrzInfo.dateOfBirth),
            expiryDate = DateUtil.convertFromMrzDate(mrzInfo.dateOfExpiry),
            serialNumber = mrzInfo.documentNumber,
            nationality = mrzInfo.nationality,
            issuerAuthority = mrzInfo.issuingState,
            faceImage = image.bitmapImage,
            faceImageBase64 = image.base64Image
        )

        if (mrzInfo.documentCode == "I") {
            docType = DocType.ID_CARD
        } else if (mrzInfo.documentCode == "P") {
            docType = DocType.PASSPORT
        }

        val eDocument = EDocument(
            docType = docType,
            personDetails = personDetails
        )

        return Observable.just(eDocument)
    }
}
