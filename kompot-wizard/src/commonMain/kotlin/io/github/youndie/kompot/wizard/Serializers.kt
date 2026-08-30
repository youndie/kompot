package io.github.youndie.kompot.wizard

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.KompotAction

// The serialisation "plug-in" for the wizard actions. The component itself is not listed here: the
// @KompotComponentMarker on WizardScreenComponent makes :kompot-registry-processor generate its
// polymorphic registration into generatedWizardSerializersModule.
public val kompotWizardSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(KompotAction::class) {
            subclass(NextStepAction::class)
            subclass(PrevStepAction::class)
            subclass(FinishWizardAction::class)
            subclass(WizardStepAction::class)
        }
    }
