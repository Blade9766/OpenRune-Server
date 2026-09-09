package org.rsmod.content.areas.misc.stronghold

/**
 * One account-security question the doors may ask. [correct] indexes [options]; [explanation] is
 * what the door says after the answer, right or wrong.
 */
class SecurityQuestion(
    val text: String,
    val options: List<String>,
    val correct: Int,
    val explanation: String,
) {
    init {
        require(options.size in 2..3) { "A door question needs two or three answers: $text" }
        require(correct in options.indices) { "Bad answer index for: $text" }
    }
}

/** The questions the doors ask, as listed on the OSRS wiki. */
object StrongholdQuestions {
    val ALL: List<SecurityQuestion> =
        listOf(
            SecurityQuestion(
                "What do you do if someone asks you for your password or bank PIN to make you a " +
                    "member for free?",
                listOf(
                    "Give them the information they asked for.",
                    "Don't tell them anything and ignore them.",
                    "Don't tell them anything and click the 'Report Abuse' button.",
                ),
                correct = 2,
                explanation =
                    "Nobody from Jagex will ever ask for your password or PIN. Never give them " +
                        "out, and report anyone who asks so they can be dealt with.",
            ),
            SecurityQuestion(
                "You have been offered a free giveaway or double XP invitation via in-game chat or " +
                    "email. What should you do?",
                listOf(
                    "Report the incident and do not click any links.",
                    "Respond quickly so as not to miss the offer.",
                ),
                correct = 0,
                explanation =
                    "Such offers are almost always phishing attempts. Jagex announces real events " +
                        "on the official website only. Report the message and never follow its " +
                        "links.",
            ),
            SecurityQuestion(
                "You have been offered a free giveaway or double XP invitation via social media or " +
                    "a livestream. What should you do?",
                listOf(
                    "Respond quickly so as not to miss the offer.",
                    "Report the incident and do not click any links.",
                ),
                correct = 1,
                explanation =
                    "Streams and posts offering free rewards for your login details are scams. " +
                        "Report them and do not click their links.",
            ),
            SecurityQuestion(
                "Is it safe to get someone to level your account?",
                listOf(
                    "Yes, so long as you change your password when they have finished.",
                    "Yes, so long as they don't raise its levels by too much.",
                    "No, you should never allow anyone to use your account.",
                ),
                correct = 2,
                explanation =
                    "Sharing your account is against the rules and anyone you share it with can " +
                        "steal it. Your account is for you alone.",
            ),
            SecurityQuestion(
                "Hey adventurer! You've been randomly selected for a prize of 1 year of free " +
                    "membership! I'm just going to need some of your account details so I can put " +
                    "it on your account!",
                listOf(
                    "Wowee! Let me just write those down for you.",
                    "No way! I'm reporting you to Jagex!",
                    "I'm not sure about this... let me get back to you on it.",
                ),
                correct = 1,
                explanation =
                    "Prizes that need your account details are never real. Report the player and " +
                        "keep your details to yourself.",
            ),
            SecurityQuestion(
                "What is the best security step you can take to keep your registered email secure?",
                listOf(
                    "Set up two-factor authentication with my email provider.",
                    "Have a complicated password set to my email.",
                    "Use an email address just for Old School RuneScape.",
                ),
                correct = 0,
                explanation =
                    "Your email is the key to recovering your account, so protect it with " +
                        "two-factor authentication. A strong password alone is not enough.",
            ),
            SecurityQuestion(
                "What is the best way to secure your account?",
                listOf(
                    "A long and complicated password because you have good memory.",
                    "Two-factor authentication on your account and your registered email.",
                ),
                correct = 1,
                explanation =
                    "Two-factor authentication on both your account and your email means a " +
                        "stolen password alone cannot get anyone in.",
            ),
            SecurityQuestion(
                "How do I set a bank PIN?",
                listOf("Talk to any banker.", "Use the account management section on the website."),
                correct = 0,
                explanation =
                    "Any banker can set a bank PIN for you. A PIN keeps your bank safe even if " +
                        "someone gets into your account.",
            ),
            SecurityQuestion(
                "What should I do if I receive an email asking me to verify my identity or account " +
                    "details due to suspicious activity?",
                listOf(
                    "Email them back with the information it asks for.",
                    "Click the links in the email to visit the website.",
                    "Delete it - it is fake!",
                ),
                correct = 2,
                explanation =
                    "Jagex never asks for your details by email. Emails like that are phishing: " +
                        "delete them and never click their links.",
            ),
            SecurityQuestion(
                "Who can I give my password to?",
                listOf("My friends.", "My brother or sister.", "Nobody."),
                correct = 2,
                explanation =
                    "Your password is yours alone. Even people you trust can lose it or misuse it.",
            ),
            SecurityQuestion(
                "What do I do if my account is compromised?",
                listOf("Post on social media about it.", "Secure my device and reset my password."),
                correct = 1,
                explanation =
                    "First make sure your device is clean, then change your password and enable " +
                        "two-factor authentication so it cannot happen again.",
            ),
            SecurityQuestion(
                "What do I do if a moderator asks me for my account details?",
                listOf(
                    "Tell them whatever they want to know.",
                    "Politely tell them no, and ignore them.",
                    "Politely tell them no, then use the 'Report Abuse' button.",
                ),
                correct = 2,
                explanation =
                    "Real moderators never need your account details. Anyone asking is an " +
                        "impostor, so refuse and report them.",
            ),
            SecurityQuestion(
                "A player trades you some valuable items, provides you with a bond, then asks if " +
                    "you want to share your account so he can help you make progress. How do you " +
                    "respond?",
                listOf(
                    "Decline the offer and report that player.",
                    "Give the player access since they are a higher level.",
                    "Tell them you'll trade your account info for theirs.",
                ),
                correct = 0,
                explanation =
                    "Gifts are a common way to earn trust before stealing an account. Never share " +
                        "your login, no matter what you are offered.",
            ),
            SecurityQuestion(
                "Where is it safe to use my Old School RuneScape password?",
                listOf(
                    "On Old School RuneScape and all fansites.",
                    "Only on the Old School RuneScape website.",
                    "On all websites I visit.",
                ),
                correct = 1,
                explanation =
                    "Use your password only on the official website and game client. A fansite " +
                        "that asks for it is trying to steal it.",
            ),
            SecurityQuestion(
                "Whose responsibility is it to keep your account secure?",
                listOf("Me.", "Jagex.", "My internet provider."),
                correct = 0,
                explanation =
                    "Jagex provides the tools, but only you can keep your password private and " +
                        "your computer clean.",
            ),
            SecurityQuestion(
                "Psst! Adventurer! I've got a special offer for you, but you're going to have to " +
                    "trust me. If you give me some gold coins, I'll give you back twice whatever " +
                    "you gave me! How does that sound?",
                listOf(
                    "No way! You'll just take my gold for your own! Reported!",
                    "I'm not sure... but giving a few coins to test it won't hurt.",
                    "WoW! You're so generous, thank you! Here's all my gold.",
                ),
                correct = 0,
                explanation =
                    "Doubling money is a scam. They will keep whatever you give them, so report " +
                        "the player instead.",
            ),
            SecurityQuestion(
                "Is it okay to buy an Old School RuneScape account?",
                listOf(
                    "Yes if it is from someone you know.",
                    "Yes if you pay for it with GP.",
                    "No, you should never buy an account.",
                ),
                correct = 2,
                explanation =
                    "Buying accounts is against the rules, and the seller can recover the account " +
                        "at any time and take it back.",
            ),
            SecurityQuestion(
                "My friend asks me for my password so that he can do a difficult quest for me. Do I " +
                    "give it to him?",
                listOf(
                    "Yes. He is my best friend and I've already spent ages trying this quest.",
                    "Don't give them my password.",
                    "Let them do the quest, but in the same room the whole time.",
                ),
                correct = 1,
                explanation =
                    "Never share your password, even with your best friend. Do the quest " +
                        "yourself - the reward is much sweeter that way.",
            ),
            SecurityQuestion(
                "A player tells you to search for a video online, click the link in the " +
                    "description and comment on the forum post to win a cash prize. What do you do?",
                listOf(
                    "Do what they ask, using the provided link in the video description.",
                    "Report the player for phishing.",
                    "Tell your friends so they can get free gold too.",
                ),
                correct = 1,
                explanation =
                    "Links promising prizes lead to phishing sites that steal your login. Report " +
                        "the player and stay away from the link.",
            ),
            SecurityQuestion(
                "Adventurer, I'll trade items with you for an amazing price, but you've got to come " +
                    "immediately to a particular place on a different game world. Hurry up! Come now " +
                    "before you lose out! What do you say?",
                listOf(
                    "Okay, I'll take my valuables there now so we can trade.",
                    "Nope, you're tricking me into going somewhere dangerous.",
                ),
                correct = 1,
                explanation =
                    "Luring you somewhere dangerous with your valuables is a classic trick. " +
                        "Trade only where it is safe, and never in a hurry.",
            ),
            SecurityQuestion(
                "Which of these is an important characteristic of a secure password?",
                listOf(
                    "It incorporates your real name or birthday.",
                    "It's never used on other websites or accounts.",
                    "It's never changed over many months or years.",
                ),
                correct = 1,
                explanation =
                    "A password used on other sites is only as safe as the weakest of them. Use a " +
                        "unique password for your account.",
            ),
            SecurityQuestion(
                "You're watching a stream by someone claiming to be Jagex offering double XP. What " +
                    "do you do?",
                listOf(
                    "Click the link! I love double XP!",
                    "Report the stream. Real Jagex streams have a 'verified' mark.",
                    "Ignore it.",
                ),
                correct = 1,
                explanation =
                    "Fake streams copy official ones to steal logins. Check for the verified mark " +
                        "and report the impostors.",
            ),
            SecurityQuestion(
                "Will Jagex prevent me from saying my PIN in game?",
                listOf("Yes.", "No."),
                correct = 1,
                explanation =
                    "Nothing stops you from typing your PIN in chat, so be careful never to do " +
                        "it. Keep it secret, like your password.",
            ),
            SecurityQuestion(
                "A website claims that they can make me a player moderator. What should I do?",
                listOf("Nothing, it's a fake.", "Give them my account info and password."),
                correct = 0,
                explanation =
                    "Only Jagex chooses player moderators, and there is no way to apply. Any " +
                        "site claiming otherwise is a scam.",
            ),
            SecurityQuestion(
                "What do I do if I think I have a keylogger or virus?",
                listOf(
                    "Virus scan my device then change my password.",
                    "Change my password then virus scan my device.",
                    "Nothing, it will go away on its own.",
                ),
                correct = 0,
                explanation =
                    "Clean your device first, or the keylogger will simply capture your new " +
                        "password too.",
            ),
            SecurityQuestion(
                "What should you do if another player messages you recommending a website to " +
                    "purchase items and/or gold?",
                listOf(
                    "Check out the website, it never hurts to look around!",
                    "Visit the website in a private browser for added security.",
                    "Do not visit the website and report the player who messaged you.",
                ),
                correct = 2,
                explanation =
                    "Gold-selling sites break the rules and often carry malware or phishing " +
                        "pages. Report the player and stay away.",
            ),
            SecurityQuestion(
                "Can I leave my account logged in while I'm out of the room?",
                listOf("Yes, for up to an hour.", "No.", "Yes, if I'll only be a minute or two."),
                correct = 1,
                explanation =
                    "Anyone who walks past could use your account. Always log out when you step " +
                        "away.",
            ),
            SecurityQuestion(
                "What do you do if someone asks you for your password or bank PIN to make you a " +
                    "player moderator?",
                listOf(
                    "Don't give them the information and send an 'Abuse report'.",
                    "Don't tell them anything and ignore them.",
                    "Give them the information they asked for.",
                ),
                correct = 0,
                explanation =
                    "Player moderators are never chosen this way. Refuse, and report the player " +
                        "so others are protected too.",
            ),
            SecurityQuestion(
                "What is an example of a good bank PIN?",
                listOf(
                    "Your real life bank PIN.",
                    "Your birthday.",
                    "The birthday of a famous person or event.",
                ),
                correct = 2,
                explanation =
                    "A PIN should be something nobody can guess from knowing you, and never a " +
                        "number you use elsewhere.",
            ),
            SecurityQuestion(
                "What should you do if your real-life friend asks for your password so he can " +
                    "check your stats?",
                listOf(
                    "Give them your password since they're a friend in real life.",
                    "Don't give out your password to anyone. Not even close friends.",
                    "Log in for your friend and let them play.",
                ),
                correct = 1,
                explanation =
                    "Your friend can see your stats on the hiscores. Your password stays with you.",
            ),
            SecurityQuestion(
                "A player starts asking you about very specific details linked to your account, " +
                    "such as when you created your account, your birthday date, internet provider " +
                    "etc. How should you react?",
                listOf(
                    "Be friendly and answer the questions.",
                    "Don't share your information and report the player.",
                    "Answer questions and ask the player back for their details.",
                ),
                correct = 1,
                explanation =
                    "Those details are exactly what is needed to recover, and steal, your " +
                        "account. Keep them private and report the player.",
            ),
            SecurityQuestion(
                "How do I remove a hijacker from my account?",
                listOf(
                    "Ask on social media.",
                    "Email Old School RuneScape.",
                    "Use the Account Recovery system.",
                ),
                correct = 2,
                explanation =
                    "The Account Recovery system on the official website is the only way to " +
                        "get a hijacked account back.",
            ),
            SecurityQuestion(
                "You are part way through the Stronghold of Security when you have to answer " +
                    "another question. After you answer the question, you should...",
                listOf(
                    "Click through the text as fast as possible to get your reward sooner.",
                    "Read the text, but forget the info moments later.",
                    "Read the text and follow the advice given.",
                ),
                correct = 2,
                explanation =
                    "The advice here is what keeps your account safe long after you leave. Read " +
                        "it, remember it, and follow it.",
            ),
        )
}
