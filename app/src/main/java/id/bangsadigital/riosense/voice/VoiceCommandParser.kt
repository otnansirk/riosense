package id.bangsadigital.riosense.voice

import id.bangsadigital.riosense.ui.components.ControlsTab
import id.bangsadigital.riosense.ui.i18n.AppLanguage

enum class MediaAction { NEXT, PREVIOUS, PLAY, PAUSE }
enum class VolumeAction { UP, DOWN, MUTE }
enum class StatusTopic { OVERVIEW, COOLANT, BATTERY, TIRES, SERVICE, ERRORS }
enum class MusicAppPreference { AUTO, SPOTIFY, YOUTUBE_MUSIC, YOUTUBE }

sealed interface VoiceCommand {
    data class NavigateMaps(
        val query: String,
        val isSearchOnly: Boolean = false,
        val waypoint: String? = null,
        val avoidTolls: Boolean = false
    ) : VoiceCommand
    data object OpenMaps : VoiceCommand
    data class PlayMusic(
        val query: String,
        val isPlaylist: Boolean = false,
        val preferredApp: MusicAppPreference = MusicAppPreference.AUTO
    ) : VoiceCommand
    data class PlayVideo(val query: String) : VoiceCommand
    data object OpenSpotify : VoiceCommand
    data class OpenApp(val appName: String) : VoiceCommand
    data class MediaControl(val action: MediaAction) : VoiceCommand
    data class VolumeControl(val action: VolumeAction) : VoiceCommand
    data class SetVolumePercent(val percent: Int) : VoiceCommand
    data class QueryStatus(val topic: StatusTopic) : VoiceCommand
    data class OpenControls(val tab: ControlsTab?) : VoiceCommand
    data object CloseMenus : VoiceCommand
    data object MinimizeApp : VoiceCommand
    data class SetAlarmMute(val muted: Boolean) : VoiceCommand
    data class SetLanguage(val language: AppLanguage) : VoiceCommand
    data class Unknown(val rawText: String) : VoiceCommand
}

data class ParsedVoiceResult(
    val command: VoiceCommand,
    val matchedByAi: Boolean = false,
    val confidence: Float = 1.0f,
    val inferenceMs: Long = 0L
)

/**
 * Hybrid 2-Tier 100% Offline Bilingual (Indonesian & English) Voice Command Parser:
 * - Tier 1: Ultra-fast deterministic parser (< 1 ms) with spoken number-word conversion
 * - Tier 2: On-Device ONNX Multilingual Semantic AI fallback (~20-45 ms) + Smart Slot Extractor
 */
object VoiceCommandParser {

    /**
     * Hybrid 2-Tier parse: runs Tier 1 first, and seamlessly falls back to the
     * On-Device Offline Semantic AI when natural/casual phrasing is not matched lexically.
     */
    suspend fun parseHybrid(
        rawInput: String,
        semanticEngine: SemanticNluEngine?
    ): ParsedVoiceResult {
        val cleanedForNlu = normalizeIndonesianColloquial(rawInput)
        val tier1 = parse(cleanedForNlu)
        if (tier1 !is VoiceCommand.Unknown) {
            return ParsedVoiceResult(command = tier1, matchedByAi = false, confidence = 1.0f, inferenceMs = 0L)
        }

        val match = semanticEngine?.classify(cleanedForNlu)
            ?: return ParsedVoiceResult(command = VoiceCommand.Unknown(rawInput), matchedByAi = false, confidence = 0f, inferenceMs = 0L)

        val mapped = mapSemanticIntent(match.intentId, cleanedForNlu)
        return if (mapped !is VoiceCommand.Unknown) {
            ParsedVoiceResult(
                command = mapped,
                matchedByAi = true,
                confidence = match.confidence,
                inferenceMs = match.inferenceMs
            )
        } else {
            ParsedVoiceResult(command = VoiceCommand.Unknown(rawInput), matchedByAi = false, confidence = match.confidence, inferenceMs = match.inferenceMs)
        }
    }

