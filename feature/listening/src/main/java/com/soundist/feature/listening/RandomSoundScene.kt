package com.soundist.feature.listening

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.random.Random

data class GeneratedSoundScene(val signature: String, val sounds: Map<String, Float>, val title: String)

private enum class SoundRole { ACCENT, BASE, DETAIL, RARE, TEXTURE }
private enum class SceneTier { RICH, SMALL, STANDARD }
private data class CatalogSound(val categoryId: String, val id: String, val role: SoundRole)
private data class CatalogCategory(val id: String, val sounds: List<CatalogSound>)
private data class HistoryEntry(val categoryIds: List<String>, val soundIds: List<String>)
private data class SoundProfile(val role: SoundRole, val volume: ClosedFloatingPointRange<Float>)
private data class TitleRule(val categories: List<String>?, val ids: List<String>?, val titles: List<String>)

private const val HISTORY_LIMIT = 8

/** sound-scenes.ts soundCatalog (identical order to ListeningCatalog.SoundCatalog.items). */
private val CATALOG_CATEGORIES: List<Pair<String, List<String>>> = listOf(
    "nature" to listOf("river", "waves", "campfire", "wind", "howling-wind", "wind-in-trees", "waterfall", "walk-in-snow", "walk-on-leaves", "walk-on-gravel", "droplets", "jungle"),
    "rain" to listOf("light-rain", "heavy-rain", "thunder", "rain-on-window", "rain-on-car-roof", "rain-on-umbrella", "rain-on-tent", "rain-on-leaves"),
    "animals" to listOf("birds", "seagulls", "crickets", "wolf", "owl", "frog", "dog-barking", "horse-gallop", "cat-purring", "crows", "whale", "beehive", "woodpecker", "chickens", "cows", "sheep"),
    "urban" to listOf("highway", "road", "ambulance-siren", "busy-street", "crowd", "traffic", "fireworks"),
    "places" to listOf("cafe", "airport", "church", "temple", "construction-site", "underwater", "crowded-bar", "night-village", "subway-station", "office", "supermarket", "carousel", "laboratory", "laundry-room", "restaurant", "library"),
    "transport" to listOf("train", "inside-a-train", "airplane", "submarine", "sailboat", "rowing-boat"),
    "things" to listOf("keyboard", "typewriter", "paper", "clock", "wind-chimes", "singing-bowl", "ceiling-fan", "dryer", "slide-projector", "boiling-water", "bubbles", "tuning-radio", "morse-code", "washing-machine", "vinyl-effect", "windshield-wipers"),
    "noise" to listOf("white-noise", "pink-noise", "brown-noise"),
)

/** sound-scenes.ts DEFAULT_CATEGORY_ROLE. */
private val DEFAULT_CATEGORY_ROLE: Map<String, SoundRole> = mapOf(
    "animals" to SoundRole.DETAIL,
    "nature" to SoundRole.BASE,
    "noise" to SoundRole.BASE,
    "places" to SoundRole.TEXTURE,
    "rain" to SoundRole.BASE,
    "things" to SoundRole.DETAIL,
    "transport" to SoundRole.TEXTURE,
    "urban" to SoundRole.TEXTURE,
)

/** sound-scenes.ts ROLE_VOLUME. */
private val ROLE_VOLUME: Map<SoundRole, ClosedFloatingPointRange<Float>> = mapOf(
    SoundRole.ACCENT to .12f.. .3f,
    SoundRole.BASE to .32f.. .56f,
    SoundRole.DETAIL to .1f.. .28f,
    SoundRole.RARE to .08f.. .2f,
    SoundRole.TEXTURE to .22f.. .4f,
)

