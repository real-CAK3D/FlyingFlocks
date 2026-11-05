package com.cak3d.biscuitmesh.sensors

/** One BLE device kind, the "room" it belongs to, and the name fragments that identify it. */
data class BtCategory(val name: String, val room: String, val examples: String, val keywords: List<String>)

/**
 * Classifies a BLE device by its advertised name into a category + room — the "BT House"
 * / Lost & Found taxonomy from the WDGWars portal. Matching is by lowercase name fragment;
 * more specific kinds (buds, watches) are ordered before the generic ones (phone, computer)
 * so "Galaxy Watch" lands on Smartwatch, not Phone. Nameless devices stay unclassified.
 */
object BtTaxonomy {
    val categories: List<BtCategory> = listOf(
        // --- specific audio/wearables first so they beat the generic phone/computer ---
        BtCategory("Headphones", "Living room", "AirPods, WH-1000", listOf("airpod", "buds", "wh-1000", "wf-1000", "beats", "jabra", "momentum", "freebuds", "quietcomfort", " qc")),
        BtCategory("Smartwatch", "Bathroom", "Galaxy Watch, Apple Watch", listOf("watch", "gt 2", "gt 3", "gt2", "gt3", "amazfit", "ticwatch", "fossil")),
        BtCategory("Fitness tracker", "Bathroom", "Fitbit, WHOOP", listOf("fitbit", "whoop", "mi band", "mi smart band", "inspire", "charge", "versa", "luxe")),
        BtCategory("Heart rate monitor", "Bathroom", "Polar, Coospo", listOf("polar", "coospo", "tickr", "hrm", "h9", "h10", "heart rate")),
        BtCategory("Toothbrush", "Bathroom", "Oral-B, Sonicare", listOf("oral-b", "oralb", "sonicare", "toothbrush")),
        BtCategory("Glucose monitor", "Bathroom", "Dexcom, Libre", listOf("dexcom", "libre", "glucose")),
        BtCategory("Hearing aid", "Bathroom", "Phonak, ReSound", listOf("phonak", "resound", "oticon", "widex", "hearing")),
        BtCategory("Medical device", "Bathroom", "ResMed, Omron", listOf("resmed", "omron", "cpap", "airsense", "contour")),
        BtCategory("Speaker", "Living room", "JBL Charge, Sonos", listOf("jbl", "sonos", "bose", "soundcore", "flip", "charge 5", "marshall", "ue boom", "boombox", "soundlink", "megaboom", "wonderboom")),
        BtCategory("TV", "Living room", "Samsung TV, Roku", listOf("tv", "roku", "bravia", "vizio", "webos", "aquos", "firetv", "chromecast", "shield")),
        BtCategory("Audio receiver", "Living room", "Denon, FiiO", listOf("denon", "fiio", "marantz", "yamaha", "receiver", "soundbar")),
        BtCategory("Game controller", "Living room", "Xbox, DualSense", listOf("xbox", "dualsense", "dualshock", "playstation", "joy-con", "pro controller", "8bitdo")),
        BtCategory("VR headset", "Living room", "Quest 3S", listOf("quest", "vive", "index", "pico", "oculus")),
        BtCategory("Fan", "Living room", "DREO, Dyson", listOf("dreo", "dyson", " fan", "vornado")),
        BtCategory("Air purifier", "Living room", "Levoit, Coway", listOf("levoit", "coway", "purifier", "blueair")),
        BtCategory("HVAC", "Living room", "Midea, Daikin", listOf("midea", "daikin", "hvac", "mini split", "pioneer ac")),
        // --- office ---
        BtCategory("Headset / mic", "Office", "Poly, Blue", listOf("poly", "plantronics", "yeti", "rode", "wireless mic", "dji mic")),
        BtCategory("Keyboard", "Office", "Keychron, MX Keys", listOf("keychron", "mx keys", "k380", "k8", "keyboard", "magic keyboard")),
        BtCategory("Mouse", "Office", "MX Master, Logitech", listOf("mx master", "mx anywhere", "m720", "mouse", "magic mouse", "razer")),
        BtCategory("Printer", "Office", "Epson, Brother", listOf("epson", "brother", "canon", "officejet", "deskjet", "laserjet", "printer")),
        BtCategory("Camera", "Office", "GoPro, Insta360", listOf("gopro", "insta360", "sony a7", "alpha", "fujifilm", "osmo", "nikon")),
        BtCategory("Dev board", "Office", "ESP32, M5Stack", listOf("esp32", "esp-", "m5stack", "arduino", "xiao", "nordic", "nrf52", "raspberry", "pico w", "wtbleep", "biscuit")),
        BtCategory("IoT module", "Office", "CC2652, BGM", listOf("cc2652", "cc2640", "bgm", "bg22", "silabs", "telink", "ble module")),
        BtCategory("Computer", "Office", "MacBook, ThinkPad", listOf("macbook", "thinkpad", "surface", "dell", "latitude", "xps", "imac", "desktop-", "laptop")),
        BtCategory("Phone", "Office", "iPhone, Redmi", listOf("iphone", "redmi", "pixel", "galaxy", "oneplus", "poco", "moto ", "nokia ", "honor")),
        // --- kitchen / laundry ---
        BtCategory("Fridge", "Kitchen", "Family Hub", listOf("family hub", "fridge", "refriger")),
        BtCategory("Washer", "Kitchen", "LG, Samsung", listOf("washer", "washing")),
        BtCategory("Dryer", "Kitchen", "Whirlpool", listOf("dryer", "whirlpool")),
        // --- garage / vehicles ---
        BtCategory("OBD dongle", "Garage", "FIXD, ELM327", listOf("fixd", "elm327", "obd", "obdlink", "veepeak", "carista")),
        BtCategory("Dashcam", "Garage", "70mai, BlackVue", listOf("70mai", "blackvue", "viofo", "nextbase", "dashcam")),
        BtCategory("E-bike", "Garage", "VanMoof, Rad", listOf("vanmoof", "radrover", "e-bike", "ebike", "specialized", "bosch ebike")),
        BtCategory("E-scooter", "Garage", "Ninebot, Segway", listOf("ninebot", "segway", "scooter", "xiaomi scooter")),
        BtCategory("Drone", "Garage", "DJI, Autel", listOf("dji", "autel", "mavic", "drone", "mini 3", "mini 4")),
        BtCategory("Bike sensor", "Garage", "Wahoo, Garmin", listOf("wahoo", "kickr", "cadence", "speed sensor", "varia")),
        BtCategory("GPS device", "Garage", "Garmin, TomTom", listOf("garmin", "tomtom", "edge ", "gpsmap", "montana")),
        BtCategory("Garage controller", "Garage", "MyQ", listOf("myq", "chamberlain", "liftmaster", "garage")),
        BtCategory("Tracker", "Garage", "AirTag, Tile", listOf("airtag", "tile", "chipolo", "smarttag", "galaxy smarttag")),
        BtCategory("Car", "Garage", "Tesla, BMW", listOf("tesla", "bmw", "toyota", "ford", "subaru", "hyundai", "mazda", "audi", "mercedes", "carplay", "sync ", "uconnect")),
        // --- home systems ---
        BtCategory("Smart light", "Home systems", "Govee, ELK-BLEDOM", listOf("govee", "elk-bledom", "bledom", " hue", "lifx", "nanoleaf", "yeelight", "wiz ", "smart bulb", "led strip")),
        BtCategory("Smart plug", "Home systems", "Kasa, Meross", listOf("kasa", "meross", "wemo", "smart plug", "tp-link plug")),
        BtCategory("Thermostat", "Home systems", "Nest, Ecobee", listOf("nest ", "ecobee", "thermostat", "honeywell t")),
        BtCategory("Temp sensor", "Home systems", "SensorPush", listOf("sensorpush", "govee h5", "h5075", "th sensor", "thermometer")),
        BtCategory("Smart lock", "Home systems", "August, Yale", listOf("august", "yale", "schlage", "wyze lock", "level lock", "smart lock")),
        BtCategory("Doorbell", "Home systems", "Ring, Nest", listOf("doorbell", "ring ", "nest hello")),
        BtCategory("Security camera", "Home systems", "Wyze, Arlo", listOf("wyze", "arlo", "blink", "eufycam", "reolink")),
        BtCategory("Pet gear", "Home systems", "Furbo, Tractive", listOf("furbo", "tractive", "whistle", "fi collar", "petkit")),
        // --- the street ---
        BtCategory("BLE beacon", "The street", "Kontakt, Estimote", listOf("kontakt", "estimote", "ibeacon", "eddystone", "beacon")),
        BtCategory("Access control", "The street", "HID, Salto", listOf("hid ", "salto", "access control", "wiegand")),
        BtCategory("POS terminal", "The street", "Square, Clover", listOf("square", "clover", "verifone", "ingenico", "pax ", "terminal")),
    )

    private val lcCats = categories.map { it to it.keywords }

    /** The category a device name belongs to, or null if it's nameless / unknown. */
    fun classify(name: String): BtCategory? {
        if (name.isBlank()) return null
        val n = name.lowercase()
        for ((cat, kws) in lcCats) if (kws.any { n.contains(it) }) return cat
        return null
    }

    /** Rooms in a stable display order. */
    val roomOrder: List<String> = listOf("Living room", "Office", "Bathroom", "Kitchen", "Garage", "Home systems", "The street")
}
