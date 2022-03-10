package io.rsocket.kotlin

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.frame.io.*
import kotlin.experimental.*

//used for MimeType, AuthType and later in LeaseStrategy
public interface CompactType {
    public interface WithId : CompactType {
        public val identifier: Byte
    }

    public interface WithName : CompactType {
        public val text: String
    }

    public interface WellKnown : CompactType, WithId, WithName
}

@Suppress("UNCHECKED_CAST")
//TODO: type relations
public abstract class CompactTypeFactory<
        Type : CompactType,
        WithId : CompactType.WithId,
        WithName : CompactType.WithName,
        WellKnown,
        > internal constructor(
    private val withIdConstructor: (identifier: Byte) -> WithId,
    private val withNameConstructor: (text: String) -> WithName,
    wellKnownValues: Array<WellKnown>,
) where WellKnown : CompactType.WellKnown, WellKnown : Enum<WellKnown> {
    private val wellKnownByIdentifier: Array<Any?> = arrayOfNulls<Any?>(128)
    private val wellKnownByName: MutableMap<String, WellKnown> = HashMap(128)

    init {
        wellKnownValues.forEach {
            wellKnownByIdentifier[it.identifier.toInt()] = it
            wellKnownByName[it.text] = it
        }
    }

    //TODO is it needed?
    public fun WellKnown(text: String): WellKnown? = wellKnownByName[text]
    public fun WellKnown(identifier: Byte): WellKnown? = wellKnownByIdentifier[identifier.toInt()] as WellKnown?
    public fun WellKnown(identifier: Int): WellKnown? = wellKnownByIdentifier[identifier] as WellKnown?

    public fun WithName(text: String): WithName = (WellKnown(text) ?: withNameConstructor(text)) as WithName
    public fun WithId(identifier: Byte): WithId = (WellKnown(identifier) ?: withIdConstructor(identifier)) as WithId
    public fun WithId(identifier: Int): WithId = WithId(identifier.toByte())

    public operator fun invoke(text: String): WithName = WithName(text)
    public operator fun invoke(identifier: Byte): WithId = WithId(identifier)
    public operator fun invoke(identifier: Int): WithId = WithId(identifier)
}

internal fun CompactType.WellKnown.toString(typeName: String): String = "$typeName(id=$identifier, text=$text)"
internal fun CompactType.WithId.toString(typeName: String): String = "$typeName(id=$identifier)"
internal fun CompactType.WithName.toString(typeName: String): String = "$typeName(text=$text)"

internal abstract class AbstractCompactTypeWithId(final override val identifier: Byte) : CompactType.WithId {
    init {
        require(identifier > 0) { "Mime-type identifier must be positive but was '${identifier}'" }
    }

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AbstractCompactTypeWithId

        if (identifier != other.identifier) return false

        return true
    }

    final override fun hashCode(): Int {
        return identifier.hashCode()
    }

    abstract override fun toString(): String
}

internal abstract class AbstractCompactTypeWithName(final override val text: String) : CompactType.WithName {
    init {
        require(text.all { it.code <= 0x7f }) { "String should be an ASCII encodded string" }
        require(text.length in 1..128) { "Mime-type text length must be in range 1..128 but was '${text.length}'" }
    }

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AbstractCompactTypeWithName

        if (text != other.text) return false

        return true
    }

    final override fun hashCode(): Int {
        return text.hashCode()
    }

    abstract override fun toString(): String
}

private const val KnownTypeFlag: Byte = Byte.MIN_VALUE

internal fun BytePacketBuilder.writeCompactType(type: CompactType) {
    when (type) {
        is CompactType.WithId   -> writeByte(type.identifier or KnownTypeFlag)
        is CompactType.WithName -> {
            val typeBytes = type.text.encodeToByteArray()
            writeByte(typeBytes.size.toByte()) //write length
            writeFully(typeBytes) //write type
        }
    }
}

internal fun <T : CompactType> ByteReadPacket.readCompactType(factory: CompactTypeFactory<T, *, *, *>): T {
    val byte = readByte()
    return if (byte check KnownTypeFlag) {
        val identifier = byte xor KnownTypeFlag
        factory(identifier)
    } else {
        val stringType = readTextExactBytes(byte.toInt())
        factory(stringType)
    } as T
}