/** sound-scenes.ts SOUND_PROFILES — per-sound role + volume profile (84 entries). */
private val SOUND_PROFILES: Map<String, SoundProfile> = mapOf(
    "airplane" to SoundProfile(SoundRole.RARE, .1f.. .2f),
    "airport" to SoundProfile(SoundRole.RARE, .12f.. .22f),
    "ambulance-siren" to SoundProfile(SoundRole.RARE, .06f.. .14f),
    "beehive" to SoundProfile(SoundRole.ACCENT, .1f.. .22f),
    "birds" to SoundProfile(SoundRole.DETAIL, .12f.. .26f),
    "boiling-water" to SoundProfile(SoundRole.DETAIL, .12f.. .26f),
    "brown-noise" to SoundProfile(SoundRole.BASE, .24f.. .42f),
    "bubbles" to SoundProfile(SoundRole.DETAIL, .1f.. .24f),
    "busy-street" to SoundProfile(SoundRole.TEXTURE, .18f.. .32f),
    "cafe" to SoundProfile(SoundRole.TEXTURE, .22f.. .38f),
    "campfire" to SoundProfile(SoundRole.BASE, .3f.. .52f),
    "carousel" to SoundProfile(SoundRole.RARE, .1f.. .2f),
    "cat-purring" to SoundProfile(SoundRole.DETAIL, .1f.. .22f),
    "ceiling-fan" to SoundProfile(SoundRole.TEXTURE, .18f.. .34f),
    "chickens" to SoundProfile(SoundRole.RARE, .08f.. .18f),
    "church" to SoundProfile(SoundRole.TEXTURE, .16f.. .3f),
    "clock" to SoundProfile(SoundRole.DETAIL, .1f.. .22f),
    "construction-site" to SoundProfile(SoundRole.RARE, .08f.. .18f),
    "cows" to SoundProfile(SoundRole.RARE, .08f.. .18f),
    "crickets" to SoundProfile(SoundRole.DETAIL, .12f.. .26f),
    "crowd" to SoundProfile(SoundRole.TEXTURE, .12f.. .26f),
    "crowded-bar" to SoundProfile(SoundRole.RARE, .1f.. .22f),
    "crows" to SoundProfile(SoundRole.RARE, .07f.. .16f),
    "dog-barking" to SoundProfile(SoundRole.RARE, .06f.. .14f),
    "droplets" to SoundProfile(SoundRole.DETAIL, .14f.. .3f),
    "dryer" to SoundProfile(SoundRole.TEXTURE, .16f.. .3f),
    "fireworks" to SoundProfile(SoundRole.RARE, .06f.. .16f),
    "frog" to SoundProfile(SoundRole.ACCENT, .1f.. .22f),
    "heavy-rain" to SoundProfile(SoundRole.ACCENT, .24f.. .42f),
    "highway" to SoundProfile(SoundRole.TEXTURE, .2f.. .36f),
    "horse-gallop" to SoundProfile(SoundRole.RARE, .08f.. .18f),
    "howling-wind" to SoundProfile(SoundRole.ACCENT, .18f.. .34f),
    "inside-a-train" to SoundProfile(SoundRole.BASE, .3f.. .46f),
    "jungle" to SoundProfile(SoundRole.BASE, .28f.. .46f),
    "keyboard" to SoundProfile(SoundRole.DETAIL, .12f.. .26f),
    "laboratory" to SoundProfile(SoundRole.TEXTURE, .16f.. .3f),
    "laundry-room" to SoundProfile(SoundRole.TEXTURE, .18f.. .34f),
    "library" to SoundProfile(SoundRole.TEXTURE, .2f.. .34f),
    "light-rain" to SoundProfile(SoundRole.BASE, .34f.. .54f),
    "morse-code" to SoundProfile(SoundRole.RARE, .06f.. .14f),
    "night-village" to SoundProfile(SoundRole.TEXTURE, .18f.. .34f),
    "office" to SoundProfile(SoundRole.TEXTURE, .2f.. .34f),
    "owl" to SoundProfile(SoundRole.DETAIL, .1f.. .22f),
    "paper" to SoundProfile(SoundRole.DETAIL, .1f.. .22f),
    "pink-noise" to SoundProfile(SoundRole.BASE, .22f.. .38f),
    "rain-on-car-roof" to SoundProfile(SoundRole.TEXTURE, .24f.. .42f),
    "rain-on-leaves" to SoundProfile(SoundRole.BASE, .3f.. .5f),
    "rain-on-tent" to SoundProfile(SoundRole.TEXTURE, .24f.. .42f),
    "rain-on-umbrella" to SoundProfile(SoundRole.TEXTURE, .22f.. .4f),
    "rain-on-window" to SoundProfile(SoundRole.BASE, .3f.. .5f),
    "restaurant" to SoundProfile(SoundRole.TEXTURE, .2f.. .36f),
    "river" to SoundProfile(SoundRole.BASE, .34f.. .56f),
    "road" to SoundProfile(SoundRole.TEXTURE, .18f.. .34f),
    "rowing-boat" to SoundProfile(SoundRole.TEXTURE, .18f.. .32f),
    "sailboat" to SoundProfile(SoundRole.TEXTURE, .18f.. .32f),
    "seagulls" to SoundProfile(SoundRole.DETAIL, .1f.. .22f),
    "sheep" to SoundProfile(SoundRole.RARE, .08f.. .18f),
    "singing-bowl" to SoundProfile(SoundRole.ACCENT, .1f.. .24f),
    "slide-projector" to SoundProfile(SoundRole.ACCENT, .1f.. .24f),
    "submarine" to SoundProfile(SoundRole.RARE, .1f.. .2f),
    "subway-station" to SoundProfile(SoundRole.TEXTURE, .16f.. .3f),
    "supermarket" to SoundProfile(SoundRole.RARE, .1f.. .2f),
    "temple" to SoundProfile(SoundRole.TEXTURE, .16f.. .3f),
    "thunder" to SoundProfile(SoundRole.ACCENT, .12f.. .26f),
    "traffic" to SoundProfile(SoundRole.TEXTURE, .18f.. .34f),
    "train" to SoundProfile(SoundRole.TEXTURE, .22f.. .38f),
    "tuning-radio" to SoundProfile(SoundRole.RARE, .06f.. .14f),
    "typewriter" to SoundProfile(SoundRole.ACCENT, .1f.. .24f),
    "underwater" to SoundProfile(SoundRole.BASE, .3f.. .5f),
    "vinyl-effect" to SoundProfile(SoundRole.DETAIL, .1f.. .22f),
    "walk-in-snow" to SoundProfile(SoundRole.DETAIL, .08f.. .18f),
    "walk-on-gravel" to SoundProfile(SoundRole.DETAIL, .08f.. .18f),
    "walk-on-leaves" to SoundProfile(SoundRole.DETAIL, .08f.. .18f),
    "washing-machine" to SoundProfile(SoundRole.TEXTURE, .16f.. .3f),
    "waterfall" to SoundProfile(SoundRole.ACCENT, .22f.. .4f),
    "waves" to SoundProfile(SoundRole.BASE, .34f.. .56f),
    "whale" to SoundProfile(SoundRole.ACCENT, .08f.. .18f),
    "white-noise" to SoundProfile(SoundRole.BASE, .24f.. .42f),
    "wind" to SoundProfile(SoundRole.BASE, .28f.. .48f),
    "wind-chimes" to SoundProfile(SoundRole.DETAIL, .1f.. .24f),
    "wind-in-trees" to SoundProfile(SoundRole.BASE, .28f.. .5f),
    "windshield-wipers" to SoundProfile(SoundRole.DETAIL, .08f.. .2f),
    "wolf" to SoundProfile(SoundRole.RARE, .07f.. .16f),
    "woodpecker" to SoundProfile(SoundRole.ACCENT, .08f.. .18f),
)

