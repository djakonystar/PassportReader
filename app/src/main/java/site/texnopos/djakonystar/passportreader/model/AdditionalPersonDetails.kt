package site.texnopos.djakonystar.passportreader.model

data class AdditionalPersonDetails (
    var custodyInformation: String,
    var fullDateOfBirth: String,
    var nameOfHolder: String,
    var otherNames: List<String>,
    var otherValidTDNumbers: List<String>,
    var permanentAddress: List<String>,
    var personalNumber: String,
    var personalSummary: String,
    var placeOfBirth: List<String>,
    var profession: String,
    var proofOfCitizenship: ByteArray,
    var tag: Int,
    var tagPresenceList: List<Int>,
    var telephone: String,
    var title: String,
)
