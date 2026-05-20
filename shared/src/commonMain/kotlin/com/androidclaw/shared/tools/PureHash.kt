package com.androidclaw.shared.tools

/**
 * Pure-Kotlin implementations of common cryptographic hashes for KMP.
 * Returns lowercase hex digests.
 *
 * Note: MD5 and SHA-1 are provided for compatibility / non-security uses
 * (checksums, cache keys). Use SHA-256 for security-sensitive hashing.
 */
internal object PureHash {

    fun digestHex(algorithm: String, bytes: ByteArray): String? = when (algorithm.uppercase()) {
        "MD5" -> md5(bytes).toHex()
        "SHA-1", "SHA1" -> sha1(bytes).toHex()
        "SHA-256", "SHA256" -> sha256(bytes).toHex()
        else -> null
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0xF])
        }
        return sb.toString()
    }

    // -------- MD5 (RFC 1321) --------
    private val MD5_S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    )

    private val MD5_K = intArrayOf(
        -0x28955b88, -0x173848aa, 0x242070db, -0x3e423112,
        -0xa83f051, 0x4787c62a, -0x57cfb9ed, -0x2b96aff,
        0x698098d8, -0x74bb0851, -0xa44f, -0x76a32842,
        0x6b901122, -0x2678e6d, -0x5986bc72, 0x49b40821,
        -0x9e1da9e, -0x3fbf4cc0, 0x265e5a51, -0x16493856,
        -0x29d0efa3, 0x02441453, -0x275e197f, -0x182c0438,
        0x21e1cde6, -0x3cc8f82a, -0xb2af279, 0x455a14ed,
        -0x561c16fb, -0x3105c08, 0x676f02d9, -0x72d5b376,
        -0x5c6be, -0x788e097f, 0x6d9d6122, -0x21ac7f4,
        -0x5b4115bc, 0x4bdecfa9, -0x944b4a0, -0x41404390,
        0x289b7ec6, -0x155ed806, -0x2b10cf7b, 0x04881d05,
        -0x262b2fc7, -0x1924661b, 0x1fa27cf8, -0x3b53a99b,
        -0xbd6ddbc, 0x432aff97, -0x546bdc59, -0x36c5fc7,
        0x655b59c3, -0x70f3336e, -0x100b83, -0x7a7ba22f,
        0x6fa87e4f, -0x1d31920, -0x5cfebcec, 0x4e0811a1,
        -0x8ac817e, -0x42c50dcb, 0x2ad7d2bb, -0x14792c6f
    )

    fun md5(input: ByteArray): ByteArray {
        var a0 = 0x67452301
        var b0 = -0x10325477  // 0xefcdab89
        var c0 = -0x67452302  // 0x98badcfe
        var d0 = 0x10325476

        // Pad message: append 0x80, then zeros, then 64-bit little-endian bit length.
        val origLenBits = input.size.toLong() * 8
        val withOne = input + byteArrayOf(0x80.toByte())
        val padLen = ((56 - withOne.size % 64) + 64) % 64
        val padded = withOne + ByteArray(padLen) + ByteArray(8).also {
            for (i in 0..7) it[i] = (origLenBits ushr (8 * i)).toByte()
        }

        val m = IntArray(16)
        var chunk = 0
        while (chunk < padded.size) {
            for (i in 0..15) {
                val off = chunk + i * 4
                m[i] = (padded[off].toInt() and 0xFF) or
                    ((padded[off + 1].toInt() and 0xFF) shl 8) or
                    ((padded[off + 2].toInt() and 0xFF) shl 16) or
                    ((padded[off + 3].toInt() and 0xFF) shl 24)
            }
            var a = a0; var b = b0; var c = c0; var d = d0
            for (i in 0..63) {
                var f: Int
                var g: Int
                when {
                    i < 16 -> { f = (b and c) or (b.inv() and d); g = i }
                    i < 32 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    i < 48 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                val temp = d
                d = c
                c = b
                val sum = a + f + MD5_K[i] + m[g]
                b += (sum shl MD5_S[i]) or (sum ushr (32 - MD5_S[i]))
                a = temp
            }
            a0 += a; b0 += b; c0 += c; d0 += d
            chunk += 64
        }

        val out = ByteArray(16)
        intArrayOf(a0, b0, c0, d0).forEachIndexed { i, v ->
            for (j in 0..3) out[i * 4 + j] = (v ushr (8 * j)).toByte()
        }
        return out
    }

    // -------- SHA-1 (RFC 3174) --------
    fun sha1(input: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = -0x10325477   // 0xefcdab89
        var h2 = -0x67452302   // 0x98badcfe
        var h3 = 0x10325476
        var h4 = -0x3c2d1e10   // 0xc3d2e1f0

        val origLenBits = input.size.toLong() * 8
        val withOne = input + byteArrayOf(0x80.toByte())
        val padLen = ((56 - withOne.size % 64) + 64) % 64
        val padded = withOne + ByteArray(padLen) + ByteArray(8).also {
            for (i in 0..7) it[i] = (origLenBits ushr (8 * (7 - i))).toByte()
        }

        val w = IntArray(80)
        var chunk = 0
        while (chunk < padded.size) {
            for (i in 0..15) {
                val off = chunk + i * 4
                w[i] = ((padded[off].toInt() and 0xFF) shl 24) or
                    ((padded[off + 1].toInt() and 0xFF) shl 16) or
                    ((padded[off + 2].toInt() and 0xFF) shl 8) or
                    (padded[off + 3].toInt() and 0xFF)
            }
            for (i in 16..79) {
                val v = w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]
                w[i] = (v shl 1) or (v ushr 31)
            }
            var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
            for (i in 0..79) {
                val (f, k) = when {
                    i < 20 -> Pair((b and c) or (b.inv() and d), 0x5A827999)
                    i < 40 -> Pair(b xor c xor d, 0x6ED9EBA1)
                    i < 60 -> Pair((b and c) or (b and d) or (c and d), -0x70e44324) // 0x8F1BBCDC
                    else -> Pair(b xor c xor d, -0x359d3e2a) // 0xCA62C1D6
                }
                val temp = ((a shl 5) or (a ushr 27)) + f + e + k + w[i]
                e = d; d = c; c = (b shl 30) or (b ushr 2); b = a; a = temp
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
            chunk += 64
        }

        val out = ByteArray(20)
        intArrayOf(h0, h1, h2, h3, h4).forEachIndexed { i, v ->
            for (j in 0..3) out[i * 4 + j] = (v ushr (8 * (3 - j))).toByte()
        }
        return out
    }

    // -------- SHA-256 (FIPS 180-4) --------
    private val SHA256_K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e
    )

    fun sha256(input: ByteArray): ByteArray {
        var h0 = 0x6a09e667
        var h1 = -0x4498517b  // 0xbb67ae85
        var h2 = 0x3c6ef372
        var h3 = -0x5ab00ac6  // 0xa54ff53a
        var h4 = 0x510e527f
        var h5 = -0x64fa9774  // 0x9b05688c
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19

        val origLenBits = input.size.toLong() * 8
        val withOne = input + byteArrayOf(0x80.toByte())
        val padLen = ((56 - withOne.size % 64) + 64) % 64
        val padded = withOne + ByteArray(padLen) + ByteArray(8).also {
            for (i in 0..7) it[i] = (origLenBits ushr (8 * (7 - i))).toByte()
        }

        val w = IntArray(64)
        var chunk = 0
        while (chunk < padded.size) {
            for (i in 0..15) {
                val off = chunk + i * 4
                w[i] = ((padded[off].toInt() and 0xFF) shl 24) or
                    ((padded[off + 1].toInt() and 0xFF) shl 16) or
                    ((padded[off + 2].toInt() and 0xFF) shl 8) or
                    (padded[off + 3].toInt() and 0xFF)
            }
            for (i in 16..63) {
                val s0 = ((w[i - 15] ushr 7) or (w[i - 15] shl 25)) xor
                    ((w[i - 15] ushr 18) or (w[i - 15] shl 14)) xor (w[i - 15] ushr 3)
                val s1 = ((w[i - 2] ushr 17) or (w[i - 2] shl 15)) xor
                    ((w[i - 2] ushr 19) or (w[i - 2] shl 13)) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (i in 0..63) {
                val s1 = ((e ushr 6) or (e shl 26)) xor ((e ushr 11) or (e shl 21)) xor ((e ushr 25) or (e shl 7))
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + SHA256_K[i] + w[i]
                val s0 = ((a ushr 2) or (a shl 30)) xor ((a ushr 13) or (a shl 19)) xor ((a ushr 22) or (a shl 10))
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                h = g; g = f; f = e; e = d + temp1
                d = c; c = b; b = a; a = temp1 + temp2
            }
            h0 += a; h1 += b; h2 += c; h3 += d
            h4 += e; h5 += f; h6 += g; h7 += h
            chunk += 64
        }

        val out = ByteArray(32)
        intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).forEachIndexed { i, v ->
            for (j in 0..3) out[i * 4 + j] = (v ushr (8 * (3 - j))).toByte()
        }
        return out
    }
}
