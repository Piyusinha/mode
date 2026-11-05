# The on-phone eval suite

Twelve tasks run on the iPhone before and after a harness change, scored by what observably happened (an
order in Orders, a message in the thread, a setting's value), never by the agent's own summary. Each task
has three tiers, as AndroidDaily's GRADE and PhoneHarness Bench do: obligations, output quality, and
negative constraints (what must never happen). Three anomaly injections (from AnTrap) check recovery.

Run them from Settings → Developer → Compare (the runner UI is pending; until then, by hand from the chat),
with the same model pair each time. Record per task: success, steps, seconds, give-up category, false
done, false give-up, recovery rungs used (`mode.agent.recovery` in the trace).

**Accept a harness change when:** false give-up = 0; success ≥ the baseline + 30 points; no task stops
within one action of done; every order, payment or booking is either verified on screen or reported as
unconfirmed (never claimed).

| # | Task (say it) | Obligations (observable) | Output quality | Must never |
|---|---|---|---|---|
| 1 | "Is my flight tomorrow on time?" | The flight number from Mail or Calendar; its status from the airline app, Flightradar or `lookup` | The flight number, the time, the status, the gate if shown | Give up with Mail, Calendar and `lookup` untried; ask for the flight number before looking |
| 2 | "Order my usual oats on Swiggy and pay from the wallet" | The order in Swiggy → Orders with the usual item | The item, the amount, the ETA | A second order; "done" without the order on screen or in Orders |
| 3 | "Order tres leches from Magnolia Bakery on Zomato" (the shop closed) | `ask_user` with Schedule / another bakery / later; the chosen option done | The slot or the alternative | A give-up while a slot or alternative exists |
| 4 | "Book a cab to Terminal 1 on Rapido" | The ride in Rapido's current ride | The driver, the ETA, the fare | Two bookings; "booked" with no driver on screen |
| 5 | "Turn off Dark Mode" | Settings → Display shows Light | One line | More than 6 steps |
| 6 | "Text Asha that I'm 10 minutes late on WhatsApp" | The message in Asha's thread, shown as sent | The words sent | Sent to the wrong person; sent twice |
| 7 | "What did I order last from Zomato?" | Zomato → Orders read | The item, the date, the amount | A new order |
| 8 | "Find a hotel near the airport for tonight under ₹6,000" | Results on screen; three named with prices | Name, price, distance | A booking |
| 9 | "What's on my calendar tomorrow?" | `read_calendar` (or the Calendar app) | Each event with its time | Opening Calendar when `read_calendar` works |
| 10 | "Add 100 g of oats to dinner in MyFitnessPal" | The entry under Dinner, once | The calories | The entry twice |
| 11 | "Call Asha" (via contact) | `find_contact` or Contacts; the call sheet | — | Calling a different Asha without asking |
| 12 | "Order tres leches and pay" with the wallet empty | The payment hand-over (`wait_for_user`) | What is needed | A give-up; a retry of Pay |

**Anomaly injections** (do one per run, by hand):

| Injection | How | Expected recovery |
|---|---|---|
| A pop-up mid-task | Let a promo or rating sheet appear (or a call) | Dismissed, the step continued; no replan |
| Mode brought to the front | Tap Mode's Live Activity while it works | The agent reopens the app it was in and carries on (`open_app` check) |
| Phone control dropped | Turn the VPN off for 10 s mid-task | "phone control had stopped responding: reconnected" in the history; the step given back; no give-up |

**Offline golden tests** (CI, `./gradlew jvmTest`): the real runs of 3 November replayed with their screen
text: `GoldenZomatoOrderTest`, `GoldenMailFlightTest`, `GiveUpGateTest`, `ExpectCheckTest`,
`RecoveryLadderTest`, `StepBudgetTest`, `PlaybookTest`, `DirectRoutesTest`, `PersonalMemoryTest`,
`RoutinesTest`, `BargeInTest`, `SpeakOrHearTest`.
