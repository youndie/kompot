package io.github.youndie.kompot.preview

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.TextComponent
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

// The example this module ships is the one thing in it a reader copies, so it is worth holding to
// the same standard as the harness: that it draws, and draws the screen it claims to.
//
// Asserted on the nodes rather than on "an image came out": a headless render succeeds on a blank
// frame just as happily as on a correct one, and an example that quietly drew nothing would look
// exactly like this test passing.
//
// The PNG beside the assertions is for a person to look at — the IDE renders this same composable
// through its own facade, and this is the same picture without an IDE in the loop.
@OptIn(ExperimentalTestApi::class)
class IdePreviewRendersTest {
    @Test
    fun `the shipped example draws its screen`() =
        runDesktopComposeUiTest(width = 320, height = 160) {
            setContent { KompotTreeIdePreview() }

            // Read off the tree rather than repeated here: the assertion is "what the example says is
            // what it drew", and a copy of the strings would only pin the copy — and fail the build on
            // a retyped label, which is a thing that happens to an example on purpose.
            previewScreen.children.forEach { child ->
                val label =
                    when (child) {
                        is TextComponent -> child.text
                        is ButtonComponent -> child.text
                        else -> error("the example grew a component this test does not know how to read: $child")
                    }
                onNodeWithText(label).assertIsDisplayed()
            }

            val out = File("build/preview/ide-preview.png")
            out.parentFile.mkdirs()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
        }
}
