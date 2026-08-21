package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import io.github.youndie.kompot.form.FormController

interface KompotComponentRenderer<T : KompotComponent> {
    @Composable
    fun Render(
        component: T,
        actionHandler: KompotActionHandler,
        formController: FormController,
    )
}