    /**
     * Tier 1 deterministic parser with Indonesian & English spoken number normalization.
     */
    fun parse(rawInput: String): VoiceCommand {
        val rawLower = rawInput.trim().lowercase().replace(Regex("\\s+"), " ")
        if (rawLower.isEmpty()) return VoiceCommand.Unknown(rawInput)
        val text = normalizeSpokenNumbers(rawLower)

        // 1. Media playback controls (Next / Previous / Pause / Play)
        if (containsAny(text, "next song", "next track", "lagu berikutnya", "lagu selanjutnya", "skip lagu", "ganti lagu", "next")) {
            if (!text.contains("service") && !text.contains("servis")) {
                return VoiceCommand.MediaControl(MediaAction.NEXT)
            }
        }
        if (containsAny(text, "previous song", "previous track", "lagu sebelumnya", "kembali lagu", "prev song")) {
            return VoiceCommand.MediaControl(MediaAction.PREVIOUS)
        }
        if (containsAny(text, "pause musik", "pause music", "pause lagu", "stop musik", "stop music", "berhenti musik", "jeda musik") || text == "pause") {
            return VoiceCommand.MediaControl(MediaAction.PAUSE)
        }
        if (containsAny(text, "lanjut musik", "lanjutkan musik", "resume music", "play music", "play musik") || text == "play") {
            return VoiceCommand.MediaControl(MediaAction.PLAY)
        }

        // 2. Volume controls (exact percentage bypass + relative up/down/mute)
        parseExactVolumePercent(text, requireVolumeKeyword = true)?.let {
            return VoiceCommand.SetVolumePercent(it)
        }
        if (containsAny(text, "volume naik", "naikkan volume", "besarkan volume", "volume up", "turn up", "louder", "tambah volume", "keraskan suara", "keraskan volume")) {
            return VoiceCommand.VolumeControl(VolumeAction.UP)
        }
        if (containsAny(text, "volume turun", "turunkan volume", "kecilkan volume", "volume down", "turn down", "quieter", "kurangi volume", "pelankan suara", "pelankan volume")) {
            return VoiceCommand.VolumeControl(VolumeAction.DOWN)
        }
        if (containsAny(text, "mute musik", "mute music", "mute volume", "matikan suara", "senyapkan musik")) {
            return VoiceCommand.VolumeControl(VolumeAction.MUTE)
        }

        // 3. Video playback / YouTube search (default: YouTube)
        parseVideoCommand(rawLower)?.let { return it }

        // 4. Music playback (default priority: Spotify -> YouTube Music -> Other Music -> YouTube)
        parseMusicCommand(rawLower)?.let { return it }

        // 5. Google Maps navigation & location search
        parseMapsCommand(rawLower)?.let { return it }

        // 5. Language switching
        if (containsAny(text, "bahasa inggris", "switch to english", "english language", "ganti inggris", "change to english")) {
            return VoiceCommand.SetLanguage(AppLanguage.EN)
        }
        if (containsAny(text, "bahasa indonesia", "switch to indonesian", "ganti indonesia", "indonesian language")) {
            return VoiceCommand.SetLanguage(AppLanguage.ID)
        }

        // 6. Alarm chime mute/unmute
        if (containsAny(text, "matikan alarm", "senyapkan alarm", "mute alarm", "bisukan alarm")) {
            return VoiceCommand.SetAlarmMute(true)
        }
        if (containsAny(text, "nyalakan alarm", "aktifkan alarm", "unmute alarm", "hidupkan alarm")) {
            return VoiceCommand.SetAlarmMute(false)
        }

        // 7. Vehicle status & maintenance queries
        if (containsAny(text, "kapan ganti oli", "jadwal servis", "cek servis", "cek service", "next service", "oil change", "maintenance status", "cek oli")) {
            return VoiceCommand.QueryStatus(StatusTopic.SERVICE)
        }
        if (containsAny(text, "suhu mesin", "cek coolant", "temperatur mesin", "coolant temp", "engine temp")) {
            return VoiceCommand.QueryStatus(StatusTopic.COOLANT)
        }
        if (containsAny(text, "cek aki", "cek baterai", "tegangan aki", "battery status", "check battery", "voltage")) {
            return VoiceCommand.QueryStatus(StatusTopic.BATTERY)
        }
        if (containsAny(text, "cek ban", "tekanan ban", "cek tpms", "tire pressure", "check tires", "check tyres")) {
            return VoiceCommand.QueryStatus(StatusTopic.TIRES)
        }
        if (containsAny(text, "ada error", "cek error", "check error", "fault code", "dtc")) {
            return VoiceCommand.QueryStatus(StatusTopic.ERRORS)
        }
        if (containsAny(text, "cek kondisi mobil", "status mobil", "kondisi mobil", "cek mobil", "car status", "vehicle status", "check car")) {
            return VoiceCommand.QueryStatus(StatusTopic.OVERVIEW)
        }

        // 8. RioSense UI navigation
        if (containsAny(text, "buka servis", "buka service", "open service", "open maintenance")) {
            return VoiceCommand.OpenControls(ControlsTab.SERVICE)
        }
        if (containsAny(text, "buka pengaturan", "buka setting", "open settings", "open setting")) {
            return VoiceCommand.OpenControls(ControlsTab.SETTINGS)
        }
        if (containsAny(text, "buka error", "open error")) {
            return VoiceCommand.OpenControls(ControlsTab.ERROR_LOG)
        }
        if (containsAny(text, "buka activity", "buka aktivitas", "open activity")) {
            return VoiceCommand.OpenControls(ControlsTab.ACTIVITY_LOG)
        }
        if (containsAny(text, "buka menu", "buka controls", "open controls", "open menu")) {
            return VoiceCommand.OpenControls(null)
        }
        if (containsAny(text, "tutup menu", "close menu", "kembali ke dashboard", "back to dashboard")) {
            return VoiceCommand.CloseMenus
        }
        if (containsAny(text, "kecilkan tampilan", "mode mini", "minimize", "perkecil")) {
            return VoiceCommand.MinimizeApp
        }

        // 9. Universal App Launcher ("buka youtube", "buka waze", "open netflix", etc.)
        parseOpenAppCommand(rawLower)?.let { return it }

        return VoiceCommand.Unknown(rawInput)
    }