/** sound-scenes.ts TITLE_RULES — id and category conjunctions, in order. */
private val TITLE_RULES: List<TitleRule> = listOf(
    TitleRule(null, listOf("rain-on-window", "keyboard", "paper"), listOf("Rain Desk", "Window Work")),
    TitleRule(null, listOf("cafe", "light-rain"), listOf("Rainy Cafe", "Cafe Drizzle")),
    TitleRule(null, listOf("library", "clock"), listOf("Quiet Library", "Study Hour")),
    TitleRule(null, listOf("office", "keyboard"), listOf("Soft Office", "Focus Room")),
    TitleRule(null, listOf("river", "birds"), listOf("Forest Creek", "Green Stream")),
    TitleRule(null, listOf("waves", "seagulls"), listOf("Sea Breeze", "Coastal Air")),
    TitleRule(null, listOf("campfire", "owl"), listOf("Fireside Night", "Ember Woods")),
    TitleRule(null, listOf("inside-a-train", "rain-on-window"), listOf("Night Train", "Window Journey")),
    TitleRule(null, listOf("wind-chimes", "wind"), listOf("Wind Chimes", "Light Breeze")),
    TitleRule(null, listOf("brown-noise", "library"), listOf("Muted Study", "Deep Focus")),
    TitleRule(null, listOf("clock", "paper"), listOf("Paper Hour", "Quiet Notes")),
    TitleRule(null, listOf("typewriter", "rain-on-window"), listOf("Rain Manuscript", "Typing Rain")),
    TitleRule(null, listOf("restaurant", "vinyl-effect"), listOf("Warm Evening", "Old Table")),
    TitleRule(null, listOf("ceiling-fan", "office"), listOf("Still Office", "Workday Hush")),
    TitleRule(null, listOf("underwater", "whale"), listOf("Blue Depths", "Deep Water")),
    TitleRule(null, listOf("sailboat", "waves"), listOf("Slow Sail", "Open Water")),
    TitleRule(null, listOf("crickets", "night-village"), listOf("Village Night", "Cricket Dusk")),
    TitleRule(null, listOf("boiling-water", "rain-on-leaves"), listOf("Tea Rain", "Warm Window")),
    TitleRule(null, listOf("rain-on-car-roof", "traffic"), listOf("Car Roof Rain", "Commute Rain", "Wet Drive")),
    TitleRule(null, listOf("rain-on-umbrella", "busy-street"), listOf("Umbrella Walk", "Rain Errand", "Sidewalk Rain")),
    TitleRule(null, listOf("rain-on-tent", "campfire"), listOf("Camp Shelter", "Tent Embers", "Rain Camp")),
    TitleRule(null, listOf("walk-on-gravel", "birds"), listOf("Gravel Morning", "Garden Path", "Slow Footpath")),
    TitleRule(null, listOf("washing-machine", "ceiling-fan"), listOf("Laundry Hush", "Utility Room", "Soft Machines")),
    TitleRule(null, listOf("bubbles", "underwater"), listOf("Bubble Depths", "Blue Bubbles", "Soft Submerge")),
    TitleRule(null, listOf("light-rain", "birds"), listOf("Morning Drizzle", "Birdsong Rain", "Soft Garden")),
    TitleRule(null, listOf("rain-on-leaves", "wind-in-trees"), listOf("Leaf Rain", "Wet Canopy", "Green Shelter")),
    TitleRule(null, listOf("waterfall", "wind"), listOf("Mist Valley", "Falling Air", "Water Veil")),
    TitleRule(null, listOf("walk-on-leaves", "wind"), listOf("Autumn Walk", "Leaf Path", "Quiet Trail")),
    TitleRule(null, listOf("walk-in-snow", "wind"), listOf("Snow Path", "White Silence", "Winter Steps")),
    TitleRule(null, listOf("jungle", "birds"), listOf("Green Canopy", "Jungle Morning", "Hidden Birds")),
    TitleRule(null, listOf("cafe", "vinyl-effect"), listOf("Cafe Vinyl", "Warm Record", "Old Cafe")),
    TitleRule(null, listOf("library", "paper"), listOf("Paper Library", "Study Pages", "Silent Pages")),
    TitleRule(null, listOf("office", "clock"), listOf("Office Hours", "Clockwork Focus", "Quiet Shift")),
    TitleRule(null, listOf("keyboard", "brown-noise"), listOf("Low Keyboard", "Typing Focus", "Desk Noise")),
    TitleRule(null, listOf("typewriter", "clock"), listOf("Old Writing", "Clock & Keys", "Drafting Hour")),
    TitleRule(null, listOf("slide-projector", "vinyl-effect"), listOf("Analog Room", "Old Projector", "Memory Reel")),
    TitleRule(null, listOf("boiling-water", "clock"), listOf("Tea Break", "Kettle Hour", "Warm Pause")),
    TitleRule(null, listOf("train", "brown-noise"), listOf("Rail Haze", "Low Rails", "Steady Train")),
    TitleRule(null, listOf("rowing-boat", "waves"), listOf("Rowing Tide", "Small Boat", "Water Oars")),
    TitleRule(null, listOf("seagulls", "wind"), listOf("Harbor Wind", "Gull Coast", "Salt Air")),
    TitleRule(null, listOf("owl", "wind-in-trees"), listOf("Owl Woods", "Moon Forest", "Night Branches")),
    TitleRule(null, listOf("crickets", "campfire"), listOf("Cricket Fire", "Warm Camp", "Dusk Embers")),
    TitleRule(null, listOf("pink-noise", "clock"), listOf("Pink Hour", "Soft Ticks", "Muted Time")),
    TitleRule(null, listOf("thunder", "rain-on-tent"), listOf("Storm Tent", "Canvas Thunder", "Shelter Storm")),
    TitleRule(null, listOf("airport", "brown-noise"), listOf("Airport Hush", "Terminal Focus", "Waiting Gate")),
    TitleRule(null, listOf("fireworks", "crowd"), listOf("Distant Festival", "Night Celebration", "City Sparks")),
    TitleRule(null, listOf("construction-site", "traffic"), listOf("Urban Works", "City Build", "Hard Hat Haze")),
    TitleRule(listOf("rain", "noise"), null, listOf("Rain Blanket", "Soft Rainfield")),
    TitleRule(listOf("nature", "animals"), null, listOf("Living Forest", "Wild Morning")),
    TitleRule(listOf("transport", "rain"), null, listOf("Moving Window", "Rain Transit")),
    TitleRule(listOf("urban", "things"), null, listOf("City Focus", "Desk Motion")),
    TitleRule(listOf("places", "things"), null, listOf("Room Texture", "Interior Calm")),
    TitleRule(listOf("nature", "things"), null, listOf("Natural Details", "Small Rituals")),
    TitleRule(listOf("places", "noise"), null, listOf("Soft Room", "Hushed Interior")),
    TitleRule(listOf("transport", "noise"), null, listOf("Low Transit", "Steady Motion")),
    TitleRule(listOf("rain", "animals"), null, listOf("Wet Garden", "Rainy Morning")),
    TitleRule(listOf("nature", "rain"), null, listOf("Green Rain", "Leaf Shelter")),
    TitleRule(listOf("urban", "places"), null, listOf("City Room", "Window District")),
    TitleRule(listOf("things", "noise"), null, listOf("Tiny Focus", "Desk Haze")),
    TitleRule(listOf("animals", "noise"), null, listOf("Soft Wildlife", "Distant Calls", "Muted Creatures")),
    TitleRule(listOf("animals", "places"), null, listOf("Living Roomscape", "Distant Habitat", "Quiet Habitat")),
    TitleRule(listOf("nature", "noise"), null, listOf("Natural Haze", "Green Noise", "Earth Tone")),
    TitleRule(listOf("nature", "places"), null, listOf("Outdoor Room", "Open Shelter", "Window Nature")),
    TitleRule(listOf("rain", "places"), null, listOf("Sheltered Rain", "Indoor Rain", "Room with Rain")),
    TitleRule(listOf("rain", "things"), null, listOf("Rain Ritual", "Wet Desk", "Small Rainwork")),
    TitleRule(listOf("transport", "places"), null, listOf("Station Room", "Travel Lounge", "Passing Place")),
    TitleRule(listOf("transport", "things"), null, listOf("Travel Details", "Moving Desk", "Transit Notes")),
    TitleRule(listOf("urban", "noise"), null, listOf("City Haze", "Urban Blanket", "Soft Streets")),
    TitleRule(listOf("urban", "rain"), null, listOf("Rainy Streets", "Wet City", "City Drizzle")),
    TitleRule(listOf("urban", "transport"), null, listOf("City Transit", "Moving City", "Street Motion")),
    TitleRule(listOf("places", "rain", "things"), null, listOf("Rainy Interior", "Window Ritual", "Cozy Weather")),
    TitleRule(listOf("nature", "animals", "rain"), null, listOf("Rainforest Hour", "Wet Wildlife", "Living Rain")),
    TitleRule(listOf("nature", "transport"), null, listOf("Open Journey", "Landscape Motion", "Outside Passage")),
    TitleRule(listOf("noise", "things", "places"), null, listOf("Productive Hush", "Focused Interior", "Soft Workspace")),
)

