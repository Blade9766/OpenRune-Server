package org.rsmod.content.quest.area.lumbridge.tearsofguthix

/** The adventures a player can tell Juna about, each tied to the quest it retells. */
internal class JunaStory(val quest: String, val told: String, val reply: String? = null)

internal val JUNA_STORIES =
    listOf(
        JunaStory(
            "quest_cooksassistant",
            "... and in the end I found all the ingredients, so the Duke of Lumbridge had a birthday " +
                "cake after all.",
            "Ah, a happy ending. It would not be good for such an anniversary to go unmarked.",
        ),
        JunaStory(
            "quest_demonslayer",
            "... So I destroyed the demon Delrith and saved Varrock!",
            "I remember Delrith. A most unpleasant character; I am glad he has been dispatched.",
        ),
        JunaStory(
            "quest_druidicritual",
            "... So Kaqemeex taught me how to use the Herblore skill.",
            "A generous reward indeed.",
        ),
        JunaStory(
            "quest_blackknightsfortress",
            "... So in the end the Black Knights were defeated by cabbage!",
            "One should never underestimate the uses of the vegetables of Guthix.",
        ),
        JunaStory(
            "quest_biohazard",
            "... So it turned out there was no plague after all!",
            "Deception of the people by their rulers is a terrible thing.",
        ),
        JunaStory(
            "quest_anothersliceofham",
            "... and the Dorgeshuun city is now connected to Keldagrim by a rapid train line.",
            "I am always glad to hear tales of the Dorgeshuun.",
        ),
        JunaStory(
            "quest_deathtothedorgeshuun",
            "... Sigmund escaped again, but Zanik and I destroyed the machine and Dorgesh-Kaan was saved!",
            "Zanik still has her destiny to fulfil, and I have a feeling you will have a part in that.",
        ),
        JunaStory(
            "quest_gertrudescat",
            "...I returned Fluffs safely to Gertrude, and then she gave me a cat of my own!",
            "Cats are one of the most mysterious creatures of Guthix. I hope you take your " +
                "responsibility seriously.",
        ),
        JunaStory(
            "quest_fishingcontest",
            "... and after I had won the fishing contest, the Dwarves let me go under White Wolf Mountain.",
            "Fishing? A strange test of worthiness to pass through an underground tunnel!",
        ),
        JunaStory(
            "quest_goblindiplomacy",
            "... So the goblins ended up wearing the armour colour they had to start off with!",
            "Poor silly goblins! Their race had such potential, if only they could rise above their " +
                "petty squabbles.",
        ),
        JunaStory(
            "quest_impcatcher",
            "... It took some time, but I finally got all four beads back, and Mizgog gave me my reward.",
            "Imps! I remember the age of great war, when armies of Zamorak's imps bloodied the ankles " +
                "of the other gods' creatures.",
        ),
        JunaStory(
            "quest_witchspotion",
            "... and once I got her all the ingredients, Hetty's potion increased my magical power!",
            "I see you are on your way to becoming strong in the magical arts.",
        ),
        JunaStory(
            "quest_witchshouse",
            "... All that trouble just to get a ball out of someone's garden!",
            "It is often hard to know how long a task will take when we begin it.",
        ),
        JunaStory(
            "quest_ernestthechicken",
            "... So once I had found all the parts for the machine, poor Ernest could be himself once more.",
            "That was a good deed. It is a terrible thing to be locked out of one's natural form.",
        ),
        JunaStory(
            "quest_dorics",
            "... So once I had got all the ores he wanted, Doric let me use his anvils.",
            "Such a small task hardly seems worthy of the term 'quest'.",
        ),
        JunaStory(
            "quest_digsite",
            "... and the examiner was very impressed that I had discovered an ancient altar of Zaros.",
            "Zaros? I had not heard that name for a thousand years even before the start of my " +
                "sojourn here.",
        ),
        JunaStory(
            "quest_golem",
            "... and I had to reprogram the golem before it would believe that the demon was dead.",
            "I remember well the battle of Uzer.",
        ),
        JunaStory(
            "quest_creatureoffenkenstrain",
            "... But in the end I stopped Fenkenstrain from continuing his horrible experiments.",
            "With the power to create life comes responsibility. I see that Fenkenstrain was not up to " +
                "the task.",
        ),
        JunaStory(
            "quest_dwarfcannon",
            "... and that was how I fixed the Dwarf multicannon.",
            "So war still rages in the world above? Will you never tire of creating machines of " +
                "destruction?",
        ),
        JunaStory(
            "quest_fairytale1",
            "... So I defeated the tanglefoot and returned the Fairy Queen's Enchanted Secateurs to the " +
                "Fairy Godfather.",
            "These fairies seem to be well versed in the powers of nature.",
        ),
        JunaStory(
            "quest_vampyreslayer",
            "... and once the vampyre was dead, the people of Draynor no longer lived in fear.",
        ),
        JunaStory("quest_waterfall", "... and that was how I retrieved the treasure from the waterfall."),
        JunaStory(
            "quest_insearchofthemyreque",
            "... So I suppose it was my fault that the young members of the Myreque were killed by " +
                "Vanstrom.",
        ),
        JunaStory(
            "quest_junglepotion",
            "... and once I had gathered all the herbs, Trufitus Shakaya was able to commune with his gods.",
        ),
        JunaStory(
            "quest_holygrail",
            "... and out of all the Knights of the Round Table, it was I who found the Holy Grail.",
        ),
        JunaStory(
            "quest_heroes",
            "... So after I had retrieved all the items, I became a member of the Heroes' Guild!",
        ),
        JunaStory(
            "quest_legends",
            "... and when I had completed all the tasks, I became a member of the Legends' Guild!",
        ),
        JunaStory(
            "quest_knightssword",
            "... So that was how I found the Imcando Dwarves and got the Knight a new sword.",
        ),
        JunaStory("quest_familycrest", "... So all three parts of the family crest were reunited."),
        JunaStory(
            "quest_dragonslayer1",
            "... So with Elvarg the dragon dead, the master of the Champions' Guild let me in, and I was " +
                "able to wear Rune Plate!",
        ),
        JunaStory(
            "quest_elementalworkshop1",
            "... and once I had repaired the Elemental Workshop I was able to make an Elemental Shield.",
        ),
    )
