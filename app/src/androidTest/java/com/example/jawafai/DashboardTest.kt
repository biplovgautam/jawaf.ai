package com.example.jawafai

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.example.jawafai.view.dashboard.DashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for Dashboard navigation using Compose testing
 *
 * Tests the navigation from Dashboard to Settings via navigation bar
 */
@RunWith(AndroidJUnit4::class)
class DashboardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<DashboardActivity>()

    @get:Rule
    val activityRule = ActivityTestRule(DashboardActivity::class.java, true, false)

    @Before
    fun setup() {
        // Mock Firebase Auth static methods
        mockkStatic(FirebaseAuth::class)
        mockkStatic(FirebaseFirestore::class)

        val mockFirebaseAuth = mockk<FirebaseAuth>(relaxed = true)
        val mockFirebaseUser = mockk<FirebaseUser>(relaxed = true)
        val mockFirestore = mockk<FirebaseFirestore>(relaxed = true)

        every { FirebaseAuth.getInstance() } returns mockFirebaseAuth
        every { FirebaseFirestore.getInstance() } returns mockFirestore
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        every { mockFirebaseUser.uid } returns "test-user-123"
        every { mockFirebaseUser.email } returns "test@gmail.com"
        every { mockFirebaseUser.displayName } returns "Test User"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testNavigateToSettingsFromDashboard() {
        // Launch the activity
        activityRule.launchActivity(null)

        // Wait for the dashboard to load completely
        composeTestRule.waitForIdle()
        Thread.sleep(2000) // Give extra time for Firebase initialization

        // Verify dashboard loaded by checking if any navigation item is displayed
        composeTestRule.onNodeWithContentDescription("Home")
            .assertExists()

        // Try to find and click Settings tab
        composeTestRule.onNodeWithContentDescription("Settings")
            .assertExists()
            .assertIsDisplayed()
            .performClick()

        // Wait for navigation to complete
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        // Verify we navigated to Settings by checking Settings content
        composeTestRule.onNodeWithText("Settings")
            .assertExists()
            .assertIsDisplayed()
    }
}
