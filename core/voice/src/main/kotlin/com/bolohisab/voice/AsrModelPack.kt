package com.bolohisab.voice

import android.content.Context
import android.util.Log
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.android.play.core.assetpacks.model.AssetPackStorageMethod
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The speech model's Play Asset Delivery pack (`:asr_model`, fast-follow). Play downloads it just
 * after install and keeps it as plain files, so hotwords work without copying the model.
 *
 * Installs that did not come from Play (debug builds from Android Studio) have no pack; they carry
 * the model inside the APK instead and every call here simply finds nothing.
 */
class AsrModelPack(context: Context) {

    private val manager = runCatching { AssetPackManagerFactory.getInstance(context.applicationContext) }.getOrNull()
    private val requested = AtomicBoolean(false)

    /** The pack's model folder on disk, once Play has delivered it. */
    fun modelDir(): File? {
        val location = runCatching { manager?.getPackLocation(PACK) }.getOrNull() ?: return null
        if (location.packStorageMethod() != AssetPackStorageMethod.STORAGE_FILES) return null
        val assets = location.assetsPath() ?: return null
        return File(assets, AsrModelLocator.ASSET_DIR).takeIf { it.isDirectory }
    }

    /**
     * Asks Play for the pack if it is not on the phone yet (a fast-follow download can still be
     * running on first launch, or was cleared), and calls [onReady] once it arrives. At most once.
     */
    fun requestIfMissing(onReady: () -> Unit) {
        val m = manager ?: return
        if (modelDir() != null || !requested.compareAndSet(false, true)) return
        val listener = object : AssetPackStateUpdateListener {
            override fun onStateUpdate(state: com.google.android.play.core.assetpacks.AssetPackState) {
                if (state.name() != PACK) return
                when (state.status()) {
                    AssetPackStatus.COMPLETED -> { m.unregisterListener(this); onReady() }
                    AssetPackStatus.FAILED, AssetPackStatus.CANCELED -> {
                        m.unregisterListener(this)
                        requested.set(false)
                        Log.w(TAG, "Speech model pack download ended: ${state.status()} (error ${state.errorCode()})")
                    }
                    else -> Unit
                }
            }
        }
        runCatching {
            m.registerListener(listener)
            m.fetch(listOf(PACK))
        }.onFailure {
            m.unregisterListener(listener)
            requested.set(false)
            Log.w(TAG, "Could not request the speech model pack", it)
        }
    }

    private companion object {
        const val PACK = "asr_model"
        const val TAG = "AsrModelPack"
    }
}
