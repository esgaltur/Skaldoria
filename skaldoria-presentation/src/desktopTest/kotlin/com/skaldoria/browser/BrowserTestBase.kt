package com.skaldoria.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.skaldoria.PresentationStateTestBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Runs a test against a real, headless Chromium.
 *
 * The companion portals and the HTML export are the only parts of Skaldoria that execute in a
 * browser, and their string-level tests (`PortalAssetsTest`, `OfflineHtmlExportTest`) can only
 * assert what the server *sends*. Whether the JavaScript then renders a question as text, drops
 * the token from the address bar, or actually reaches the deck when NEXT is tapped is only
 * observable by running it.
 *
 * One browser per class keeps the suite fast; every test gets a fresh [BrowserContext], so
 * cookies, history and storage never leak between tests.
 *
 * Always headless: the suite must run on CI and on a build box with no display. Set
 * `-Dskaldoria.skipBrowserTests=true` (Gradle: `-PskipBrowserTests`) to stand the suite down on a
 * machine that cannot download Chromium — it is then reported as skipped, never as passed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BrowserTestBase : PresentationStateTestBase() {

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var context: BrowserContext
    private val extraContexts = mutableListOf<BrowserContext>()

    protected lateinit var page: Page
        private set

    /** Every `console.error` and uncaught exception the page produced during the test. */
    protected val pageErrors = mutableListOf<String>()

    /** Dialogs (`alert`, `confirm`, `prompt`) the page opened — an injected script's tell. */
    protected val dialogs = mutableListOf<String>()

    @BeforeAll
    fun launchBrowser() {
        Assumptions.assumeFalse(
            System.getProperty(SKIP_PROPERTY)?.toBooleanStrictOrNull() == true,
            "browser tests skipped ($SKIP_PROPERTY=true)"
        )
        playwright = Playwright.create()
        browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
    }

    @AfterAll
    fun closeBrowser() {
        if (::browser.isInitialized) browser.close()
        if (::playwright.isInitialized) playwright.close()
    }

    @BeforeTest
    fun openPage() {
        pageErrors.clear()
        dialogs.clear()
        context = browser.newContext(Browser.NewContextOptions().setViewportSize(PHONE_WIDTH, PHONE_HEIGHT))
        page = context.newPage().apply {
            onConsoleMessage { if (it.type() == "error") pageErrors += it.text() }
            onPageError { pageErrors += it }
            onDialog { dialog ->
                dialogs += dialog.message()
                dialog.dismiss()
            }
        }
    }

    @AfterTest
    fun closePage() {
        extraContexts.forEach { it.close() }
        extraContexts.clear()
        if (::context.isInitialized) context.close()
    }

    /** A second, independent device — e.g. an audience phone beside the speaker's remote. */
    protected fun openSecondDevice(): Page =
        browser.newContext(Browser.NewContextOptions().setViewportSize(PHONE_WIDTH, PHONE_HEIGHT))
            .also { extraContexts += it }
            .newPage()

    companion object {
        const val SKIP_PROPERTY = "skaldoria.skipBrowserTests"

        /** The portals are phone UIs; test them at a phone's size. */
        private const val PHONE_WIDTH = 390
        private const val PHONE_HEIGHT = 844
    }
}
