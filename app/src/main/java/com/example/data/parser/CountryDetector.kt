package com.example.data.parser

data class CountryInfo(
    val code: String,
    val nameFa: String,
    val nameEn: String,
    val flag: String
)

object CountryDetector {
    private val defaultCountry = CountryInfo("AUTO", "اتصال خودکار", "Auto Global", "🌐")

    private val countryMap = mapOf(
        "US" to CountryInfo("US", "آمریکا", "United States", "🇺🇸"),
        "DE" to CountryInfo("DE", "آلمان", "Germany", "🇩🇪"),
        "JP" to CountryInfo("JP", "ژاپن", "Japan", "🇯🇵"),
        "NL" to CountryInfo("NL", "هلند", "Netherlands", "🇳🇱"),
        "FI" to CountryInfo("FI", "فنلاند", "Finland", "🇫🇮"),
        "CA" to CountryInfo("CA", "کانادا", "Canada", "🇨🇦"),
        "GB" to CountryInfo("GB", "بریتانیا", "United Kingdom", "🇬🇧"),
        "UK" to CountryInfo("GB", "بریتانیا", "United Kingdom", "🇬🇧"),
        "FR" to CountryInfo("FR", "فرانسه", "France", "🇫🇷"),
        "SG" to CountryInfo("SG", "سنگاپور", "Singapore", "🇸🇬"),
        "TR" to CountryInfo("TR", "ترکیه", "Turkey", "🇹🇷"),
        "AE" to CountryInfo("AE", "امارات", "UAE", "🇦🇪"),
        "CH" to CountryInfo("CH", "سوئیس", "Switzerland", "🇨🇭"),
        "SE" to CountryInfo("SE", "سوئد", "Sweden", "🇸🇪"),
        "IT" to CountryInfo("IT", "ایتالیا", "Italy", "🇮🇹"),
        "KR" to CountryInfo("KR", "کره جنوبی", "South Korea", "🇰🇷"),
        "PL" to CountryInfo("PL", "لهستان", "Poland", "🇵🇱"),
        "AU" to CountryInfo("AU", "استرالیا", "Australia", "🇦🇺"),
        "IR" to CountryInfo("IR", "ایران", "Iran", "🇮🇷")
    )

    fun detect(text: String, fallbackCode: String? = null): CountryInfo {
        if (!fallbackCode.isNullOrBlank()) {
            val upper = fallbackCode.trim().uppercase()
            countryMap[upper]?.let { return it }
        }

        val lower = text.lowercase()
        return when {
            lower.contains("us") || lower.contains("united states") || lower.contains("america") || lower.contains("usa") || lower.contains("آمریکا") ->
                countryMap["US"] ?: defaultCountry
            lower.contains("de") || lower.contains("germany") || lower.contains("frankfurt") || lower.contains("berlin") || lower.contains("آلمان") ->
                countryMap["DE"] ?: defaultCountry
            lower.contains("jp") || lower.contains("japan") || lower.contains("tokyo") || lower.contains("ژاپن") ->
                countryMap["JP"] ?: defaultCountry
            lower.contains("nl") || lower.contains("netherlands") || lower.contains("amsterdam") || lower.contains("هلند") ->
                countryMap["NL"] ?: defaultCountry
            lower.contains("fi") || lower.contains("finland") || lower.contains("helsinki") || lower.contains("فنلاند") ->
                countryMap["FI"] ?: defaultCountry
            lower.contains("ca") || lower.contains("canada") || lower.contains("toronto") || lower.contains("کانادا") ->
                countryMap["CA"] ?: defaultCountry
            lower.contains("uk") || lower.contains("gb") || lower.contains("london") || lower.contains("britain") || lower.contains("انگلیس") || lower.contains("بریتانیا") ->
                countryMap["GB"] ?: defaultCountry
            lower.contains("fr") || lower.contains("france") || lower.contains("paris") || lower.contains("فرانسه") ->
                countryMap["FR"] ?: defaultCountry
            lower.contains("sg") || lower.contains("singapore") || lower.contains("سنگاپور") ->
                countryMap["SG"] ?: defaultCountry
            lower.contains("tr") || lower.contains("turkey") || lower.contains("istanbul") || lower.contains("ترکیه") ->
                countryMap["TR"] ?: defaultCountry
            lower.contains("ch") || lower.contains("switzerland") || lower.contains("سوئیس") ->
                countryMap["CH"] ?: defaultCountry
            lower.contains("se") || lower.contains("sweden") || lower.contains("سوئد") ->
                countryMap["SE"] ?: defaultCountry
            else -> defaultCountry
        }
    }
}
