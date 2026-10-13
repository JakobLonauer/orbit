package io.github.thelony97.orbit.ui.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Work started from the UI that must outlive the screen that started it – above all the update
 * download (several MB). A `rememberCoroutineScope` is cancelled as soon as its composable leaves
 * the screen (scrolling the banner away, switching tab), which made updates fail with
 * "rememberCoroutineScope left the composition". This scope lives as long as the app process.
 */
object AppWork {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}
