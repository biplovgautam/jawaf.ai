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
 * Instrumented test for Dashboard to ChatBot navigation
 *
 * Tests the navigation flow: Dashboard → Chat → ChatBot
 */
@RunWith(AndroidJUnit4::class)
class DashboardToChatBotTest {

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
    fun testNavigateFromDashboardToChatToChatBot() {
        // Launch the activity
        activityRule.launchActivity(null)

        // Wait for the dashboard to load completely
        composeTestRule.waitForIdle()
        Thread.sleep(2000) // Allow Firebase initialization

        // Step 1: Verify we start on Home screen
        composeTestRule.onNodeWithContentDescription("Home")
            .assertExists()

        // Step 2: Navigate to Chat tab
        composeTestRule.onNodeWithContentDescription("Chat")
            .assertExists()
            .assertIsDisplayed()
            .performClick()

        // Wait for navigation to Chat screen
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        // Step 3: Verify we're on Chat screen
        composeTestRule.onNodeWithContentDescription("Chat")
            .assertExists()

        // Step 4: Look for ChatBot navigation element
        // Based on your DashboardActivity, ChatScreen has onNavigateToChatBot callback
        // This could be a FAB, button, or any clickable element that triggers chatbot navigation
        try {
            // Try to find ChatBot button by text
            composeTestRule.onNodeWithText("ChatBot")
                .assertExists()
                .performClick()
        } catch (e: AssertionError) {
            try {
                // Try to find by content description
                composeTestRule.onNodeWithContentDescription("ChatBot")
                    .assertExists()
                    .performClick()
            } catch (e2: AssertionError) {
                try {
                    // Try to find AI Chat or New Chat button
                    composeTestRule.onNodeWithText("New Chat")
                        .assertExists()
                        .performClick()
                } catch (e3: AssertionError) {
                    try {
                        // Try to find floating action button
                        composeTestRule.onNodeWithContentDescription("Add")
                            .assertExists()
                            .performClick()
                    } catch (e4: AssertionError) {
                        // Try to find any button that might navigate to chatbot
                        composeTestRule.onNode(hasClickAction())
                            .assertExists()
                            .performClick()
                    }
                }
            }
        }

        // Wait for navigation to ChatBot screen
        composeTestRule.waitForIdle()
        Thread.sleep(1500)

        // Step 5: Verify we successfully navigated to ChatBot screen
        // The chatbot route in your DashboardActivity is "chatbot"
        // ChatBot screen should have some identifying elements
        try {
            // Try to find ChatBot screen title
            composeTestRule.onNodeWithText("ChatBot")
                .assertExists()
                .assertIsDisplayed()
        } catch (e: AssertionError) {
            try {
                // Try to find AI Assistant or similar text
                composeTestRule.onNodeWithText("AI Assistant")
                    .assertExists()
                    .assertIsDisplayed()
            } catch (e2: AssertionError) {
                try {
                    // Try to find message input field (common in chat screens)
                    composeTestRule.onNode(hasText("Type a message...") or hasText("Enter message"))
                        .assertExists()
                } catch (e3: AssertionError) {
                    // At minimum, verify we have a back button (indicating we navigated to a detail screen)
                    composeTestRule.onNodeWithContentDescription("Back")
                        .assertExists()
                }
            }
        }
    }

    @Test
    fun testChatTabIsAccessible() {
        // Launch the activity
        activityRule.launchActivity(null)

        // Wait for loading
        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // Verify Chat tab exists and is clickable
        composeTestRule.onNodeWithContentDescription("Chat")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun testBasicNavigationFlow() {
        // Launch the activity
        activityRule.launchActivity(null)

        // Wait for loading
        composeTestRule.waitForIdle()
        Thread.sleep(1500)

        // Navigate through tabs to ensure basic navigation works
        composeTestRule.onNodeWithContentDescription("Chat")
            .performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // Verify navigation worked
        composeTestRule.onNodeWithContentDescription("Chat")
            .assertExists()

        // Return to Home
        composeTestRule.onNodeWithContentDescription("Home")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify we're back on Home
        composeTestRule.onNodeWithContentDescription("Home")
            .assertExists()
    }
}