/** sound-scenes.ts FALLBACK_TITLES. */
private val FALLBACK_TITLES: List<String> = listOf(
    "Soft Soundscape", "Gentle Mix", "Focus Blend", "Quiet Canvas", "Layered Calm",
    "Drifting Room", "Ambient Drift", "Light Atmosphere", "Balanced Space", "Mellow Field",
    "Calm Layers", "Daily Soundscape", "Quiet Motion", "Soft Horizon", "Clean Focus",
    "Slow Texture", "Open Calm", "Hidden Corner", "Evening Layers", "Morning Haze",
    "Gentle Current", "Silent Weather", "Warm Layers", "Low Atmosphere", "Small Landscape",
    "Soft Distance", "Calm Pattern", "Quiet Signals", "Tender Noise", "Hidden Weather",
    "Misty Focus", "Slow Room", "Still Texture", "Pale Evening", "Fresh Silence",
    "Subtle Field", "Wandering Calm", "Soft Passage", "Open Texture", "Distant Room",
)

/** sound-scenes.ts SOUND_SCENE_TITLE_ZH. */
private val SCENE_TITLE_ZH: Map<String, String> = mapOf(
    "Rain Desk" to "雨窗书案", "Window Work" to "窗边微光", "Rainy Cafe" to "雨巷小馆", "Cafe Drizzle" to "小馆细霖",
    "Quiet Library" to "旧书微光", "Study Hour" to "长桌夜读", "Soft Office" to "柔光静室", "Focus Room" to "沉心小室",
    "Forest Creek" to "林溪晨雾", "Green Stream" to "青溪入梦", "Sea Breeze" to "海风远岸", "Coastal Air" to "岸边薄明",
    "Fireside Night" to "炉边夜色", "Ember Woods" to "余烬森林", "Night Train" to "夜车听雨", "Window Journey" to "窗外远行",
    "Wind Chimes" to "风铃入梦", "Light Breeze" to "浅风过境", "Muted Study" to "低声自习", "Deep Focus" to "静默深处",
    "Paper Hour" to "纸页时光", "Quiet Notes" to "笔记低语", "Rain Manuscript" to "雨夜手稿", "Typing Rain" to "键雨微光",
    "Warm Evening" to "暖灯黄昏", "Old Table" to "旧桌微光", "Still Office" to "静室流风", "Workday Hush" to "午后静班",
    "Blue Depths" to "深蓝之下", "Deep Water" to "深水静息", "Slow Sail" to "慢帆远行", "Open Water" to "开阔水面",
    "Village Night" to "村庄夜色", "Cricket Dusk" to "暮色虫鸣", "Tea Rain" to "茶烟听雨", "Warm Window" to "暖窗茶声",
    "Car Roof Rain" to "车顶听雨", "Commute Rain" to "雨中通勤", "Wet Drive" to "湿路慢行", "Umbrella Walk" to "伞下微雨",
    "Rain Errand" to "雨中小径", "Sidewalk Rain" to "街边雨线", "Camp Shelter" to "雨夜营帐", "Tent Embers" to "帐内余火",
    "Rain Camp" to "雨中营地", "Gravel Morning" to "碎石清晨", "Garden Path" to "园径微光", "Slow Footpath" to "慢行小径",
    "Laundry Hush" to "洗衣房夜色", "Utility Room" to "暖雾小室", "Soft Machines" to "轻转入眠", "Bubble Depths" to "气泡深处",
    "Blue Bubbles" to "蓝色气泡", "Soft Submerge" to "柔光潜游", "Morning Drizzle" to "晨间细雨", "Birdsong Rain" to "鸟鸣微雨",
    "Soft Garden" to "柔光花园", "Leaf Rain" to "叶上微雨", "Wet Canopy" to "湿叶穹顶", "Green Shelter" to "绿荫雨幕",
    "Mist Valley" to "雾谷清响", "Falling Air" to "瀑雾入风", "Water Veil" to "水幕微光", "Autumn Walk" to "秋叶小径",
    "Leaf Path" to "落叶小路", "Quiet Trail" to "静林小径", "Snow Path" to "雪径无声", "White Silence" to "白雪静处",
    "Winter Steps" to "冬日脚步", "Green Canopy" to "青林穹顶", "Jungle Morning" to "雨林清晨", "Hidden Birds" to "深林鸟语",
    "Cafe Vinyl" to "小馆黑胶", "Warm Record" to "暖灯唱片", "Old Cafe" to "旧日咖啡", "Paper Library" to "纸页书馆",
    "Study Pages" to "书页微灯", "Silent Pages" to "静默书页", "Office Hours" to "静室时段", "Clockwork Focus" to "钟摆专注",
    "Quiet Shift" to "静班微光", "Low Keyboard" to "低声键盘", "Typing Focus" to "键声专注", "Desk Noise" to "案头白噪",
    "Old Writing" to "旧稿微光", "Clock & Keys" to "钟键微声", "Drafting Hour" to "草稿时分", "Analog Room" to "模拟小室",
    "Old Projector" to "旧影微光", "Memory Reel" to "胶片回声", "Tea Break" to "暖壶间歇", "Kettle Hour" to "温壶时分",
    "Warm Pause" to "暖光片刻", "Rail Haze" to "铁轨薄雾", "Low Rails" to "低声长轨", "Steady Train" to "长轨安流",
    "Rowing Tide" to "划桨潮声", "Small Boat" to "小舟远风", "Water Oars" to "水面船桨", "Harbor Wind" to "港口清晨",
    "Gull Coast" to "鸥声海岸", "Salt Air" to "盐风远处", "Owl Woods" to "月下林声", "Moon Forest" to "月下森林",
    "Night Branches" to "夜枝低语", "Cricket Fire" to "虫鸣余火", "Warm Camp" to "暖火营地", "Dusk Embers" to "暮色余烬",
    "Pink Hour" to "粉雾时分", "Soft Ticks" to "柔和滴答", "Muted Time" to "静默时钟", "Storm Tent" to "风雨营帐",
    "Canvas Thunder" to "帐篷雷声", "Shelter Storm" to "暴雨庇所", "Airport Hush" to "候机微光", "Terminal Focus" to "航站静思",
    "Waiting Gate" to "登机口薄雾", "Distant Festival" to "远处节庆", "Night Celebration" to "夜色庆典", "City Sparks" to "城市烟火",
    "Urban Works" to "城市灰调", "City Build" to "远街尘声", "Hard Hat Haze" to "街角灰光", "Rain Blanket" to "雨声薄被",
    "Soft Rainfield" to "柔雨原野", "Living Forest" to "有声森林", "Wild Morning" to "野地清晨", "Moving Window" to "移动的窗",
    "Rain Transit" to "雨中转乘", "City Focus" to "街区静思", "Desk Motion" to "案头流动", "Room Texture" to "房间质地",
    "Interior Calm" to "室内安流", "Natural Details" to "自然细部", "Small Rituals" to "小小仪式", "Soft Room" to "柔声小室",
    "Hushed Interior" to "静息室内", "Low Transit" to "低声转乘", "Steady Motion" to "稳定行进", "Wet Garden" to "湿润花园",
    "Rainy Morning" to "雨晨微光", "Green Rain" to "青绿雨幕", "Leaf Shelter" to "叶下庇处", "City Room" to "城市房间",
    "Window District" to "窗外街区", "Tiny Focus" to "微小专注", "Desk Haze" to "案头薄雾", "Soft Wildlife" to "柔野生息",
    "Distant Calls" to "远处呼声", "Muted Creatures" to "低声生灵", "Living Roomscape" to "有声房间", "Distant Habitat" to "远处栖居",
    "Quiet Habitat" to "静谧栖地", "Natural Haze" to "自然薄雾", "Green Noise" to "绿意白噪", "Earth Tone" to "土色回声",
    "Outdoor Room" to "户外小室", "Open Shelter" to "开阔庇处", "Window Nature" to "窗边自然", "Sheltered Rain" to "檐下听雨",
    "Indoor Rain" to "室内雨声", "Room with Rain" to "雨中的房间", "Rain Ritual" to "听雨仪式", "Wet Desk" to "潮湿书桌",
    "Small Rainwork" to "微雨案头", "Station Room" to "车站小室", "Travel Lounge" to "旅途休息室", "Passing Place" to "经过之地",
    "Travel Details" to "旅途细部", "Moving Desk" to "移动书桌", "Transit Notes" to "转乘笔记", "City Haze" to "城市薄雾",
    "Urban Blanket" to "街区薄被", "Soft Streets" to "柔声街道", "Rainy Streets" to "雨中街道", "Wet City" to "湿润城市",
    "City Drizzle" to "城市细雨", "City Transit" to "城市转乘", "Moving City" to "流动城市", "Street Motion" to "街道流景",
    "Rainy Interior" to "雨中室内", "Window Ritual" to "窗边仪式", "Cozy Weather" to "暖窗天气", "Rainforest Hour" to "雨林时刻",
    "Wet Wildlife" to "湿林生灵", "Living Rain" to "有生命的雨", "Open Journey" to "开阔旅程", "Landscape Motion" to "山野流动",
    "Outside Passage" to "户外经过", "Productive Hush" to "专注静流", "Focused Interior" to "专注小室", "Soft Workspace" to "柔光工作间",
    "Soft Soundscape" to "柔声景", "Gentle Mix" to "温柔混音", "Focus Blend" to "专注混合", "Quiet Canvas" to "静夜画布",
    "Layered Calm" to "安静层次", "Drifting Room" to "漂流小室", "Ambient Drift" to "环境漂流", "Light Atmosphere" to "轻盈气氛",
    "Balanced Space" to "平衡空间", "Mellow Field" to "柔和声场", "Calm Layers" to "静谧层次", "Daily Soundscape" to "日常声景",
    "Quiet Motion" to "安静流动", "Soft Horizon" to "柔和地平线", "Clean Focus" to "清澈专注", "Slow Texture" to "缓慢纹理",
    "Open Calm" to "开阔安静", "Hidden Corner" to "隐秘角落", "Evening Layers" to "暮色层次", "Morning Haze" to "晨间薄雾",
    "Gentle Current" to "温柔流向", "Silent Weather" to "无声天气", "Warm Layers" to "暖色层次", "Low Atmosphere" to "低声气氛",
    "Small Landscape" to "小小风景", "Soft Distance" to "柔和远方", "Calm Pattern" to "安静图案", "Quiet Signals" to "安静信号",
    "Tender Noise" to "温柔噪声", "Hidden Weather" to "隐秘天气", "Misty Focus" to "雾中专注", "Slow Room" to "缓慢房间",
    "Still Texture" to "静止纹理", "Pale Evening" to "淡色傍晚", "Fresh Silence" to "新鲜安静", "Subtle Field" to "微妙声场",
    "Wandering Calm" to "漫游安静", "Soft Passage" to "柔和通道", "Open Texture" to "开阔纹理", "Distant Room" to "远处房间",
)

