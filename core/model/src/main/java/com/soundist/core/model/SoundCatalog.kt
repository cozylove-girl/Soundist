package com.soundist.core.model

object SoundCatalog {
    private val groups = linkedMapOf(
        SoundCategory.NATURE to listOf("river","waves","campfire","wind","howling-wind","wind-in-trees","waterfall","walk-in-snow","walk-on-leaves","walk-on-gravel","droplets","jungle"),
        SoundCategory.RAIN to listOf("light-rain","heavy-rain","thunder","rain-on-window","rain-on-car-roof","rain-on-umbrella","rain-on-tent","rain-on-leaves"),
        SoundCategory.ANIMAL to listOf("birds","seagulls","crickets","wolf","owl","frog","dog-barking","horse-gallop","cat-purring","crows","whale","beehive","woodpecker","chickens","cows","sheep"),
        SoundCategory.URBAN to listOf("highway","road","ambulance-siren","busy-street","crowd","traffic","fireworks"),
        SoundCategory.PLACE to listOf("cafe","airport","church","temple","construction-site","underwater","crowded-bar","night-village","subway-station","office","supermarket","carousel","laboratory","laundry-room","restaurant","library"),
        SoundCategory.TRANSPORT to listOf("train","inside-a-train","airplane","submarine","sailboat","rowing-boat"),
        SoundCategory.OBJECT to listOf("keyboard","typewriter","paper","clock","wind-chimes","singing-bowl","ceiling-fan","dryer","slide-projector","boiling-water","bubbles","tuning-radio","morse-code","washing-machine","vinyl-effect","windshield-wipers"),
        SoundCategory.NOISE to listOf("white-noise","pink-noise","brown-noise"),
    )
    val sounds: List<Sound> = groups.flatMap { (category, ids) -> ids.map { id ->
        val folder = when(category) { SoundCategory.NATURE -> "nature"; SoundCategory.ANIMAL -> "animals"; SoundCategory.URBAN -> "urban"; SoundCategory.PLACE -> "places"; SoundCategory.TRANSPORT -> "transport"; SoundCategory.OBJECT -> "things"; SoundCategory.NOISE -> "noise"; SoundCategory.RAIN -> "rain" }
        Sound(id, id.replace('-', ' ').replaceFirstChar { it.uppercase() }, category, "asset:///sounds/$folder/$id.${if (category == SoundCategory.NOISE) "wav" else "mp3"}")
    }}
    init { check(sounds.size == 84) { "Soundist catalogue must contain exactly 84 sounds" } }
}