    /**
     * Maps a Tier 2 On-Device Semantic AI intent ID to an executable [VoiceCommand]
     * using smart slot extraction for dynamic values (volume %, destination, music query).
     */
    fun mapSemanticIntent(intentId: String, rawInput: String): VoiceCommand {
        val rawLower = rawInput.trim().lowercase().replace(Regex("\\s+"), " ")
        val normalized = normalizeSpokenNumbers(rawLower)

        return when (intentId) {
            "VOLUME_UP" -> {
                // If the user said e.g. "suaranya kekecilan naikin ke 75 dong", honor the exact target!
                parseExactVolumePercent(normalized, requireVolumeKeyword = false)
                    ?.let { VoiceCommand.SetVolumePercent(it) }
                    ?: VoiceCommand.VolumeControl(VolumeAction.UP)
            }
            "VOLUME_DOWN" -> {
                parseExactVolumePercent(normalized, requireVolumeKeyword = false)
                    ?.let { VoiceCommand.SetVolumePercent(it) }
                    ?: VoiceCommand.VolumeControl(VolumeAction.DOWN)
            }
            "VOLUME_MUTE" -> VoiceCommand.VolumeControl(VolumeAction.MUTE)
            "SET_VOLUME_PERCENT" -> {
                val pct = parseExactVolumePercent(normalized, requireVolumeKeyword = false)
                if (pct != null) VoiceCommand.SetVolumePercent(pct)
                else VoiceCommand.VolumeControl(VolumeAction.UP)
            }
            "MEDIA_NEXT" -> VoiceCommand.MediaControl(MediaAction.NEXT)
            "MEDIA_PREVIOUS" -> VoiceCommand.MediaControl(MediaAction.PREVIOUS)
            "MEDIA_PAUSE" -> VoiceCommand.MediaControl(MediaAction.PAUSE)
            "MEDIA_PLAY" -> VoiceCommand.MediaControl(MediaAction.PLAY)
            "PLAY_SPOTIFY" -> {
                val query = extractNaturalMusicQuery(rawLower)
                val isPlaylist = rawLower.contains("playlist") || rawLower.contains("daftar putar") || rawLower.contains("santai")
                val pref = detectPreferredMusicApp(rawLower)
                if (query.isNotEmpty()) VoiceCommand.PlayMusic(query = query, isPlaylist = isPlaylist, preferredApp = pref)
                else VoiceCommand.OpenSpotify
            }
            "OPEN_SPOTIFY" -> VoiceCommand.OpenSpotify
            "NAVIGATE_MAPS" -> {
                // Guard: never hijack vehicle status questions or non-navigation sentences into Google Maps
                val hasNavCue = containsAny(
                    rawLower,
                    " ke ", " menuju ", "arahkan", "arahin", "navigasi", "rute", "jalan", "maps", "pergi",
                    " to ", "navigate", "directions", "drive", "take me"
                )
                if (!hasNavCue) return VoiceCommand.Unknown(rawInput)
                val dest = extractNaturalDestination(rawLower)
                if (dest.isNotEmpty()) VoiceCommand.NavigateMaps(query = dest, isSearchOnly = false)
                else VoiceCommand.OpenMaps
            }
            "SEARCH_MAPS" -> {
                val hasSearchCue = containsAny(
                    rawLower,
                    "cari", "spbu", "pom bensin", "bengkel", "restoran", "makan", "kafe", "masjid",
                    "atm", "parkir", "terdekat", "sekitar", "lokasi", "tempat", "maps",
                    "find", "search", "nearby", "nearest", "gas", "restaurant", "workshop", "parking"
                )
                if (!hasSearchCue) return VoiceCommand.Unknown(rawInput)
                val place = extractNaturalSearchPlace(rawLower)
                if (place.isNotEmpty()) VoiceCommand.NavigateMaps(query = place, isSearchOnly = true)
                else VoiceCommand.OpenMaps
            }
            "OPEN_MAPS" -> VoiceCommand.OpenMaps
            "STATUS_TIRES" -> VoiceCommand.QueryStatus(StatusTopic.TIRES)
            "STATUS_COOLANT" -> VoiceCommand.QueryStatus(StatusTopic.COOLANT)
            "STATUS_BATTERY" -> VoiceCommand.QueryStatus(StatusTopic.BATTERY)
            "STATUS_SERVICE" -> VoiceCommand.QueryStatus(StatusTopic.SERVICE)
            "STATUS_ERRORS" -> VoiceCommand.QueryStatus(StatusTopic.ERRORS)
            "STATUS_OVERVIEW" -> VoiceCommand.QueryStatus(StatusTopic.OVERVIEW)
            "OPEN_SERVICE" -> VoiceCommand.OpenControls(ControlsTab.SERVICE)
            "OPEN_SETTINGS" -> VoiceCommand.OpenControls(ControlsTab.SETTINGS)
            "OPEN_ERROR_LOG" -> VoiceCommand.OpenControls(ControlsTab.ERROR_LOG)
            "OPEN_ACTIVITY_LOG" -> VoiceCommand.OpenControls(ControlsTab.ACTIVITY_LOG)
            "OPEN_CONTROLS" -> VoiceCommand.OpenControls(null)
            "CLOSE_MENUS" -> VoiceCommand.CloseMenus
            "MINIMIZE_APP" -> VoiceCommand.MinimizeApp
            "ALARM_MUTE" -> VoiceCommand.SetAlarmMute(true)
            "ALARM_UNMUTE" -> VoiceCommand.SetAlarmMute(false)
            "LANG_EN" -> VoiceCommand.SetLanguage(AppLanguage.EN)
            "LANG_ID" -> VoiceCommand.SetLanguage(AppLanguage.ID)
            else -> VoiceCommand.Unknown(rawInput)
        }
    }