private val CATEGORY_BY_SOUND: Map<String, String> =
    CATALOG_CATEGORIES.flatMap { (catId, soundIds) -> soundIds.map { it to catId } }.toMap()

/**
 * Faithful port of src/lib/sound-scenes.ts createRandomSoundScene / buildWeightedScene.
 * Frontend is the single authority: rolePlan, roleWeight, per-sound profiles and title rules.
 */
class RandomSoundSceneGenerator(private val random: Random = Random.Default) {
    private val history = ArrayDeque<HistoryEntry>()

    fun create(available: List<AmbientSound>, previousSignature: String = ""): GeneratedSoundScene {
        val catalog = buildCatalog(available)
        if (catalog.isEmpty()) {
            return GeneratedSoundScene("empty:", emptyMap(), "静夜画布")
        }
        var scene = buildWeightedScene(catalog)
        var attempt = 0
        while (attempt < 8 && scene.signature == previousSignature) {
            scene = buildWeightedScene(catalog)
            attempt += 1
        }
        rememberScene(scene)
        return scene
    }

    /** Rebuild a truthful title from the tracks that are still actually available/active. */
    fun titleFor(soundIds: Collection<String>): String {
        val picked = soundIds.mapNotNull { id ->
            CATEGORY_BY_SOUND[id]?.let { category -> CatalogSound(category, id, getSoundProfile(id, category).role) }
        }
        return if (picked.isEmpty()) "静夜画布" else getSceneTitleZh(createSceneTitle(picked))
    }

