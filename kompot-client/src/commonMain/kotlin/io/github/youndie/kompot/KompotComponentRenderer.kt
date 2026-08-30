package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import io.github.youndie.kompot.form.FormController

public interface KompotComponentRenderer<T : KompotComponent> {
    @Composable
    public fun Render(
        component: T,
        actionHandler: KompotActionHandler,
        formController: FormController,
    )
}
