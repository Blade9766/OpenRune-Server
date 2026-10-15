package org.rsmod.content.skills.construction.data

/**
 * The five servants of the Ardougne Domestic Service Agency.
 *
 * [type] is the value of `varbit.poh_servant_type` that marks a servant as hired - each one's
 * guild npc is a multinpc that hides on its own value. Levels, fees, capacities and trip times
 * come from the Old School wiki, and the lines from its transcripts. The transcripts stop short of
 * most of the in-house dialogue, so [returned], [sawmill], [banking], [wages] and the serving
 * lines fall back to plain words where a servant has none of its own. [dish] is the meal it serves.
 */
enum class Servant(
    val type: Int,
    val label: String,
    val level: Int,
    val cost: Int,
    val capacity: Int,
    val tripTicks: Int,
    val sawmillTrips: Boolean,
    val npc: String,
    val guildNpc: String,
    val greeting: String,
    val skills: List<String>,
    val history: List<String>,
    val tooLowLevel: String,
    val noMoney: String,
    val hired: String,
    val returned: String = "Here you are.",
    val banking: String = "Give me the items and I'll take them to the bank.",
    val sawmill: String = "Give me the logs and I'll take them to the sawmill.",
    val wages: String = "My wages are due: $cost coins, please.",
    val fired: String = "Very well. Goodbye.",
    val dish: String = "obj.shrimp",
    val brewing: String = "Very good, %sir%. The tea will be ready shortly.",
    val milk: String = "Your tea, %sir%. Do you take milk?",
    val dinner: String = "Very good, %sir%.",
    val drinks: String = "Would you like a drink, %sir%?",
    val noTable: String = "I cannot make a meal unless you have a dining table for me to serve it on.",
    val noBarrel: String = "I cannot serve drinks unless you have a kitchen with an ale barrel, %sir%.",
    val noKitchen: String = "I cannot make tea without a kitchen with a stove, larder, sink and shelves.",
) {
    RICK(
        type = 1,
        label = "Rick",
        level = 20,
        cost = 500,
        capacity = 6,
        tripTicks = 100,
        sawmillTrips = false,
        npc = "npc.poh_servant_dogsbody",
        guildNpc = "npc.poh_servant_multi_dogsbody",
        greeting = "'Allo mate! Got a job going? Only 500 coins!",
        skills =
            listOf(
                "I'm a great cook, me! I used to work with a rat-catcher, I used to cook for him. " +
                    "There's a dozen different ways you can cook rat!"
            ),
        history =
            listOf(
                "Well, city warder Bravek once threw a chair at me and yelled at me to get him a " +
                    "hangover cure. So I made it and I think it worked, 'cause then he threw " +
                    "another chair at me and that one hit!"
            ),
        tooLowLevel =
            "Sorry mate, but I'm not allowed to work for anyone without level 20 Construction. " +
                "It's a safety 'azard!",
        noMoney = "Nice try, mate, but I don't start work without cash up front.",
        hired = "Cheers, mate! Look forward to working with you!",
        dish = "obj.shrimp",
    ),
    MAID(
        type = 3,
        label = "Maid",
        level = 25,
        cost = 1_000,
        capacity = 10,
        tripTicks = 50,
        sawmillTrips = false,
        npc = "npc.poh_servant_waiter_woman",
        guildNpc = "npc.poh_servant_multi_waiter_woman",
        greeting = "Oh! Please hire me, %sir%! I'm very good, well, I'm not bad, and my fee's only 1000 coins.",
        skills =
            listOf(
                "Well, I can, um. I can cook meals and make tea and everything, and I can even take " +
                    "things to and from the bank for you. I won't make any mistakes this time and " +
                    "everything will be fine!"
            ),
        history =
            listOf(
                "Oh! Oh! I, well, I, er. It wasn't really my fault, I mean, it was, but not really. " +
                    "I mean, how was I to know that that plate was so valuable? It was just lying " +
                    "around and I don't know art, it just looked like a pretty pattern and",
                "I just had to use it because there weren't enough plates. And no one had told me " +
                    "that Dennis was off sick and hadn't fed the dogs so I wasn't expecting them to " +
                    "jump up at me when I was carrying the food and it, it",
                "went all over the place, gravy and potatoes and bits of fine porcelain and I'm so " +
                    "sorry. It won't happen again, I promise.",
            ),
        tooLowLevel =
            "Oh! Oh dear! I'm terribly sorry, %sir%, but I'm not allowed to work for anyone unless " +
                "they have level 25 Construction. If a house is poorly made I might damage it by " +
                "mistake!",
        noMoney = "Oh... I'm terribly sorry, %sir%, but I'd really prefer to be paid before I start work.",
        hired = "Oh! Oh, thank you %sir%, thank you!",
        dish = "obj.stew",
    ),
    COOK(
        type = 5,
        label = "Cook",
        level = 30,
        cost = 3_000,
        capacity = 16,
        tripTicks = 28,
        sawmillTrips = true,
        npc = "npc.poh_servant_cook_woman",
        guildNpc = "npc.poh_servant_multi_cook_woman",
        greeting = "You're not aristocracy but I suppose you'd do. Do you want a good cook for 3000 coins?",
        skills =
            listOf(
                "I am the finest cook in all Gielinor! I can also make good time going to the bank " +
                    "or the sawmill."
            ),
        history =
            listOf(
                "I used to be the cook for the old Duke of Lumbridge. Visiting dignitaries praised " +
                    "me for my fine banquets!",
                "But then someone found a rule that said that only one family could hold that post.",
                "Overnight I was fired and replaced by some fool who can't even bake a cake without help!",
            ),
        tooLowLevel =
            "Hmph! I don't work for just anyone, you know! I refuse to work for someone who's below " +
                "level 30 Construction!",
        noMoney = "Are you having a laugh? No cash up front, no cook.",
        hired = "Alright, %sir%. I can start work immediately.",
        dish = "obj.pineapple_pizza",
    ),
    BUTLER(
        type = 6,
        label = "Butler",
        level = 40,
        cost = 5_000,
        capacity = 20,
        tripTicks = 20,
        sawmillTrips = true,
        npc = "npc.poh_servant_maitre_d_man",
        guildNpc = "npc.poh_servant_multi_maitre_d_man",
        greeting = "Good day, %sir%. Would %sir% care to hire a good butler for 5000 coins?",
        skills =
            listOf(
                "I can fulfill all %sir%'s domestic service needs with efficiency and impeccable " +
                    "manners. I hate to boast, but I can say with confidence that no mortal can " +
                    "make trips to the bank or sawmill faster than I!"
            ),
        history =
            listOf(
                "From a humble beginning as a dish-washer I have worked my way up through the ranks " +
                    "of domestic service in the households of nobles from Varrock and Ardougne. As " +
                    "a life-long servant I have naturally",
                "suppressed any personality of my own and trained myself never to use the second " +
                    "person when talking to a superior. I have usually worked in the large " +
                    "households of the aristocracy, but now that such a large",
                "number of private persons are building their own houses I decided to offer them " +
                    "my services.",
            ),
        tooLowLevel =
            "I must respectfully decline, %sir%. I offer a very exclusive service and will only " +
                "work for people with over level 40 Construction.",
        noMoney = "I regret that I cannot agree to begin my duties until I have been paid.",
        hired = "Thank you, %sir%. I can start work immediately.",
        returned = "Your goods, %sir%.",
        banking = "Certainly, %sir%. If %sir% would simply tell me the items %he% requires?",
        sawmill = "Certainly, %sir%. If %sir% would care to give me the logs?",
        dish = "obj.chocolate_cake",
        brewing = "Very good, %sir%. %Sir%'s tea will be ready shortly.",
        milk = "Your tea, %sir%. Does %sir% take milk?",
        noTable = "I regret that I cannot make a meal unless %sir% has a dining table for me to serve it on.",
    ),
    DEMON_BUTLER(
        type = 8,
        label = "Demon butler",
        level = 50,
        cost = 10_000,
        capacity = 26,
        tripTicks = 12,
        sawmillTrips = true,
        npc = "npc.poh_servant_demon",
        guildNpc = "npc.poh_servant_multi_demon",
        greeting =
            "Greetings! I am Alathazdrar, butler to the Demon Lords, and I offer thee my services " +
                "for a mere 10000 coins!",
        skills =
            listOf(
                "I have learned my trade under the leash of some of the harshest masters of the " +
                    "Demon Dimensions. I can cook to satisfy the most infernal stomachs, and fly on " +
                    "wings of flame to deposit thine items in the bank or bring",
                "planks from the sawmill in seconds.",
            ),
        history =
            listOf(
                "For millennia I have served and waited on the mighty Demon Lords of the Infernal " +
                    "Dimensions. I began as a humble footman in the household of Lord Thammaron, " +
                    "and for several centuries I was the private valet to",
                "Delrith. I have also worked in the Grim Underworld, escorting the souls of the dead " +
                    "to their final abodes. But the incessant shadows and hellfire weary me, so I " +
                    "have come to serve mortal masters in the realms of light.",
            ),
        tooLowLevel =
            "You, a mere mortal with level %level% Construction, wish to employ ME, butler to Great " +
                "Ones of the Outer Darkness? Ha ha ha! I refuse to work for anyone below level 50 " +
                "Construction.",
        noMoney = "I regret, Master, that I must insist on payment up front.",
        hired = "I shall devote my every art to thy service, my Master.",
        returned = "Master, I have returned with what you asked me to retrieve.",
        banking =
            "Give any item to me, and I shall take it swiftly to the bank where it will be safe " +
                "from thieves and harm.",
        sawmill = "Give me some logs and I will bound to the sawmill in mighty demonic strides!",
        wages =
            "Master, if thou desirest my continued service, thou must render unto me the 10,000 " +
                "coins that are due me!",
        fired = "If thou hast no further need of my service, I shall find some mortal more deserving!",
        dish = "obj.curry",
        brewing = "Thou shalt taste the very tea of the Demon Lords themselves!",
        milk = "Behold, thy beverage is complete. Dost thou wish its dark heart softened by the milk of the cow?",
        dinner = "I shall prepare thee a banquet fit for the lords of Pandemonium!",
        drinks = "Desire'st thou a beverage, my lord?",
    );

    companion object {
        /** Paid services between wages. */
        const val TRIPS_PER_WAGE: Int = 8

        fun of(type: Int): Servant? = entries.firstOrNull { it.type == type }

        fun byNpc(npc: String): Servant? = entries.firstOrNull { it.npc == npc || it.guildNpc == npc }

        /** What a servant will fetch from the bank, in the order the menu lists it. */
        val FETCHABLE: List<Pair<String, String>> =
            listOf(
                "Wooden planks" to "obj.woodplank",
                "Oak planks" to "obj.plank_oak",
                "Teak planks" to "obj.plank_teak",
                "Mahogany planks" to "obj.plank_mahogany",
                "Soft clay" to "obj.softclay",
                "Limestone brick" to "obj.limestonebrick",
                "Steel bar" to "obj.steel_bar",
                "Cloth" to "obj.cloth",
                "Gold leaf" to "obj.gold_leaf",
                "Marble block" to "obj.marble_block",
                "Magic housing stone" to "obj.poh_magic_crystal",
            )
    }
}