    /**
     * Normalizes common Indonesian colloquial suffixes (`-nya`) on automotive domain nouns
     * so "kondisi bannya bagaimana" -> "kondisi ban bagaimana" before lexical & semantic matching.
     */
    internal fun normalizeIndonesianColloquial(input: String): String {
        return input
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\bban\\s*nya\\b"), "ban")
            .replace(Regex("\\broda\\s*nya\\b"), "roda")
            .replace(Regex("\\baki\\s*nya\\b"), "aki")
            .replace(Regex("\\bbaterai\\s*nya\\b"), "baterai")
            .replace(Regex("\\bmesin\\s*nya\\b"), "mesin")
            .replace(Regex("\\bsuhu\\s*nya\\b"), "suhu")
            .replace(Regex("\\btemperatur\\s*nya\\b"), "temperatur")
            .replace(Regex("\\bcoolant\\s*nya\\b"), "coolant")
            .replace(Regex("\\boli\\s*nya\\b"), "oli")
            .replace(Regex("\\bservis\\s*nya\\b"), "servis")
            .replace(Regex("\\bservice\\s*nya\\b"), "service")
            .replace(Regex("\\bsuara\\s*nya\\b"), "suara")
            .replace(Regex("\\bvolume\\s*nya\\b"), "volume")
            .replace(Regex("\\blagu\\s*nya\\b"), "lagu")
            .replace(Regex("\\bmusik\\s*nya\\b"), "musik")
            .replace(Regex("\\bpengaturan\\s*nya\\b"), "pengaturan")
            .replace(Regex("\\bmenu\\s*nya\\b"), "menu")
            .replace(Regex("\\bmobil\\s*nya\\b"), "mobil")
    }

