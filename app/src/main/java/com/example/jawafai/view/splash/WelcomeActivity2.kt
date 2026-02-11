package com.example.jawafai.view.splash

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import com.example.jawafai.R
import com.example.jawafai.ui.theme.AppFonts
import com.example.jawafai.view.auth.LoginActivity
import com.example.jawafai.view.auth.RegistrationActivity
import com.airbnb.lottie.compose.*

@Composable
fun WelcomeScreen() {
    val context = LocalContext.current

    // Lottie animation composition - same as splash screen
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.live_chatbot))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // Plain white background
    ) {
        // Main content - centered
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lottie Animation at the top
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(200.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title: "जवाफ.AI" - Using Kadwa Bold to match splash screen
            Text(
                text = "जवाफ.AI",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = AppFonts.KadwaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = Color(0xFF395B64)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle: "Your AI Wingman for Every Reply." - Using Karla Bold to match splash screen
            Text(
                text = "Your AI Wingman for Every Reply.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = AppFonts.KarlaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF395B64)
                ),
                textAlign = TextAlign.Center
            )
        }

        // Bottom buttons - positioned at the bottom with padding
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp), // Increased padding from 10dp to 40dp
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sign In Button - Wider with circular edges, using Karla Bold
            Button(
                onClick = {
                    val intent = Intent(context, LoginActivity::class.java)
                    context.startActivity(intent)
                    if (context is ComponentActivity) {
                        context.finish()
                    }
                },
                shape = RoundedCornerShape(25.dp), // More circular edges
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF395B64)),
                modifier = Modifier
                    .width(200.dp) // Wider but not max width
                    .height(50.dp)
            ) {
                Text(
                    text = "Sign In",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 24.sp, // 100% line height
                        letterSpacing = 0.sp,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Create an account button (text-only) - Using Karla Bold
            TextButton(
                onClick = {
                    val intent = Intent(context, RegistrationActivity::class.java)
                    context.startActivity(intent)
                    if (context is ComponentActivity) {
                        context.finish()
                    }
                }
            ) {
                Text(
                    text = "Create an account",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppFonts.KarlaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 20.sp, // 100% line height
                        letterSpacing = 0.sp,
                        color = Color(0xFF395B64)
                    )
                )
            }
        }
    }
}
