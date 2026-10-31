package com.guidelight.app.navigation

import java.util.Locale
import com.guidelight.app.domain.VoicePhrases

enum class NearbyCategory(val label: String, val plural: String, private vararg val aliases: String) {
    CHEMIST("Chemist", "chemists", "chemist", "chemists", "chemist shop", "chemist store", "chhemist", "chhemists", "chemest", "chemests", "chemist's",
        "pharmacy", "pharmacies", "pharmacy shop", "pharmacy store", "medical shop", "medical store", "medicine shop", "medicine store", "drugstore", "drug store", "drug shop"),
    SHOPS("Shops", "shops", "shop", "shops", "store", "stores", "retail shop", "retail store", "shopping places"),
    GROCERIES("Groceries", "grocery shops", "grocery", "groceries", "grocery shop", "grocery store", "supermarket", "supermarkets", "kirana", "kirana shop", "kirana store",
        "general store", "convenience store", "provision store", "provisions store", "ration shop", "mini mart", "minimart", "grocer", "greengrocer", "vegetable shop"),
    FOOD("Food", "places to eat", "food", "restaurant", "restaurants", "restraunt", "restraunts", "resturant", "resturants", "restaurent", "restaurents",
        "cafe", "cafes", "café", "cafés", "coffee shop", "coffee", "place to eat", "places to eat", "eatery", "eateries", "dhaba", "dhabas", "diner", "diners", "cafeteria", "fast food", "fast food place"),
    ATM("ATM", "ATMs", "atm", "atms", "atm machine", "cash machine", "cashpoint", "cash point", "cash withdrawal", "automated teller machine"),
    HOSPITAL("Hospital", "hospitals", "hospital", "hospitals"),
    CLINIC("Clinics", "clinics", "clinic", "clinics", "doctor", "doctors", "doctor clinic", "doctor's clinic", "medical clinic", "health clinic", "health centre", "health center",
        "medical centre", "medical center", "homeopathic clinic", "homoeopathic clinic", "homeopathy clinic", "homoeopathy clinic", "homeopath", "homoeopath", "physician", "family doctor"),
    TEMPLE("Temple", "temples", "temple", "temples"),
    MANDIR("Mandir", "mandirs", "mandir", "mandirs", "hindu temple", "hindu temples"),
    MOSQUE("Mosque", "mosques", "mosque", "mosques", "masjid", "masjids", "masjed", "masajid", "muslim prayer place"),
    CHURCH("Church", "churches", "church", "churches", "chapel", "chapels", "christian church"),
    GURDWARA("Gurdwara", "gurdwaras", "gurdwara", "gurdwaras", "gurudwara", "gurudwaras", "sikh temple"),
    SYNAGOGUE("Synagogue", "synagogues", "synagogue", "synagogues", "jewish temple"),
    BUDDHIST_TEMPLE("Buddhist temple", "Buddhist temples", "buddhist temple", "buddhist temples", "buddhist monastery"),
    WORSHIP("Place of worship", "places of worship", "place of worship", "places of worship", "prayer place", "religious place"),
    TOILET("Public toilet", "public toilets", "toilet", "toilets", "public toilet", "public toilets", "washroom", "washrooms", "restroom", "restrooms", "public bathroom", "bathroom", "wc"),
    WATER("Drinking water", "drinking water points", "drinking water", "drinking water point", "drinking fountain", "potable water", "water refill"),
    BUS_STOP("Bus stop", "bus stops", "bus stop", "bus stops"),
    BUS_STATION("Bus station", "bus stations", "bus station", "bus stations", "bus stand", "bus terminal"),
    METRO("Metro station", "metro stations", "metro", "metro station", "metro stations", "subway station", "subway entrance", "underground station"),
    TRAIN_STATION("Railway station", "railway stations", "railway station", "railway stations", "train station", "rail station"),
    TAXI("Taxi stand", "taxi stands", "taxi", "taxi stand", "taxi rank", "cab stand"),
    BANK("Bank", "banks", "bank", "banks", "bank branch"),
    POLICE("Police station", "police stations", "police", "police station", "police stations"),
    POST_OFFICE("Post office", "post offices", "post office", "post offices", "postal office"),
    DENTIST("Dentist", "dentists", "dentist", "dentists", "dental clinic", "dental surgery"),
    OPTICIAN("Optician", "opticians", "optician", "opticians", "optical shop", "spectacle shop", "optometrist"),
    LAB("Medical laboratory", "medical laboratories", "medical laboratory", "medical lab", "diagnostic lab", "pathology lab"),
    SUPERMARKET("Supermarket", "supermarkets", "supermarket", "supermarkets", "super market"),
    RESTAURANT("Restaurant", "restaurants", "restaurant", "restaurants", "restraunt", "restraunts", "resturant", "resturants", "restaurent", "restaurents", "dhaba", "dhabas", "diner", "diners"),
    CAFE("Cafe", "cafes", "cafe", "cafes", "café", "cafés", "coffee", "coffee shop", "cafeteria"),
    BAKERY("Bakery", "bakeries", "bakery", "bakeries", "bread shop"),
    FRUIT_VEG("Fruit and vegetable shop", "fruit and vegetable shops", "vegetable shop", "fruit shop", "greengrocer", "vegetable store", "fruit and vegetable shop"),
    DAIRY("Dairy shop", "dairy shops", "dairy", "dairy shop", "milk shop", "milk store"),
    MARKET("Market", "markets", "market", "markets", "marketplace", "market place", "bazaar"),
    LAUNDRY("Laundry", "laundries", "laundry", "laundries", "laundromat", "launderette", "washing shop"),
    HAIRDRESSER("Hairdresser", "hairdressers", "hairdresser", "hairdressers", "barber", "barber shop", "hair salon"),
    TAILOR("Tailor", "tailors", "tailor", "tailors", "tailor shop", "tailoring shop"),
    CLOTHES("Clothes shop", "clothes shops", "clothes shop", "clothing shop", "clothes store", "clothing store", "garment shop"),
    SHOES("Shoe shop", "shoe shops", "shoe shop", "shoe store", "footwear shop", "footwear store"),
    HARDWARE("Hardware shop", "hardware shops", "hardware shop", "hardware store", "hardware"),
    MOBILE_PHONE("Mobile phone shop", "mobile phone shops", "mobile shop", "mobile phone shop", "phone shop", "mobile store", "cellphone shop"),
    ELECTRONICS("Electronics shop", "electronics shops", "electronics shop", "electronics store", "electrical shop"),
    PARK("Park", "parks", "park", "parks", "public park", "garden"),
    BENCH("Bench", "benches", "bench", "benches", "public bench", "place to sit", "rest bench"),
    LIBRARY("Library", "libraries", "library", "libraries", "public library"),
    COMMUNITY_CENTRE("Community centre", "community centres", "community centre", "community center", "community hall"),
    SCHOOL("School", "schools", "school", "schools"),
    COLLEGE("College", "colleges", "college", "colleges"),
    UNIVERSITY("University", "universities", "university", "universities"),
    HOTEL("Hotel", "hotels", "hotel", "hotels"),
    FUEL("Petrol station", "petrol stations", "petrol pump", "petrol station", "fuel station", "gas station", "diesel pump"),
    CHARGING_STATION("Charging station", "charging stations", "charging station", "ev charger", "electric vehicle charger");

    companion object {
        /** Exact category matching after removing voice wrappers preserves named destinations. */
        fun fromQuery(query: String): NearbyCategory? {
            var text = VoicePhrases.normalize(query)
            val prefix = Regex("^(?:find me|find|show me|show|search for|look for|locate|where is|where are|where can i find|i need to find|i want to find|i am looking for|i need|i want|the|a|an|nearest|closest|nearby|local)\\s+")
            val suffix = Regex("\\s+(?:near my current location|near my location|near me|around me|close to me|nearby|near here|around here|close by|closeby|closest to me)$")
            while (true) {
                val next = text.replace(prefix, "").replace(suffix, "").trim()
                if (next == text) break
                text = next
            }
            return entries.sortedBy { if (it in setOf(FOOD, GROCERIES, SHOPS)) 1 else 0 }
                .firstOrNull { text in it.aliases || text.removeSuffix("s") in it.aliases }
        }
    }
}
