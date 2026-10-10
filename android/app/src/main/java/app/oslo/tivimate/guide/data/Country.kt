package app.oslo.tivimate.guide.data

data class NameClass(
    val banner: Boolean,
    val country: String?,
    val groupCode: String?,
    val section: String? = null,
    val bannerLabel: String? = null,
)

private val PREFIX = Regex("""^([^:]{1,24}):\s*(?=\S)""")

private val ISO = """
AD Andorra
AE United Arab Emirates
AF Afghanistan
AL Albania
AM Armenia
AO Angola
AR Argentina
AT Austria
AU Australia
AZ Azerbaijan
BA Bosnia and Herzegovina
BD Bangladesh
BE Belgium
BG Bulgaria
BH Bahrain
BR Brazil
CA Canada
CH Switzerland
CL Chile
CN China
CO Colombia
CR Costa Rica
CU Cuba
CV Cabo Verde
CY Cyprus
CZ Czechia
DE Germany
DK Denmark
DO Dominican Republic
DZ Algeria
EC Ecuador
EE Estonia
EG Egypt
ES Spain
ET Ethiopia
FI Finland
FJ Fiji
FR France
GB United Kingdom
GE Georgia
GH Ghana
GR Greece
GT Guatemala
HK Hong Kong
HN Honduras
HR Croatia
HT Haiti
HU Hungary
ID Indonesia
IE Ireland
IL Israel
IN India
IQ Iraq
IR Iran
IS Iceland
IT Italy
JM Jamaica
JO Jordan
JP Japan
KE Kenya
KH Cambodia
KR South Korea
KW Kuwait
KZ Kazakhstan
LB Lebanon
LK Sri Lanka
LT Lithuania
LU Luxembourg
LV Latvia
LY Libya
MA Morocco
MC Monaco
MD Moldova
ME Montenegro
MK North Macedonia
MT Malta
MU Mauritius
MV Maldives
MX Mexico
MY Malaysia
MZ Mozambique
NA Namibia
NG Nigeria
NI Nicaragua
NL Netherlands
NO Norway
NP Nepal
NZ New Zealand
OM Oman
PA Panama
PE Peru
PH Philippines
PK Pakistan
PL Poland
PR Puerto Rico
PS Palestine
PT Portugal
PY Paraguay
QA Qatar
RO Romania
RS Serbia
RU Russia
SA Saudi Arabia
SD Sudan
SE Sweden
SG Singapore
SI Slovenia
SK Slovakia
SN Senegal
SO Somalia
SV El Salvador
SY Syria
TH Thailand
TN Tunisia
TR Turkey
TT Trinidad and Tobago
TW Taiwan
TZ Tanzania
UA Ukraine
UG Uganda
US United States
UY Uruguay
UZ Uzbekistan
VE Venezuela
VN Vietnam
YE Yemen
ZA South Africa
ZM Zambia
ZW Zimbabwe
""".trimIndent()

private val COUNTRIES: Map<String, String> = buildMap {
    ISO.lineSequence().forEach { line ->
        val space = line.indexOf(' ')
        if (space > 0) put(line.substring(0, space), line.substring(space + 1))
    }
    put("UK", "United Kingdom")
    put("IRL", "Ireland")
    put("UAE", "United Arab Emirates")
    put("AFG", "Afghanistan")
    put("UGA", "Uganda")
    put("ETH", "Ethiopia")
    put("GHA", "Ghana")
    put("NIG", "Nigeria")
    put("SEN", "Senegal")
    put("SRI", "Sri Lanka")
    put("SOM", "Somalia")
    put("CABO", "Cabo Verde")
    put("ARM", "Armenia")
    put("PAK", "Pakistan")
    put("BAN", "Bangladesh")
    put("KSA", "Saudi Arabia")
    // This playlist uses these prefixes for regions, not the ISO countries.
    put("AR", "Arabic")
    put("LA", "Latin America")
    put("AFR", "Africa")
}

fun countryLabel(code: String): String = COUNTRIES[code] ?: code

fun cleanBanner(name: String): String = name.trim().trim('#').trim().ifEmpty { "Section" }

fun classifyName(name: String): NameClass {
    val trimmed = name.trim()
    if (trimmed.startsWith("#")) {
        return NameClass(banner = true, country = null, groupCode = null, bannerLabel = cleanBanner(trimmed))
    }
    val match = PREFIX.find(trimmed) ?: return NameClass(banner = false, country = null, groupCode = null)
    val raw = match.groupValues[1].trim()
    val token = raw.split(' ', '/', '-').first().uppercase()
    return if (token in COUNTRIES) {
        NameClass(banner = false, country = token, groupCode = null)
    } else {
        NameClass(banner = false, country = null, groupCode = raw)
    }
}

fun assignSections(rows: List<NameClass>): List<NameClass> {
    val donor = arrayOfNulls<NameClass>(rows.size)
    var upcoming: NameClass? = null
    for (index in rows.lastIndex downTo 0) {
        if (!rows[index].banner) upcoming = rows[index]
        donor[index] = upcoming
    }
    val placed = rows.mapIndexed { index, row ->
        if (!row.banner) row
        else {
            val next = donor[index]
            row.copy(country = next?.country, groupCode = next?.groupCode)
        }
    }
    var current: String? = null
    return placed.map { row ->
        if (row.banner) {
            current = row.bannerLabel
            row
        } else {
            row.copy(section = current)
        }
    }
}
