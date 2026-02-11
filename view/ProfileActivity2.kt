// ...existing code...
class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProfileScreen()
        }
    }
}

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val profileImage = painterResource(id = R.drawable.profile)
    val background = painterResource(id = R.drawable.background)

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = background,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        ProfileContent(
            profileImage = profileImage,
            userName = "Manisha",
            userEmail = "Manisha@example.com",
            onSave = { name, email ->
                Toast.makeText(context, "Saved: $name, $email", Toast.LENGTH_SHORT).show()
            },
            onChangePassword = {
                Toast.makeText(context, "Change password clicked", Toast.LENGTH_SHORT).show()
            },
            onImageChange = {
                Toast.makeText(context, "Open image picker", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ProfileContent(
    profileImage: Painter,
    userName: String,
    userEmail: String,
    onSave: (String, String) -> Unit,
    onChangePassword: () -> Unit,
    onImageChange: () -> Unit
) {
    var name by remember { mutableStateOf(userName) }
    var email by remember { mutableStateOf(userEmail) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Edit Profile",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF004D40),
                fontFamily = KaiseiDecolFontFamily
            )
            Spacer(modifier = Modifier.height(40.dp))
        }

        item {
            Box(modifier = Modifier.size(130.dp)) {
                Image(
                    painter = profileImage,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                        .clickable { onImageChange() },
                    contentScale = ContentScale.Crop
                )

                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = Color(0xFF004D40),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.White, CircleShape)
                        .padding(4.dp)
                        .size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(
                        "Name",
                        fontFamily = KaiseiDecolFontFamily
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = {
                    Text(
                        "Email",
                        fontFamily = KaiseiDecolFontFamily
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Button(
                onClick = { onSave(name, email) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006064)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Save Changes",
                    color = Color.White,
                    fontFamily = KaiseiDecolFontFamily
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onChangePassword,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Change Password",
                    fontFamily = KaiseiDecolFontFamily
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp)) // Optional space at bottom
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProfileActivityPreview() {
    ProfileScreen()
}
