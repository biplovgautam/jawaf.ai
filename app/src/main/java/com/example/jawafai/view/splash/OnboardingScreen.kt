package com.example.jawafai.view.splash

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.jawafai.R
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.utils.OnboardingPreferences
import kotlinx.coroutines.launch
import com.airbnb.lottie.compose.*

@Composable
fun OnboardingScreen(
    navController: NavController,
    onFinishOnboarding: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Function to handle onboarding completion
    fun completeOnboarding() {
        // Use the same SharedPreferences system as MainActivity
        val sharedPreferences = context.getSharedPreferences("JawafaiPrefs", android.content.Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("hasSeenOnboarding", true)
            .apply()
        onFinishOnboarding()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> OnboardingPage1()
                1 -> OnboardingPage2()
                2 -> OnboardingPage3()
                3 -> OnboardingPage4()
            }
        }

        // Skip Button - Positioned above the pager content with higher z-index
        if (pagerState.currentPage < 3) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 24.dp),
                color = Color.Transparent
            ) {
                TextButton(
                    onClick = {
                        // Use the same SharedPreferences system as MainActivity
                        val sharedPreferences = context.getSharedPreferences("JawafaiPrefs", Context.MODE_PRIVATE)
                        sharedPreferences.edit()
                            .putBoolean("hasSeenOnboarding", true)
                            .apply()
                        // Navigate directly to welcome screen
                        navController.navigate("welcome") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Skip",
                        fontSize = 20.sp,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = AppFonts.KaiseiDecolFontFamily,
                            color = Color(0xFF395B64)
                        )
                    )
                }
            }
        }

        // Next Button
        Button(
            onClick = {
                if (pagerState.currentPage < 3) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    // Mark onboarding as completed when "Start" is clicked
                    completeOnboarding()
                }
            },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF395B64)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp) // Moved up to make space for indicators
                .height(48.dp)
                .width(120.dp)
        ) {
            Text(
                text = if (pagerState.currentPage < 3) "Next" else "Start",
                color = Color.White,
                fontSize = 16.sp,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppFonts.KaiseiDecolFontFamily
                )
            )
        }

        // Smooth Animated Page Indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) { index ->
                // Animate the width based on current page
                val animatedWidth by animateDpAsState(
                    targetValue = if (index == pagerState.currentPage) 24.dp else 8.dp,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    ),
                    label = "indicator_width_$index"
                )

                // Animate the color based on current page
                val animatedColor by animateColorAsState(
                    targetValue = if (index == pagerState.currentPage) Color(0xFF395B64) else Color.Gray,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    ),
                    label = "indicator_color_$index"
                )

                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(animatedWidth)
                        .background(
                            color = animatedColor,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}

@Composable
fun OnboardingPage1() {
    // Lottie animation composition for first onboarding screen
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.onboard1))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lottie Animation instead of static image
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Title using Kadwa Bold
        Text(
            text = "Smart Replies",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 50.dp),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = AppFonts.KadwaFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF395B64),
                fontSize = 28.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Subtitle using Karla Bold
        Text(
            text = "Replies that sound just like you - only faster.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = AppFonts.KarlaFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF395B64),
                fontSize = 16.sp
            )
        )
    }
}

@Composable
fun OnboardingPage2() {
    // Lottie animation composition for second onboarding screen
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.onboard2))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lottie Animation instead of static image
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Title using Kadwa Bold
        Text(
            text = "Cross-Platform",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 50.dp),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = AppFonts.KadwaFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF395B64),
                fontSize = 28.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Subtitle using Karla Bold
        Text(
            text = "From Insta DMs to whatsapp — Jawafai's got it.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = AppFonts.KarlaFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF395B64),
                fontSize = 16.sp
            )
        )
    }
}

@Composable
fun OnboardingPage3() {
    // Lottie animation composition for third onboarding screen
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.onboard3))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lottie Animation instead of static image
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Title using Kadwa Bold
        Text(
            text = "Personalized",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 50.dp),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = AppFonts.KadwaFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF395B64),
                fontSize = 28.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Subtitle using Karla Bold
        Text(
            text = "Learn your style and preferences for better responses.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = AppFonts.KarlaFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF395B64),
                fontSize = 16.sp
            )
        )
    }
}

@Composable
fun OnboardingPage4() {
    // Lottie animation composition for fourth onboarding screen
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.onboard4))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lottie Animation instead of static image
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Title using Kadwa Bold
        Text(
            text = "Ready to Start",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 50.dp),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = AppFonts.KadwaFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF395B64),
                fontSize = 28.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Subtitle using Karla Bold
        Text(
            text = "Let's get you set up and ready to go!",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = AppFonts.KarlaFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF395B64),
                fontSize = 16.sp
            )
        )
    }
}
