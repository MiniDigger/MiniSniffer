package dev.benndorf.minisniffer

import io.nacular.doodle.application.Application
import io.nacular.doodle.application.Modules.Companion.PointerModule
import io.nacular.doodle.application.application
import io.nacular.doodle.core.WindowGroup
import io.nacular.doodle.core.view
import io.nacular.doodle.theme.basic.DarkBasicTheme
import io.nacular.doodle.theme.basic.DarkBasicTheme.Companion.DarkBasicTheme
import org.kodein.di.instance

class MiniSnifferApplication(
    windows: WindowGroup,
    theme: DarkBasicTheme,
) : Application {
    init {
        windows.main.title = "Mini Sniffer"
        windows.main.display += view {
            theme.install(this)
        }
    }

    override fun shutdown() {}
}

fun main() {
    val modules = listOf(
        DarkBasicTheme,
        PointerModule
    )
    application(modules = modules) {
        MiniSnifferApplication(
            windows = instance(),
            theme = instance()
        )
    }
}
