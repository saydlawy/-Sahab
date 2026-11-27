package com.saydlawy.sahab.feature.torrent.domain

object StreamMetadataParser {
    private val sizeRegex = Regex("""(?i)(\d+(?:\.\d+)?)\s*(TB|GB|GiB|MB|MiB)""")
    private val seederRegex = Regex("""(?:👤|🌱|S:|Seeds?:)\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val cachedRegex = Regex("""(?i)(\[(?:RD|AD|TB|PM|DL)\+\]|⚡|instant\s*cached)""")

    fun parse(
        id: String,
        addonName: String,
        nameField: String?,
        titleField: String?,
        directUrl: String?,
        infoHash: String?,
        fileIdx: Int?,
        trackers: List<String>,
    ): TorrentStreamCandidate {
        val combined = listOfNotNull(nameField, titleField).joinToString(" \n ").trim()
        val upper = combined.uppercase()

        val (res, rank) = when {
            "2160P" in upper || "4K" in upper || "UHD" in upper -> "4K" to 4
            "1080P" in upper || "FHD" in upper -> "1080p" to 3
            "720P" in upper -> "720p" to 2
            "480P" in upper || "SD" in upper -> "480p" to 1
            else -> "" to 0
        }

        val source = when {
            "REMUX" in upper -> "REMUX"
            "BLURAY" in upper || "BLU-RAY" in upper || "BDRIP" in upper || "BRRIP" in upper -> "BluRay"
            "WEB-DL" in upper || "WEBDL" in upper -> "WEB-DL"
            "WEBRIP" in upper -> "WEBRip"
            "HDRIP" in upper -> "HDRip"
            else -> ""
        }

        val hdr = when {
            "DV" in upper.split(' ', '.', '|', '[', ']', '(', ')') || "DOLBY VISION" in upper || "DOVI" in upper -> "DV"
            "HDR10+" in upper -> "HDR10+"
            "HDR10" in upper || "HDR" in upper -> "HDR"
            else -> ""
        }

        val audio = when {
            "ATMOS" in upper -> "Atmos"
            "TRUEHD" in upper -> "TrueHD"
            "DTS-HD" in upper || "DTSHD" in upper -> "DTS-HD"
            "DTS" in upper -> "DTS"
            "EAC3" in upper || "DDP" in upper || "DD+" in upper -> "DD+"
            "AC3" in upper || "DD5.1" in upper -> "AC3"
            "AAC" in upper -> "AAC"
            else -> ""
        }

        val sizeGb = sizeRegex.find(combined)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let null
            when (m.groupValues[2].uppercase()) {
                "TB" -> v * 1024.0
                "GB", "GIB" -> v
                "MB", "MIB" -> v / 1024.0
                else -> null
            }
        }

        val seeders = seederRegex.find(combined)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val cached = (!directUrl.isNullOrBlank() && directUrl.startsWith("http")) || cachedRegex.containsMatchIn(combined)
        val cleanTitle = (titleField ?: nameField).orEmpty()
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

        return TorrentStreamCandidate(
            id = id,
            addonName = addonName,
            rawTitle = cleanTitle.ifBlank { addonName },
            directUrl = directUrl?.trim()?.takeIf { it.isNotBlank() },
            infoHash = infoHash?.trim()?.lowercase()?.takeIf { it.length >= 32 },
            fileIdx = fileIdx,
            trackers = trackers,
            resolution = res,
            resolutionRank = rank,
            sourceTag = source,
            hdrTag = hdr,
            audioTag = audio,
            sizeGb = sizeGb,
            seeders = seeders,
            isInstantCached = cached,
        )
    }

    fun sortCandidates(items: List<TorrentStreamCandidate>): List<TorrentStreamCandidate> =
        items.sortedWith(
            compareByDescending<TorrentStreamCandidate> { it.isInstantCached }
                .thenByDescending { it.resolutionRank }
                .thenByDescending { it.seeders ?: -1 }
                .thenBy { it.sizeGb ?: 999.0 },
        )
}