    /**
     * Converts Indonesian and English spoken number words (0..100) into digits
     * so commands like "suaranya jadiin tujuh puluh lima dong" or "set volume to eighty"
     * are parsed cleanly into exact numbers.
     */
    internal fun normalizeSpokenNumbers(input: String): String {
        var s = input

        // Exact 100
        s = s.replace(Regex("\\b(seratus|satu ratus|one hundred|a hundred|hundred)\\b"), "100")

        // Indonesian tens + units: e.g. "tujuh puluh lima" -> "75", "delapan puluh" -> "80"
        val idUnits = mapOf(
            "satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4,
            "lima" to 5, "enam" to 6, "tujuh" to 7, "delapan" to 8, "sembilan" to 9
        )
        val unitPattern = idUnits.keys.joinToString("|")

        s = Regex("\\b($unitPattern)\\s+puluh\\s+($unitPattern)\\b").replace(s) { m ->
            val tens = idUnits[m.groupValues[1]] ?: 0
            val ones = idUnits[m.groupValues[2]] ?: 0
            (tens * 10 + ones).toString()
        }
        s = Regex("\\b($unitPattern)\\s+puluh\\b").replace(s) { m ->
            val tens = idUnits[m.groupValues[1]] ?: 0
            (tens * 10).toString()
        }
        // Indonesian teens: "sebelas" -> "11", "lima belas" -> "15"
        s = s.replace(Regex("\\bsebelas\\b"), "11")
        s = s.replace(Regex("\\bsepuluh\\b"), "10")
        s = Regex("\\b($unitPattern)\\s+belas\\b").replace(s) { m ->
            val ones = idUnits[m.groupValues[1]] ?: 0
            (10 + ones).toString()
        }

        // English tens + units: e.g. "seventy five" -> "75", "eighty" -> "80"
        val enTens = mapOf(
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
            "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
        )
        val enOnes = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4,
            "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9
        )
        val enTensPat = enTens.keys.joinToString("|")
        val enOnesPat = enOnes.keys.joinToString("|")

        s = Regex("\\b($enTensPat)[\\s-]+($enOnesPat)\\b").replace(s) { m ->
            ((enTens[m.groupValues[1]] ?: 0) + (enOnes[m.groupValues[2]] ?: 0)).toString()
        }
        s = Regex("\\b($enTensPat)\\b").replace(s) { m ->
            (enTens[m.groupValues[1]] ?: 0).toString()
        }
        val enTeens = mapOf(
            "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
            "fourteen" to "14", "fifteen" to "15", "sixteen" to "16",
            "seventeen" to "17", "eighteen" to "18", "nineteen" to "19"
        )
        for ((word, digit) in enTeens) {
            s = s.replace(Regex("\\b$word\\b"), digit)
        }