    private fun buildCatalog(available: List<AmbientSound>): List<CatalogCategory> {
        val availableIds = available.mapTo(HashSet()) { it.id }
        return CATALOG_CATEGORIES.mapNotNull { (catId, soundIds) ->
            val sounds = soundIds.filter { it in availableIds }.map { id ->
                CatalogSound(catId, id, getSoundProfile(id, catId).role)
            }
            if (sounds.isEmpty()) null else CatalogCategory(catId, sounds)
        }
    }

    private fun buildWeightedScene(catalog: List<CatalogCategory>): GeneratedSoundScene {
        val tier = getSceneTier()
        val targetCount = getTargetSoundCount(catalog, tier)
        val picked = mutableListOf<CatalogSound>()
        val pickedIds = mutableSetOf<String>()
        val categoryPickCount = mutableMapOf<String, Int>()

        getRolePlan(targetCount, tier).forEach { role ->
            val sound = pickSoundForRole(catalog, role, pickedIds, categoryPickCount) ?: return@forEach
            picked += sound
            pickedIds += sound.id
            categoryPickCount[sound.categoryId] = getCategoryCount(categoryPickCount, sound.categoryId) + 1
        }

        while (picked.size < targetCount) {
            val sound = pickSoundForRole(catalog, null, pickedIds, categoryPickCount) ?: break
            picked += sound
            pickedIds += sound.id
            categoryPickCount[sound.categoryId] = getCategoryCount(categoryPickCount, sound.categoryId) + 1
        }

        val sounds = picked.associate { it.id to getSceneVolume(it, targetCount) }
        val titleEn = createSceneTitle(picked)
        val titleZh = getSceneTitleZh(titleEn)
        val signature = "role:${picked.map { it.id }.sorted().joinToString(",")}"
        return GeneratedSoundScene(signature, sounds, titleZh)
    }

