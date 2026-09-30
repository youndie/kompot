package io.github.youndie.kompot.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class PresentationHeaderTest {
    private val sheetRoute = ScreenRoute(deeplink = "app://confirm", endpoint = "/screens/confirm", presentation = ScreenRoutePresentation.SHEET)

    @Test
    fun `a response asks for a sheet on an address that has no hint`() {
        assertEquals(ScreenRoutePresentation.SHEET, PresentationHeader.presentedAs("sheet"))
        assertEquals(ScreenRoutePresentation.DIALOG, PresentationHeader.presentedAs(" Dialog "))
    }

    // The case the header exists for: one address, a state that is a sheet and a state that is not.
    @Test
    fun `the response wins over the route - a screen included`() {
        assertEquals(ScreenRoutePresentation.SCREEN, PresentationHeader.presentedAs("screen", sheetRoute))
        assertEquals(ScreenRoutePresentation.DIALOG, PresentationHeader.presentedAs("dialog", sheetRoute))
    }

    @Test
    fun `without a header the route decides and without a route it is a screen`() {
        assertEquals(ScreenRoutePresentation.SHEET, PresentationHeader.presentedAs(null, sheetRoute))
        assertEquals(ScreenRoutePresentation.SCREEN, PresentationHeader.presentedAs(null))
    }

    // Unknown or undrawable is not "screen": it is "no word from the response", so the route still counts.
    @Test
    fun `a header the client cannot draw leaves the decision to the route`() {
        assertEquals(ScreenRoutePresentation.SHEET, PresentationHeader.presentedAs("popover", sheetRoute))
        assertEquals(ScreenRoutePresentation.SCREEN, PresentationHeader.presentedAs("popover"))
        assertEquals(
            ScreenRoutePresentation.SCREEN,
            PresentationHeader.presentedAs("sheet", supported = setOf(ScreenRoutePresentation.SCREEN)),
        )
    }
}
