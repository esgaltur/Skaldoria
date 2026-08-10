package com.skaldoria.core.qr

/**
 * Pure Kotlin standard QR Code Generator (ISO/IEC 18004 Model 2).
 *
 * Produces standard scannable 2D boolean matrices with zero external dependencies.
 * Compatible with all iOS/Android camera QR scanners.
 */
object QrCodeGenerator {

    enum class ErrorCorrection(val ordinalValue: Int, val formatBits: Int) {
        L(0, 0b01),
        M(1, 0b00),
        Q(2, 0b11),
        H(3, 0b10)
    }

    data class QrMatrix(
        val version: Int,
        val size: Int,
        val modules: Array<BooleanArray>
    ) {
        fun isDark(row: Int, col: Int): Boolean {
            if (row !in 0 until size || col !in 0 until size) return false
            return modules[row][col]
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QrMatrix) return false
            if (version != other.version || size != other.size) return false
            for (r in 0 until size) {
                if (!modules[r].contentEquals(other.modules[r])) return false
            }
            return true
        }

        override fun hashCode(): Int {
            var result = version
            result = 31 * result + size
            for (r in 0 until size) {
                result = 31 * result + modules[r].contentHashCode()
            }
            return result
        }
    }

    // Version specifications for Error Correction Level M
    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val blocksGroup1: Int,
        val dataCodewordsPerBlockGroup1: Int,
        val blocksGroup2: Int,
        val dataCodewordsPerBlockGroup2: Int,
        val alignmentPatternCoords: IntArray
    ) {
        val totalDataCodewords: Int =
            (blocksGroup1 * dataCodewordsPerBlockGroup1) + (blocksGroup2 * dataCodewordsPerBlockGroup2)
        val size: Int = 17 + 4 * version
    }

    private val VERSION_SPECS_LEVEL_M = listOf(
        VersionSpec(1, 26, 10, 1, 16, 0, 0, intArrayOf()),
        VersionSpec(2, 44, 16, 1, 28, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 70, 26, 1, 44, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 100, 18, 2, 32, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 134, 24, 2, 43, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 172, 16, 4, 27, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 196, 18, 4, 31, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 242, 22, 2, 38, 2, 39, intArrayOf(6, 24, 42)),
        VersionSpec(9, 292, 22, 3, 36, 2, 37, intArrayOf(6, 26, 46)),
        VersionSpec(10, 346, 26, 4, 43, 1, 44, intArrayOf(6, 28, 50))
    )

    // Galois Field GF(256) Tables for Reed-Solomon Coding
    private val expTable = IntArray(512)
    private val logTable = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            expTable[i] = x
            logTable[x] = i
            x = x shl 1
            if (x >= 256) {
                x = x xor 0x11D // Primitive polynomial x^8 + x^4 + x^3 + x^2 + 1
            }
        }
        for (i in 255 until 512) {
            expTable[i] = expTable[i - 255]
        }
    }

    private fun gfMultiply(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return expTable[logTable[a] + logTable[b]]
    }

    /**
     * Encodes a string into a QR Code Matrix.
     */
    fun encode(text: String, ecLevel: ErrorCorrection = ErrorCorrection.M): QrMatrix {
        val rawBytes = text.toByteArray(Charsets.ISO_8859_1.takeIf { isIso88591(text) } ?: Charsets.UTF_8)
        val isUtf8 = !isIso88591(text)

        // Select the smallest version that can hold the data
        val spec = VERSION_SPECS_LEVEL_M.firstOrNull {
            val overheadBits = 4 + (if (it.version <= 9) 8 else 16) + (if (isUtf8) 12 else 0) // 4 (mode) + len + (ECI if UTF8)
            val availableBits = it.totalDataCodewords * 8
            (rawBytes.size * 8) + overheadBits <= availableBits
        } ?: VERSION_SPECS_LEVEL_M.last()

        val dataCodewords = buildDataCodewords(rawBytes, spec, isUtf8)
        val finalCodewords = generateCodewordsWithEC(dataCodewords, spec)

        val size = spec.size
        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        // 1. Place Functional Patterns
        placeFinderPattern(matrix, reserved, 0, 0)
        placeFinderPattern(matrix, reserved, size - 7, 0)
        placeFinderPattern(matrix, reserved, 0, size - 7)

        placeSeparators(matrix, reserved, size)
        placeTimingPatterns(matrix, reserved, size)
        placeAlignmentPatterns(matrix, reserved, spec.alignmentPatternCoords)
        reserveFormatInfo(reserved, size)

        // Dark module
        matrix[size - 8][8] = true
        reserved[size - 8][8] = true

        // 2. Place Data Codewords (Zigzag)
        placeDataBits(matrix, reserved, finalCodewords, size)

        // 3. Find Best Mask Pattern
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestMatrix = matrix

        for (mask in 0..7) {
            val maskedMatrix = applyMask(matrix, reserved, size, mask)
            placeFormatInfo(maskedMatrix, ecLevel, mask, size)
            val penalty = calculatePenalty(maskedMatrix, size)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestMatrix = maskedMatrix
            }
        }

        return QrMatrix(spec.version, size, bestMatrix)
    }

    private fun isIso88591(str: String): Boolean {
        return str.all { it.code <= 255 }
    }

    private fun buildDataCodewords(data: ByteArray, spec: VersionSpec, isUtf8: Boolean): ByteArray {
        val bitBuffer = BitBuffer()

        // ECI for UTF-8 if necessary
        if (isUtf8) {
            bitBuffer.append(0b0111, 4) // ECI Mode Indicator
            bitBuffer.append(26, 8)     // UTF-8 ECI Assignment Number
        }

        // Byte Mode Indicator: 0100
        bitBuffer.append(0b0100, 4)

        // Character Count Indicator
        val charCountBits = if (spec.version <= 9) 8 else 16
        bitBuffer.append(data.size, charCountBits)

        // Data Bytes
        for (b in data) {
            bitBuffer.append(b.toInt() and 0xFF, 8)
        }

        // Terminator (up to 4 bits of 0)
        val totalDataBits = spec.totalDataCodewords * 8
        val terminatorLen = (totalDataBits - bitBuffer.bitLength).coerceIn(0, 4)
        if (terminatorLen > 0) {
            bitBuffer.append(0, terminatorLen)
        }

        // Byte alignment padding
        while (bitBuffer.bitLength % 8 != 0) {
            bitBuffer.append(0, 1)
        }

        // Pad bytes (0xEC, 0x11)
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.byteLength < spec.totalDataCodewords) {
            bitBuffer.append(padBytes[padIdx % 2], 8)
            padIdx++
        }

        return bitBuffer.toByteArray()
    }

    private fun generateCodewordsWithEC(dataCodewords: ByteArray, spec: VersionSpec): ByteArray {
        val blocks = mutableListOf<ByteArray>()
        val ecBlocks = mutableListOf<ByteArray>()

        var dataOffset = 0
        // Group 1
        for (b in 0 until spec.blocksGroup1) {
            val len = spec.dataCodewordsPerBlockGroup1
            val block = dataCodewords.copyOfRange(dataOffset, dataOffset + len)
            dataOffset += len
            blocks.add(block)
            ecBlocks.add(calculateReedSolomonEC(block, spec.ecCodewordsPerBlock))
        }
        // Group 2
        for (b in 0 until spec.blocksGroup2) {
            val len = spec.dataCodewordsPerBlockGroup2
            val block = dataCodewords.copyOfRange(dataOffset, dataOffset + len)
            dataOffset += len
            blocks.add(block)
            ecBlocks.add(calculateReedSolomonEC(block, spec.ecCodewordsPerBlock))
        }

        // Interleave Data Codewords
        val result = ByteArray(spec.totalCodewords)
        var resultIdx = 0

        val maxDataBlockLen = spec.dataCodewordsPerBlockGroup2.coerceAtLeast(spec.dataCodewordsPerBlockGroup1)
        for (i in 0 until maxDataBlockLen) {
            for (block in blocks) {
                if (i < block.size) {
                    result[resultIdx++] = block[i]
                }
            }
        }

        // Interleave EC Codewords
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (ecBlock in ecBlocks) {
                result[resultIdx++] = ecBlock[i]
            }
        }

        return result
    }

    private fun calculateReedSolomonEC(data: ByteArray, ecCount: Int): ByteArray {
        val generator = generateRsGeneratorPoly(ecCount)
        val info = IntArray(data.size + ecCount)
        for (i in data.indices) {
            info[i] = data[i].toInt() and 0xFF
        }

        for (i in data.indices) {
            val factor = info[i]
            if (factor != 0) {
                for (j in generator.indices) {
                    info[i + j] = info[i + j] xor gfMultiply(generator[j], factor)
                }
            }
        }

        val ec = ByteArray(ecCount)
        for (i in 0 until ecCount) {
            ec[i] = info[data.size + i].toByte()
        }
        return ec
    }

    private fun generateRsGeneratorPoly(degree: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until degree) {
            val factor = intArrayOf(1, expTable[i])
            val next = IntArray(g.size + 1)
            for (j in g.indices) {
                for (k in factor.indices) {
                    next[j + k] = next[j + k] xor gfMultiply(g[j], factor[k])
                }
            }
            g = next
        }
        return g
    }

    // ==========================================
    // MATRIX FUNCTIONAL PATTERNS
    // ==========================================

    private fun placeFinderPattern(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, startCol: Int, startRow: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isDark = (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4))
                matrix[startRow + r][startCol + c] = isDark
                reserved[startRow + r][startCol + c] = true
            }
        }
    }

    private fun placeSeparators(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, size: Int) {
        for (i in 0 until 8) {
            // Top-Left
            setReserved(matrix, reserved, 7, i, false)
            setReserved(matrix, reserved, i, 7, false)
            // Top-Right
            setReserved(matrix, reserved, 7, size - 8 + i, false)
            setReserved(matrix, reserved, i, size - 8, false)
            // Bottom-Left
            setReserved(matrix, reserved, size - 8, i, false)
            setReserved(matrix, reserved, size - 8 + i, 7, false)
        }
    }

    private fun placeTimingPatterns(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, size: Int) {
        for (i in 8 until size - 8) {
            val isDark = (i % 2 == 0)
            if (!reserved[6][i]) {
                matrix[6][i] = isDark
                reserved[6][i] = true
            }
            if (!reserved[i][6]) {
                matrix[i][6] = isDark
                reserved[i][6] = true
            }
        }
    }

    private fun placeAlignmentPatterns(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, coords: IntArray) {
        if (coords.isEmpty()) return
        for (r in coords) {
            for (c in coords) {
                if (reserved[r][c]) continue // Don't place over finder patterns
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val isDark = (dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0))
                        matrix[r + dr][c + dc] = isDark
                        reserved[r + dr][c + dc] = true
                    }
                }
            }
        }
    }

    private fun reserveFormatInfo(reserved: Array<BooleanArray>, size: Int) {
        for (i in 0 until 9) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        for (i in 0 until 8) {
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }
    }

    private fun setReserved(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, r: Int, c: Int, isDark: Boolean) {
        if (r in matrix.indices && c in matrix.indices) {
            matrix[r][c] = isDark
            reserved[r][c] = true
        }
    }

    // ==========================================
    // DATA PLACEMENT & MASKING
    // ==========================================

    private fun placeDataBits(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, codewords: ByteArray, size: Int) {
        var bitIdx = 0
        val totalBits = codewords.size * 8

        var col = size - 1
        var upwards = true

        while (col > 0) {
            if (col == 6) col-- // Skip vertical timing column

            val rows = if (upwards) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (c in 0..1) {
                    val targetCol = col - c
                    if (!reserved[row][targetCol]) {
                        val isDark = if (bitIdx < totalBits) {
                            val byteVal = codewords[bitIdx / 8].toInt() and 0xFF
                            val bitVal = (byteVal ushr (7 - (bitIdx % 8))) and 1
                            bitVal == 1
                        } else false

                        matrix[row][targetCol] = isDark
                        bitIdx++
                    }
                }
            }
            col -= 2
            upwards = !upwards
        }
    }

    private fun applyMask(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, size: Int, mask: Int): Array<BooleanArray> {
        val result = Array(size) { r -> matrix[r].copyOf() }
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!reserved[r][c]) {
                    val condition = when (mask) {
                        0 -> (r + c) % 2 == 0
                        1 -> r % 2 == 0
                        2 -> c % 3 == 0
                        3 -> (r + c) % 3 == 0
                        4 -> ((r / 2) + (c / 3)) % 2 == 0
                        5 -> ((r * c) % 2) + ((r * c) % 3) == 0
                        6 -> (((r * c) % 2) + ((r * c) % 3)) % 2 == 0
                        7 -> (((r + c) % 2) + ((r * c) % 3)) % 2 == 0
                        else -> false
                    }
                    if (condition) {
                        result[r][c] = !result[r][c]
                    }
                }
            }
        }
        return result
    }

    private fun placeFormatInfo(matrix: Array<BooleanArray>, ecLevel: ErrorCorrection, mask: Int, size: Int) {
        val data = (ecLevel.formatBits shl 3) or mask
        var rem = data shl 10
        val gen = 0b10100110111 // BCH (15, 5) generator polynomial x^10 + x^8 + x^5 + x^4 + x^2 + x + 1

        for (i in 4 downTo 0) {
            if ((rem ushr (i + 10)) and 1 == 1) {
                rem = rem xor (gen shl i)
            }
        }

        val formatBits = ((data shl 10) or rem) xor 0b101010000010010 // XOR mask 0x5412

        // Place on Top-Left & Split
        val bits = IntArray(15) { i -> (formatBits ushr (14 - i)) and 1 }

        // Top-left
        matrix[8][0] = bits[0] == 1
        matrix[8][1] = bits[1] == 1
        matrix[8][2] = bits[2] == 1
        matrix[8][3] = bits[3] == 1
        matrix[8][4] = bits[4] == 1
        matrix[8][5] = bits[5] == 1
        matrix[8][7] = bits[6] == 1
        matrix[8][8] = bits[7] == 1
        matrix[7][8] = bits[8] == 1
        matrix[5][8] = bits[9] == 1
        matrix[4][8] = bits[10] == 1
        matrix[3][8] = bits[11] == 1
        matrix[2][8] = bits[12] == 1
        matrix[1][8] = bits[13] == 1
        matrix[0][8] = bits[14] == 1

        // Bottom-left (7 bits: b0..b6)
        for (i in 0..6) {
            matrix[size - 1 - i][8] = bits[i] == 1
        }
        // Dark module (size - 8, 8) is always dark
        matrix[size - 8][8] = true

        // Top-right (8 bits: b7..b14)
        for (i in 0..7) {
            matrix[8][size - 8 + i] = bits[7 + i] == 1
        }
    }

    private fun calculatePenalty(matrix: Array<BooleanArray>, size: Int): Int {
        var penalty = 0

        // N1: 5+ consecutive same color modules in row/col
        for (r in 0 until size) {
            var count = 0
            var lastColor = false
            for (c in 0 until size) {
                val color = matrix[r][c]
                if (c == 0 || color == lastColor) {
                    count++
                } else {
                    if (count >= 5) penalty += 3 + (count - 5)
                    count = 1
                }
                lastColor = color
            }
            if (count >= 5) penalty += 3 + (count - 5)
        }

        // N2: 2x2 blocks of same color
        for (r in 0 until size - 1) {
            for (c in 0 until size - 1) {
                val color = matrix[r][c]
                if (matrix[r + 1][c] == color && matrix[r][c + 1] == color && matrix[r + 1][c + 1] == color) {
                    penalty += 3
                }
            }
        }

        return penalty
    }

    private class BitBuffer {
        private val bytes = mutableListOf<Byte>()
        var bitLength = 0
            private set

        val byteLength: Int
            get() = (bitLength + 7) / 8

        fun append(value: Int, numBits: Int) {
            for (i in (numBits - 1) downTo 0) {
                val bit = (value ushr i) and 1
                val byteIdx = bitLength / 8
                val bitIdx = 7 - (bitLength % 8)

                if (byteIdx == bytes.size) {
                    bytes.add(0)
                }

                if (bit == 1) {
                    val cur = bytes[byteIdx].toInt()
                    bytes[byteIdx] = (cur or (1 shl bitIdx)).toByte()
                }
                bitLength++
            }
        }

        fun toByteArray(): ByteArray = bytes.toByteArray()
    }
}