        return s
    }

    private fun parseExactVolumePercent(text: String, requireVolumeKeyword: Boolean): Int? {
        if (requireVolumeKeyword && !containsAny(text, "volume", "suara", "sound", "audio")) return null

        // Named level shortcuts
        if (containsAny(text, "maksimal", "maksimum", "max", "full", "penuh")) return 100
        if (containsAny(text, "setengah", "half")) return 50
        if (containsAny(text, "nol", "zero")) return 0

        if (requireVolumeKeyword) {
            val match = Regex("(?:volume|suara|suaranya|sound|audio)[^0-9]{0,24}(\\d{1,3})\\s*(?:%|persen|percent)?").find(text)
                ?: Regex("(\\d{1,3})\\s*(?:%|persen|percent)[^a-z0-9]{0,8}(?:volume|suara|suaranya|sound|audio)").find(text)
            val value = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            return value.coerceIn(0, 100)
        } else {
            val anyNum = Regex("\\b(\\d{1,3})\\b").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            return anyNum.coerceIn(0, 100)
        }
    }

    private fun extractNaturalMusicQuery(text: String): String {
        val cleaned = text
            .replace(Regex("\\b(tolong|dong|nih|ya|deh|aja|saja|please|some|di|on|in|spotify|pengen|ingin|mau|dengerin|dengarkan|listen|to|putar|puterin|play|carikan|cari)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifEmpty { "chill driving" }
    }

    private fun extractNaturalDestination(text: String): String {
        val afterKe = Regex("\\b(?:ke|menuju|to)\\s+(.+)$").find(text)?.groupValues?.getOrNull(1)
        val raw = afterKe ?: text
        return raw
            .replace(Regex("\\b(tolong|dong|nih|ya|deh|please)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractNaturalSearchPlace(text: String): String {
        val cleaned = text
            .replace(Regex("\\b(tolong|dong|nih|ya|deh|please|carikan|cariin|cari|find|search|locate|me|a|for|di|maps|google)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned
    }

    private fun parseVideoCommand(text: String): VoiceCommand? {
        val videoRegexes = listOf(
            Regex("\\b(?:putar(?:kan|in)?|cari(?:kan|in)?|tonton|nonton|play|watch|search)\\s+(?:video\\s+)(.+)$"),
            Regex("\\b(?:putar(?:kan|in)?|cari(?:kan|in)?|tonton|nonton|play|watch|search)\\s+(.+?)\\s+(?:di|on|in)\\s+(?:youtube|yt)\\b")
        )
        for (regex in videoRegexes) {
            val raw = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!raw.isNullOrEmpty()) {
                // Skip if explicitly requesting YouTube Music (handled by parseMusicCommand)
                if (text.contains("youtube music") || text.contains("yt music")) continue
                val clean = raw
                    .replace(Regex("\\s+(?:di|on|in)\\s+(?:youtube|yt)$"), "")
                    .replace(Regex("\\b(tolong|dong|ya|deh|please)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (clean.isNotEmpty()) {
                    return VoiceCommand.PlayVideo(query = clean)
                }
            }
        }
        return null
    }

    private fun detectPreferredMusicApp(text: String): MusicAppPreference = when {
        text.contains("spotify") -> MusicAppPreference.SPOTIFY
        text.contains("youtube music") || text.contains("yt music") -> MusicAppPreference.YOUTUBE_MUSIC
        text.contains("youtube") || Regex("\\byt\\b").containsMatchIn(text) -> MusicAppPreference.YOUTUBE
        else -> MusicAppPreference.AUTO
    }

    private fun parseMusicCommand(text: String): VoiceCommand? {
        if (text == "buka spotify" || text == "open spotify") {
            return VoiceCommand.OpenSpotify
        }

        val isPlaylist = text.contains("playlist") || text.contains("daftar putar")
        val preferredApp = detectPreferredMusicApp(text)
        val musicTriggers = listOf(
            "putar musik playlist ",
            "putar lagu playlist ",
            "putarkan playlist ",
            "puterin playlist ",
            "putar playlist ",
            "play playlist ",
            "carikan musik ",
            "cariin musik ",
            "cari musik ",
            "carikan lagu ",
            "cariin lagu ",
            "cari lagu ",
            "putarkan musik ",
            "puterin musik ",
            "putar musik ",
            "putarkan lagu ",
            "puterin lagu ",
            "putar lagu ",
            "play song ",
            "play music ",
            "play ",
            "putar "
        )

        val matchedTrigger = musicTriggers.firstOrNull { text.contains(it) } ?: return null
        val after = text.substringAfter(matchedTrigger)
            .replace(Regex("\\s+(?:di|on|in|from|dari)\\s+(?:spotify|youtube\\s+music|yt\\s+music|youtube|yt)$"), "")
            .replace(Regex("\\s+(?:lalu|terus|dan|then|and)\\s+(?:play|putar)$"), "")
            .replace(Regex("^(?:spotify|youtube\\s+music|yt\\s+music)\\s+"), "")
            .replace(Regex("\\b(tolong|dong|ya|deh|please)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (after.isEmpty()) {
            return when (preferredApp) {
                MusicAppPreference.SPOTIFY -> VoiceCommand.OpenSpotify
                MusicAppPreference.YOUTUBE_MUSIC -> VoiceCommand.OpenApp("youtube music")
                MusicAppPreference.YOUTUBE -> VoiceCommand.OpenApp("youtube")
                MusicAppPreference.AUTO -> null
            }
        }

        val explicitMusicContext = preferredApp != MusicAppPreference.AUTO ||
            text.contains("musik") || text.contains("music") ||
            text.contains("lagu") || text.contains("song") || isPlaylist
        if (!explicitMusicContext && (matchedTrigger == "play " || matchedTrigger == "putar ")) {
            return null
        }

        return VoiceCommand.PlayMusic(
            query = after,
            isPlaylist = isPlaylist,
            preferredApp = preferredApp
        )
    }

    private fun parseOpenAppCommand(text: String): VoiceCommand? {
        val match = Regex("^(?:tolong\\s+)?(?:buka(?:kan|in)?|jalankan|open|launch|start)\\s+(?:aplikasi\\s+|app\\s+)?(.+)$")
            .find(text) ?: return null
        val rawName = match.groupValues[1]
            .replace(Regex("\\b(dong|ya|deh|aja|saja|please)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (rawName.isEmpty()) return null
        return VoiceCommand.OpenApp(appName = rawName)
    }

    private fun parseMapsCommand(text: String): VoiceCommand? {
        if (containsAny(text, "buka maps", "buka google maps", "open maps", "open google maps") &&
            !containsAny(text, " ke ", " menuju ", " cari", " to ", " search", " find")
        ) {
            return VoiceCommand.OpenMaps
        }

        // 1. Universal Navigation patterns (ID + EN):
        //    e.g. "rute ke mall pakuwon jogja", "rute tercepat ke bandara yia",
        //    "ayo jalan ke coffie merapi", "ayo kita ke merapi view dengan rute tercepat",
        //    "arah ke madiun via tol", "arah ke ponorogo via wonogiri", "mau ke solo"
        val navRegexes = listOf(
            Regex("\\b(?:arahkan|arahin|arah|navigasi|rute(?:\\s+(?:tercepat|terdekat|cepat|alternatif))?|jalan|pergi|berangkat|antar(?:kan|in)?|tunjukkan\\s+jalan|buka\\s+(?:google\\s+)?maps|ayo(?:\\s+kita)?|yuk(?:\\s+kita)?|kita|mau|gas|otw)\\s+(?:ke|menuju)\\s+(.+)$"),
            Regex("\\bmenuju\\s+(?:ke\\s+)?(.+)$"),
            Regex("\\b(?:navigate|directions|fastest\\s+route|route|drive|go|take\\s+me|head|let'?s\\s+go)\\s+to\\s+(.+)$")
        )
        for (regex in navRegexes) {
            val rawDest = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!rawDest.isNullOrEmpty()) {
                val parsed = parseDestinationModifiers(rawDest)
                if (parsed.destination.isNotEmpty()) {
                    return VoiceCommand.NavigateMaps(
                        query = parsed.destination,
                        isSearchOnly = false,
                        waypoint = parsed.waypoint,
                        avoidTolls = parsed.avoidTolls
                    )
                }
            }
        }

        // 2. Universal Place Search patterns (any place, not limited to a hardcoded list):
        //    e.g. "cari warung nasi pecel terdekat", "cari indomaret terdekat",
        //    "cariin apotek", "find nearby coffee shop", "indomaret terdekat"
        val searchRegexes = listOf(
            Regex("\\b(?:carikan|cariin|cari|temukan)\\s+(?:lokasi\\s+|tempat\\s+|di\\s+(?:google\\s+)?maps\\s+)?(.+)$"),
            Regex("\\b(?:search\\s+(?:google\\s+)?maps\\s+(?:for\\s+)?|find\\s+(?:me\\s+)?(?:a\\s+)?|locate\\s+)(.+)$"),
            Regex("\\b(?:di\\s*mana|ada)\\s+(.+?\\s+terdekat)\\b"),
            Regex("^(.+?\\s+(?:terdekat|di\\s+sekitar\\s+sini|nearby|nearest))$")
        )
        for (regex in searchRegexes) {
            val rawQuery = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!rawQuery.isNullOrEmpty()) {
                // Avoid hijacking car-diagnostic or music queries
                if (containsAny(rawQuery, "lagu", "musik", "song", "music", "playlist", "error", "fault", "dtc")) {
                    continue
                }
                val cleanQuery = rawQuery
                    .replace(Regex("\\b(tolong|dong|nih|ya|deh|aja|saja|please|di\\s+maps|di\\s+google\\s+maps)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (cleanQuery.isNotEmpty()) {
                    return VoiceCommand.NavigateMaps(query = cleanQuery, isSearchOnly = true)
                }
            }
        }

        return null
    }

    private data class DestinationParts(
        val destination: String,
        val waypoint: String?,
        val avoidTolls: Boolean
    )

    private fun parseDestinationModifiers(raw: String): DestinationParts {
        var s = raw
            .replace(Regex("\\b(dengan|pakai|pake|lewat|menggunakan)\\s+rute\\s+(tercepat|cepat|terdekat)\\b"), " ")
            .replace(Regex("\\brute\\s+(tercepat|cepat)\\b"), " ")
            .replace(Regex("\\b(tolong|dong|nih|ya|deh|aja|saja|please)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        var avoidTolls = false
        if (Regex("\\b(tanpa\\s+tol|hindari\\s+tol|jangan\\s+lewat\\s+tol|non\\s+tol|avoid\\s+tolls?)\\b").containsMatchIn(s)) {
            avoidTolls = true
            s = s.replace(Regex("\\b(tanpa\\s+tol|hindari\\s+tol|jangan\\s+lewat\\s+tol|non\\s+tol|avoid\\s+tolls?)\\b"), " ")
        }

        // "via tol" / "lewat tol" -> Google Maps defaults to toll routes when fastest, so strip it from the address query
        s = s.replace(Regex("\\b(via|lewat|melalui)\\s+(jalan\\s+)?tol\\b"), " ")

        // Optional waypoint city/area: e.g. "ponorogo via wonogiri" or "bandung lewat puncak"
        var waypoint: String? = null
        val viaMatch = Regex("^(.+?)\\s+(?:via|lewat|melalui)\\s+(.+)$").find(s.trim())
        if (viaMatch != null) {
            val destPart = viaMatch.groupValues[1].trim()
            val wpPart = viaMatch.groupValues[2].trim()
            if (destPart.isNotEmpty() && wpPart.isNotEmpty()) {
                s = destPart
                waypoint = wpPart
            }
        }

        val cleanDest = s.replace(Regex("\\s+"), " ").trim()
        return DestinationParts(
            destination = cleanDest,
            waypoint = waypoint,
            avoidTolls = avoidTolls
        )
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean =
        phrases.any { text.contains(it) }
}
