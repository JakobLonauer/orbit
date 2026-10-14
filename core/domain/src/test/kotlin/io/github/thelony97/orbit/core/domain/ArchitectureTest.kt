package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PLAN 8.3: the dependency direction as a test. Gradle already keeps `:core:*` from seeing `:app`;
 * this guards what Gradle cannot: the pure modules stay free of Android, `:core:model` never
 * reaches into `:core:domain`, and no core module names an app package.
 */
class ArchitectureTest {
    // Gradle runs module tests with the module directory as working directory (core/domain).
    private val core = File("..").canonicalFile

    private fun imports(module: String): List<Pair<String, String>> =
        File(core, "$module/src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> f.readLines().filter { it.startsWith("import ") }.map { f.name to it.removePrefix("import ").trim() } }
            .toList()

    @Test fun modulesAreThere() {
        listOf("model", "domain", "designsystem").forEach { m ->
            assertTrue("missing core/$m", File(core, "$m/src/main").isDirectory)
        }
    }

    @Test fun pureModulesHaveNoAndroid() {
        val bad = (imports("model") + imports("domain")).filter { (_, i) -> i.startsWith("android.") || i.startsWith("androidx.") }
        assertTrue("Android in a pure module: $bad", bad.isEmpty())
    }

    @Test fun modelDoesNotUseDomain() {
        val bad = imports("model").filter { (_, i) -> i.startsWith("io.github.thelony97.orbit.core.domain") }
        assertTrue("core:model imports core:domain: $bad", bad.isEmpty())
    }

    @Test fun coreNeverNamesTheApp() {
        val bad = listOf("model", "domain", "designsystem").flatMap { imports(it) }
            .filter { (_, i) -> i.startsWith("io.github.thelony97.orbit.") && !i.startsWith("io.github.thelony97.orbit.core.") }
        assertTrue("core imports app code: $bad", bad.isEmpty())
    }
}