    private fun getSceneTier(): SceneTier {
        val roll = random.nextFloat()
        return when {
            roll < .7f -> SceneTier.SMALL
            roll < .94f -> SceneTier.STANDARD
            else -> SceneTier.RICH
        }
    }

    private fun getTargetSoundCount(catalog: List<CatalogCategory>, tier: SceneTier): Int {
        val availableCount = catalog.sumOf { it.sounds.size }
        val target = when (tier) {
            SceneTier.SMALL -> randomInteger(4, 6)
            SceneTier.STANDARD -> randomInteger(7, 9)
            SceneTier.RICH -> randomInteger(10, 12)
        }
        return min(availableCount, target)
    }

    private fun getRolePlan(targetCount: Int, tier: SceneTier): List<SoundRole> {
        val rareCount = if (random.nextFloat() < when (tier) { SceneTier.SMALL -> .04f; SceneTier.STANDARD -> .07f; SceneTier.RICH -> .1f }) 1 else 0
        val accentCount = when {
            tier == SceneTier.RICH -> 1
            else -> if (random.nextFloat() < if (tier == SceneTier.SMALL) .35f else .55f) 1 else 0
        }
        val baseCount = when (tier) {
            SceneTier.SMALL -> randomInteger(1, 2)
            SceneTier.STANDARD -> 2
            SceneTier.RICH -> randomInteger(2, 3)
        }
        val textureCount = when (tier) {
            SceneTier.SMALL -> randomInteger(1, 2)
            SceneTier.STANDARD -> randomInteger(2, 3)
            SceneTier.RICH -> 3
        }
        val reserved = baseCount + textureCount + accentCount + rareCount
        val detailCount = max(0, targetCount - reserved)

        val roles = buildList {
            repeat(baseCount) { add(SoundRole.BASE) }
            repeat(textureCount) { add(SoundRole.TEXTURE) }
            repeat(detailCount) { add(SoundRole.DETAIL) }
            repeat(accentCount) { add(SoundRole.ACCENT) }
            repeat(rareCount) { add(SoundRole.RARE) }
        }
        return shuffleRoles(roles).take(targetCount)
    }

