package com.soundist.feature.listening

import androidx.compose.ui.graphics.vector.ImageVector
import com.soundist.core.designsystem.*

/** 1:1 of the App.tsx SoundIcon byId mapping; icon glyphs come from FrontendIcons.kt. */
fun soundIcon(id: String): ImageVector = when (id) {
    // NATURE
    "river", "waves" -> waves
    "campfire" -> gicampfire
    "wind" -> wind
    "howling-wind" -> giwindslap
    "wind-in-trees", "jungle" -> trees
    "waterfall" -> giwaterfall
    "walk-in-snow", "walk-on-leaves", "walk-on-gravel" -> footprints
    "droplets" -> mdwaterdrop
    // RAIN
    "light-rain", "heavy-rain", "thunder", "rain-on-window", "rain-on-tent", "rain-on-leaves" -> cloudRain
    "rain-on-car-roof" -> facarside
    "rain-on-umbrella" -> umbrella
    // ANIMAL
    "birds" -> pibirdfill
    "seagulls" -> giseagull
    "crickets" -> gicricket
    "wolf" -> giwolfhead
    "owl" -> bird
    "frog" -> gifrog
    "dog-barking" -> pidogbold
    "horse-gallop" -> fahorsehead
    "cat-purring" -> facat
    "crows" -> facrow
    "whale" -> giwhaletail
    "beehive" -> gitreebeehive
    "woodpecker" -> giegyptianbird
    "chickens" -> gichicken
    "cows" -> gicow
    "sheep" -> gisheep
    // URBAN
    "highway" -> car
    "road" -> faroad
    "busy-street" -> bssoundwave
    "traffic" -> bisolidtraffic
    "ambulance-siren" -> pisirenbold
    "crowd" -> bspeoplefill
    "fireworks" -> risparkling2fill
    "crowded-bar" -> tbbeerfilled
    // PLACE
    "cafe" -> bisolidcoffeealt
    "airport", "airplane" -> bisolidplanealt
    "church" -> fachurch
    "temple" -> mdtemplebuddhist
    "construction-site" -> mdconstruction
    "underwater" -> tbscubamask
    "night-village" -> building2
    "subway-station" -> fasubway
    "office" -> hiofficebuilding
    "supermarket" -> fashoppingbasket
    "carousel" -> gicarousel
    "laboratory" -> aifillexperiment
    "laundry-room", "dryer" -> bisoliddryer
    "washing-machine" -> giwashingmachine
    "restaurant" -> iorestaurant
    "library" -> fabookopen
    // TRANSPORT
    "train", "inside-a-train" -> bisolidtrain
    "submarine" -> gisubmarine
    "sailboat" -> gisailboat
    "rowing-boat" -> tbsailboat
    // OBJECT
    "keyboard" -> bsfillkeyboardfill
    "typewriter" -> fakeyboard
    "paper" -> rifilepaper2fill
    "clock" -> faclock
    "wind-chimes" -> giwindchimes
    "singing-bowl" -> tbbowlfilled
    "ceiling-fan" -> fafan
    "slide-projector" -> gifilmprojector
    "boiling-water" -> mdwaterdrop
    "bubbles" -> ribubblechartfill
    "tuning-radio" -> mdradio
    "morse-code" -> ioiosradio
    "vinyl-effect" -> pivinylrecord
    "windshield-wipers" -> tbwiper
    // NOISE
    "white-noise", "pink-noise", "brown-noise" -> gisoundwaves
    // fallback
    else -> volume2
}
