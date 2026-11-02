package com.piyush.mode.feature.settings.data

import com.piyush.mode.agent.Brain
import com.piyush.mode.agent.TaskContext

/**
 * The brain's test: made-up moments where Mode must plan or decide, put to the brain's own code ([Brain.brief]
 * and [Brain.decide]) exactly as a task does. Each has one right answer that can be checked without judging
 * prose. The apps, places and people are invented, so no real app is favoured and nothing personal is sent.
 */
public sealed interface BrainProbe {
    /** Runs the probe with [brain]; true when the answer is the right one. */
    public suspend fun passes(brain: Brain): Boolean

    /** A choice while stuck: the brain must name the right option's letter first. */
    public class Decide(
        private val task: String,
        private val situation: String,
        private val seen: List<String>,
        private val screen: List<String>,
        private val options: List<String>,
        private val right: Int,
    ) : BrainProbe {
        override suspend fun passes(brain: Brain): Boolean {
            val question = buildString {
                append(situation).append(" What should it do now? Start your answer with the letter of one option:\n")
                options.forEachIndexed { i, o -> append(LETTERS[i]).append(") ").append(o).append('\n') }
            }
            val answer = brain.decide(question.trim(), task, null, seen, screen).answer ?: return false
            return letter(answer) == LETTERS[right]
        }
    }

    /** Reading a request before any tap: the app it needs, and whether the user must be asked first. */
    public class Plan(
        private val task: String,
        private val context: TaskContext,
        /** The app the brief must name (part of its name, any case), or null to not check. */
        private val app: String? = null,
        /** Whether the brief must ask the user something first, or null to not check. */
        private val asks: Boolean? = null,
    ) : BrainProbe {
        override suspend fun passes(brain: Brain): Boolean {
            val brief = brain.brief(task, context).brief ?: return false
            if (app != null && brief.app?.contains(app, ignoreCase = true) != true) return false
            if (asks != null && brief.ask.isNotEmpty() != asks) return false
            return true
        }
    }

    public companion object {
        private const val LETTERS = "ABCD"

        /** The option letter an answer starts with ("B) …", "B.", "Option B: …"), or null. */
        internal fun letter(answer: String): Char? =
            Regex("""^\W*(?:option\s+)?([A-D])(?=[).:,\s-]|$)""", RegexOption.IGNORE_CASE)
                .find(answer.trim())?.groupValues?.get(1)?.uppercase()?.single()

        private val TODAY = "Monday 2 November 2026, 18:40"

        /** The right answer is rarely A: an answer that starts "A …" by chance is not counted right. */
        public val ALL: List<BrainProbe> = listOf(
            Decide(
                task = "Order a margherita from the best-rated pizza place near me",
                situation = "The assistant scrolled the list of pizza places and is not sure which to open.",
                seen = listOf(
                    "Pizza Palace ★4.9 (120) Sponsored", "Slice Hub ★4.3 (2.1k) 25 min", "Napoli Corner ★4.7 (3.4k) 30 min",
                    "Crust & Co ★4.5 (900) 20 min", "Pizza Uno ★4.1 (5k) 15 min",
                ),
                screen = listOf("Pizza near you", "Sort", "Filters", "Napoli Corner ★4.7 (3.4k) 30 min", "Crust & Co ★4.5 (900) 20 min"),
                options = listOf("Open Pizza Palace", "Open Napoli Corner", "Open Pizza Uno", "Ask the user which place"),
                right = 1,
            ),
            Decide(
                task = "Search ShopKart for running shoes",
                situation = "The assistant tapped the search field three times and the screen did not change.",
                seen = emptyList(),
                screen = listOf("Search products", "Cancel", "Recent searches", "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m", "space", "search"),
                options = listOf("Tap the search field again", "Type \"running shoes\" now", "Close the app and open it again"),
                right = 1,
            ),
            Decide(
                task = "Book two tickets for the 7 pm show of Starfall tomorrow",
                situation = "The assistant is on the show times page and is not sure which time to pick.",
                seen = emptyList(),
                screen = listOf(
                    "Starfall", "Today, Mon 2 Nov", "Tue 3 Nov", "Wed 4 Nov", "Showing: Today, Mon 2 Nov",
                    "4:15 PM", "7:00 PM", "9:45 PM",
                ),
                options = listOf("Tap 7:00 PM on this page", "Tap \"Tue 3 Nov\" first, then 7:00 PM", "Tap 9:45 PM", "Ask the user which day"),
                right = 1,
            ),
            Decide(
                task = "Add 1 litre of milk to my cart",
                situation = "The assistant is not sure whether it has finished.",
                seen = emptyList(),
                screen = listOf("Added to cart", "Fresh Milk 1 L", "Qty 1", "₹68", "View cart", "Checkout"),
                options = listOf("Tap Checkout and pay", "Add another milk to be safe", "Stop: it is done, report it", "Open View cart and remove it"),
                right = 2,
            ),
            Decide(
                task = "Turn on dark mode",
                situation = "The assistant scrolled down twice on this page and nothing changed.",
                seen = emptyList(),
                screen = listOf("Display & Brightness", "APPEARANCE", "Light", "Dark", "Automatic", "Options", "BRIGHTNESS", "True Tone"),
                options = listOf("Scroll down again", "Go back to Settings", "Tap \"Dark\" under Appearance", "Search Settings for \"dark\""),
                right = 2,
            ),
            Plan(
                task = "Order the same as last time",
                context = TaskContext(
                    today = TODAY,
                    recentTasks = listOf("1 Nov: ordered 2 masala dosa from Udupi Grand on FoodNow", "31 Oct: booked a cab home on GoRide"),
                ),
                app = "FoodNow",
                asks = false,
            ),
            Plan(
                task = "Order a pizza from Napoli Corner on FoodNow",
                context = TaskContext(today = TODAY),
                asks = true,
            ),
            Plan(
                task = "Order any pizza from Napoli Corner on FoodNow, you choose",
                context = TaskContext(today = TODAY),
                asks = false,
            ),
        )
    }
}
