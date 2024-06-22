package com.xdmpx.autoapks.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.protobuf.InvalidProtocolBufferException
import com.xdmpx.autoapks.datastore.SettingsProto
import com.xdmpx.autoapks.datastore.ThemeType
import java.io.InputStream
import java.io.OutputStream

abstract class Settings {

    companion object {
        @Volatile
        private var INSTANCE: SettingsViewModel? = null

        fun getInstance(): SettingsViewModel {
            synchronized(this) {
                return INSTANCE ?: SettingsViewModel(
                ).also {
                    INSTANCE = it
                }
            }
        }
    }
}

object SettingsSerializer : Serializer<SettingsProto> {
    override val defaultValue: SettingsProto =
        SettingsProto.getDefaultInstance().toBuilder().apply {
            usePureDark = false
            useDynamicColor = true
            theme = ThemeType.SYSTEM
        }.build()

    override suspend fun readFrom(input: InputStream): SettingsProto {
        try {
            return SettingsProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(
        t: SettingsProto, output: OutputStream
    ) = t.writeTo(output)
}
