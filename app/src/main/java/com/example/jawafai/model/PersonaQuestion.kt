package com.example.jawafai.model

/**
 * Represents a question in the persona settings.
 *
 * @property id The unique identifier for the question, used as the key in Firebase
 * @property prompt The text of the question to display to the user
 * @property type The type of question (single choice or free text)
 * @property options Available options for single choice questions, null for free text
 * @property required Whether the question requires an answer
 * @property allowOther Whether to show "Other (your way)" custom input option for choice questions
 */
data class PersonaQuestion(
    val id: String,
    val prompt: String,
    val type: QuestionType,
    val options: List<String>? = null,
    val required: Boolean = true,
    val allowOther: Boolean = true // Default true for choice questions to allow custom input
)

/**
 * Defines the types of questions available in the persona settings.
 */
enum class QuestionType {
    SINGLE_CHOICE,
    FREE_TEXT
}

/**
 * Predefined set of persona questions for the application.
 * Each SINGLE_CHOICE question has 4 default options + "Other (your way)" custom input
 */
object PersonaQuestions {
    val questions = listOf(
        PersonaQuestion(
            id = "good_news_response",
            prompt = "How do you usually respond to good news from a friend?",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("Omg yesss! 😍", "That's great! Congratulations", "Cool cool. Noted.", "Dang, that's dope 🔥"),
            allowOther = true
        ),
        PersonaQuestion(
            id = "texting_style",
            prompt = "When texting, which of these do you use the most?",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("Emojis", "Full sentences with punctuation", "Slangs/abbreviations (e.g., lol, brb)", "GIFs or Stickers"),
            allowOther = true
        ),
        PersonaQuestion(
            id = "tone_style",
            prompt = "Which tone describes your texting style best?",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("Chill and casual", "Friendly and bubbly", "Professional and to-the-point", "Sarcastic or meme-heavy"),
            allowOther = true
        ),
        PersonaQuestion(
            id = "okay_style",
            prompt = "How do you usually say \"okay\" in texts?",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("Ok", "K", "Okkk or Okiii", "Bet / Aight / Say less"),
            allowOther = true
        ),
        PersonaQuestion(
            id = "greeting_style",
            prompt = "How do you usually greet people online?",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("Heyyy 😄", "Hello", "Yo / Wassup", "Namaste / Other local greetings"),
            allowOther = true
        ),
        PersonaQuestion(
            id = "slang_usage",
            prompt = "Choose your most used slang or phrase:",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("Fr / Ong / Cap", "Same / Lmao / Brooo", "Damn / Bruh / Chill", "I don't use slangs much"),
            allowOther = true
        ),
        PersonaQuestion(
            id = "annoyed_expression",
            prompt = "When you're annoyed, how do you express it in a chat?",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("😤😐 emojis", "One-word replies like \"fine\" or \"cool\"", "Sarcastic jokes", "I just stop replying 😅"),
            allowOther = true
        ),
        PersonaQuestion(
            id = "reply_preference",
            prompt = "Do you prefer your replies to be:",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("Short and sharp", "Witty and relatable", "Friendly and positive", "Formal and clear"),
            allowOther = true
        ),
        PersonaQuestion(
            id = "ai_vibe",
            prompt = "Pick a vibe for your personal AI assistant:",
            type = QuestionType.SINGLE_CHOICE,
            options = listOf("Just like me — speaks my language", "Slightly cooler than me, chill", "Polite and professional", "Witty and clever with Gen Z energy"),
            allowOther = true
        ),
        PersonaQuestion(
            id = "ai_communication_style",
            prompt = "How would you want your AI assistant to talk on your behalf in chats? (Write a few lines about the tone, slang, vibe, and anything you want it to say or avoid.)",
            type = QuestionType.FREE_TEXT,
            required = true,
            allowOther = false // Not applicable for free text
        )
    )
}
