package site.texnopos.djakonystar.passportreader.model

import android.graphics.Bitmap

data class PersonDetails (
    var name: String,
    var surname: String,
    var personalNumber: String,
    var gender: String,
    var birthDate: String,
    var expiryDate: String,
    var serialNumber: String,
    var nationality: String,
    var issuerAuthority: String,
    var faceImage: Bitmap?,
    var faceImageBase64: String?
)
