package com.cacheci.apkk.parser

import com.android.apksig.ApkVerifier
import com.cacheci.apkk.model.NativeLibraryInfo
import com.cacheci.apkk.model.SignatureInfo
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object ApkBinaryInspector {

    private val certificateDateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

    private fun formatCertificateDate(value: Instant): String =
        certificateDateFormatter.format(value)

    fun nativeLibraries(
        file: File,
        zip: ZipFile,
        entries: List<ZipEntry>,
    ): List<NativeLibraryInfo> {
        val data = runCatching { file.readBytes() }.getOrNull()
        val offsets = data?.let(::centralDirectoryEntries).orEmpty()
        return entries.asSequence()
            .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
            .map { entry ->
                val zipInfo = offsets[entry.name]
                val elf = runCatching { zip.getInputStream(entry).use { parseElf(it.readBytes()) } }.getOrNull()
                NativeLibraryInfo(
                    path = entry.name,
                    abi = entry.name.split('/').getOrNull(1).orEmpty(),
                    name = entry.name.substringAfterLast('/'),
                    size = entry.size.coerceAtLeast(0),
                    compressedSize = entry.compressedSize.coerceAtLeast(0),
                    compressionMethod = entry.method,
                    zipDataOffset = zipInfo?.dataOffset,
                    zipAlignedTo4K = zipInfo?.dataOffset?.let { it % 0x1000 == 0L },
                    zipAlignedTo16K = zipInfo?.dataOffset?.let { it % 0x4000 == 0L },
                    elfClass = elf?.elfClass,
                    elfMachine = elf?.machine,
                    elfEndianness = elf?.endianness,
                    stripped = elf?.stripped,
                    loadSegmentAlignment = elf?.loadSegmentAlignment,
                    supports16KPageSize = elf?.loadSegmentAlignment?.let { it >= 0x4000 },
                )
            }
            .toList()
    }

    fun signatures(file: File, zip: ZipFile, entries: List<ZipEntry>): List<SignatureInfo> {
        val fallbackCertificates = readV1Certificates(zip, entries)
        val schemes = detectSigningSchemes(runCatching { file.readBytes() }.getOrNull(), entries)
        val verification = runCatching {
            ApkVerifier.Builder(file).build().verify()
        }
        val result = verification.getOrNull()
        if (result == null) {
            val failure = verification.exceptionOrNull()?.message?.takeIf(String::isNotEmpty)
                ?: "apksig 无法完成验证"
            return fallbackCertificates.firstOrNull()?.let { certificate ->
                listOf(certificateInfo("V1", certificate, false, errors = listOf(failure)))
            } ?: schemes.map { scheme ->
                SignatureInfo(scheme = scheme, verificationSuccessful = false, errors = listOf(failure))
            }
        }

        val globalErrors = result.getErrors().map { it.toString() }
        val globalWarnings = result.getWarnings().map { it.toString() }
        return buildList {
            result.getV1SchemeSigners().forEach { signer ->
                val certificate = signer.certificate
                if (certificate != null) {
                    add(
                        certificateInfo(
                            scheme = "V1",
                            certificate = certificate,
                            verified = result.isVerifiedUsingV1Scheme() && !signer.containsErrors(),
                            errors = (globalErrors + signer.errors.map { it.toString() }).distinct(),
                            warnings = (globalWarnings + signer.warnings.map { it.toString() }).distinct(),
                        ),
                    )
                }
            }
            result.getV2SchemeSigners().forEach { signer ->
                add(
                    apkSignatureInfo(
                        scheme = "V2",
                        certificate = signer.certificate,
                        algorithmId = signer.contentDigests.firstOrNull()?.signatureAlgorithmId,
                        verified = result.isVerifiedUsingV2Scheme() && !signer.containsErrors(),
                        errors = (globalErrors + signer.errors.map { it.toString() }).distinct(),
                        warnings = (globalWarnings + signer.warnings.map { it.toString() }).distinct(),
                        signerIndex = signer.index,
                    ),
                )
            }
            result.getV3SchemeSigners().forEach { signer ->
                add(
                    apkSignatureInfo(
                        scheme = "V3",
                        certificate = signer.certificate,
                        algorithmId = signer.contentDigests.firstOrNull()?.signatureAlgorithmId,
                        verified = result.isVerifiedUsingV3Scheme() && !signer.containsErrors(),
                        errors = (globalErrors + signer.errors.map { it.toString() }).distinct(),
                        warnings = (globalWarnings + signer.warnings.map { it.toString() }).distinct(),
                        signerIndex = signer.index,
                    ),
                )
            }
            result.getV31SchemeSigners().forEach { signer ->
                add(
                    apkSignatureInfo(
                        scheme = "V3.1",
                        certificate = signer.certificate,
                        algorithmId = signer.contentDigests.firstOrNull()?.signatureAlgorithmId,
                        verified = result.isVerifiedUsingV31Scheme() && !signer.containsErrors(),
                        errors = (globalErrors + signer.errors.map { it.toString() }).distinct(),
                        warnings = (globalWarnings + signer.warnings.map { it.toString() }).distinct(),
                        signerIndex = signer.index,
                    ),
                )
            }
            result.getV4SchemeSigners().forEach { signer ->
                add(
                    apkSignatureInfo(
                        scheme = "V4",
                        certificate = signer.certificate,
                        algorithmId = signer.contentDigests.firstOrNull()?.signatureAlgorithmId,
                        verified = result.isVerifiedUsingV4Scheme() && !signer.containsErrors(),
                        errors = (globalErrors + signer.errors.map { it.toString() }).distinct(),
                        warnings = (globalWarnings + signer.warnings.map { it.toString() }).distinct(),
                        signerIndex = signer.index,
                    ),
                )
            }
            if (isEmpty()) {
                schemes.forEach { scheme ->
                    add(
                        SignatureInfo(
                            scheme = scheme,
                            verificationSuccessful = result.isVerified && scheme in schemes,
                            errors = globalErrors,
                            warnings = globalWarnings,
                        ),
                    )
                }
            }
        }
    }

    private fun readV1Certificates(zip: ZipFile, entries: List<ZipEntry>): List<X509Certificate> =
        entries.asSequence()
            .filter { V1_SIGNATURE.matches(it.name) }
            .flatMap { entry ->
                runCatching {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    CertificateFactory.getInstance("X.509")
                        .generateCertificates(ByteArrayInputStream(bytes))
                        .asSequence()
                        .filterIsInstance<X509Certificate>()
                }.getOrElse { emptySequence() }
            }
            .toList()

    private fun certificateInfo(
        scheme: String,
        certificate: X509Certificate,
        verified: Boolean,
        errors: List<String> = emptyList(),
        warnings: List<String> = emptyList(),
    ): SignatureInfo = SignatureInfo(
        scheme = scheme,
        verificationSuccessful = verified,
        publicKeyFormat = certificate.publicKey.format,
        publicKeyAlgorithm = certificate.publicKey.algorithm,
        publicKeyAlgorithmOid = certificate.publicKey.algorithmOid(),
        issuer = certificate.issuerX500Principal.name,
        subject = certificate.subjectX500Principal.name,
        certificateSha256 = listOf(certificate.digest("SHA-256")),
        certificateSha1 = listOf(certificate.digest("SHA-1")),
        algorithm = certificate.sigAlgName,
        validFrom = formatCertificateDate(certificate.notBefore.toInstant()),
        validUntil = formatCertificateDate(certificate.notAfter.toInstant()),
        certificateValidNow = runCatching { certificate.checkValidity(); true }.getOrDefault(false),
        errors = errors,
        warnings = warnings,
    )

    private fun apkSignatureInfo(
        scheme: String,
        certificate: X509Certificate?,
        algorithmId: Int?,
        verified: Boolean,
        errors: List<String>,
        warnings: List<String>,
        signerIndex: Int,
    ): SignatureInfo {
        val details = certificate?.let {
            certificateInfo(
                scheme = "$scheme (signer $signerIndex)",
                certificate = it,
                verified = verified,
                errors = errors,
                warnings = warnings,
            )
        } ?: SignatureInfo(
            scheme = "$scheme (signer $signerIndex)",
            verificationSuccessful = verified,
            errors = errors,
            warnings = warnings,
        )
        return details.copy(
            signatureAlgorithmId = algorithmId,
            algorithm = algorithmId?.let(::signatureAlgorithmName) ?: details.algorithm,
        )
    }

    private fun signatureAlgorithmName(id: Int): String = when (id) {
        0x0101 -> "SHA256withRSA/PSS"
        0x0102 -> "SHA512withRSA/PSS"
        0x0103 -> "SHA256withRSA"
        0x0104 -> "SHA512withRSA"
        0x0201 -> "SHA256withECDSA"
        0x0202 -> "SHA512withECDSA"
        0x0301 -> "SHA256withDSA"
        0x0421 -> "SHA256withRSA (verity)"
        0x0423 -> "SHA256withECDSA (verity)"
        0x0425 -> "SHA256withDSA (verity)"
        else -> "unknown (0x${id.toString(16)})"
    }

    private fun detectSigningSchemes(data: ByteArray?, entries: List<ZipEntry>): List<String> {
        if (data == null) return emptyList()
        val schemes = mutableListOf<String>()
        if (entries.any { V1_SIGNATURE.matches(it.name) }) schemes += "V1"
        findApkSigningBlock(data)?.let { block ->
            if (0x7109871aL in block) schemes += "V2"
            if (0xf05368c0L in block) schemes += "V3"
            if (0x1b93ad61L in block) schemes += "V3.1"
        }
        return schemes.distinct()
    }

    private fun findApkSigningBlock(data: ByteArray): Set<Long>? {
        val eocd = findEndOfCentralDirectory(data) ?: return null
        val centralDirectoryOffset = data.u32At(eocd + 16)
        if (centralDirectoryOffset <= 8L || centralDirectoryOffset > data.size) return null
        val footerOffset = centralDirectoryOffset.toInt() - 24
        if (footerOffset < 0 || footerOffset + 24 > data.size) return null
        if (data.copyOfRange(footerOffset + 8, footerOffset + 24)
                .toString(Charsets.US_ASCII) != "APK Sig Block 42") return null
        val size = data.u64At(footerOffset)
        val start = centralDirectoryOffset - size - 8
        if (start < 0 || start + 8 > data.size || data.u64At(start.toInt()) != size) return null
        val ids = mutableSetOf<Long>()
        var cursor = start.toInt() + 8
        val end = footerOffset
        while (cursor + 12 <= end) {
            val pairSize = data.u64At(cursor)
            if (pairSize < 4 || pairSize > Int.MAX_VALUE || cursor + 8 + pairSize > end) break
            ids += data.u32At(cursor + 8)
            cursor += 8 + pairSize.toInt()
        }
        return ids
    }

    private fun findEndOfCentralDirectory(data: ByteArray): Int? {
        val start = (data.size - 22 - 0xffff).coerceAtLeast(0)
        for (offset in data.size - 22 downTo start) {
            if (data.u32At(offset) == 0x06054b50L) return offset
        }
        return null
    }

    private fun centralDirectoryEntries(data: ByteArray): Map<String, ZipEntryInfo> {
        val eocd = findEndOfCentralDirectory(data) ?: return emptyMap()
        val directoryOffset = data.u32At(eocd + 16).toInt()
        val directorySize = data.u32At(eocd + 12).toInt()
        if (directoryOffset < 0 || directorySize < 0 || directoryOffset + directorySize > data.size) return emptyMap()
        val result = mutableMapOf<String, ZipEntryInfo>()
        var cursor = directoryOffset
        val end = directoryOffset + directorySize
        while (cursor + 46 <= end && data.u32At(cursor) == 0x02014b50L) {
            val nameLength = data.u16At(cursor + 28)
            val extraLength = data.u16At(cursor + 30)
            val commentLength = data.u16At(cursor + 32)
            val nameStart = cursor + 46
            val next = nameStart + nameLength + extraLength + commentLength
            if (next > end) break
            val name = data.copyOfRange(nameStart, nameStart + nameLength).toString(Charsets.UTF_8)
            val localOffset = data.u32At(cursor + 42).toInt()
            if (localOffset < 0 || localOffset + 30 > data.size || data.u32At(localOffset) != 0x04034b50L) break
            val localNameLength = data.u16At(localOffset + 26)
            val localExtraLength = data.u16At(localOffset + 28)
            result[name] = ZipEntryInfo(
                dataOffset = localOffset.toLong() + 30 + localNameLength + localExtraLength,
            )
            cursor = next
        }
        return result
    }

    private fun parseElf(data: ByteArray): ElfInfo? {
        if (data.size < 20 || !data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x7f.toByte(), 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))) return null
        val is64 = data[4].toInt() == 2
        val littleEndian = data[5].toInt() == 1
        if (data[4].toInt() !in 1..2 || data[5].toInt() !in 1..2) return null
        fun u16(offset: Int): Long = data.elfUnsigned(offset, 2, littleEndian)
        fun u32(offset: Int): Long = data.elfUnsigned(offset, 4, littleEndian)
        fun u64(offset: Int): Long = data.elfUnsigned(offset, 8, littleEndian)
        val machine = when (u16(18).toInt()) {
            3 -> "x86"
            8 -> "MIPS"
            40 -> "ARM"
            62 -> "x86_64"
            183 -> "AArch64"
            243 -> "RISC-V"
            else -> "machine-${u16(18).toInt()}"
        }
        val programOffset = (if (is64) u64(32) else u32(28)).toInt()
        val programEntrySize = u16(if (is64) 54 else 42).toInt()
        val programCount = u16(if (is64) 56 else 44).toInt()
        var maxAlignment = 0L
        repeat(programCount) { index ->
            val offset = programOffset + index * programEntrySize
            if (offset + programEntrySize > data.size) return@repeat
            if (u32(offset) == 1L) {
                val align = if (is64) u64(offset + 48) else u32(offset + 28)
                maxAlignment = maxOf(maxAlignment, align)
            }
        }
        val sectionOffset = (if (is64) u64(40) else u32(32)).toInt()
        val sectionEntrySize = u16(if (is64) 58 else 46).toInt()
        val sectionCount = u16(if (is64) 60 else 48).toInt()
        var hasSymbolTable = false
        repeat(sectionCount) { index ->
            val offset = sectionOffset + index * sectionEntrySize
            if (offset + 8 <= data.size && u32(offset + 4) == 2L) hasSymbolTable = true
        }
        return ElfInfo(
            elfClass = if (is64) "ELF64" else "ELF32",
            machine = machine,
            endianness = if (littleEndian) "little" else "big",
            stripped = !hasSymbolTable,
            loadSegmentAlignment = maxAlignment.takeIf { it > 0 },
        )
    }

    private data class ZipEntryInfo(val dataOffset: Long)
    private data class ElfInfo(
        val elfClass: String,
        val machine: String,
        val endianness: String,
        val stripped: Boolean,
        val loadSegmentAlignment: Long?,
    )

    private fun X509Certificate.digest(algorithm: String): String = MessageDigest.getInstance(algorithm)
        .digest(encoded).joinToString(":") { "%02X".format(it) }

    private fun java.security.PublicKey.algorithmOid(): String? = runCatching {
        DerReader(encoded).readSequence().readSequence().readOid()
    }.getOrNull()

    private class DerReader(private val bytes: ByteArray, private var offset: Int = 0) {
        fun readSequence(): DerReader = DerReader(readValue(0x30))

        fun readOid(): String {
            val value = readValue(0x06)
            require(value.isNotEmpty())
            val first = value[0].toInt() and 0xff
            val firstArc = when {
                first < 40 -> 0
                first < 80 -> 1
                else -> 2
            }
            val arcs = mutableListOf(firstArc, first - firstArc * 40)
            var current = 0L
            value.drop(1).forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                current = (current shl 7) or (unsigned and 0x7f).toLong()
                if (unsigned and 0x80 == 0) {
                    arcs += current.toInt()
                    current = 0
                }
            }
            require(current == 0L)
            return arcs.joinToString(".")
        }

        private fun readValue(expectedTag: Int): ByteArray {
            require(offset < bytes.size && ((bytes[offset++].toInt() and 0xff) == expectedTag))
            val length = readLength()
            require(length >= 0 && offset + length <= bytes.size)
            return bytes.copyOfRange(offset, offset + length).also { offset += length }
        }

        private fun readLength(): Int {
            val first = bytes[offset++].toInt() and 0xff
            if (first and 0x80 == 0) return first
            val count = first and 0x7f
            require(count in 1..4 && offset + count <= bytes.size)
            return (0 until count).fold(0) { value, index ->
                (value shl 8) or (bytes[offset + index].toInt() and 0xff)
            }.also { offset += count }
        }
    }

    private fun ByteArray.u16At(offset: Int): Int = (u8(offset) or (u8(offset + 1) shl 8))
    private fun ByteArray.u32At(offset: Int): Long = u16At(offset).toLong() or (u16At(offset + 2).toLong() shl 16)
    private fun ByteArray.u64At(offset: Int): Long = (0..7).fold(0L) { result, index -> result or (u8(offset + index).toLong() shl (index * 8)) }
    private fun ByteArray.u8(offset: Int): Int = getOrNull(offset)?.toInt()?.and(0xff) ?: -1
    private fun ByteArray.elfUnsigned(offset: Int, width: Int, littleEndian: Boolean): Long {
        if (offset < 0 || offset + width > size) return 0
        return if (littleEndian) {
            (0 until width).fold(0L) { result, index ->
                result or ((this[offset + index].toLong() and 0xffL) shl (index * 8))
            }
        } else {
            (0 until width).fold(0L) { result, index ->
                (result shl 8) or (this[offset + index].toLong() and 0xffL)
            }
        }
    }

    private val V1_SIGNATURE = Regex("(?i)^META-INF/[^/]+\\.(RSA|DSA|EC)$")
}
