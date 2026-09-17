package stanissay.wear.board

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class ImeLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    private val savedStateRegistryController =
        SavedStateRegistryController.create(this)

    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)

        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_CREATE
        )
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_START
        )
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_RESUME
        )
    }

    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_PAUSE
        )
    }

    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_STOP
        )
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_DESTROY
        )

        viewModelStore.clear()
    }
}