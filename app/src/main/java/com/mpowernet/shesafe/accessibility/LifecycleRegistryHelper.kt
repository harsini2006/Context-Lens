package com.mpowernet.shesafe.accessibility

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class CustomLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)

    init {
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
    }

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
}

class CustomViewModelStoreOwner : ViewModelStoreOwner {
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store
}

class CustomSavedStateRegistryOwner(private val view: View) : SavedStateRegistryOwner {
    private val controller = SavedStateRegistryController.create(this)

    init {
        controller.performRestore(Bundle())
    }

    override val lifecycle: Lifecycle
        get() = (view.tag as? LifecycleOwner)?.lifecycle ?: LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.STARTED
        }

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry
}