    private fun pickSoundForRole(
        catalog: List<CatalogCategory>,
        role: SoundRole?,
        pickedIds: MutableSet<String>,
        categoryPickCount: MutableMap<String, Int>,
    ): CatalogSound? {
        val all = catalog.flatMap { it.sounds }
        val candidates = all.filter { sound ->
            sound.id !in pickedIds &&
                canPickCategory(sound.categoryId, categoryPickCount) &&
                (if (role != null) sound.role == role else sound.role != SoundRole.RARE)
        }
        val fallback = if (role != null) {
            all.filter { sound ->
                sound.id !in pickedIds &&
                    canPickCategory(sound.categoryId, categoryPickCount) &&
                    sound.role != SoundRole.RARE
            }
        } else {
            emptyList()
        }
        return pickWeighted(candidates.ifEmpty { fallback }, ::getSoundWeight)
    }

    private fun canPickCategory(categoryId: String, categoryPickCount: Map<String, Int>): Boolean =
        getCategoryCount(categoryPickCount, categoryId) < 3

    private fun getSoundWeight(sound: CatalogSound): Float {
        val soundRecentCount = history.count { sound.id in it.soundIds }
        val categoryRecentCount = history.count { sound.categoryId in it.categoryIds }
        val roleWeight = when (sound.role) {
            SoundRole.RARE -> .2f
            SoundRole.ACCENT -> .72f
            else -> 1f
        }
        val freshSoundBoost = if (soundRecentCount == 0) 1.7f else 1f
        val freshCategoryBoost = if (categoryRecentCount == 0) 1.28f else 1f
        return (roleWeight * freshSoundBoost * freshCategoryBoost * random(.86f, 1.14f)) /
            (1 + soundRecentCount * .9f + categoryRecentCount * .3f)
    }

    private fun getSceneVolume(sound: CatalogSound, targetCount: Int): Float {
        val range = getSoundProfile(sound.id, sound.categoryId).volume
        val densityFactor = when {
            targetCount <= 6 -> 1f
            targetCount <= 9 -> .88f
            else -> .76f
        }
        val adjusted = random(range.start, range.endInclusive) * densityFactor
        return round(min(.64f, max(.06f, adjusted)) * 100) / 100
    }

    private fun getSoundProfile(soundId: String, categoryId: String): SoundProfile =
        SOUND_PROFILES[soundId] ?: SoundProfile(
            DEFAULT_CATEGORY_ROLE[categoryId] ?: SoundRole.DETAIL,
            ROLE_VOLUME.getValue(DEFAULT_CATEGORY_ROLE[categoryId] ?: SoundRole.DETAIL),
        )

    private fun createSceneTitle(picked: List<CatalogSound>): String {
        val soundIds = picked.mapTo(mutableSetOf()) { it.id }
        val categoryIds = picked.mapTo(mutableSetOf()) { it.categoryId }
        val matched = TITLE_RULES.filter { rule ->
            val idsMatch = rule.ids?.all { it in soundIds } ?: true
            val categoriesMatch = rule.categories?.all { it in categoryIds } ?: true
            idsMatch && categoriesMatch
        }
        val titlePool = if (matched.isNotEmpty()) matched.flatMap { it.titles } else FALLBACK_TITLES
        return titlePool.getOrNull(randomInteger(0, titlePool.lastIndex)) ?: "Quiet Canvas"
    }

    private fun getSceneTitleZh(titleEn: String): String = SCENE_TITLE_ZH[titleEn] ?: titleEn

    private fun rememberScene(scene: GeneratedSoundScene) {
        val soundIds = scene.sounds.keys.toList()
        val categoryIds = soundIds.mapNotNull { CATEGORY_BY_SOUND[it] }.distinct()
        history.addFirst(HistoryEntry(categoryIds, soundIds))
        while (history.size > HISTORY_LIMIT) history.removeLast()
    }

    private fun <T> pickWeighted(items: List<T>, getWeight: (T) -> Float): T? {
        if (items.isEmpty()) return null
        val weighted = items.map { it to max(0f, getWeight(it)) }
        val totalWeight = weighted.sumOf { it.second.toDouble() }.toFloat()
        if (totalWeight <= 0) return null
        var cursor = random.nextFloat() * totalWeight
        for ((item, weight) in weighted) {
            cursor -= weight
            if (cursor <= 0) return item
        }
        return weighted.last().first
    }

    private fun getCategoryCount(counts: Map<String, Int>, categoryId: String): Int = counts[categoryId] ?: 0

    private fun random(min: Float, max: Float): Float = random.nextFloat() * (max - min) + min

    private fun randomInteger(min: Int, max: Int): Int = (random.nextFloat() * (max + 1 - min) + min).toInt()

    private fun shuffleRoles(roles: List<SoundRole>): List<SoundRole> {
        val shuffled = roles.toMutableList()
        for (index in shuffled.lastIndex downTo 1) {
            val swapIndex = randomInteger(0, index)
            val tmp = shuffled[index]
            shuffled[index] = shuffled[swapIndex]
            shuffled[swapIndex] = tmp
        }
        return shuffled
    }
}
