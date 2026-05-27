package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class AppIntegrationTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "app_integration"

    override val description = """Interact with 100+ popular installed apps using deep links and intents.
        |Perform app-specific actions like sending WhatsApp messages, searching Spotify,
        |composing emails in Gmail/Outlook, posting on social media, ordering food, and more.
        |
        |Parameters:
        |  - app: App name (e.g. "whatsapp", "instagram", "spotify", "gmail")
        |  - action: App-specific action (e.g. "send_message", "search", "compose", "open_profile")
        |  - phone: Phone number (with country code, e.g. "+1234567890")
        |  - message/text: Message or text content
        |  - query: Search query
        |  - username: Username/profile name
        |  - to: Email recipient
        |  - subject: Email subject
        |  - body: Email body
        |  - url/link: URL to share/open
        |  - destination: Address/location
        |  - amount: Payment amount
        |  - recipient: Payment recipient
        |
        |Supported apps by category:
        |
        |MESSAGING: whatsapp, telegram, signal, messenger, viber, wechat, discord,
        |  slack, teams, skype, line, kakaotalk, zoom, google_meet, whatsapp_business
        |
        |SOCIAL: instagram, facebook, twitter, tiktok, snapchat, linkedin, reddit,
        |  pinterest, threads, youtube, twitch, bereal
        |
        |EMAIL: gmail, outlook, yahoo_mail, protonmail
        |
        |ENTERTAINMENT: spotify, netflix, prime_video, disney_plus, hbo_max, hulu,
        |  apple_music, soundcloud, deezer, pandora, youtube_music
        |
        |PRODUCTIVITY: google_drive, google_docs, google_sheets, google_slides,
        |  ms_word, ms_excel, ms_powerpoint, notion, evernote, trello, todoist,
        |  google_keep, onenote, asana
        |
        |SHOPPING: amazon, ebay, aliexpress, walmart, target, shein, etsy, wish
        |
        |MAPS & TRAVEL: google_maps, waze, uber, lyft, airbnb, booking, expedia,
        |  google_earth
        |
        |FINANCE: paypal, venmo, cash_app, robinhood, coinbase, google_pay,
        |  samsung_pay, zelle
        |
        |FOOD: uber_eats, doordash, grubhub, starbucks, mcdonalds, instacart, postmates
        |
        |FITNESS: strava, myfitnesspal, nike_run_club, fitbit, peloton
        |
        |UTILITIES: google_translate, shazam, google_authenticator, google_calendar,
        |  google_photos, dropbox, onedrive, chrome, firefox, opera, brave, edge,
        |  adobe_acrobat, vlc, google_lens
        |
        |Common actions per category:
        |  Messaging: send_message, call, video_call, open_chat, open
        |  Social: open_profile, search, compose/post, open_dm, open_camera, open
        |  Email: compose, open_inbox, open
        |  Entertainment: search, play, open_playlist, open
        |  Productivity: create, search, open
        |  Shopping: search, open_cart, open_orders, open
        |  Maps: navigate, search, directions, open
        |  Finance: send_money, open
        |  Food: search, order, open
        |  Fitness: start_activity, log, open
        |  Utilities: translate, identify, open""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("app") {
                put("type", "string")
                put("description", "App name (e.g. whatsapp, instagram, spotify, gmail)")
            }
            putJsonObject("action") {
                put("type", "string")
                put("description", "Action to perform (e.g. send_message, search, compose, open_profile, navigate, open)")
            }
            putJsonObject("phone") {
                put("type", "string")
                put("description", "Phone number with country code")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Message text")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query")
            }
            putJsonObject("username") {
                put("type", "string")
                put("description", "Username or profile handle")
            }
            putJsonObject("to") {
                put("type", "string")
                put("description", "Email recipient address")
            }
            putJsonObject("subject") {
                put("type", "string")
                put("description", "Email subject line")
            }
            putJsonObject("body") {
                put("type", "string")
                put("description", "Email or message body text")
            }
            putJsonObject("url") {
                put("type", "string")
                put("description", "URL to open or share")
            }
            putJsonObject("destination") {
                put("type", "string")
                put("description", "Destination address for navigation/rides")
            }
            putJsonObject("amount") {
                put("type", "string")
                put("description", "Payment amount")
            }
            putJsonObject("recipient") {
                put("type", "string")
                put("description", "Payment recipient username/handle")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "General text content to share or post")
            }
            putJsonObject("video_id") {
                put("type", "string")
                put("description", "Video ID for YouTube etc.")
            }
            putJsonObject("track_id") {
                put("type", "string")
                put("description", "Track/song ID for Spotify etc.")
            }
            putJsonObject("playlist_id") {
                put("type", "string")
                put("description", "Playlist ID")
            }
            putJsonObject("latitude") {
                put("type", "number")
                put("description", "Latitude for maps/location")
            }
            putJsonObject("longitude") {
                put("type", "number")
                put("description", "Longitude for maps/location")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "Content for notes, tasks, etc.")
            }
        }
        putJsonArray("required") { add("app"); add("action") }
    }

    // ========================================================================
    // App Registry: name → (packageName, webFallback)
    // ========================================================================

    private data class AppInfo(
        val packageName: String,
        val webFallback: String? = null
    )

    private val appRegistry = mapOf(
        // --- MESSAGING ---
        "whatsapp" to AppInfo("com.whatsapp", "https://wa.me"),
        "whatsapp_business" to AppInfo("com.whatsapp.w4b", "https://wa.me"),
        "telegram" to AppInfo("org.telegram.messenger", "https://t.me"),
        "signal" to AppInfo("org.thoughtcrime.securesms"),
        "messenger" to AppInfo("com.facebook.orca", "https://m.me"),
        "viber" to AppInfo("com.viber.voip"),
        "wechat" to AppInfo("com.tencent.mm"),
        "discord" to AppInfo("com.discord", "https://discord.com"),
        "slack" to AppInfo("com.Slack", "https://slack.com"),
        "teams" to AppInfo("com.microsoft.teams", "https://teams.microsoft.com"),
        "skype" to AppInfo("com.skype.raider"),
        "line" to AppInfo("jp.naver.line.android"),
        "kakaotalk" to AppInfo("com.kakao.talk"),
        "zoom" to AppInfo("us.zoom.videomeetings", "https://zoom.us"),
        "google_meet" to AppInfo("com.google.android.apps.meetings", "https://meet.google.com"),

        // --- SOCIAL MEDIA ---
        "instagram" to AppInfo("com.instagram.android", "https://www.instagram.com"),
        "facebook" to AppInfo("com.facebook.katana", "https://www.facebook.com"),
        "twitter" to AppInfo("com.twitter.android", "https://x.com"),
        "x" to AppInfo("com.twitter.android", "https://x.com"),
        "tiktok" to AppInfo("com.zhiliaoapp.musically", "https://www.tiktok.com"),
        "snapchat" to AppInfo("com.snapchat.android", "https://www.snapchat.com"),
        "linkedin" to AppInfo("com.linkedin.android", "https://www.linkedin.com"),
        "reddit" to AppInfo("com.reddit.frontpage", "https://www.reddit.com"),
        "pinterest" to AppInfo("com.pinterest", "https://www.pinterest.com"),
        "threads" to AppInfo("com.instagram.barcelona", "https://www.threads.net"),
        "youtube" to AppInfo("com.google.android.youtube", "https://www.youtube.com"),
        "twitch" to AppInfo("tv.twitch.android.app", "https://www.twitch.tv"),
        "bereal" to AppInfo("com.bereal.ft"),

        // --- EMAIL ---
        "gmail" to AppInfo("com.google.android.gm", "https://mail.google.com"),
        "outlook" to AppInfo("com.microsoft.office.outlook", "https://outlook.live.com"),
        "yahoo_mail" to AppInfo("com.yahoo.mobile.client.android.mail", "https://mail.yahoo.com"),
        "protonmail" to AppInfo("ch.protonmail.android", "https://mail.proton.me"),

        // --- ENTERTAINMENT ---
        "spotify" to AppInfo("com.spotify.music", "https://open.spotify.com"),
        "netflix" to AppInfo("com.netflix.mediaclient", "https://www.netflix.com"),
        "prime_video" to AppInfo("com.amazon.avod.thirdpartyclient", "https://www.primevideo.com"),
        "disney_plus" to AppInfo("com.disney.disneyplus", "https://www.disneyplus.com"),
        "hbo_max" to AppInfo("com.hbo.hbonow", "https://play.max.com"),
        "hulu" to AppInfo("com.hulu.plus", "https://www.hulu.com"),
        "apple_music" to AppInfo("com.apple.android.music"),
        "soundcloud" to AppInfo("com.soundcloud.android", "https://soundcloud.com"),
        "deezer" to AppInfo("deezer.android.app", "https://www.deezer.com"),
        "pandora" to AppInfo("com.pandora.android", "https://www.pandora.com"),
        "youtube_music" to AppInfo("com.google.android.apps.youtube.music", "https://music.youtube.com"),

        // --- PRODUCTIVITY ---
        "google_drive" to AppInfo("com.google.android.apps.docs", "https://drive.google.com"),
        "google_docs" to AppInfo("com.google.android.apps.docs.editors.docs", "https://docs.google.com"),
        "google_sheets" to AppInfo("com.google.android.apps.docs.editors.sheets", "https://sheets.google.com"),
        "google_slides" to AppInfo("com.google.android.apps.docs.editors.slides", "https://slides.google.com"),
        "ms_word" to AppInfo("com.microsoft.office.word"),
        "ms_excel" to AppInfo("com.microsoft.office.excel"),
        "ms_powerpoint" to AppInfo("com.microsoft.office.powerpoint"),
        "notion" to AppInfo("notion.id", "https://www.notion.so"),
        "evernote" to AppInfo("com.evernote", "https://www.evernote.com"),
        "trello" to AppInfo("com.trello", "https://trello.com"),
        "todoist" to AppInfo("com.todoist", "https://todoist.com"),
        "google_keep" to AppInfo("com.google.android.keep", "https://keep.google.com"),
        "onenote" to AppInfo("com.microsoft.office.onenote"),
        "asana" to AppInfo("com.asana.app", "https://app.asana.com"),

        // --- SHOPPING ---
        "amazon" to AppInfo("com.amazon.mShop.android.shopping", "https://www.amazon.com"),
        "ebay" to AppInfo("com.ebay.mobile", "https://www.ebay.com"),
        "aliexpress" to AppInfo("com.alibaba.aliexpresshd", "https://www.aliexpress.com"),
        "walmart" to AppInfo("com.walmart.android", "https://www.walmart.com"),
        "target" to AppInfo("com.target.ui", "https://www.target.com"),
        "shein" to AppInfo("com.zzkko", "https://www.shein.com"),
        "etsy" to AppInfo("com.etsy.android", "https://www.etsy.com"),
        "wish" to AppInfo("com.contextlogic.wish", "https://www.wish.com"),

        // --- MAPS & TRAVEL ---
        "google_maps" to AppInfo("com.google.android.apps.maps", "https://maps.google.com"),
        "waze" to AppInfo("com.waze", "https://www.waze.com"),
        "uber" to AppInfo("com.ubercab", "https://m.uber.com"),
        "lyft" to AppInfo("me.lyft.android", "https://www.lyft.com"),
        "airbnb" to AppInfo("com.airbnb.android", "https://www.airbnb.com"),
        "booking" to AppInfo("com.booking", "https://www.booking.com"),
        "expedia" to AppInfo("com.expedia.bookings", "https://www.expedia.com"),
        "google_earth" to AppInfo("com.google.earth"),

        // --- FINANCE ---
        "paypal" to AppInfo("com.paypal.android.p2pmobile", "https://www.paypal.com"),
        "venmo" to AppInfo("com.venmo", "https://venmo.com"),
        "cash_app" to AppInfo("com.squareup.cash", "https://cash.app"),
        "robinhood" to AppInfo("com.robinhood.android"),
        "coinbase" to AppInfo("com.coinbase.android", "https://www.coinbase.com"),
        "google_pay" to AppInfo("com.google.android.apps.nbu.paisa.user"),
        "samsung_pay" to AppInfo("com.samsung.android.spay"),
        "zelle" to AppInfo("com.zellepay.zelle"),

        // --- FOOD DELIVERY ---
        "uber_eats" to AppInfo("com.ubercab.eats", "https://www.ubereats.com"),
        "doordash" to AppInfo("com.dd.doordash", "https://www.doordash.com"),
        "grubhub" to AppInfo("com.grubhub.android", "https://www.grubhub.com"),
        "starbucks" to AppInfo("com.starbucks.mobilecard"),
        "mcdonalds" to AppInfo("com.mcdonalds.app"),
        "instacart" to AppInfo("com.instacart.client", "https://www.instacart.com"),
        "postmates" to AppInfo("com.postmates.android"),

        // --- FITNESS ---
        "strava" to AppInfo("com.strava", "https://www.strava.com"),
        "myfitnesspal" to AppInfo("com.myfitnesspal.android"),
        "nike_run_club" to AppInfo("com.nike.plusgps"),
        "fitbit" to AppInfo("com.fitbit.FitbitMobile"),
        "peloton" to AppInfo("com.onepeloton.callisto"),

        // --- UTILITIES ---
        "google_translate" to AppInfo("com.google.android.apps.translate", "https://translate.google.com"),
        "shazam" to AppInfo("com.shazam.android", "https://www.shazam.com"),
        "google_authenticator" to AppInfo("com.google.android.apps.authenticator2"),
        "google_calendar" to AppInfo("com.google.android.calendar", "https://calendar.google.com"),
        "google_photos" to AppInfo("com.google.android.apps.photos", "https://photos.google.com"),
        "dropbox" to AppInfo("com.dropbox.android", "https://www.dropbox.com"),
        "onedrive" to AppInfo("com.microsoft.skydrive"),
        "chrome" to AppInfo("com.android.chrome"),
        "google" to AppInfo("com.google.android.googlequicksearchbox", "https://www.google.com"),
        "firefox" to AppInfo("org.mozilla.firefox"),
        "opera" to AppInfo("com.opera.browser"),
        "brave" to AppInfo("com.brave.browser"),
        "edge" to AppInfo("com.microsoft.emmx"),
        "adobe_acrobat" to AppInfo("com.adobe.reader"),
        "vlc" to AppInfo("org.videolan.vlc"),
        "google_lens" to AppInfo("com.google.ar.lens"),

        // Dating
        "tinder" to AppInfo("com.tinder", "https://tinder.com"),
        "bumble" to AppInfo("com.bumble.app", "https://bumble.com"),
        "hinge" to AppInfo("co.hinge.app"),
        "okcupid" to AppInfo("com.okcupid.okcupid", "https://www.okcupid.com"),
        "badoo" to AppInfo("com.badoo.mobile", "https://badoo.com"),
        "grindr" to AppInfo("com.grindr.android"),
        "match" to AppInfo("com.match.android.matchmobile"),
        "coffee_meets_bagel" to AppInfo("com.coffeemeetsbagel"),

        // News & Magazines
        "cnn" to AppInfo("com.cnn.mobile.android.phone", "https://www.cnn.com"),
        "bbc_news" to AppInfo("bbc.mobile.news.ww", "https://www.bbc.com/news"),
        "nytimes" to AppInfo("com.nytimes.android", "https://www.nytimes.com"),
        "fox_news" to AppInfo("com.foxnews.android", "https://www.foxnews.com"),
        "reuters" to AppInfo("com.thomsonreuters.reuters", "https://www.reuters.com"),
        "flipboard" to AppInfo("flipboard.app", "https://flipboard.com"),
        "google_news" to AppInfo("com.google.android.apps.magazines", "https://news.google.com"),
        "guardian" to AppInfo("com.guardian", "https://www.theguardian.com"),
        "washington_post" to AppInfo("com.washingtonpost.rainbow", "https://www.washingtonpost.com"),
        "huffpost" to AppInfo("com.huffingtonpost.android", "https://www.huffpost.com"),
        "al_jazeera" to AppInfo("com.aljazeera.mobile", "https://www.aljazeera.com"),
        "npr" to AppInfo("org.npr.one"),
        "apple_news" to AppInfo("com.apple.news"),
        "newsbreak" to AppInfo("com.particlenews.newsbreak"),

        // Weather
        "weather_channel" to AppInfo("com.weather.Weather", "https://weather.com"),
        "accuweather" to AppInfo("com.accuweather.android", "https://www.accuweather.com"),

        // Education
        "duolingo" to AppInfo("com.duolingo", "https://www.duolingo.com"),
        "khan_academy" to AppInfo("org.khanacademy.android", "https://www.khanacademy.org"),
        "coursera" to AppInfo("org.coursera.android", "https://www.coursera.org"),
        "udemy" to AppInfo("com.udemy.android", "https://www.udemy.com"),
        "quizlet" to AppInfo("com.quizlet.quizletandroid", "https://quizlet.com"),
        "photomath" to AppInfo("com.microblink.photomath"),
        "brainly" to AppInfo("co.brainly", "https://brainly.com"),
        "skillshare" to AppInfo("com.skillshare.Skillshare", "https://www.skillshare.com"),

        // Health & Wellness
        "headspace" to AppInfo("com.getsomeheadspace.android", "https://www.headspace.com"),
        "calm" to AppInfo("com.calm.android", "https://www.calm.com"),
        "flo" to AppInfo("org.iggymedia.periodtracker"),
        "betterhelp" to AppInfo("com.betterhelp"),
        "webmd" to AppInfo("com.webmd.android", "https://www.webmd.com"),
        "noom" to AppInfo("com.wsl.noom"),
        "sleep_cycle" to AppInfo("com.northcube.sleepcycle"),

        // Photography & Video Editing
        "canva" to AppInfo("com.canva.editor", "https://www.canva.com"),
        "capcut" to AppInfo("com.lemon.lvoverseas"),
        "lightroom" to AppInfo("com.adobe.lrmobile"),
        "picsart" to AppInfo("com.picsart.studio"),
        "snapseed" to AppInfo("com.niksoftware.snapseed"),
        "vsco" to AppInfo("com.vsco.cam"),
        "inshot" to AppInfo("com.camerasideas.instashot"),
        "kinemaster" to AppInfo("com.nexstreaming.app.kinemasterfree"),

        // Books & Reading
        "kindle" to AppInfo("com.amazon.kindle"),
        "audible" to AppInfo("com.audible.application"),
        "medium" to AppInfo("com.medium.reader", "https://medium.com"),
        "goodreads" to AppInfo("com.goodreads.android.books", "https://www.goodreads.com"),
        "pocket" to AppInfo("com.ideashower.readitlater.pro", "https://getpocket.com"),
        "feedly" to AppInfo("com.devhd.feedly", "https://feedly.com"),
        "google_play_books" to AppInfo("com.google.android.apps.books"),
        "libby" to AppInfo("com.overdrive.mobile.android.libby"),
        "scribd" to AppInfo("com.scribd.app.reader0", "https://www.scribd.com"),

        // Real Estate
        "zillow" to AppInfo("com.zillow.android.zillowmap", "https://www.zillow.com"),
        "realtor" to AppInfo("com.move.realtor", "https://www.realtor.com"),
        "redfin" to AppInfo("com.redfin.android", "https://www.redfin.com"),
        "trulia" to AppInfo("com.trulia.android", "https://www.trulia.com"),
        "apartments" to AppInfo("com.apartmentlist", "https://www.apartments.com"),

        // Gaming
        "steam" to AppInfo("com.valvesoftware.android.steam.community", "https://store.steampowered.com"),
        "roblox" to AppInfo("com.roblox.client", "https://www.roblox.com"),
        "xbox" to AppInfo("com.microsoft.xboxone.smartglass"),
        "playstation" to AppInfo("com.scee.psxandroid"),
        "epic_games" to AppInfo("com.epicgames.portal"),
        "youtube_kids" to AppInfo("com.google.android.apps.youtube.kids"),

        // Crypto & Advanced Finance
        "binance" to AppInfo("com.binance.dev", "https://www.binance.com"),
        "crypto_com" to AppInfo("co.mona.android", "https://crypto.com"),
        "kraken" to AppInfo("com.kraken.trade", "https://www.kraken.com"),
        "webull" to AppInfo("com.webull.android"),
        "fidelity" to AppInfo("com.fidelity.android"),
        "schwab" to AppInfo("com.schwab.mobile"),
        "etrade" to AppInfo("com.etrade.mobilepro.activity"),
        "wise" to AppInfo("com.transferwise.android", "https://wise.com"),
        "revolut" to AppInfo("com.revolut.revolut"),

        // Job Search
        "indeed" to AppInfo("com.indeed.android.jobsearch", "https://www.indeed.com"),
        "glassdoor" to AppInfo("com.glassdoor.app", "https://www.glassdoor.com"),
        "ziprecruiter" to AppInfo("com.ziprecruiter.android.release", "https://www.ziprecruiter.com"),
        "handshake" to AppInfo("com.joinhandshake.student"),

        // VPN & Security
        "nordvpn" to AppInfo("com.nordvpn.android"),
        "expressvpn" to AppInfo("com.expressvpn.vpn"),
        "bitwarden" to AppInfo("com.x8bit.bitwarden"),
        "1password" to AppInfo("com.onepassword.android"),
        "lastpass" to AppInfo("com.lastpass.lpandroid"),
        "authy" to AppInfo("com.authy.authy"),

        // Travel & Transport
        "tripadvisor" to AppInfo("com.tripadvisor.tripadvisor", "https://www.tripadvisor.com"),
        "kayak" to AppInfo("com.kayak.android", "https://www.kayak.com"),
        "hopper" to AppInfo("com.hopper.mountainview.play"),
        "citymapper" to AppInfo("com.citymapper.app.release"),
        "flightradar24" to AppInfo("com.flightradar24free"),
        "grab" to AppInfo("com.grabtaxi.passenger"),
        "careem" to AppInfo("com.careem.acma"),
        "moovit" to AppInfo("com.tranzmate"),

        // Social (more)
        "tumblr" to AppInfo("com.tumblr", "https://www.tumblr.com"),
        "quora" to AppInfo("com.quora.android", "https://www.quora.com"),
        "clubhouse" to AppInfo("com.clubhouse.app"),
        "mastodon" to AppInfo("org.joinmastodon.android"),
        "lemon8" to AppInfo("com.bd.nproject"),

        // Misc Utilities
        "google_home" to AppInfo("com.google.android.apps.chromecast.app"),
        "google_fit" to AppInfo("com.google.android.apps.fitness"),
        "samsung_health" to AppInfo("com.sec.android.app.shealth"),
        "samsung_notes" to AppInfo("com.samsung.android.app.notes"),
        "files_by_google" to AppInfo("com.google.android.apps.nbu.files"),
        "google_voice" to AppInfo("com.google.android.apps.googlevoice"),
        "textnow" to AppInfo("com.enflick.android.TextNow"),
        "calculator_google" to AppInfo("com.google.android.calculator"),
        "google_clock" to AppInfo("com.google.android.deskclock"),
        "google_weather" to AppInfo("com.google.android.apps.weather"),
        "microsoft_to_do" to AppInfo("com.microsoft.todos"),
        "ticktick" to AppInfo("com.ticktick.task", "https://ticktick.com"),
        "any_do" to AppInfo("com.anydo"),

        // Regional & Misc
        "mercado_libre" to AppInfo("com.mercadolibre", "https://www.mercadolibre.com"),
        "rappi" to AppInfo("com.grability.rappi"),
        "shopee" to AppInfo("com.shopee.android"),
        "lazada" to AppInfo("com.lazada.android"),
        "swiggy" to AppInfo("in.swiggy.android"),
        "zomato" to AppInfo("com.application.zomato", "https://www.zomato.com"),
        "yelp" to AppInfo("com.yelp.android", "https://www.yelp.com"),
        "temu" to AppInfo("com.einnovation.temu", "https://www.temu.com"),

        // Podcasts
        "pocket_casts" to AppInfo("au.com.shiftyjelly.pocketcasts", "https://pocketcasts.com"),
        "castbox" to AppInfo("fm.castbox.audiobook.radio.podcast", "https://castbox.fm"),
        "iheartradio" to AppInfo("com.clearchannel.iheartradio.controller", "https://www.iheart.com"),
        "overcast" to AppInfo("com.overcast.overcast"),
        "amazon_music" to AppInfo("com.amazon.mp3", "https://music.amazon.com"),
        "tidal" to AppInfo("com.aspiro.tidal", "https://tidal.com"),

        // Sports
        "espn" to AppInfo("com.espn.score_center", "https://www.espn.com"),
        "nfl" to AppInfo("com.gotv.nflgamecenter.us.lite", "https://www.nfl.com"),
        "nba" to AppInfo("com.nba.game", "https://www.nba.com"),
        "mlb" to AppInfo("com.bamnetworks.mobile.android.gameday.atbat", "https://www.mlb.com"),
        "cbs_sports" to AppInfo("com.handmark.sportcaster", "https://www.cbssports.com"),
        "fanduel" to AppInfo("com.fanduel.android.self", "https://www.fanduel.com"),
        "draftkings" to AppInfo("com.draftkings.dknativermgGP", "https://www.draftkings.com"),
        "the_score" to AppInfo("com.fivemobile.thescore", "https://www.thescore.com"),

        // Home Automation / Smart Home
        "alexa" to AppInfo("com.amazon.dee.app", "https://alexa.amazon.com"),
        "smartthings" to AppInfo("com.samsung.android.oneconnect"),
        "philips_hue" to AppInfo("com.philips.lighting.hue2"),
        "ring" to AppInfo("com.ringapp"),
        "wyze" to AppInfo("com.hualai.wyzecam"),
        "google_nest" to AppInfo("com.nest.android"),

        // Business & Project Management
        "jira" to AppInfo("com.atlassian.android.jira.core", "https://www.atlassian.com/software/jira"),
        "monday" to AppInfo("com.monday.monday", "https://monday.com"),
        "basecamp" to AppInfo("com.basecamp.bc3", "https://basecamp.com"),
        "hubspot" to AppInfo("com.hubspot.android", "https://www.hubspot.com"),
        "salesforce" to AppInfo("com.salesforce.chatter", "https://www.salesforce.com"),
        "confluence" to AppInfo("com.atlassian.android.confluence.core", "https://www.atlassian.com/software/confluence"),
        "clickup" to AppInfo("com.clickup.android", "https://clickup.com"),

        // Dev Tools
        "github" to AppInfo("com.github.android", "https://github.com"),
        "gitlab" to AppInfo("com.commit451.gitlab", "https://gitlab.com"),
        "stack_overflow" to AppInfo("com.stackexchange.marvin", "https://stackoverflow.com"),

        // Banking
        "chase" to AppInfo("com.chase.sig.android"),
        "bank_of_america" to AppInfo("com.infonow.bofa"),
        "wells_fargo" to AppInfo("com.wf.wellsfargomobile"),
        "capital_one" to AppInfo("com.konylabs.capitalone"),
        "citi" to AppInfo("com.citi.citimobile"),

        // Document & Scanner
        "camscanner" to AppInfo("com.intsig.camscanner"),
        "microsoft_lens" to AppInfo("com.microsoft.office.officelens"),
        "adobe_scan" to AppInfo("com.adobe.scan.android"),

        // Airlines
        "united_airlines" to AppInfo("com.united.mobile.android", "https://www.united.com"),
        "delta" to AppInfo("com.delta.mobile.android", "https://www.delta.com"),
        "american_airlines" to AppInfo("com.aa.android", "https://www.aa.com"),
        "southwest" to AppInfo("com.southwestairlines.mobile", "https://www.southwest.com"),

        // More Finance
        "credit_karma" to AppInfo("com.creditkarma.mobile", "https://www.creditkarma.com"),
        "ynab" to AppInfo("com.youneedabudget.evergreen.app", "https://www.ynab.com"),
        "acorns" to AppInfo("com.acorns.android", "https://www.acorns.com"),
        "sofi" to AppInfo("com.sofi.mobile", "https://www.sofi.com"),
        "chime" to AppInfo("com.onedebit.chime"),
        "empower" to AppInfo("com.personalcapital.pcapandroid", "https://www.empower.com"),

        // Deals & Rewards
        "ibotta" to AppInfo("com.ibotta.android"),
        "rakuten" to AppInfo("com.ebates", "https://www.rakuten.com"),
        "groupon" to AppInfo("com.groupon", "https://www.groupon.com"),
        "retailmenot" to AppInfo("com.whaleshark.retailmenot", "https://www.retailmenot.com"),

        // More Fitness
        "alltrails" to AppInfo("com.alltrails.alltrails", "https://www.alltrails.com"),
        "runkeeper" to AppInfo("com.fitnesskeeper.runkeeper.pro"),
        "adidas_running" to AppInfo("com.runtastic.android"),
        "map_my_run" to AppInfo("com.mapmyrun.android2"),
        "seven" to AppInfo("se.perigee.android.seven"),

        // Grocery & Wholesale
        "kroger" to AppInfo("com.kroger.mobile", "https://www.kroger.com"),
        "costco" to AppInfo("com.costco.app.android", "https://www.costco.com"),
        "sams_club" to AppInfo("com.rfi.sam.android", "https://www.samsclub.com"),
        "whole_foods" to AppInfo("com.amazon.wholefoodsmarket"),
        "aldi" to AppInfo("us.aldi.mobile", "https://www.aldi.us"),

        // More Ride & Delivery
        "bolt" to AppInfo("ee.mtakso.client", "https://bolt.eu"),
        "didi" to AppInfo("com.sdu.didi.gsui"),
        "gopuff" to AppInfo("com.gopuff.android"),
        "getir" to AppInfo("com.getir.getirmobile"),

        // Family & Parenting
        "life360" to AppInfo("com.life360.android.safetymapd"),
        "family_link" to AppInfo("com.google.android.apps.kids.familylink"),

        // More Communication
        "truecaller" to AppInfo("com.truecaller"),
        "webex" to AppInfo("com.cisco.webex.meetings", "https://www.webex.com"),
        "groupme" to AppInfo("com.groupme.android"),

        // Design & Creative
        "figma" to AppInfo("com.figma.mirror", "https://www.figma.com"),
        "miro" to AppInfo("com.realtimeboard", "https://miro.com"),
        "adobe_express" to AppInfo("com.adobe.spark.post"),

        // More Notes
        "obsidian" to AppInfo("md.obsidian"),
        "simplenote" to AppInfo("com.automattic.simplenote"),
        "bear" to AppInfo("net.shinyfrog.bear"),

        // Remittance
        "western_union" to AppInfo("com.westernunion.android.mtapp", "https://www.westernunion.com"),
        "remitly" to AppInfo("com.remitly.androidapp", "https://www.remitly.com"),
        "worldremit" to AppInfo("com.worldremit.android", "https://www.worldremit.com"),

        // Pets
        "chewy" to AppInfo("com.chewy.android", "https://www.chewy.com"),
        "rover" to AppInfo("com.rover.android", "https://www.rover.com"),

        // Social & Events
        "nextdoor" to AppInfo("com.nextdoor", "https://nextdoor.com"),
        "meetup" to AppInfo("com.meetup", "https://www.meetup.com"),
        "eventbrite" to AppInfo("com.eventbrite.attendee", "https://www.eventbrite.com"),

        // Language Learning
        "deepl" to AppInfo("com.deepl.mobiletranslator", "https://www.deepl.com"),
        "babbel" to AppInfo("com.babbel.mobile.android.en", "https://www.babbel.com"),
        "rosetta_stone" to AppInfo("air.com.rosettastone.mobile.CoursePlayer", "https://www.rosettastone.com"),

        // Car & Auto
        "gasbuddy" to AppInfo("gbis.gbandroid", "https://www.gasbuddy.com"),
        "parkmobile" to AppInfo("net.sharewire.parkmobilev2"),
        "turo" to AppInfo("com.relayrides.android", "https://turo.com"),

        // More Utilities
        "ifttt" to AppInfo("com.ifttt.ifttt"),
        "forest" to AppInfo("cc.forestapp"),
        "widgetsmith" to AppInfo("com.widgetsmith.widgetsmith"),

        // Streaming Video
        "paramount_plus" to AppInfo("com.cbs.app", "https://www.paramountplus.com"),
        "peacock" to AppInfo("com.peacocktv.peacockandroid", "https://www.peacocktv.com"),
        "crunchyroll" to AppInfo("com.crunchyroll.crunchyroid", "https://www.crunchyroll.com"),
        "apple_tv_plus" to AppInfo("com.apple.atve.androidtv.appletv", "https://tv.apple.com"),
        "discovery_plus" to AppInfo("com.discovery.discoveryplus", "https://www.discoveryplus.com"),
        "pluto_tv" to AppInfo("tv.pluto.android", "https://pluto.tv"),
        "tubi" to AppInfo("com.tubitv", "https://tubitv.com"),
        "plex" to AppInfo("com.plexapp.android", "https://www.plex.tv"),
        "roku_channel" to AppInfo("com.roku.remote", "https://therokuchannel.roku.com"),
        "mubi" to AppInfo("com.mubi", "https://mubi.com"),
        "curiosity_stream" to AppInfo("com.curiositystream.curiositystream", "https://curiositystream.com"),
        "fandango_at_home" to AppInfo("com.vudu.air", "https://www.vudu.com"),

        // Anime & Comics
        "webtoon" to AppInfo("com.naver.linewebtoon", "https://www.webtoons.com"),
        "myanimelist" to AppInfo("net.myanimelist.app", "https://myanimelist.net"),
        "tapas" to AppInfo("com.tapastic", "https://tapas.io"),
        "manga_plus" to AppInfo("com.shueisha.mangaplus", "https://mangaplus.shueisha.co.jp"),

        // Resale & Marketplace
        "mercari" to AppInfo("com.kouzoh.mercari", "https://www.mercari.com"),
        "poshmark" to AppInfo("com.poshmark.app", "https://poshmark.com"),
        "depop" to AppInfo("com.depop", "https://www.depop.com"),
        "offerup" to AppInfo("com.offerup", "https://offerup.com"),
        "vinted" to AppInfo("fr.vinted", "https://www.vinted.com"),
        "thredup" to AppInfo("com.thredup.thredup", "https://www.thredup.com"),

        // Pharmacy & Health Services
        "goodrx" to AppInfo("com.goodrx", "https://www.goodrx.com"),
        "cvs" to AppInfo("com.cvs.launchers.cvs", "https://www.cvs.com"),
        "walgreens" to AppInfo("com.walgreens.wag", "https://www.walgreens.com"),
        "teladoc" to AppInfo("com.teladoc.members", "https://www.teladoc.com"),
        "zocdoc" to AppInfo("com.zocdoc.android", "https://www.zocdoc.com"),
        "mychart" to AppInfo("epic.mychart.android", "https://mychart.com"),
        "one_medical" to AppInfo("com.onemedical.android", "https://www.onemedical.com"),

        // Home Services
        "taskrabbit" to AppInfo("com.taskrabbit.droid.consumer", "https://www.taskrabbit.com"),
        "thumbtack" to AppInfo("com.thumbtack.consumer", "https://www.thumbtack.com"),
        "angi" to AppInfo("com.angieslist.android", "https://www.angi.com"),

        // Insurance
        "geico" to AppInfo("com.geico.mobile", "https://www.geico.com"),
        "progressive" to AppInfo("com.progressive.mobile", "https://www.progressive.com"),
        "state_farm" to AppInfo("com.statefarm.pocketagent", "https://www.statefarm.com"),
        "lemonade" to AppInfo("com.lemonade.insurance", "https://www.lemonade.com"),

        // Tax
        "turbotax" to AppInfo("com.intuit.turbotax.mobile", "https://turbotax.intuit.com"),
        "hr_block" to AppInfo("com.hrblock.blockmobile", "https://www.hrblock.com"),

        // Document Signing
        "docusign" to AppInfo("com.docusign.ink", "https://www.docusign.com"),

        // Rewards & Cashback
        "fetch_rewards" to AppInfo("com.fetchrewards.fetchrewards.hop", "https://www.fetchrewards.com"),
        "shopkick" to AppInfo("com.shopkick.app", "https://www.shopkick.com"),
        "swagbucks" to AppInfo("com.prodege.swagbucks", "https://www.swagbucks.com"),

        // Crypto & Web3
        "metamask" to AppInfo("io.metamask", "https://metamask.io"),
        "trust_wallet" to AppInfo("com.wallet.crypto.trustapp", "https://trustwallet.com"),
        "phantom" to AppInfo("app.phantom", "https://phantom.app"),
        "ledger_live" to AppInfo("com.ledger.live", "https://www.ledger.com"),
        "uniswap" to AppInfo("org.uniswap.mobile", "https://app.uniswap.org"),

        // Password Managers
        "one_password" to AppInfo("com.onepassword.android", "https://1password.com"),
        "dashlane" to AppInfo("com.dashlane", "https://www.dashlane.com"),

        // Car Marketplace
        "carvana" to AppInfo("com.carvana.android", "https://www.carvana.com"),
        "autotrader" to AppInfo("com.autotrader.android", "https://www.autotrader.com"),
        "cargurus" to AppInfo("com.cargurus.android", "https://www.cargurus.com"),

        // Transit & Flight Tracking
        "transit_app" to AppInfo("com.thetransitapp.droid", "https://transitapp.com"),

        // Radio & Podcasts
        "tunein" to AppInfo("tunein.player", "https://tunein.com"),
        "siriusxm" to AppInfo("com.sirius", "https://www.siriusxm.com"),
        "audiomack" to AppInfo("com.audiomack", "https://audiomack.com"),
        "podcast_addict" to AppInfo("com.bambuna.podcastaddict"),

        // Weather (more)
        "windy" to AppInfo("com.windyty.android", "https://www.windy.com"),
        "weather_underground" to AppInfo("com.wunderground.android.weather", "https://www.wunderground.com"),

        // Email Clients
        "spark_email" to AppInfo("com.readdle.spark", "https://sparkmailapp.com"),
        "edison_mail" to AppInfo("com.easilydo.mail"),

        // Language Learning (more)
        "busuu" to AppInfo("com.busuu.android.enc", "https://www.busuu.com"),
        "memrise" to AppInfo("com.memrise.android.memrisecompanion", "https://www.memrise.com"),
        "hellotalk" to AppInfo("com.hellotalk", "https://www.hellotalk.com"),
        "tandem" to AppInfo("net.tandem", "https://www.tandem.net"),

        // Meditation & Wellness
        "insight_timer" to AppInfo("com.spotlightsix.zentimerlite2", "https://insighttimer.com"),
        "talkspace" to AppInfo("com.talkspace.talkspaceapp", "https://www.talkspace.com"),
        "waking_up" to AppInfo("com.wakingup.android", "https://www.wakingup.com"),

        // Parenting & Kids
        "babycenter" to AppInfo("com.babycenter.pregnancytracker", "https://www.babycenter.com"),
        "pbs_kids" to AppInfo("org.pbskids.video"),
        "abcmouse" to AppInfo("com.aofl.abcmouse"),
        "nick_jr" to AppInfo("com.nick.android.nickjr"),
        "the_bump" to AppInfo("com.xogrp.thebump", "https://www.thebump.com"),
        "peanut" to AppInfo("com.teampeanut.peanut", "https://www.peanut-app.io"),

        // Religious
        "muslim_pro" to AppInfo("com.bitsmedia.android.muslimpro"),
        "quran_app" to AppInfo("com.quran.labs.androidquran"),
        "bible_app" to AppInfo("com.sirma.mobile.bible.android"),
        "pray" to AppInfo("com.pray.app", "https://pray.com"),

        // Bills & Splitting
        "splitwise" to AppInfo("com.Splitwise.SplitwiseMobile", "https://www.splitwise.com"),
        "google_one" to AppInfo("com.google.android.apps.subscriptions.red"),

        // Habits
        "habitica" to AppInfo("com.habitrpg.android.habitica", "https://habitica.com"),

        // Cloud Storage (more)
        "box" to AppInfo("com.box.android", "https://www.box.com"),
        "mega" to AppInfo("mega.privacy.android.app", "https://mega.io"),

        // Home & Furniture
        "wayfair" to AppInfo("com.wayfair.wayfair", "https://www.wayfair.com"),
        "ikea" to AppInfo("com.ingka.ikea.app", "https://www.ikea.com"),
        "houzz" to AppInfo("com.houzz.app", "https://www.houzz.com"),

        // Fast Food & Restaurant
        "chick_fil_a" to AppInfo("com.chickfila.cfaone"),
        "chipotle" to AppInfo("com.chipotle.ordering", "https://www.chipotle.com"),
        "dominos" to AppInfo("com.dominospizza", "https://www.dominos.com"),
        "burger_king" to AppInfo("com.emn8.mobilePayBK", "https://www.bk.com"),
        "subway" to AppInfo("com.subway.mobile.subwayapp03"),
        "dunkin" to AppInfo("com.dunkinbrands.otgo", "https://www.dunkindonuts.com"),
        "papa_johns" to AppInfo("com.papajohns", "https://www.papajohns.com"),
        "pizza_hut" to AppInfo("com.pizzahut.phorder", "https://www.pizzahut.com"),
        "wendys" to AppInfo("com.wendys.nutritiontool", "https://www.wendys.com"),
        "taco_bell" to AppInfo("com.tacobell.ordering", "https://www.tacobell.com"),
        "popeyes" to AppInfo("com.plowright.popeyes"),
        "panera" to AppInfo("com.panerabread.ordering", "https://www.panerabread.com"),

        // Airlines (more)
        "jetblue" to AppInfo("com.jetblue.JetBlueAndroid", "https://www.jetblue.com"),
        "british_airways" to AppInfo("com.ba.mobile", "https://www.britishairways.com"),
        "spirit_airlines" to AppInfo("com.spirit.customerapp", "https://www.spirit.com"),
        "frontier_airlines" to AppInfo("com.flyfrontier.android", "https://www.flyfrontier.com"),
        "emirates" to AppInfo("com.emirates.ek.android", "https://www.emirates.com"),
        "turkish_airlines" to AppInfo("com.thy.android", "https://www.turkishairlines.com"),
        "lufthansa" to AppInfo("com.lh.lhconsumer", "https://www.lufthansa.com"),
        "qatar_airways" to AppInfo("com.qatarairways.qmobile", "https://www.qatarairways.com"),

        // Travel (more)
        "vrbo" to AppInfo("com.homeaway.android", "https://www.vrbo.com"),
        "hostelworld" to AppInfo("com.hostelworld.app", "https://www.hostelworld.com"),
        "skyscanner" to AppInfo("net.skyscanner.android.main", "https://www.skyscanner.com"),
        "google_flights" to AppInfo("com.google.android.apps.travel.onthego", "https://www.google.com/travel/flights"),
        "agoda" to AppInfo("com.agoda.mobile.consumer", "https://www.agoda.com"),
        "hotels_com" to AppInfo("com.hcom.android", "https://www.hotels.com"),

        // Retail & Shopping
        "best_buy" to AppInfo("com.bestbuy.android", "https://www.bestbuy.com"),
        "home_depot" to AppInfo("com.thehomedepot", "https://www.homedepot.com"),
        "lowes" to AppInfo("com.lowes.android", "https://www.lowes.com"),
        "nike" to AppInfo("com.nike.omega", "https://www.nike.com"),
        "adidas" to AppInfo("com.adidas.app", "https://www.adidas.com"),
        "sephora" to AppInfo("com.sephora", "https://www.sephora.com"),
        "ulta" to AppInfo("com.ulta.mobile", "https://www.ulta.com"),
        "macys" to AppInfo("com.macys.android", "https://www.macys.com"),
        "nordstrom" to AppInfo("com.nordstrom.app", "https://www.nordstrom.com"),
        "zara" to AppInfo("com.inditex.zara", "https://www.zara.com"),
        "h_and_m" to AppInfo("com.hm.goe", "https://www2.hm.com"),
        "uniqlo" to AppInfo("com.uniqlo.catalogue", "https://www.uniqlo.com"),
        "gap" to AppInfo("com.gap.flagship", "https://www.gap.com"),
        "old_navy" to AppInfo("com.oldnavy.oldnavy", "https://oldnavy.gap.com"),
        "asos" to AppInfo("com.asos.app", "https://www.asos.com"),
        "fashion_nova" to AppInfo("com.fashionnova.app", "https://www.fashionnova.com"),

        // Shipping & Tracking
        "fedex" to AppInfo("com.fedex.ida.android", "https://www.fedex.com"),
        "ups" to AppInfo("com.ups.mobile.android", "https://www.ups.com"),
        "usps" to AppInfo("com.usps", "https://www.usps.com"),
        "dhl" to AppInfo("com.dhl.ship", "https://www.dhl.com"),
        "package_tracker" to AppInfo("com.17track.mobile", "https://www.17track.net"),

        // Social (more)
        "bluesky" to AppInfo("xyz.blueskyweb.app", "https://bsky.app"),

        // Parking & EV
        "spothero" to AppInfo("com.spothero.spothero", "https://spothero.com"),
        "chargepoint" to AppInfo("com.coulombtech", "https://www.chargepoint.com"),
        "plugshare" to AppInfo("com.xatori.Plugshare", "https://www.plugshare.com"),

        // Video Communication
        "loom" to AppInfo("com.loom.android", "https://www.loom.com"),
        "marco_polo" to AppInfo("co.happybits.marcopolo"),

        // Education (more)
        "google_classroom" to AppInfo("com.google.android.apps.classroom"),
        "canvas_student" to AppInfo("com.instructure.candroid"),
        "remind" to AppInfo("com.remind101"),
        "classdojo" to AppInfo("com.classdojo.android"),
        "chegg" to AppInfo("com.chegg", "https://www.chegg.com"),
        "socratic" to AppInfo("com.google.socratic"),

        // Gaming
        "minecraft" to AppInfo("com.mojang.minecraftpe"),
        "pubg_mobile" to AppInfo("com.tencent.ig"),
        "call_of_duty_mobile" to AppInfo("com.activision.callofduty.shooter"),
        "genshin_impact" to AppInfo("com.miHoYo.GenshinImpact"),
        "candy_crush" to AppInfo("com.king.candycrushsaga"),
        "clash_of_clans" to AppInfo("com.supercell.clashofclans"),
        "clash_royale" to AppInfo("com.supercell.clashroyale"),
        "brawl_stars" to AppInfo("com.supercell.brawlstars"),
        "pokemon_go" to AppInfo("com.nianticlabs.pokemongo"),
        "coin_master" to AppInfo("com.moonactive.coinmaster"),

        // Banking (international)
        "n26" to AppInfo("de.number26.android", "https://n26.com"),
        "monzo" to AppInfo("co.uk.getmondo", "https://monzo.com"),
        "nubank" to AppInfo("com.nu.production", "https://nubank.com.br"),
        "starling" to AppInfo("com.starlingbank.android", "https://www.starlingbank.com"),

        // Car Rental
        "zipcar" to AppInfo("com.zcar", "https://www.zipcar.com"),
        "hertz" to AppInfo("com.hertz.android.app", "https://www.hertz.com"),
        "enterprise" to AppInfo("com.ehi.enterprise.android", "https://www.enterprise.com"),

        // VPN (more)
        "surfshark" to AppInfo("com.surfshark.vpnclient.android", "https://surfshark.com"),
        "cyberghost" to AppInfo("de.mobileconcepts.cyberghost", "https://www.cyberghostvpn.com"),

        // Printing
        "hp_smart" to AppInfo("com.hp.printercontrol"),

        // Fitness (more)
        "nike_training" to AppInfo("com.nike.ntc"),
        "sweat" to AppInfo("com.kaylaitsines.sweatwithkayla"),

        // Music Creation
        "bandlab" to AppInfo("com.bandlab.bandlab", "https://www.bandlab.com"),

        // Scanner & PDF
        "genius_scan" to AppInfo("com.thegrizzlylabs.geniusscan.free"),

        // Budgeting
        "rocket_money" to AppInfo("com.truebill", "https://www.rocketmoney.com"),

        // Mental Health
        "cerebral" to AppInfo("com.cerebral.patient", "https://cerebral.com"),

        // Period Tracker
        "clue" to AppInfo("com.clue.android", "https://helloclue.com"),

        // Home Security (more)
        "arlo" to AppInfo("com.arlo.app", "https://www.arlo.com"),
        "blink" to AppInfo("com.amazonaws.blink"),
        "simplisafe" to AppInfo("com.simplisafe.mobile", "https://simplisafe.com"),
        "adt" to AppInfo("com.adt.pulse"),

        // Grocery (more)
        "instacart_shopper" to AppInfo("com.instacart.shopper"),
        "shipt" to AppInfo("com.shipt.shopper", "https://www.shipt.com"),
        "publix" to AppInfo("com.publix.mobile"),
        "trader_joes" to AppInfo("com.traderjoes.app"),
        "safeway" to AppInfo("com.safeway.client.android.safeway"),

        // Loyalty & Rewards (brands)
        "chime_credit" to AppInfo("com.chime.credit"),
        "stash" to AppInfo("com.stash.stashinvest", "https://www.stash.com"),

        // Audiobooks
        "google_play_audiobooks" to AppInfo("com.google.android.apps.books"),

        // Horoscope
        "co_star" to AppInfo("co.star.ios", "https://www.costarastrology.com"),
        "the_pattern" to AppInfo("com.thepattern.app"),

        // Social Audio
        "twitter_spaces" to AppInfo("com.twitter.android"),

        // Booking & Appointments
        "vagaro" to AppInfo("com.vagaro.consumer", "https://www.vagaro.com"),
        "booksy" to AppInfo("com.booksy.us", "https://booksy.com"),

        // Kids Learning
        "abc_kids" to AppInfo("com.rvappstudios.abc_kids"),
        "youtube_kids_app" to AppInfo("com.google.android.apps.youtube.kids"),
        "duolingo_abc" to AppInfo("com.duolingo.literacy"),

        // Surveys & Earnings
        "google_opinion_rewards" to AppInfo("com.google.android.apps.paidtasks"),

        // Ride (more)
        "freenow" to AppInfo("taxi.android.client", "https://www.free-now.com"),
        "ola" to AppInfo("com.olacabs.customer", "https://www.olacabs.com"),
        "indriver" to AppInfo("sinet.startup.inDriver", "https://indriver.com"),

        // Ticketing & Events (more)
        "ticketmaster" to AppInfo("com.ticketmaster.mobile.android.na", "https://www.ticketmaster.com"),
        "stubhub" to AppInfo("com.stubhub", "https://www.stubhub.com"),
        "seatgeek" to AppInfo("com.seatgeek.android", "https://seatgeek.com"),
        "dice" to AppInfo("fm.dice", "https://dice.fm"),

        // Habit & Focus
        "focus_keeper" to AppInfo("com.bytesize.focuskeeper"),
        "fabulous" to AppInfo("co.thefabulous.app", "https://www.thefabulous.co"),
        "productive" to AppInfo("com.apalon.to.do.list"),

        // Photo Printing
        "shutterfly" to AppInfo("com.shutterfly", "https://www.shutterfly.com"),
        "snapfish" to AppInfo("com.snapfish.mobile", "https://www.snapfish.com"),

        // Dog Walking & Pet (more)
        "wag" to AppInfo("com.wagwalking.app", "https://wagwalking.com"),
        "petco" to AppInfo("com.petco", "https://www.petco.com"),

        // Communication (more)
        "telegram_x" to AppInfo("org.thunderdog.challegram"),
        "imo" to AppInfo("com.imo.android.imoim"),
        "botim" to AppInfo("com.algotelecom.aetalk"),
        "zangi" to AppInfo("me.zangi.android"),

        // News Aggregator
        "smartnews" to AppInfo("jp.gocro.smartnews.android", "https://www.smartnews.com"),
        "inshorts" to AppInfo("com.nis.app"),
        "ground_news" to AppInfo("com.groundnews"),

        // Finance Tracker
        "personal_capital" to AppInfo("com.personalcapital.pcapandroid"),

        // Coupons (more)
        "honey" to AppInfo("com.joinhoney.honey", "https://www.joinhoney.com"),
        "flipp" to AppInfo("com.wishabi.flipp"),

        // Fast Food & Restaurants (more)
        "kfc" to AppInfo("com.yum.kfc", "https://www.kfc.com"),
        "wingstop" to AppInfo("com.wingstop.order", "https://www.wingstop.com"),
        "sonic" to AppInfo("com.sonic.sonicdrivein", "https://www.sonicdrivein.com"),
        "arbys" to AppInfo("com.arbys.menu", "https://www.arbys.com"),
        "panda_express" to AppInfo("com.pandaexpress.mobile", "https://www.pandaexpress.com"),
        "buffalo_wild_wings" to AppInfo("com.buffalowildwings.app", "https://www.buffalowildwings.com"),
        "olive_garden" to AppInfo("com.darden.og.android", "https://www.olivegarden.com"),
        "ihop" to AppInfo("com.ihop.mobile", "https://www.ihop.com"),
        "jack_in_the_box" to AppInfo("com.jackinthebox.app", "https://www.jackinthebox.com"),
        "whataburger" to AppInfo("com.whataburger.app", "https://www.whataburger.com"),
        "nandos" to AppInfo("com.nandos.loyalty", "https://www.nandos.com"),
        "five_guys" to AppInfo("com.fiveguys.olo.android", "https://www.fiveguys.com"),
        "cookpad" to AppInfo("com.mufumbo.android.recipe.search", "https://cookpad.com"),
        "allrecipes" to AppInfo("com.allrecipes.spinner.free", "https://www.allrecipes.com"),
        "tasty" to AppInfo("com.buzzfeed.tasty", "https://tasty.co"),

        // Shopping & Retail (more)
        "zappos" to AppInfo("com.zappos.android", "https://www.zappos.com"),
        "footlocker" to AppInfo("com.footlocker.android", "https://www.footlocker.com"),
        "kohls" to AppInfo("com.kohls.mcommerce.opal", "https://www.kohls.com"),
        "jcpenney" to AppInfo("com.jcpenney.android", "https://www.jcpenney.com"),
        "bath_body_works" to AppInfo("com.bbw.app", "https://www.bathandbodyworks.com"),
        "urban_outfitters" to AppInfo("com.urbanoutfitters.app", "https://www.urbanoutfitters.com"),
        "forever_21" to AppInfo("com.forever21.android", "https://www.forever21.com"),
        "lululemon" to AppInfo("com.lululemon.shop", "https://www.lululemon.com"),
        "under_armour" to AppInfo("com.underarmour.shop", "https://www.underarmour.com"),
        "crocs" to AppInfo("com.crocs.app", "https://www.crocs.com"),
        "new_balance" to AppInfo("com.newbalance.app", "https://www.newbalance.com"),
        "puma" to AppInfo("com.puma.app", "https://www.puma.com"),
        "converse" to AppInfo("com.converse.app", "https://www.converse.com"),
        "tjmaxx" to AppInfo("com.tjx.tjmaxx", "https://www.tjmaxx.com"),
        "dicks_sporting" to AppInfo("com.dcsg.android", "https://www.dickssportinggoods.com"),
        "pottery_barn" to AppInfo("com.williams_sonoma.potterybarn", "https://www.potterybarn.com"),
        "anthropologie" to AppInfo("com.anthropologie.app", "https://www.anthropologie.com"),
        "west_elm" to AppInfo("com.williams_sonoma.westelm", "https://www.westelm.com"),
        "aldo" to AppInfo("com.aldo.android", "https://www.aldoshoes.com"),

        // Finance & Banking (more)
        "klarna" to AppInfo("com.myklarnamobile", "https://www.klarna.com"),
        "afterpay" to AppInfo("com.afterpay.afterpay", "https://www.afterpay.com"),
        "affirm" to AppInfo("com.affirm.central", "https://www.affirm.com"),
        "ally_bank" to AppInfo("com.ally.MobileBanking", "https://www.ally.com"),
        "discover" to AppInfo("com.discoverfinancial.mobile", "https://www.discover.com"),
        "barclays" to AppInfo("com.barclays.android.barclaysmobilebanking", "https://www.barclays.co.uk"),
        "hsbc" to AppInfo("com.htsu.hsbcpersonalbanking", "https://www.hsbc.com"),
        "payoneer" to AppInfo("com.payoneer.android", "https://www.payoneer.com"),
        "marcus" to AppInfo("com.marcus.android", "https://www.marcus.com"),
        "root_insurance" to AppInfo("com.joinroot.root", "https://www.joinroot.com"),

        // Streaming & TV (more)
        "sling_tv" to AppInfo("com.sling", "https://www.sling.com"),
        "fubo" to AppInfo("com.fubo.firetv", "https://www.fubo.tv"),
        "philo" to AppInfo("com.philo.philo", "https://www.philo.com"),
        "dazn" to AppInfo("com.dazn", "https://www.dazn.com"),
        "espn_plus" to AppInfo("com.espn.score_center", "https://plus.espn.com"),
        "britbox" to AppInfo("com.britbox.us", "https://www.britbox.com"),
        "rumble" to AppInfo("com.rumble.battles", "https://rumble.com"),
        "viki" to AppInfo("com.viki.android", "https://www.viki.com"),
        "kick" to AppInfo("com.kick.app", "https://kick.com"),

        // Communication (more)
        "kik" to AppInfo("kik.android", "https://www.kik.com"),
        "threema" to AppInfo("ch.threema.app", "https://threema.ch"),
        "element" to AppInfo("im.vector.app", "https://element.io"),
        "guilded" to AppInfo("com.guilded.guilded", "https://www.guilded.gg"),
        "telegram_premium" to AppInfo("org.telegram.messenger.web", "https://t.me"),

        // Travel & Transport (more)
        "trivago" to AppInfo("com.trivago", "https://www.trivago.com"),
        "rome2rio" to AppInfo("com.rome2rio.www.Rome2Rio", "https://www.rome2rio.com"),
        "maps_me" to AppInfo("com.mapswithme.maps.pro", "https://maps.me"),
        "omio" to AppInfo("de.goeuro.rosie", "https://www.omio.com"),
        "flixbus" to AppInfo("de.flixbus.app", "https://www.flixbus.com"),
        "amtrak" to AppInfo("com.amtrak.rider", "https://www.amtrak.com"),
        "greyhound" to AppInfo("com.greyhound.express", "https://www.greyhound.com"),

        // Health & Fitness (more)
        "medisafe" to AppInfo("com.medisafe.android.client"),
        "lose_it" to AppInfo("com.fitnow.loseit", "https://www.loseit.com"),
        "zero_fasting" to AppInfo("com.zerofasting.zero"),
        "pacer" to AppInfo("cc.pacer.androidapp"),
        "ada_health" to AppInfo("com.ada.app", "https://ada.com"),

        // Freelance & Jobs (more)
        "upwork" to AppInfo("com.upwork.android", "https://www.upwork.com"),
        "fiverr" to AppInfo("com.fiverr.fiverr", "https://www.fiverr.com"),
        "monster" to AppInfo("com.jobrapp.jobr", "https://www.monster.com"),
        "snagajob" to AppInfo("com.snagajob.jobseeker", "https://www.snagajob.com"),
        "toptal" to AppInfo("com.toptal.app", "https://www.toptal.com"),

        // Sports (more)
        "yahoo_sports" to AppInfo("com.yahoo.mobile.client.android.sportacular", "https://sports.yahoo.com"),
        "bleacher_report" to AppInfo("com.bleacherreport.android.teamstream", "https://bleacherreport.com"),
        "sofascore" to AppInfo("com.sofascore.results", "https://www.sofascore.com"),
        "fotmob" to AppInfo("com.mobilefootie.wc2010", "https://www.fotmob.com"),
        "livescore" to AppInfo("com.livescore", "https://www.livescore.com"),
        "onefootball" to AppInfo("de.motain.iliga", "https://onefootball.com"),
        "garmin_connect" to AppInfo("com.garmin.android.apps.connectmobile"),
        "whoop" to AppInfo("com.whoop.android"),

        // Music (more)
        "anghami" to AppInfo("com.anghami", "https://www.anghami.com"),
        "boomplay" to AppInfo("com.transsnet.store", "https://www.boomplay.com"),
        "gaana" to AppInfo("com.gaana", "https://gaana.com"),
        "jiosaavn" to AppInfo("com.jio.media.jiobeats", "https://www.jiosaavn.com"),

        // Photography & Editing (more)
        "b612" to AppInfo("com.linecorp.b612.android"),
        "snow" to AppInfo("com.campmobile.snow"),
        "prequel" to AppInfo("com.prequel.app"),
        "afterlight" to AppInfo("com.afterlight"),
        "darkroom" to AppInfo("co.bergen.Darkroom"),
        "retrica" to AppInfo("com.venticake.retrica"),
        "foodie_camera" to AppInfo("com.linecorp.foodcam.android"),

        // Kids & Education (more)
        "scratch" to AppInfo("org.scratch", "https://scratch.mit.edu"),
        "tynker" to AppInfo("com.tynker.TynkerIDE", "https://www.tynker.com"),
        "prodigy_math" to AppInfo("com.prodigygame.prodigy", "https://www.prodigygame.com"),
        "epic_reading" to AppInfo("com.getepic.Epic", "https://www.getepic.com"),
        "toca_boca" to AppInfo("com.tocaboca.tocalifeworld"),
        "lego" to AppInfo("com.lego.city.my_city2", "https://www.lego.com"),

        // Productivity (more)
        "airtable" to AppInfo("com.formagrid.airtable", "https://airtable.com"),
        "coda" to AppInfo("io.coda.codadocs", "https://coda.io"),
        "craft" to AppInfo("com.luki.craft", "https://www.craft.do"),
        "superhuman" to AppInfo("com.superhuman.mail", "https://superhuman.com"),
        "fantastical" to AppInfo("com.flexibits.fantastical2"),
        "things_3" to AppInfo("com.culturedcode.ThingsMac"),

        // Utilities (more)
        "speedtest" to AppInfo("org.zwanoo.android.speedtest", "https://www.speedtest.net"),
        "malwarebytes" to AppInfo("org.malwarebytes.antimalware"),
        "hotspot_shield" to AppInfo("hotspotshield.android.vpn"),

        // Real Estate (more)
        "hotpads" to AppInfo("com.hotpads.android", "https://hotpads.com"),
        "compass_real_estate" to AppInfo("com.urbancompass.android", "https://www.compass.com"),
        "opendoor" to AppInfo("com.opendoor.buyerapp", "https://www.opendoor.com"),

        // Automotive (more)
        "carfax" to AppInfo("com.vast.carfax", "https://www.carfax.com"),
        "cars_com" to AppInfo("com.cars.android", "https://www.cars.com"),
        "edmunds" to AppInfo("com.edmunds", "https://www.edmunds.com"),
        "kelley_blue_book" to AppInfo("com.kbb.mobile", "https://www.kbb.com"),

        // News (more)
        "inoreader" to AppInfo("com.innologica.inoreader", "https://www.inoreader.com"),
        "yahoo_news" to AppInfo("com.yahoo.mobile.client.android.yahoo", "https://news.yahoo.com"),
        "news_republic" to AppInfo("com.mobilesrepublic.appy"),
        "ap_news" to AppInfo("mnn.Android", "https://apnews.com"),

        // --- Batch 2: Additional popular apps ---
        // Food & Dining
        "opentable" to AppInfo("com.opentable", "https://www.opentable.com"),
        "resy" to AppInfo("com.resy.android", "https://resy.com"),
        "deliveroo" to AppInfo("com.deliveroo.orderapp", "https://deliveroo.co.uk"),
        "foodpanda" to AppInfo("com.global.foodpanda.android", "https://www.foodpanda.com"),
        "just_eat" to AppInfo("com.justeat.app.uk", "https://www.just-eat.co.uk"),
        "seamless" to AppInfo("com.seamless.consumer", "https://www.seamless.com"),
        "caviar" to AppInfo("com.trycaviar.consumer", "https://www.trycaviar.com"),
        "slice" to AppInfo("com.slicelife.pizza", "https://slicelife.com"),
        "hungrypanda" to AppInfo("com.nicetomeetyou.hungrypanda", "https://www.hungrypanda.co"),
        "gopuff_food" to AppInfo("com.gopuff.consumer", "https://gopuff.com"),
        "shake_shack" to AppInfo("com.app_shackburger.android", "https://www.shakeshack.com"),
        "five_below" to AppInfo("com.fivebelow.fivebelow", "https://www.fivebelow.com"),
        "wawa" to AppInfo("com.wawa", "https://www.wawa.com"),
        "raising_canes" to AppInfo("com.raisingcanes.app", "https://www.raisingcanes.com"),
        "little_caesars" to AppInfo("com.littlecaesars", "https://littlecaesars.com"),
        "jersey_mikes" to AppInfo("com.jerseymikes.jmapp", "https://www.jerseymikes.com"),
        "firehouse_subs" to AppInfo("olo.firehousesubs", "https://www.firehousesubs.com"),
        "crumbl" to AppInfo("com.crumbl.app", "https://crumblcookies.com"),
        "sweetgreen" to AppInfo("com.sweetgreen.android", "https://www.sweetgreen.com"),
        "cava" to AppInfo("com.cfrp.cava", "https://cava.com"),
        "noodles_co" to AppInfo("com.noodles.android.myapp", "https://www.noodles.com"),
        "culvers" to AppInfo("com.culvers.culvers", "https://www.culvers.com"),

        // Streaming & Entertainment
        "vimeo" to AppInfo("com.vimeo.android.videoapp", "https://vimeo.com"),
        "bilibili" to AppInfo("tv.danmaku.bili", "https://www.bilibili.com"),
        "iqiyi" to AppInfo("com.qiyi.video", "https://www.iq.com"),
        "viu" to AppInfo("com.vuclip.viu", "https://www.viu.com"),
        "zee5" to AppInfo("com.graymatrix.did", "https://www.zee5.com"),
        "jio_cinema" to AppInfo("com.jio.media.ondemand", "https://www.jiocinema.com"),
        "sonyliv" to AppInfo("com.sonyliv", "https://www.sonyliv.com"),
        "voot" to AppInfo("com.viacom18.vootkids", "https://www.voot.com"),
        "altbalaji" to AppInfo("com.balaji.alt", "https://www.altbalaji.com"),
        "bigo_live" to AppInfo("sg.bigo.live", "https://www.bigo.tv"),
        "likee" to AppInfo("video.like", "https://likee.video"),
        "kwai" to AppInfo("com.kwai.video", "https://www.kwai.com"),
        "triller" to AppInfo("co.triller.diesel", "https://triller.co"),
        "lomotif" to AppInfo("com.lomotif.android", "https://lomotif.com"),
        "caffeine" to AppInfo("tv.caffeine.android", "https://www.caffeine.tv"),

        // Games
        "among_us" to AppInfo("com.innersloth.spacemafia", null),
        "subway_surfers" to AppInfo("com.kiloo.subwaysurf", null),
        "chess_com" to AppInfo("com.chess", "https://www.chess.com"),
        "lichess" to AppInfo("org.lichess.mobileapp", "https://lichess.org"),
        "wordle" to AppInfo("com.nytimes.crossword", "https://www.nytimes.com/games/wordle"),
        "gardenscapes" to AppInfo("com.playrix.gardenscapes", null),
        "homescapes" to AppInfo("com.playrix.homescapes", null),
        "angry_birds" to AppInfo("com.rovio.angrybirds", null),
        "temple_run" to AppInfo("com.imangi.templerun2", null),
        "8_ball_pool" to AppInfo("com.miniclip.eightballpool", null),
        "asphalt_9" to AppInfo("com.gameloft.android.ANMP.GloftA9HM", null),
        "free_fire" to AppInfo("com.dts.freefireth", null),
        "mobile_legends" to AppInfo("com.mobile.legends", null),
        "hay_day" to AppInfo("com.supercell.hayday", null),
        "stumble_guys" to AppInfo("com.kitkagames.fallbuddies", null),
        "royal_match" to AppInfo("com.dreamgames.royalmatch", null),
        "monopoly_go" to AppInfo("com.scopely.monopolygo", null),
        "honor_of_kings" to AppInfo("com.levelinfinite.hotta.gp", null),
        "diablo_immortal" to AppInfo("com.blizzard.diablo.immortal", null),

        // Finance & Banking
        "n26" to AppInfo("de.number26.android", "https://n26.com"),
        "current_bank" to AppInfo("com.current.app", "https://current.com"),
        "varo" to AppInfo("com.varomoney", "https://www.varomoney.com"),
        "dave" to AppInfo("com.dave", "https://dave.com"),
        "greenlight" to AppInfo("com.greenlight", "https://www.greenlight.com"),
        "step" to AppInfo("com.step.app", "https://step.com"),
        "albert" to AppInfo("com.albert.saving", "https://albert.com"),
        "digit" to AppInfo("com.digit.android", "https://digit.co"),
        "oportun" to AppInfo("com.oportun.mobile", "https://oportun.com"),
        "aspiration" to AppInfo("com.aspiration.app", "https://www.aspiration.com"),
        "wealthfront" to AppInfo("com.wealthfront", "https://www.wealthfront.com"),
        "betterment" to AppInfo("com.betterment", "https://www.betterment.com"),
        "titan" to AppInfo("com.titan.invest", "https://www.titan.com"),
        "m1_finance" to AppInfo("com.m1finance.android", "https://www.m1finance.com"),
        "webull_options" to AppInfo("com.webull.android", "https://www.webull.com"),
        "tastyworks" to AppInfo("com.tastyworks.tastyworks", "https://www.tastyworks.com"),
        "bamboo" to AppInfo("com.investbamboo.app", "https://investbamboo.com"),

        // Shopping & Marketplace
        "stockx" to AppInfo("com.stockx.stockx", "https://stockx.com"),
        "goat" to AppInfo("com.airgoat.goat", "https://www.goat.com"),
        "grailed" to AppInfo("com.grailed.grailed", "https://www.grailed.com"),
        "reverb" to AppInfo("com.reverb.android", "https://reverb.com"),
        "whatnot" to AppInfo("com.whatnot.whatnot", "https://www.whatnot.com"),
        "curtsy" to AppInfo("com.curtsy.curtsy", "https://www.curtsy.com"),
        "kidizen" to AppInfo("com.kidizen.app", "https://www.kidizen.com"),
        "chairish" to AppInfo("com.chairish.buyer", "https://www.chairish.com"),
        "tradesy" to AppInfo("com.tradesy.tradesy", "https://www.tradesy.com"),
        "decluttr" to AppInfo("com.decluttr.app", "https://www.decluttr.com"),
        "boxed" to AppInfo("com.boxed.consumer", "https://www.boxed.com"),
        "flaconi" to AppInfo("com.flaconi.app", "https://www.flaconi.de"),
        "rue_la_la" to AppInfo("com.ruelala.ruelala", "https://www.ruelala.com"),
        "gilt" to AppInfo("com.gilt.android", "https://www.gilt.com"),
        "sierra" to AppInfo("com.tjx.sierra", "https://www.sierra.com"),
        "burlington" to AppInfo("com.burlington.app", "https://www.burlington.com"),
        "dollar_tree" to AppInfo("com.dollartree", "https://www.dollartree.com"),
        "dollar_general" to AppInfo("com.dollargeneral.android", "https://www.dollargeneral.com"),
        "family_dollar" to AppInfo("com.familydollar.android", "https://www.familydollar.com"),
        "big_lots" to AppInfo("com.biglots.biglots", "https://www.biglots.com"),
        "temu_uk" to AppInfo("com.einnovation.temu", "https://www.temu.com"),

        // Education & Learning
        "kahoot" to AppInfo("no.mobitroll.kahoot.android", "https://kahoot.it"),
        "blinkist" to AppInfo("com.blinkslabs.blinkist.android", "https://www.blinkist.com"),
        "khan_academy_kids" to AppInfo("org.khanacademy.kids", "https://learn.khanacademy.org/khan-academy-kids"),
        "brilliant" to AppInfo("org.brilliant.android", "https://brilliant.org"),
        "codecademy" to AppInfo("com.ryzac.codecademygo", "https://www.codecademy.com"),
        "sololearn" to AppInfo("com.sololearn", "https://www.sololearn.com"),
        "mimo" to AppInfo("com.getmimo", "https://getmimo.com"),
        "grasshopper" to AppInfo("com.area120.grasshopper", "https://grasshopper.app"),
        "enki" to AppInfo("com.enki.insights", "https://www.enki.com"),
        "elevate" to AppInfo("com.wonder.cortex", "https://www.elevateapp.com"),
        "lumosity" to AppInfo("com.lumoslabs.lumosity", "https://www.lumosity.com"),
        "peak" to AppInfo("com.brainbow.peak.app", "https://www.peak.net"),
        "studocu" to AppInfo("com.studocu.app", "https://www.studocu.com"),
        "chegg_study" to AppInfo("com.chegg.study", "https://www.chegg.com"),
        "wolfram_alpha" to AppInfo("com.wolfram.android.alpha", "https://www.wolframalpha.com"),
        "symbolab" to AppInfo("com.devsense.symbolab", "https://www.symbolab.com"),
        "mathway" to AppInfo("com.bagatrix.mathway.android", "https://www.mathway.com"),
        "quillbot" to AppInfo("com.quillbot.app", "https://quillbot.com"),
        "grammarly" to AppInfo("com.grammarly.android.keyboard", "https://www.grammarly.com"),

        // Health & Wellness
        "bettersleep" to AppInfo("com.ipnos.sleepcycle", "https://www.bettersleep.com"),
        "sleep_sounds" to AppInfo("com.relaxio.relax", null),
        "seven_minute" to AppInfo("se.perigee.android.seven", null),
        "waterllama" to AppInfo("com.codium.waterllama", null),
        "plant_nanny" to AppInfo("com.fourdesire.plantnanny2", null),
        "period_tracker" to AppInfo("com.period.tracker.lite", null),
        "ovia" to AppInfo("com.ovuline.pregnancy", "https://www.oviahealth.com"),
        "nurx" to AppInfo("com.nurx.app", "https://www.nurx.com"),
        "pill_reminder" to AppInfo("com.medisafe.android.client", null),
        "blood_pressure" to AppInfo("com.bloodpressureapp.tracker", null),
        "glucose_buddy" to AppInfo("com.skyhealth.glucosebuddyfree", null),
        "mayo_clinic" to AppInfo("com.mayoclinic.patient", "https://www.mayoclinic.org"),
        "peppy" to AppInfo("com.peppy.app", "https://peppy.health"),
        "mindbody" to AppInfo("com.mindbodyonline.connect", "https://www.mindbodyonline.com"),
        "classpass" to AppInfo("com.classpass.classpass", "https://classpass.com"),

        // Travel & Transportation
        "couchsurfing" to AppInfo("com.couchsurfing.mobile", "https://www.couchsurfing.com"),
        "rome2rio" to AppInfo("com.rome2rio.www.rome2rio", "https://www.rome2rio.com"),
        "kiwi" to AppInfo("com.skypicker.main", "https://www.kiwi.com"),
        "komoot" to AppInfo("de.komoot.android", "https://www.komoot.com"),
        "momondo" to AppInfo("com.momondo.flightsearch", "https://www.momondo.com"),
        "trainline" to AppInfo("com.thetrainline", "https://www.thetrainline.com"),
        "railcard" to AppInfo("com.atoc.railcard", null),
        "rome2go" to AppInfo("com.rome2go.app", null),
        "sygic" to AppInfo("com.sygic.aura", "https://www.sygic.com"),
        "wego" to AppInfo("com.wego.android", "https://www.wego.com"),
        "cleartrip" to AppInfo("com.cleartrip.android", "https://www.cleartrip.com"),
        "makemytrip" to AppInfo("com.makemytrip", "https://www.makemytrip.com"),
        "yatra" to AppInfo("com.yatra.base", "https://www.yatra.com"),
        "ixigo" to AppInfo("com.ixigo.train.ixitrain", "https://www.ixigo.com"),
        "bird" to AppInfo("co.bird.android", "https://www.bird.co"),
        "lime" to AppInfo("com.limebike", "https://www.li.me"),
        "tier" to AppInfo("com.tier.app", "https://www.tier.app"),
        "voi" to AppInfo("io.voiapp.voi", "https://www.voiscooters.com"),
        "free_now" to AppInfo("com.freenow.app", "https://www.free-now.com"),

        // Communication & Messaging
        "lark" to AppInfo("com.larksuite.suite", "https://www.larksuite.com"),
        "zalo" to AppInfo("com.zing.zalo", "https://zalo.me"),
        "band" to AppInfo("com.naver.band", "https://band.us"),
        "mattermost" to AppInfo("com.mattermost.rn", "https://mattermost.com"),
        "rocket_chat" to AppInfo("chat.rocket.android", "https://rocket.chat"),
        "wire" to AppInfo("com.wire", "https://wire.com"),
        "session" to AppInfo("network.loki.messenger", "https://getsession.org"),
        "wickr" to AppInfo("com.wickr.pro", null),
        "dust" to AppInfo("com.radicalapp.dust", null),

        // Music & Audio
        "soundhound" to AppInfo("com.melodis.midomiMusicIdentifier", "https://www.soundhound.com"),
        "smule" to AppInfo("com.smule.singandroid", "https://www.smule.com"),
        "moises" to AppInfo("ai.moises.app", "https://moises.ai"),
        "genius" to AppInfo("com.genius.android", "https://genius.com"),
        "musixmatch" to AppInfo("com.musixmatch.android.lyrify", "https://www.musixmatch.com"),
        "tiktok_music" to AppInfo("com.zhiliaoapp.musically.go", null),
        "resso" to AppInfo("com.moonvideo.android.resso", null),
        "wynk" to AppInfo("com.bsbportal.music", "https://wynk.in"),

        // Sports & Fitness
        "zwift" to AppInfo("com.zwift.zwiftgame", "https://www.zwift.com"),
        "nike_snkrs" to AppInfo("com.nike.snkrs", "https://www.nike.com/snkrs"),
        "sofascore_live" to AppInfo("com.sofascore.results", "https://www.sofascore.com"),
        "flashscore" to AppInfo("eu.livesport.FlashScore_com", "https://www.flashscore.com"),
        "365scores" to AppInfo("com.scores365", "https://www.365scores.com"),
        "theScore_bet" to AppInfo("com.thescore.bet", "https://www.thescore.com"),
        "betmgm" to AppInfo("com.betmgm.retail.sportsbook.android.client", "https://www.betmgm.com"),
        "caesars" to AppInfo("com.williamhill.sportsbook.wh", "https://www.caesars.com/sportsbook-and-casino"),
        "pointsbet" to AppInfo("com.pointsbet.sportsbook.us", "https://www.pointsbet.com"),
        "bet365" to AppInfo("com.bet365Ede.sportsbook", "https://www.bet365.com"),

        // Utilities & Tools
        "adguard" to AppInfo("com.adguard.android", "https://adguard.com"),
        "pushbullet" to AppInfo("com.pushbullet.android", "https://www.pushbullet.com"),
        "raindrop" to AppInfo("io.raindrop.raindropio", "https://raindrop.io"),
        "airdroid" to AppInfo("com.sand.airdroid", "https://www.airdroid.com"),
        "solid_explorer" to AppInfo("pl.solidexplorer2", null),
        "total_commander" to AppInfo("com.ghisler.android.TotalCommander", null),
        "tasker" to AppInfo("net.dinglisch.android.taskerm", null),
        "macrodroid" to AppInfo("com.arlosoft.macrodroid", null),
        "automate" to AppInfo("com.llamalab.automate", null),
        "nova_launcher" to AppInfo("com.teslacoilsw.launcher", null),
        "niagara_launcher" to AppInfo("bitpit.launcher", null),
        "lawnchair" to AppInfo("ch.deletescape.lawnchair.plah", null),
        "microsoft_launcher" to AppInfo("com.microsoft.launcher", null),
        "sesame" to AppInfo("ninja.sesame.app.edge", null),
        "greenify" to AppInfo("com.oasisfeng.greenify", null),
        "sdmaid" to AppInfo("eu.thedarken.sdm", null),
        "bouncer" to AppInfo("com.samruston.permission", null),
        "dns_changer" to AppInfo("com.frostnerd.dnschanger", null),
        "netguard" to AppInfo("eu.faircode.netguard", null),

        // Parenting & Family
        "baby_tracker" to AppInfo("com.nighp.babytracker_android", null),
        "huckleberry" to AppInfo("com.huckleberry.app", "https://huckleberrycare.com"),
        "wonder_weeks" to AppInfo("com.ximedes.wonderweeks", null),
        "what_to_expect" to AppInfo("com.whattoexpect.wte", "https://www.whattoexpect.com"),
        "tinybeans" to AppInfo("com.tinybeans", "https://www.tinybeans.com"),
        "lifecake" to AppInfo("com.lifecake.lifecake", null),
        "cozi" to AppInfo("com.cozi.androidfree", "https://www.cozi.com"),
        "famcal" to AppInfo("com.appxy.planner", null),
        "life360" to AppInfo("com.life360.android.safetymapd", "https://www.life360.com"),

        // Smart Home & IoT
        "tuya" to AppInfo("com.tuya.smart", "https://www.tuya.com"),
        "homebridge" to AppInfo("com.homebridge.hb", null),
        "hubitat" to AppInfo("com.hubitat.app", "https://hubitat.com"),
        "home_assistant" to AppInfo("io.homeassistant.companion.android", "https://www.home-assistant.io"),
        "ecobee" to AppInfo("com.ecobee.athemis", "https://www.ecobee.com"),
        "myq" to AppInfo("com.chamberlain.android.liftmaster.myq", "https://www.myq.com"),
        "roomba" to AppInfo("com.irobot.home", "https://www.irobot.com"),
        "eufy_security" to AppInfo("com.oceanwing.battery.cam", "https://www.eufylife.com"),
        "tp_link_tapo" to AppInfo("com.tplink.iot", "https://www.tapo.com"),
        "govee" to AppInfo("com.govee.home", "https://www.govee.com"),
        "nanoleaf" to AppInfo("me.nanoleaf.nanoleaf", "https://nanoleaf.me"),
        "yeelight" to AppInfo("com.yeelight.cherry", "https://www.yeelight.com"),

        // Meditation & Mindfulness
        "ten_percent" to AppInfo("com.changecollective.tenpercenthappier", "https://www.tenpercent.com"),
        "simple_habit" to AppInfo("com.simplehabit.simplehabitapp", "https://www.simplehabit.com"),
        "meditopia" to AppInfo("app.meditasyon", "https://meditopia.com"),
        "balance_meditation" to AppInfo("com.elevationlab.balance", null),
        "breethe" to AppInfo("com.breethe.android", "https://breethe.com"),
        "aura_health" to AppInfo("com.aurahealth", "https://www.aurahealth.io"),

        // Podcasts & News Readers
        "google_podcasts" to AppInfo("com.google.android.apps.podcasts", "https://podcasts.google.com"),
        "castro" to AppInfo("com.supertop.castro", "https://castro.fm"),
        "fountain" to AppInfo("fm.fountain.apps", "https://www.fountain.fm"),
        "player_fm" to AppInfo("fm.player", "https://player.fm"),
        "podcast_republic" to AppInfo("com.itunestoppodcastplayer.app", null),
        "flipboard_tv" to AppInfo("com.flipboard.app", "https://flipboard.com"),
        "nuzzel" to AppInfo("com.nuzzel.android", null),
        "news_360" to AppInfo("com.news360.news360app", null),

        // Crypto & Web3
        "opensea" to AppInfo("io.opensea.app", "https://opensea.io"),
        "blur" to AppInfo("io.blur.app", "https://blur.io"),
        "dextools" to AppInfo("io.dextools.app", "https://www.dextools.io"),
        "moonpay" to AppInfo("com.moonpay", "https://www.moonpay.com"),
        "exodus" to AppInfo("exodusmovement.exodus", "https://www.exodus.com"),
        "blockfi" to AppInfo("com.blockfi.mobile", "https://blockfi.com"),
        "nexo" to AppInfo("io.nexo", "https://nexo.io"),
        "celsius" to AppInfo("com.celsius.celsius", null),
        "kucoin" to AppInfo("com.kubi.kucoin", "https://www.kucoin.com"),
        "bybit" to AppInfo("com.bybit.app", "https://www.bybit.com"),
        "okx" to AppInfo("com.okinc.okex.gp", "https://www.okx.com"),
        "gate_io" to AppInfo("com.gateio.gateio", "https://www.gate.io"),
        "bitget" to AppInfo("com.bitget.exchange", "https://www.bitget.com"),

        // Project Management & Work
        "wrike" to AppInfo("com.wrike", "https://www.wrike.com"),
        "smartsheet" to AppInfo("com.smartsheet.android", "https://www.smartsheet.com"),
        "height" to AppInfo("com.height.app", "https://height.app"),
        "linear" to AppInfo("com.linear", "https://linear.app"),
        "shortcut" to AppInfo("io.clubhouse.clubhouse", "https://shortcut.com"),
        "fibery" to AppInfo("io.fibery.app", "https://fibery.io"),
        "teamwork" to AppInfo("com.teamwork.android.projects", "https://www.teamwork.com"),
        "productive_app" to AppInfo("com.agilebits.productive", null),
        "sunsama" to AppInfo("com.sunsama.app", "https://sunsama.com"),
        "reclaim" to AppInfo("ai.reclaim.android", "https://reclaim.ai"),
        "motion" to AppInfo("com.motion.o", "https://www.usemotion.com"),

        // VPN & Privacy
        "mullvad" to AppInfo("net.mullvad.mullvadvpn", "https://mullvad.net"),
        "protonvpn" to AppInfo("ch.protonvpn.android", "https://protonvpn.com"),
        "windscribe" to AppInfo("com.windscribe.vpn", "https://windscribe.com"),
        "tunnelbear" to AppInfo("com.tunnelbear.android", "https://www.tunnelbear.com"),
        "ivpn" to AppInfo("net.ivpn.client", "https://www.ivpn.net"),
        "pia" to AppInfo("com.privateinternetaccess.android", "https://www.privateinternetaccess.com"),
        "torguard" to AppInfo("com.torguard.android", "https://torguard.net"),

        // Weather
        "carrot_weather" to AppInfo("com.grailr.carrotweather", "https://www.meetcarrot.com/weather"),
        "weather_radar" to AppInfo("com.clime.weatherradar", null),
        "hello_weather" to AppInfo("com.helloweather", null),
        "overdrop" to AppInfo("widget.dd.com.overdrop.free", null),
        "flowx" to AppInfo("com.enzuredigital.weatherbomb", null),
        "today_weather" to AppInfo("mobi.lockdown.weather", null)
    )

    override suspend fun execute(input: JsonObject): ToolResult {
        val appName = input["app"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?: return ToolResult("Missing required parameter: app", isError = true)
        val action = input["action"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val appInfo = appRegistry[appName]
            ?: return ToolResult("Unknown app: $appName. Supported apps: ${appRegistry.keys.sorted().joinToString(", ")}", isError = true)

        // Helper to extract optional params
        fun param(key: String) = input[key]?.jsonPrimitive?.contentOrNull
        fun encode(s: String) = s.replace(" ", "%20")
            .replace("&", "%26")
            .replace("#", "%23")
            .replace("+", "%2B")

        // Generic "open" action — just launch the app
        if (action == "open" || action == "launch") {
            return bridge.launchApp(appInfo.packageName).fold(
                onSuccess = { ToolResult(it) },
                onFailure = {
                    // Try web fallback
                    if (appInfo.webFallback != null) {
                        bridge.openDeepLink(appInfo.webFallback, null, null).fold(
                            onSuccess = { ToolResult("App not installed, opened web: ${appInfo.webFallback}") },
                            onFailure = { e -> ToolResult("Failed: ${e.message}", isError = true) }
                        )
                    } else {
                        ToolResult("Failed: ${it.message}", isError = true)
                    }
                }
            )
        }

        // Messaging apps with auto-send support: actually send the message (tap Send via the
        // accessibility service) instead of only opening the chat. Requires a phone number;
        // username-only chats fall through to the deep-link path below.
        val autoSendPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "signal" to "org.thoughtcrime.securesms",
        )
        if (action == "send_message" && appName in autoSendPackages) {
            val phone = param("phone")
            val msg = param("message") ?: param("text")
            if (!phone.isNullOrBlank() && !msg.isNullOrBlank()) {
                return bridge.sendIntentMessage(autoSendPackages.getValue(appName), phone, msg).fold(
                    onSuccess = { ToolResult(it) },
                    onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
                )
            }
        }

        // Music apps: actually start playback for play/play_music via the system play-from-search
        // intent (capable apps play the best match) instead of only opening a search screen.
        val musicPlayApps = setOf("spotify", "youtube_music", "deezer", "tidal", "soundcloud", "amazon_music", "apple_music")
        if ((action == "play" || action == "play_music") && appName in musicPlayApps) {
            val query = param("query") ?: param("text")
            if (!query.isNullOrBlank()) {
                return bridge.playMusic(query, appName).fold(
                    onSuccess = { ToolResult(it) },
                    onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
                )
            }
        }

        // Build the deep link URI based on app + action
        val deepLink = buildDeepLink(appName, action, ::param, ::encode)
            ?: return ToolResult(
                "Unsupported action '$action' for app '$appName'. Try 'open' to just launch the app.",
                isError = true
            )

        val result = bridge.openDeepLink(deepLink, appInfo.packageName, appInfo.webFallback)
        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }

    // ========================================================================
    // Deep-link builder: returns a URI string or null if action is unsupported
    // ========================================================================

    private fun buildDeepLink(
        app: String,
        action: String,
        param: (String) -> String?,
        encode: (String) -> String
    ): String? {
        return buildDeepLinkPart1(app, action, param, encode)
            ?: buildDeepLinkPart2(app, action, param, encode)
            ?: buildDeepLinkPart3(app, action, param, encode)
            ?: buildDeepLinkPart4(app, action, param, encode)
            ?: buildDeepLinkPart5(app, action, param, encode)
            ?: buildDeepLinkPart6(app, action, param, encode)
            ?: buildDeepLinkPart7(app, action, param, encode)
            ?: buildDeepLinkPart8(app, action, param, encode)
    }

    private fun buildDeepLinkPart1(
        app: String,
        action: String,
        param: (String) -> String?,
        encode: (String) -> String
    ): String? {
        return when (app) {
            // =======================
            // MESSAGING
            // =======================
            "whatsapp", "whatsapp_business" -> when (action) {
                "send_message" -> {
                    val phone = param("phone")?.replace("+", "")?.replace(" ", "") ?: ""
                    val msg = param("message") ?: param("text") ?: ""
                    if (phone.isNotEmpty()) "https://wa.me/$phone" + if (msg.isNotEmpty()) "?text=${encode(msg)}" else ""
                    else if (msg.isNotEmpty()) "whatsapp://send?text=${encode(msg)}"
                    else null
                }
                "call" -> {
                    val phone = param("phone")?.replace("+", "")?.replace(" ", "") ?: return null
                    "whatsapp://call?phone=$phone"
                }
                "video_call" -> {
                    val phone = param("phone")?.replace("+", "")?.replace(" ", "") ?: return null
                    "whatsapp://videocall?phone=$phone"
                }
                "open_chat" -> {
                    val phone = param("phone")?.replace("+", "")?.replace(" ", "") ?: return null
                    "https://wa.me/$phone"
                }
                "status" -> "whatsapp://status"
                else -> null
            }
            "telegram" -> when (action) {
                "send_message" -> {
                    val user = param("username") ?: return null
                    val msg = param("message") ?: param("text")
                    "tg://resolve?domain=$user" + if (msg != null) "&text=${encode(msg)}" else ""
                }
                "open_chat" -> {
                    val user = param("username") ?: return null
                    "tg://resolve?domain=$user"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "tg://search?query=${encode(q)}"
                }
                "open_channel" -> {
                    val user = param("username") ?: return null
                    "https://t.me/$user"
                }
                "call" -> {
                    val user = param("username") ?: return null
                    "tg://resolve?domain=$user&voicechat="
                }
                else -> null
            }
            "signal" -> when (action) {
                "send_message" -> "sgnl://signal.me"
                "open_chat" -> "sgnl://signal.me"
                else -> null
            }
            "messenger" -> when (action) {
                "send_message" -> {
                    val user = param("username") ?: param("recipient")
                    if (user != null) "https://m.me/$user" else "fb-messenger://"
                }
                "call" -> "fb-messenger://call"
                "video_call" -> "fb-messenger://call"
                "open_chat" -> {
                    val user = param("username") ?: param("recipient")
                    if (user != null) "https://m.me/$user" else "fb-messenger://"
                }
                else -> null
            }
            "viber" -> when (action) {
                "send_message" -> {
                    val phone = param("phone")?.replace("+", "")?.replace(" ", "") ?: return null
                    "viber://chat?number=$phone"
                }
                "call" -> {
                    val phone = param("phone")?.replace("+", "")?.replace(" ", "") ?: return null
                    "viber://call?number=$phone"
                }
                else -> null
            }
            "wechat" -> when (action) {
                "scan" -> "weixin://dl/scan"
                "moments" -> "weixin://dl/moments"
                else -> null
            }
            "discord" -> when (action) {
                "open_server" -> {
                    val url = param("url") ?: return null
                    "https://discord.gg/$url"
                }
                "open_channel" -> "discord://channels"
                else -> null
            }
            "slack" -> when (action) {
                "open_channel" -> {
                    val channel = param("username") ?: param("query") ?: return null
                    "slack://channel?team=&id=$channel"
                }
                "send_message" -> "slack://open"
                else -> null
            }
            "teams" -> when (action) {
                "open_chat" -> "msteams://teams.microsoft.com/l/chat"
                "call" -> "msteams://teams.microsoft.com/l/call/0/0"
                "meeting" -> "msteams://teams.microsoft.com/l/meeting"
                "send_message" -> "msteams://teams.microsoft.com/l/chat"
                else -> null
            }
            "skype" -> when (action) {
                "call" -> {
                    val user = param("username") ?: return null
                    "skype:$user?call"
                }
                "send_message" -> {
                    val user = param("username") ?: return null
                    "skype:$user?chat"
                }
                "video_call" -> {
                    val user = param("username") ?: return null
                    "skype:$user?call&video=true"
                }
                else -> null
            }
            "line" -> when (action) {
                "send_message" -> {
                    val msg = param("message") ?: param("text") ?: ""
                    "line://msg/text/${encode(msg)}"
                }
                else -> null
            }
            "kakaotalk" -> when (action) {
                "send_message" -> "kakaotalk://main"
                else -> null
            }
            "zoom" -> when (action) {
                "join" -> {
                    val id = param("query") ?: param("url") ?: return null
                    "zoomus://zoom.us/join?confno=$id"
                }
                "meeting", "start" -> "zoomus://zoom.us/start"
                else -> null
            }
            "google_meet" -> when (action) {
                "join" -> {
                    val code = param("query") ?: param("url") ?: return null
                    "https://meet.google.com/$code"
                }
                "new_meeting", "start" -> "https://meet.google.com/new"
                else -> null
            }

            // =======================
            // SOCIAL MEDIA
            // =======================
            "instagram" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.instagram.com/$user/"
                }
                "open_dm", "direct" -> "instagram://direct_inbox"
                "open_camera", "story" -> "instagram://camera"
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.instagram.com/explore/tags/${encode(q)}/"
                }
                "open_reels" -> "instagram://reels"
                "share", "post" -> "instagram://share"
                else -> null
            }
            "facebook" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.facebook.com/$user"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.facebook.com/search/top/?q=${encode(q)}"
                }
                "post", "compose" -> "fb://publish/profile"
                "marketplace" -> "fb://marketplace"
                "groups" -> "fb://groups"
                "notifications" -> "fb://notifications"
                "open_feed" -> "fb://feed"
                "events" -> "fb://events"
                else -> null
            }
            "twitter", "x" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://x.com/$user"
                }
                "compose", "post", "tweet" -> {
                    val text = param("text") ?: param("message") ?: ""
                    "https://x.com/intent/tweet?text=${encode(text)}"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "https://x.com/search?q=${encode(q)}"
                }
                "open_dm" -> "twitter://messages"
                "notifications" -> "twitter://notifications"
                "open_feed" -> "twitter://timeline"
                else -> null
            }
            "tiktok" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.tiktok.com/@$user"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.tiktok.com/search?q=${encode(q)}"
                }
                "open_camera", "create" -> "snssdk1233://camera"
                "open_inbox" -> "snssdk1233://inbox"
                else -> null
            }
            "snapchat" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.snapchat.com/add/$user"
                }
                "open_camera" -> "snapchat://"
                "open_chat" -> "snapchat://chat"
                "discover" -> "snapchat://discover"
                "map" -> "snapchat://map"
                "add_friend" -> {
                    val user = param("username") ?: return null
                    "snapchat://add/$user"
                }
                else -> null
            }
            "linkedin" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.linkedin.com/in/$user"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.linkedin.com/search/results/all/?keywords=${encode(q)}"
                }
                "open_dm", "messaging" -> "linkedin://messaging"
                "jobs" -> "linkedin://jobs"
                "post", "compose" -> "linkedin://share"
                "open_feed" -> "linkedin://feed"
                "notifications" -> "linkedin://notifications"
                else -> null
            }
            "reddit" -> when (action) {
                "open_subreddit" -> {
                    val sub = param("query") ?: param("username") ?: return null
                    "https://www.reddit.com/r/$sub"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.reddit.com/search/?q=${encode(q)}"
                }
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.reddit.com/user/$user"
                }
                "compose", "post" -> "reddit://compose"
                "inbox" -> "reddit://inbox"
                else -> null
            }
            "pinterest" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.pinterest.com/search/pins/?q=${encode(q)}"
                }
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.pinterest.com/$user/"
                }
                "create" -> "pinterest://pin/create"
                else -> null
            }
            "threads" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.threads.net/@$user"
                }
                "compose", "post" -> "barcelona://compose"
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.threads.net/search?q=${encode(q)}"
                }
                else -> null
            }
            "youtube" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "vnd.youtube://results?search_query=${encode(q)}"
                }
                "play", "watch" -> {
                    val id = param("video_id") ?: param("url") ?: return null
                    "vnd.youtube://$id"
                }
                "open_channel" -> {
                    val user = param("username") ?: return null
                    "https://www.youtube.com/@$user"
                }
                "subscriptions" -> "vnd.youtube://subscriptions"
                "trending" -> "vnd.youtube://trending"
                "library" -> "vnd.youtube://library"
                "shorts" -> "vnd.youtube://shorts"
                else -> null
            }
            "twitch" -> when (action) {
                "open_stream" -> {
                    val user = param("username") ?: return null
                    "twitch://stream/$user"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.twitch.tv/search?term=${encode(q)}"
                }
                "browse" -> "twitch://browse"
                else -> null
            }
            "bereal" -> when (action) {
                "capture" -> "bereal://camera"
                else -> null
            }

            // =======================
            // EMAIL
            // =======================
            "gmail" -> when (action) {
                "compose" -> {
                    val to = param("to") ?: ""
                    val subj = param("subject") ?: ""
                    val body = param("body") ?: ""
                    "googlegmail://co?to=${encode(to)}&subject=${encode(subj)}&body=${encode(body)}"
                }
                "open_inbox" -> "googlegmail://inbox"
                "search" -> {
                    val q = param("query") ?: return null
                    "googlegmail://search?query=${encode(q)}"
                }
                else -> null
            }
            "outlook" -> when (action) {
                "compose" -> {
                    val to = param("to") ?: ""
                    val subj = param("subject") ?: ""
                    val body = param("body") ?: ""
                    "ms-outlook://compose?to=${encode(to)}&subject=${encode(subj)}&body=${encode(body)}"
                }
                "open_inbox" -> "ms-outlook://emails"
                "calendar" -> "ms-outlook://calendar"
                else -> null
            }
            "yahoo_mail" -> when (action) {
                "compose" -> {
                    val to = param("to") ?: ""
                    val subj = param("subject") ?: ""
                    "ymail://mail/compose?to=${encode(to)}&subject=${encode(subj)}"
                }
                "open_inbox" -> "ymail://mail/inbox"
                else -> null
            }
            "protonmail" -> when (action) {
                "compose" -> "protonmail://compose"
                "open_inbox" -> "protonmail://inbox"
                else -> null
            }

            // =======================
            // ENTERTAINMENT
            // =======================
            "spotify" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "spotify:search:${encode(q)}"
                }
                "play" -> {
                    val id = param("track_id") ?: param("url")
                    if (id != null) "spotify:track:$id" else null
                }
                "open_playlist" -> {
                    val id = param("playlist_id") ?: param("url") ?: return null
                    "spotify:playlist:$id"
                }
                "open_album" -> {
                    val id = param("url") ?: return null
                    "spotify:album:$id"
                }
                "open_artist" -> {
                    val name = param("username") ?: param("query") ?: return null
                    "spotify:search:${encode(name)}"
                }
                else -> null
            }
            "youtube_music" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://music.youtube.com/search?q=${encode(q)}"
                }
                "play" -> {
                    val id = param("video_id") ?: param("url") ?: return null
                    "https://music.youtube.com/watch?v=$id"
                }
                else -> null
            }
            "netflix" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.netflix.com/search?q=${encode(q)}"
                }
                "play" -> {
                    val id = param("url") ?: return null
                    "nflx://www.netflix.com/watch/$id"
                }
                else -> null
            }
            "prime_video" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.primevideo.com/search?phrase=${encode(q)}"
                }
                else -> null
            }
            "disney_plus" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.disneyplus.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "hbo_max" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://play.max.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "hulu" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.hulu.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "apple_music" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "music://search?term=${encode(q)}"
                }
                else -> null
            }
            "soundcloud" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "soundcloud://search/${encode(q)}"
                }
                "play" -> {
                    val url = param("url") ?: return null
                    "soundcloud://sounds/$url"
                }
                else -> null
            }
            "deezer" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "deezer://search/${encode(q)}"
                }
                else -> null
            }
            "pandora" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "pandora://search?query=${encode(q)}"
                }
                else -> null
            }

            // =======================
            // PRODUCTIVITY
            // =======================
            "google_drive" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://drive.google.com/drive/search?q=${encode(q)}"
                }
                "create" -> "https://drive.google.com/drive"
                "upload" -> "googledrive://upload"
                else -> null
            }
            "google_docs" -> when (action) {
                "create" -> "https://docs.google.com/document/create"
                "search" -> {
                    val q = param("query") ?: return null
                    "https://docs.google.com/?q=${encode(q)}"
                }
                else -> null
            }
            "google_sheets" -> when (action) {
                "create" -> "https://sheets.google.com/create"
                else -> null
            }
            "google_slides" -> when (action) {
                "create" -> "https://slides.google.com/create"
                else -> null
            }
            "ms_word" -> when (action) {
                "create" -> "ms-word://create"
                else -> null
            }
            "ms_excel" -> when (action) {
                "create" -> "ms-excel://create"
                else -> null
            }
            "ms_powerpoint" -> when (action) {
                "create" -> "ms-powerpoint://create"
                else -> null
            }
            "notion" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "notion://search?query=${encode(q)}"
                }
                "create" -> "notion://create"
                else -> null
            }
            "evernote" -> when (action) {
                "create" -> {
                    val title = param("subject") ?: param("content") ?: ""
                    "evernote://x-callback-url/new-note?title=${encode(title)}"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "evernote://x-callback-url/search?query=${encode(q)}"
                }
                else -> null
            }
            "trello" -> when (action) {
                "create" -> "trello://x-callback-url/createCard"
                "search" -> {
                    val q = param("query") ?: return null
                    "https://trello.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "todoist" -> when (action) {
                "create", "add_task" -> {
                    val content = param("content") ?: param("text") ?: param("message") ?: ""
                    "todoist://addtask?content=${encode(content)}"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "todoist://search?query=${encode(q)}"
                }
                else -> null
            }
            "google_keep" -> when (action) {
                "create" -> {
                    val text = param("content") ?: param("text") ?: ""
                    "https://keep.google.com/#NOTE" + if (text.isNotEmpty()) "?text=${encode(text)}" else ""
                }
                else -> null
            }
            "onenote" -> when (action) {
                "create" -> "onenote://create"
                else -> null
            }
            "asana" -> when (action) {
                "create" -> "asana://create"
                "search" -> {
                    val q = param("query") ?: return null
                    "https://app.asana.com/search?q=${encode(q)}"
                }
                else -> null
            }

            // =======================
            // SHOPPING
            // =======================
            "amazon" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.amazon.com/s?k=${encode(q)}"
                }
                "open_cart" -> "https://www.amazon.com/gp/cart/view.html"
                "open_orders" -> "https://www.amazon.com/gp/css/order-history"
                else -> null
            }
            "ebay" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.ebay.com/sch/i.html?_nkw=${encode(q)}"
                }
                else -> null
            }
            "aliexpress" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.aliexpress.com/wholesale?SearchText=${encode(q)}"
                }
                else -> null
            }
            "walmart" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.walmart.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "target" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.target.com/s?searchTerm=${encode(q)}"
                }
                else -> null
            }
            "shein" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.shein.com/pdsearch/${encode(q)}"
                }
                else -> null
            }
            "etsy" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.etsy.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "wish" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.wish.com/search/${encode(q)}"
                }
                else -> null
            }

            // =======================
            // MAPS & TRAVEL
            // =======================
            "google_maps" -> when (action) {
                "navigate", "directions" -> {
                    val dest = param("destination") ?: param("query") ?: return null
                    "google.navigation:q=${encode(dest)}"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "geo:0,0?q=${encode(q)}"
                }
                "street_view" -> {
                    val lat = param("latitude")?.toDoubleOrNull() ?: return null
                    val lng = param("longitude")?.toDoubleOrNull() ?: return null
                    "google.streetview:cbll=$lat,$lng"
                }
                else -> null
            }
            "waze" -> when (action) {
                "navigate" -> {
                    val dest = param("destination") ?: param("query") ?: return null
                    "https://waze.com/ul?q=${encode(dest)}&navigate=yes"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "https://waze.com/ul?q=${encode(q)}"
                }
                else -> null
            }
            "uber" -> when (action) {
                "request_ride", "ride" -> {
                    val dest = param("destination") ?: ""
                    "uber://?action=setPickup&dropoff[formatted_address]=${encode(dest)}"
                }
                else -> null
            }
            "lyft" -> when (action) {
                "request_ride", "ride" -> {
                    val dest = param("destination") ?: ""
                    "lyft://ridetype?id=lyft&destination[address]=${encode(dest)}"
                }
                else -> null
            }
            "airbnb" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.airbnb.com/s/${encode(q)}/homes"
                }
                else -> null
            }
            "booking" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.booking.com/searchresults.html?ss=${encode(q)}"
                }
                else -> null
            }
            "expedia" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.expedia.com/Hotel-Search?destination=${encode(q)}"
                }
                else -> null
            }
            "google_earth" -> when (action) {
                "fly_to" -> {
                    val lat = param("latitude")?.toDoubleOrNull()
                    val lng = param("longitude")?.toDoubleOrNull()
                    val q = param("query")
                    if (lat != null && lng != null) "googleearth://search/$lat,$lng"
                    else if (q != null) "googleearth://search/${encode(q)}"
                    else null
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "googleearth://search/${encode(q)}"
                }
                else -> null
            }

            // =======================
            // FINANCE
            // =======================
            "paypal" -> when (action) {
                "send_money" -> {
                    val to = param("recipient") ?: param("to") ?: ""
                    val amt = param("amount") ?: ""
                    "https://www.paypal.com/myaccount/transfer/send" +
                        if (to.isNotEmpty()) "?recipient=$to" + if (amt.isNotEmpty()) "&amount=$amt" else "" else ""
                }
                else -> null
            }
            "venmo" -> when (action) {
                "send_money", "pay" -> {
                    val to = param("recipient") ?: param("username") ?: ""
                    val amt = param("amount") ?: ""
                    val note = param("message") ?: param("text") ?: ""
                    "venmo://paycharge?txn=pay&recipients=${encode(to)}&amount=$amt&note=${encode(note)}"
                }
                "request" -> {
                    val to = param("recipient") ?: param("username") ?: ""
                    val amt = param("amount") ?: ""
                    "venmo://paycharge?txn=charge&recipients=${encode(to)}&amount=$amt"
                }
                else -> null
            }
            "cash_app" -> when (action) {
                "send_money", "pay" -> {
                    val to = param("recipient") ?: param("username") ?: ""
                    val amt = param("amount") ?: ""
                    "https://cash.app/\$$to" + if (amt.isNotEmpty()) "/$amt" else ""
                }
                else -> null
            }
            "robinhood" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "robinhood://search?query=${encode(q)}"
                }
                else -> null
            }
            "coinbase" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.coinbase.com/price/${encode(q)}"
                }
                else -> null
            }
            "google_pay" -> when (action) {
                "send_money", "pay" -> "googlepay://send"
                else -> null
            }
            "samsung_pay" -> when (action) {
                "pay" -> "samsungpay://launch"
                else -> null
            }
            "zelle" -> when (action) {
                "send_money" -> "zelle://transfer"
                else -> null
            }
            else -> null
        }
    }

    private fun buildDeepLinkPart2(
        app: String,
        action: String,
        param: (String) -> String?,
        encode: (String) -> String
    ): String? {
        return when (app) {
            // =======================
            // FOOD DELIVERY
            // =======================
            "uber_eats" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.ubereats.com/search?q=${encode(q)}"
                }
                "order" -> "ubereats://"
                else -> null
            }
            "doordash" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.doordash.com/search/store/${encode(q)}/"
                }
                "order" -> "doordash://"
                else -> null
            }
            "grubhub" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.grubhub.com/search?query=${encode(q)}"
                }
                else -> null
            }
            "starbucks" -> when (action) {
                "order" -> "starbucks://order"
                "pay" -> "starbucks://pay"
                "rewards" -> "starbucks://rewards"
                else -> null
            }
            "mcdonalds" -> when (action) {
                "order" -> "mcdonalds://order"
                "deals" -> "mcdonalds://deals"
                else -> null
            }
            "instacart" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.instacart.com/store/search/${encode(q)}"
                }
                else -> null
            }
            "postmates" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "postmates://search?query=${encode(q)}"
                }
                else -> null
            }

            // =======================
            // FITNESS
            // =======================
            "strava" -> when (action) {
                "start_activity", "record" -> "strava://record"
                "open_feed" -> "strava://feed"
                "open_profile" -> {
                    val user = param("username")
                    if (user != null) "https://www.strava.com/athletes/$user" else "strava://profile"
                }
                else -> null
            }
            "myfitnesspal" -> when (action) {
                "log_food" -> "myfitnesspal://food"
                "log_exercise" -> "myfitnesspal://exercise"
                "diary" -> "myfitnesspal://diary"
                else -> null
            }
            "nike_run_club" -> when (action) {
                "start_run" -> "nikerunclub://start"
                "history" -> "nikerunclub://history"
                else -> null
            }
            "fitbit" -> when (action) {
                "log" -> "fitbit://log"
                "dashboard" -> "fitbit://dashboard"
                else -> null
            }
            "peloton" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "peloton://search?query=${encode(q)}"
                }
                "schedule" -> "peloton://schedule"
                else -> null
            }

            // =======================
            // UTILITIES
            // =======================
            "google_translate" -> when (action) {
                "translate" -> {
                    val text = param("text") ?: param("query") ?: return null
                    "https://translate.google.com/?sl=auto&tl=en&text=${encode(text)}"
                }
                else -> null
            }
            "shazam" -> when (action) {
                "identify", "listen" -> "shazam://recognize"
                "search" -> {
                    val q = param("query") ?: return null
                    "shazam://search?query=${encode(q)}"
                }
                else -> null
            }
            "google_authenticator" -> null // Only supports open
            "google_calendar" -> when (action) {
                "create", "add_event" -> {
                    val title = param("subject") ?: param("content") ?: ""
                    "https://calendar.google.com/calendar/r/eventedit?text=${encode(title)}"
                }
                "today" -> "content://com.android.calendar/time/"
                "search" -> {
                    val q = param("query") ?: return null
                    "https://calendar.google.com/calendar/r/search?q=${encode(q)}"
                }
                else -> null
            }
            "google_photos" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "googlephotos://search/${encode(q)}"
                }
                "favorites" -> "googlephotos://favorites"
                else -> null
            }
            "dropbox" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "dbx://search?query=${encode(q)}"
                }
                "upload" -> "dbx://upload"
                else -> null
            }
            "onedrive" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "ms-onedrive://search?query=${encode(q)}"
                }
                else -> null
            }
            "chrome" -> when (action) {
                "open_url", "browse" -> {
                    val url = param("url") ?: return null
                    url // Just open URL via Chrome package
                }
                "incognito" -> "googlechrome://incognito"
                "search" -> {
                    val q = param("query") ?: return null
                    "googlechrome://navigate?url=${encode("https://www.google.com/search?q=${encode(q)}")}"
                }
                else -> null
            }
            "google" -> when (action) {
                "search", "open" -> {
                    val q = param("query") ?: return null
                    "https://www.google.com/search?q=${encode(q)}"
                }
                "open_url", "browse" -> param("url")
                else -> null
            }
            "firefox" -> when (action) {
                "open_url", "browse" -> {
                    val url = param("url") ?: return null
                    url
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.google.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "opera" -> when (action) {
                "open_url", "browse" -> param("url")
                else -> null
            }
            "brave" -> when (action) {
                "open_url", "browse" -> param("url")
                "search" -> {
                    val q = param("query") ?: return null
                    "https://search.brave.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "edge" -> when (action) {
                "open_url", "browse" -> param("url")
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.bing.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "adobe_acrobat" -> when (action) {
                "scan" -> "adobe-dc://scan"
                else -> null
            }
            "vlc" -> when (action) {
                "play" -> {
                    val url = param("url") ?: return null
                    "vlc://$url"
                }
                else -> null
            }
            "google_lens" -> when (action) {
                "scan", "search" -> "googleapp://lens"
                else -> null
            }

            // =======================
            // DATING
            // =======================
            "tinder" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://tinder.com/@$user"
                }
                "discover" -> "tinder://discover"
                "matches" -> "tinder://matches"
                else -> null
            }
            "bumble" -> when (action) {
                "discover" -> "bumble://discover"
                "matches" -> "bumble://matches"
                else -> null
            }
            "hinge" -> when (action) {
                "discover" -> "hinge://discover"
                "matches" -> "hinge://matches"
                else -> null
            }
            "okcupid" -> when (action) {
                "discover" -> "okcupid://discover"
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.okcupid.com/profile/$user"
                }
                else -> null
            }
            "badoo" -> when (action) {
                "search" -> "badoo://encounters"
                else -> null
            }
            "grindr" -> when (action) {
                "nearby" -> "grindr://nearby"
                else -> null
            }
            "match" -> when (action) {
                "search" -> "match://search"
                else -> null
            }
            "coffee_meets_bagel" -> when (action) {
                "discover" -> "cmb://discover"
                else -> null
            }

            // =======================
            // NEWS & MAGAZINES
            // =======================
            "cnn" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.cnn.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "bbc_news" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.bbc.co.uk/search?q=${encode(q)}"
                }
                else -> null
            }
            "nytimes" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.nytimes.com/search?query=${encode(q)}"
                }
                else -> null
            }
            "fox_news" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.foxnews.com/search-results/search?q=${encode(q)}"
                }
                else -> null
            }
            "reuters" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.reuters.com/search/news?query=${encode(q)}"
                }
                else -> null
            }
            "flipboard" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "flipboard://search/${encode(q)}"
                }
                else -> null
            }
            "google_news" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://news.google.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "guardian" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.theguardian.com/search?query=${encode(q)}"
                }
                else -> null
            }
            "washington_post" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.washingtonpost.com/search/?query=${encode(q)}"
                }
                else -> null
            }
            "huffpost" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.huffpost.com/search?keywords=${encode(q)}"
                }
                else -> null
            }
            "al_jazeera" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.aljazeera.com/search/${encode(q)}"
                }
                else -> null
            }
            "npr", "apple_news", "newsbreak" -> null // Only supports open

            // =======================
            // WEATHER
            // =======================
            "weather_channel" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://weather.com/weather/today/l/${encode(q)}"
                }
                else -> null
            }
            "accuweather" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.accuweather.com/en/search-locations?query=${encode(q)}"
                }
                else -> null
            }

            // =======================
            // EDUCATION
            // =======================
            "duolingo" -> when (action) {
                "practice" -> "duolingo://practice"
                "learn" -> "duolingo://learn"
                else -> null
            }
            "khan_academy" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.khanacademy.org/search?page_search_query=${encode(q)}"
                }
                else -> null
            }
            "coursera" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.coursera.org/search?query=${encode(q)}"
                }
                else -> null
            }
            "udemy" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.udemy.com/courses/search/?q=${encode(q)}"
                }
                else -> null
            }
            "quizlet" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://quizlet.com/search?query=${encode(q)}"
                }
                else -> null
            }
            "photomath" -> when (action) {
                "scan" -> "photomath://scan"
                else -> null
            }
            "brainly" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://brainly.com/question?q=${encode(q)}"
                }
                else -> null
            }
            "skillshare" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.skillshare.com/en/search?query=${encode(q)}"
                }
                else -> null
            }

            // =======================
            // HEALTH & WELLNESS
            // =======================
            "headspace" -> when (action) {
                "meditate" -> "headspace://meditation"
                "sleep" -> "headspace://sleepcast"
                else -> null
            }
            "calm" -> when (action) {
                "meditate" -> "calm://meditation"
                "sleep" -> "calm://sleep"
                else -> null
            }
            "flo" -> when (action) {
                "log" -> "flo://log"
                else -> null
            }
            "betterhelp" -> when (action) {
                "chat" -> "betterhelp://chat"
                else -> null
            }
            "webmd" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.webmd.com/search/search_results/default.aspx?query=${encode(q)}"
                }
                else -> null
            }
            "noom", "sleep_cycle" -> null // Only supports open

            // =======================
            // PHOTOGRAPHY & VIDEO
            // =======================
            "canva" -> when (action) {
                "create" -> "canva://editor/create"
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.canva.com/templates?query=${encode(q)}"
                }
                else -> null
            }
            "capcut" -> when (action) {
                "create", "edit" -> "capcut://create"
                else -> null
            }
            "lightroom" -> when (action) {
                "edit" -> "lightroom://edit"
                "camera" -> "lightroom://camera"
                else -> null
            }
            "picsart" -> when (action) {
                "create", "edit" -> "picsart://edit"
                else -> null
            }
            "snapseed" -> when (action) {
                "edit" -> "snapseed://edit"
                else -> null
            }
            "vsco" -> when (action) {
                "camera" -> "vsco://camera"
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://vsco.co/$user"
                }
                else -> null
            }
            "inshot", "kinemaster" -> null // Only supports open

            // =======================
            // BOOKS & READING
            // =======================
            "kindle" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "kindle://search?q=${encode(q)}"
                }
                "library" -> "kindle://library"
                else -> null
            }
            "audible" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "audible://search?query=${encode(q)}"
                }
                "library" -> "audible://library"
                else -> null
            }
            "medium" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://medium.com/search?q=${encode(q)}"
                }
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://medium.com/@$user"
                }
                else -> null
            }
            "goodreads" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.goodreads.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "pocket" -> when (action) {
                "save" -> {
                    val url = param("url") ?: return null
                    "pocket://add?url=${encode(url)}"
                }
                else -> null
            }
            "feedly" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "feedly://search/${encode(q)}"
                }
                else -> null
            }
            "google_play_books" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://play.google.com/store/search?q=${encode(q)}&c=books"
                }
                else -> null
            }
            "libby" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "libby://search?query=${encode(q)}"
                }
                else -> null
            }
            "scribd" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.scribd.com/search?query=${encode(q)}"
                }
                else -> null
            }

            // =======================
            // REAL ESTATE
            // =======================
            "zillow" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.zillow.com/homes/${encode(q)}"
                }
                else -> null
            }
            "realtor" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.realtor.com/realestateandhomes-search/${encode(q)}"
                }
                else -> null
            }
            "redfin" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.redfin.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "trulia" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.trulia.com/for_sale/${encode(q)}"
                }
                else -> null
            }
            "apartments" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.apartments.com/${encode(q)}"
                }
                else -> null
            }

            // =======================
            // GAMING
            // =======================
            "steam" -> when (action) {
                "open_store" -> "steam://store"
                "open_library" -> "steam://library"
                "search" -> {
                    val q = param("query") ?: return null
                    "https://store.steampowered.com/search/?term=${encode(q)}"
                }
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://steamcommunity.com/id/$user"
                }
                "open_chat" -> "steam://friends"
                else -> null
            }
            "roblox" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.roblox.com/search?q=${encode(q)}"
                }
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.roblox.com/users/profile?username=$user"
                }
                else -> null
            }
            "xbox" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "xbox://profile/$user"
                }
                "open_chat" -> "xbox://messages"
                else -> null
            }
            "playstation" -> when (action) {
                "open_store" -> "psapp://store"
                else -> null
            }
            "epic_games" -> when (action) {
                "open_store" -> "com.epicgames.portal://store"
                else -> null
            }
            "youtube_kids" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "vnd.youtube.kids://search?q=${encode(q)}"
                }
                else -> null
            }

            // =======================
            // CRYPTO & ADVANCED FINANCE
            // =======================
            "binance" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "binance://trade?symbol=${encode(q)}"
                }
                "trade" -> {
                    val q = param("query") ?: return null
                    "binance://trade?symbol=${encode(q)}"
                }
                else -> null
            }
            "crypto_com" -> when (action) {
                "trade" -> {
                    val q = param("query") ?: return null
                    "crypto://trade?symbol=${encode(q)}"
                }
                else -> null
            }
            "kraken" -> when (action) {
                "trade" -> {
                    val q = param("query") ?: return null
                    "https://www.kraken.com/prices/${encode(q)}"
                }
                else -> null
            }
            "webull" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "webull://search?query=${encode(q)}"
                }
                else -> null
            }
            "fidelity" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "fidelity://search?query=${encode(q)}"
                }
                else -> null
            }
            "schwab" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "schwab://search?query=${encode(q)}"
                }
                else -> null
            }
            "etrade" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "etrade://search?query=${encode(q)}"
                }
                else -> null
            }
            "wise" -> when (action) {
                "send_money" -> {
                    val amt = param("amount") ?: ""
                    val to = param("recipient") ?: ""
                    "https://wise.com/send#amount=$amt&recipientName=${encode(to)}"
                }
                else -> null
            }
            "revolut" -> when (action) {
                "send_money" -> "revolut://transfer"
                "exchange" -> "revolut://exchange"
                else -> null
            }
            else -> null
        }
    }

    private fun buildDeepLinkPart3(
        app: String,
        action: String,
        param: (String) -> String?,
        encode: (String) -> String
    ): String? {
        return when (app) {
            // =======================
            // JOB SEARCH
            // =======================
            "indeed" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.indeed.com/jobs?q=${encode(q)}"
                }
                else -> null
            }
            "glassdoor" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.glassdoor.com/Job/jobs.htm?sc.keyword=${encode(q)}"
                }
                else -> null
            }
            "ziprecruiter" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.ziprecruiter.com/jobs-search?search=${encode(q)}"
                }
                else -> null
            }
            "handshake" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "handshake://search?q=${encode(q)}"
                }
                else -> null
            }

            // =======================
            // VPN & SECURITY
            // =======================
            "nordvpn" -> when (action) {
                "connect" -> "nordvpn://connect"
                "disconnect" -> "nordvpn://disconnect"
                else -> null
            }
            "expressvpn" -> when (action) {
                "connect" -> "expressvpn://connect"
                else -> null
            }
            "bitwarden", "1password", "lastpass", "authy" -> null // Only supports open

            // =======================
            // TRAVEL & TRANSPORT
            // =======================
            "tripadvisor" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.tripadvisor.com/Search?q=${encode(q)}"
                }
                else -> null
            }
            "kayak" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.kayak.com/explore/${encode(q)}"
                }
                else -> null
            }
            "hopper" -> when (action) {
                "search" -> {
                    val dest = param("destination") ?: param("query") ?: return null
                    "hopper://search?destination=${encode(dest)}"
                }
                else -> null
            }
            "citymapper" -> when (action) {
                "navigate", "directions" -> {
                    val dest = param("destination") ?: param("query") ?: return null
                    "citymapper://directions?endaddress=${encode(dest)}"
                }
                else -> null
            }
            "flightradar24" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "flightradar24://search?query=${encode(q)}"
                }
                "track" -> "flightradar24://flight/${param("flight") ?: ""}"
                "airport" -> "flightradar24://airport/${param("code") ?: ""}"
                else -> null
            }
            "grab" -> when (action) {
                "ride" -> {
                    val dest = param("destination") ?: return null
                    "grab://ride?destination=${encode(dest)}"
                }
                "food" -> "grab://food"
                else -> null
            }
            "careem" -> when (action) {
                "ride" -> {
                    val dest = param("destination") ?: return null
                    "careem://ride?destination=${encode(dest)}"
                }
                "food" -> "careem://food"
                else -> null
            }
            "moovit" -> when (action) {
                "navigate", "directions" -> {
                    val dest = param("destination") ?: param("query") ?: return null
                    "moovit://directions?dest_name=${encode(dest)}"
                }
                else -> null
            }

            // =======================
            // MORE SOCIAL
            // =======================
            "tumblr" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "tumblr://search?query=${encode(q)}"
                }
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.tumblr.com/$user"
                }
                "compose", "post" -> "tumblr://compose"
                else -> null
            }
            "quora" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.quora.com/search?q=${encode(q)}"
                }
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "https://www.quora.com/profile/$user"
                }
                else -> null
            }
            "clubhouse" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "clubhouse://profile/$user"
                }
                else -> null
            }
            "mastodon" -> when (action) {
                "open_profile" -> {
                    val user = param("username") ?: return null
                    "mastodon://profile/$user"
                }
                "compose", "post" -> "mastodon://compose"
                else -> null
            }
            "lemon8" -> when (action) {
                "search" -> "lemon8://search?q=${encode(param("query") ?: "")}"
                "create" -> "lemon8://create"
                else -> null
            }

            // =======================
            // MISC UTILITIES
            // =======================
            "google_home" -> when (action) {
                "devices" -> "googlehome://devices"
                else -> null
            }
            "google_fit" -> when (action) {
                "log" -> "fit://log"
                "dashboard" -> "fit://dashboard"
                else -> null
            }
            "samsung_health" -> when (action) {
                "log" -> "samsunghealth://log"
                "dashboard" -> "samsunghealth://dashboard"
                else -> null
            }
            "samsung_notes" -> when (action) {
                "create" -> "samsungnotes://create"
                else -> null
            }
            "files_by_google" -> null // Only supports open
            "google_voice" -> when (action) {
                "call" -> {
                    val phone = param("phone") ?: return null
                    "googlevoice://call?number=$phone"
                }
                "send_message" -> {
                    val phone = param("phone") ?: return null
                    val msg = param("message") ?: param("text") ?: ""
                    "googlevoice://sms?number=$phone&body=${encode(msg)}"
                }
                else -> null
            }
            "textnow" -> when (action) {
                "call" -> {
                    val phone = param("phone") ?: return null
                    "textnow://call?number=$phone"
                }
                "send_message" -> {
                    val phone = param("phone") ?: return null
                    "textnow://message?number=$phone"
                }
                else -> null
            }
            "calculator_google", "google_clock", "google_weather" -> null // Only supports open
            "microsoft_to_do" -> when (action) {
                "create", "add_task" -> {
                    val content = param("content") ?: param("text") ?: ""
                    "ms-todo://create?title=${encode(content)}"
                }
                else -> null
            }
            "ticktick" -> when (action) {
                "create", "add_task" -> {
                    val content = param("content") ?: param("text") ?: ""
                    "ticktick://add?title=${encode(content)}"
                }
                else -> null
            }
            "any_do" -> when (action) {
                "create", "add_task" -> "anydo://add"
                else -> null
            }

            // =======================
            // REGIONAL & MISC
            // =======================
            "mercado_libre" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.mercadolibre.com/jm/search?as_word=${encode(q)}"
                }
                else -> null
            }
            "rappi" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "rappi://search?query=${encode(q)}"
                }
                else -> null
            }
            "shopee" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "shopee://search?keyword=${encode(q)}"
                }
                else -> null
            }
            "lazada" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "lazada://search?q=${encode(q)}"
                }
                else -> null
            }
            "swiggy" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "swiggy://search?query=${encode(q)}"
                }
                else -> null
            }
            "zomato" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.zomato.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "yelp" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.yelp.com/search?find_desc=${encode(q)}"
                }
                else -> null
            }
            "temu" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.temu.com/search_result.html?search_key=${encode(q)}"
                }
                else -> null
            }

            // PODCASTS & MUSIC STREAMING
            "pocket_casts" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://pocketcasts.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "castbox" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://castbox.fm/search?q=${encode(q)}"
                }
                else -> null
            }
            "iheartradio" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.iheart.com/search/?q=${encode(q)}"
                }
                "play_station" -> {
                    val station = param("station") ?: return null
                    "https://www.iheart.com/search/?q=${encode(station)}"
                }
                else -> null
            }
            "overcast" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "overcast://search?query=${encode(q)}"
                }
                else -> null
            }
            "amazon_music" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://music.amazon.com/search/${encode(q)}"
                }
                else -> null
            }
            "tidal" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://tidal.com/search?q=${encode(q)}"
                }
                else -> null
            }

            // SPORTS
            "espn" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.espn.com/search/_/q/${encode(q)}"
                }
                "scores" -> "https://www.espn.com/scores"
                else -> null
            }
            "nfl" -> when (action) {
                "scores" -> "https://www.nfl.com/scores/"
                "standings" -> "https://www.nfl.com/standings/"
                "schedule" -> "https://www.nfl.com/schedules/"
                else -> null
            }
            "nba" -> when (action) {
                "scores" -> "https://www.nba.com/games"
                "standings" -> "https://www.nba.com/standings"
                "schedule" -> "https://www.nba.com/schedule"
                else -> null
            }
            "mlb" -> when (action) {
                "scores" -> "https://www.mlb.com/scores"
                "standings" -> "https://www.mlb.com/standings"
                "schedule" -> "https://www.mlb.com/schedule"
                else -> null
            }
            "cbs_sports" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.cbssports.com/search/${encode(q)}/"
                }
                "scores" -> "https://www.cbssports.com/scores/"
                else -> null
            }
            "fanduel" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.fanduel.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "draftkings" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.draftkings.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "the_score" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.thescore.com/search?q=${encode(q)}"
                }
                else -> null
            }

            // HOME AUTOMATION / SMART HOME
            "alexa" -> when (action) {
                "set_timer" -> {
                    val duration = param("duration") ?: return null
                    "alexa://set-timer?duration=${encode(duration)}"
                }
                "set_reminder" -> {
                    val message = param("message") ?: return null
                    "alexa://set-reminder?message=${encode(message)}"
                }
                else -> null
            }
            "smartthings" -> when (action) {
                "devices" -> "smartthings://devices"
                "scenes" -> "smartthings://scenes"
                else -> null
            }
            "philips_hue" -> when (action) {
                "lights" -> "phhue://lights"
                "scenes" -> "phhue://scenes"
                else -> null
            }
            "ring" -> when (action) {
                "live_view" -> "ring://liveview"
                "history" -> "ring://history"
                else -> null
            }
            "wyze" -> when (action) {
                "devices" -> "wyze://devices"
                "live_view" -> "wyze://live"
                else -> null
            }
            "google_nest" -> when (action) {
                "cameras" -> "nest://cameras"
                "thermostat" -> "nest://thermostat"
                else -> null
            }

            // BUSINESS & PROJECT MANAGEMENT
            "jira" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.atlassian.com/search?q=${encode(q)}"
                }
                "create_issue" -> "atlassian://jira/create"
                else -> null
            }
            "monday" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://monday.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "basecamp" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "basecamp://search?query=${encode(q)}"
                }
                else -> null
            }
            "hubspot" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.hubspot.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "salesforce" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "salesforce://search?query=${encode(q)}"
                }
                else -> null
            }
            "confluence" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.atlassian.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "clickup" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://clickup.com/search?q=${encode(q)}"
                }
                else -> null
            }

            // DEV TOOLS
            "github" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://github.com/search?q=${encode(q)}"
                }
                "profile" -> {
                    val user = param("username") ?: return null
                    "https://github.com/${encode(user)}"
                }
                "repo" -> {
                    val repo = param("repository") ?: return null
                    "https://github.com/${encode(repo)}"
                }
                else -> null
            }
            "gitlab" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://gitlab.com/search?search=${encode(q)}"
                }
                else -> null
            }
            "stack_overflow" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://stackoverflow.com/search?q=${encode(q)}"
                }
                else -> null
            }

            // BANKING
            "chase" -> when (action) {
                "accounts" -> "chase://accounts"
                "transfer" -> "chase://transfer"
                else -> null
            }
            "bank_of_america" -> when (action) {
                "accounts" -> "bofa://accounts"
                "transfer" -> "bofa://transfer"
                else -> null
            }
            "wells_fargo" -> when (action) {
                "accounts" -> "wellsfargo://accounts"
                "transfer" -> "wellsfargo://transfer"
                else -> null
            }
            "capital_one" -> when (action) {
                "accounts" -> "capitalone://accounts"
                else -> null
            }
            "citi" -> when (action) {
                "accounts" -> "citi://accounts"
                else -> null
            }

            // DOCUMENT & SCANNER
            "camscanner" -> when (action) {
                "scan" -> "camscanner://scan"
                else -> null
            }
            "microsoft_lens" -> when (action) {
                "scan" -> "microsoftlens://scan"
                else -> null
            }
            "adobe_scan" -> when (action) {
                "scan" -> "adobescan://scan"
                else -> null
            }

            // AIRLINES
            "united_airlines" -> when (action) {
                "search_flights" -> {
                    val from = param("from") ?: return null
                    val to = param("to") ?: return null
                    "https://www.united.com/en/us/fsr/choose-flights?f=${encode(from)}&t=${encode(to)}"
                }
                "check_in" -> "https://www.united.com/en/us/checkin"
                "flight_status" -> "https://www.united.com/en/us/flightstatus"
                else -> null
            }
            "delta" -> when (action) {
                "search_flights" -> {
                    val from = param("from") ?: return null
                    val to = param("to") ?: return null
                    "https://www.delta.com/flight-search/search?cacheKeySuffix=bestfares&from=${encode(from)}&to=${encode(to)}"
                }
                "check_in" -> "https://www.delta.com/check-in/"
                "flight_status" -> "https://www.delta.com/flight-status/"
                else -> null
            }
            "american_airlines" -> when (action) {
                "search_flights" -> {
                    val from = param("from") ?: return null
                    val to = param("to") ?: return null
                    "https://www.aa.com/booking/search?from=${encode(from)}&to=${encode(to)}"
                }
                "check_in" -> "https://www.aa.com/check-in"
                "flight_status" -> "https://www.aa.com/flightStatus"
                else -> null
            }
            "southwest" -> when (action) {
                "search_flights" -> "https://www.southwest.com/air/booking/"
                "check_in" -> "https://www.southwest.com/air/check-in/"
                "flight_status" -> "https://www.southwest.com/air/flight-status/"
                else -> null
            }

            // MORE FINANCE
            "credit_karma" -> when (action) {
                "credit_score" -> "creditkarma://score"
                "recommendations" -> "creditkarma://recommendations"
                else -> null
            }
            "ynab" -> when (action) {
                "budget" -> "ynab://budget"
                "accounts" -> "ynab://accounts"
                else -> null
            }
            "acorns" -> when (action) {
                "invest" -> "acorns://invest"
                "portfolio" -> "acorns://portfolio"
                else -> null
            }
            "sofi" -> when (action) {
                "accounts" -> "sofi://accounts"
                "invest" -> "sofi://invest"
                else -> null
            }
            "chime" -> when (action) {
                "accounts" -> "chime://accounts"
                "transfer" -> "chime://transfer"
                else -> null
            }
            "empower" -> when (action) {
                "dashboard" -> "empower://dashboard"
                "accounts" -> "empower://accounts"
                else -> null
            }

            // DEALS & REWARDS
            "ibotta" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "ibotta://search?query=${encode(q)}"
                }
                "offers" -> "ibotta://offers"
                else -> null
            }
            "rakuten" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.rakuten.com/search?query=${encode(q)}"
                }
                else -> null
            }
            "groupon" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.groupon.com/browse?query=${encode(q)}"
                }
                else -> null
            }
            "retailmenot" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.retailmenot.com/search?q=${encode(q)}"
                }
                else -> null
            }

            // MORE FITNESS
            "alltrails" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.alltrails.com/search?q=${encode(q)}"
                }
                else -> null
            }
            "runkeeper" -> when (action) {
                "start_run" -> "runkeeper://start"
                "history" -> "runkeeper://activities"
                else -> null
            }
            "adidas_running" -> when (action) {
                "start_run" -> "runtastic://start"
                "history" -> "runtastic://activities"
                else -> null
            }
            "map_my_run" -> when (action) {
                "start_run" -> "mapmyrun://start"
                "history" -> "mapmyrun://activities"
                else -> null
            }
            "seven" -> when (action) {
                "start_workout" -> "seven://workout"
                else -> null
            }

            // GROCERY & WHOLESALE
            "kroger" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.kroger.com/search?query=${encode(q)}"
                }
                else -> null
            }
            "costco" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.costco.com/CatalogSearch?keyword=${encode(q)}"
                }
                else -> null
            }
            "sams_club" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.samsclub.com/s/${encode(q)}"
                }
                else -> null
            }
            "whole_foods" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "wholefoodsmarket://search?query=${encode(q)}"
                }
                else -> null
            }
            "aldi" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.aldi.us/search/?q=${encode(q)}"
                }
                else -> null
            }

            // MORE RIDE & DELIVERY
            "bolt" -> when (action) {
                "ride" -> {
                    val dest = param("destination") ?: return null
                    "bolt://ride?destination=${encode(dest)}"
                }
                else -> null
            }
            "didi" -> when (action) {
                "ride" -> "didi://ride"
                else -> null
            }
            "gopuff" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "gopuff://search?query=${encode(q)}"
                }
                else -> null
            }
            "getir" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "getir://search?query=${encode(q)}"
                }
                else -> null
            }

            // FAMILY & PARENTING
            "life360" -> when (action) {
                "map" -> "life360://map"
                "circles" -> "life360://circles"
                else -> null
            }
            "family_link" -> when (action) {
                "manage" -> "familylink://manage"
                else -> null
            }

            // MORE COMMUNICATION
            "truecaller" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "truecaller://search?query=${encode(q)}"
                }
                else -> null
            }
            "webex" -> when (action) {
                "join" -> {
                    val meetingId = param("meeting_id") ?: return null
                    "webex://meet/$meetingId"
                }
                else -> null
            }
            "groupme" -> when (action) {
                "compose" -> "groupme://compose"
                else -> null
            }
            else -> null
        }
    }

    private fun buildDeepLinkPart4(
        app: String,
        action: String,
        param: (String) -> String?,
        encode: (String) -> String
    ): String? {
        return when (app) {
            // DESIGN & CREATIVE
            "figma" -> when (action) {
                "open_file" -> {
                    val fileId = param("file_id") ?: return null
                    "https://www.figma.com/file/$fileId"
                }
                else -> null
            }
            "miro" -> when (action) {
                "open_board" -> {
                    val boardId = param("board_id") ?: return null
                    "https://miro.com/app/board/$boardId"
                }
                else -> null
            }
            "adobe_express" -> when (action) {
                "create" -> "adobeexpress://create"
                else -> null
            }

            // MORE NOTES
            "obsidian" -> when (action) {
                "open_vault" -> {
                    val vault = param("vault") ?: return null
                    "obsidian://open?vault=${encode(vault)}"
                }
                "new_note" -> {
                    val vault = param("vault") ?: return null
                    val name = param("name") ?: ""
                    "obsidian://new?vault=${encode(vault)}&name=${encode(name)}"
                }
                "search" -> {
                    val vault = param("vault") ?: return null
                    val q = param("query") ?: return null
                    "obsidian://search?vault=${encode(vault)}&query=${encode(q)}"
                }
                else -> null
            }
            "simplenote" -> when (action) {
                "new_note" -> "simplenote://new"
                "search" -> {
                    val q = param("query") ?: return null
                    "simplenote://search?query=${encode(q)}"
                }
                else -> null
            }
            "bear" -> when (action) {
                "create" -> {
                    val title = param("title") ?: ""
                    "bear://x-callback-url/create?title=${encode(title)}"
                }
                "search" -> {
                    val q = param("query") ?: return null
                    "bear://x-callback-url/search?term=${encode(q)}"
                }
                else -> null
            }

            // REMITTANCE
            "western_union" -> when (action) {
                "send_money" -> "https://www.westernunion.com/us/en/send-money.html"
                "track_transfer" -> "https://www.westernunion.com/us/en/track-transfer.html"
                else -> null
            }
            "remitly" -> when (action) {
                "send_money" -> "https://www.remitly.com/us/en/send"
                else -> null
            }
            "worldremit" -> when (action) {
                "send_money" -> "https://www.worldremit.com/en/send"
                else -> null
            }

            // PETS
            "chewy" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.chewy.com/s?query=${encode(q)}"
                }
                else -> null
            }
            "rover" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.rover.com/search/?${encode(q)}"
                }
                else -> null
            }

            // SOCIAL & EVENTS
            "nextdoor" -> when (action) {
                "post" -> "nextdoor://compose"
                "search" -> {
                    val q = param("query") ?: return null
                    "nextdoor://search?query=${encode(q)}"
                }
                else -> null
            }
            "meetup" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.meetup.com/find/?keywords=${encode(q)}"
                }
                else -> null
            }
            "eventbrite" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.eventbrite.com/d/online/${encode(q)}/"
                }
                else -> null
            }

            // LANGUAGE LEARNING
            "deepl" -> when (action) {
                "translate" -> {
                    val text = param("text") ?: return null
                    val targetLang = param("target_language") ?: "en"
                    "https://www.deepl.com/translator#auto/${encode(targetLang)}/${encode(text)}"
                }
                else -> null
            }
            "babbel" -> when (action) {
                "learn" -> "babbel://learn"
                "review" -> "babbel://review"
                else -> null
            }
            "rosetta_stone" -> when (action) {
                "learn" -> "rosettastone://learn"
                else -> null
            }

            // CAR & AUTO
            "gasbuddy" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://www.gasbuddy.com/home?search=${encode(q)}"
                }
                "nearby" -> "gasbuddy://nearby"
                else -> null
            }
            "parkmobile" -> when (action) {
                "park" -> "parkmobile://park"
                "find_parking" -> "parkmobile://find"
                else -> null
            }
            "turo" -> when (action) {
                "search" -> {
                    val q = param("query") ?: return null
                    "https://turo.com/search?location=${encode(q)}"
                }
                else -> null
            }

            // MORE UTILITIES
            "ifttt" -> when (action) {
                "applets" -> "ifttt://applets"
                "create" -> "ifttt://create"
                else -> null
            }
            "forest" -> when (action) {
                "plant" -> "forest://plant"
                else -> null
            }
            "widgetsmith" -> when (action) {
                "create" -> "widgetsmith://create"
                else -> null
            }

            // Streaming Video
            "paramount_plus" -> when (action) {
                "search" -> "https://www.paramountplus.com/search/?q=${encode(param("query") ?: "")}"
                "show" -> "https://www.paramountplus.com/shows/${encode(param("title") ?: "")}"
                else -> null
            }
            "peacock" -> when (action) {
                "search" -> "https://www.peacocktv.com/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "crunchyroll" -> when (action) {
                "search" -> "crunchyroll://search?q=${encode(param("query") ?: "")}"
                "series" -> "crunchyroll://series/${param("id") ?: ""}"
                else -> null
            }
            "apple_tv_plus" -> when (action) {
                "search" -> "https://tv.apple.com/search?term=${encode(param("query") ?: "")}"
                else -> null
            }
            "discovery_plus" -> when (action) {
                "search" -> "https://www.discoveryplus.com/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "pluto_tv" -> when (action) {
                "search" -> "https://pluto.tv/search?q=${encode(param("query") ?: "")}"
                "live" -> "https://pluto.tv/live-tv"
                else -> null
            }
            "tubi" -> when (action) {
                "search" -> "https://tubitv.com/search/${encode(param("query") ?: "")}"
                else -> null
            }
            "plex" -> when (action) {
                "search" -> "plex://search?query=${encode(param("query") ?: "")}"
                "play" -> "plex://play?key=${param("key") ?: ""}"
                else -> null
            }
            "roku_channel" -> when (action) {
                "search" -> "https://therokuchannel.roku.com/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "mubi" -> when (action) {
                "film" -> "mubi://film/${param("id") ?: ""}"
                else -> null
            }
            "curiosity_stream" -> when (action) {
                "search" -> "https://curiositystream.com/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "fandango_at_home" -> when (action) {
                "search" -> "https://www.vudu.com/content/movies/search?searchString=${encode(param("query") ?: "")}"
                else -> null
            }

            // Anime & Comics
            "webtoon" -> when (action) {
                "search" -> "https://www.webtoons.com/search?keyword=${encode(param("query") ?: "")}"
                "series" -> "webtoon://series/${param("id") ?: ""}"
                else -> null
            }
            "myanimelist" -> when (action) {
                "search" -> "https://myanimelist.net/search/all?q=${encode(param("query") ?: "")}"
                "anime" -> "https://myanimelist.net/anime/${param("id") ?: ""}"
                else -> null
            }
            "tapas" -> when (action) {
                "search" -> "https://tapas.io/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "manga_plus" -> when (action) {
                "title" -> "https://mangaplus.shueisha.co.jp/titles/${param("id") ?: ""}"
                else -> null
            }

            // Resale & Marketplace
            "mercari" -> when (action) {
                "search" -> "https://www.mercari.com/search/?keyword=${encode(param("query") ?: "")}"
                "sell" -> "mercari://sell"
                else -> null
            }
            "poshmark" -> when (action) {
                "search" -> "poshmark://search?query=${encode(param("query") ?: "")}"
                "sell" -> "poshmark://sell"
                else -> null
            }
            "depop" -> when (action) {
                "search" -> "depop://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "offerup" -> when (action) {
                "search" -> "offerup://search?q=${encode(param("query") ?: "")}"
                "sell" -> "offerup://sell"
                else -> null
            }
            "vinted" -> when (action) {
                "search" -> "https://www.vinted.com/catalog?search_text=${encode(param("query") ?: "")}"
                else -> null
            }
            "thredup" -> when (action) {
                "search" -> "https://www.thredup.com/products?search_tags=${encode(param("query") ?: "")}"
                else -> null
            }

            // Pharmacy & Health Services
            "goodrx" -> when (action) {
                "search" -> "goodrx://drug/${encode(param("drug") ?: param("query") ?: "")}"
                "compare" -> "https://www.goodrx.com/compare/${encode(param("drug") ?: "")}"
                else -> null
            }
            "cvs" -> when (action) {
                "pharmacy" -> "cvs://pharmacy"
                "refill" -> "cvs://pharmacy/refill"
                "store" -> "https://www.cvs.com/store-locator"
                else -> null
            }
            "walgreens" -> when (action) {
                "pharmacy" -> "walgreens://pharmacy"
                "refill" -> "walgreens://pharmacy/refill"
                "store" -> "https://www.walgreens.com/storelocator"
                "photo" -> "walgreens://photo"
                else -> null
            }
            "teladoc" -> when (action) {
                "visit" -> "teladoc://visit"
                "schedule" -> "teladoc://schedule"
                else -> null
            }
            "zocdoc" -> when (action) {
                "search" -> "zocdoc://search?specialty=${encode(param("specialty") ?: param("query") ?: "")}&location=${encode(param("location") ?: "")}"
                "book" -> "zocdoc://book"
                else -> null
            }
            "mychart" -> when (action) {
                "appointments" -> "mychart://appointments"
                "messages" -> "mychart://messages"
                "results" -> "mychart://results"
                else -> null
            }
            "one_medical" -> when (action) {
                "book" -> "onemedical://book"
                else -> null
            }

            // Home Services
            "taskrabbit" -> when (action) {
                "search" -> "taskrabbit://search?q=${encode(param("query") ?: "")}"
                "book" -> "taskrabbit://booking/new?category=${encode(param("category") ?: "")}"
                else -> null
            }
            "thumbtack" -> when (action) {
                "search" -> "https://www.thumbtack.com/search?search_term=${encode(param("query") ?: "")}"
                else -> null
            }
            "angi" -> when (action) {
                "search" -> "https://www.angi.com/search?query=${encode(param("query") ?: "")}"
                else -> null
            }

            // Insurance
            "geico" -> when (action) {
                "policy" -> "geico://policy"
                "claims" -> "geico://claims"
                "id_card" -> "geico://id-card"
                "roadside" -> "geico://roadside"
                else -> null
            }
            "progressive" -> when (action) {
                "policy" -> "progressive://policy"
                "claims" -> "progressive://claims"
                "id_card" -> "progressive://id-card"
                else -> null
            }
            "state_farm" -> when (action) {
                "policy" -> "statefarm://policy"
                "claims" -> "statefarm://claims"
                "agent" -> "statefarm://agent"
                else -> null
            }
            "lemonade" -> when (action) {
                "policy" -> "lemonade://policy"
                "claims" -> "lemonade://claims"
                else -> null
            }

            // Tax
            "turbotax" -> when (action) {
                "file" -> "turbotax://start"
                "refund" -> "turbotax://refund"
                else -> null
            }
            "hr_block" -> when (action) {
                "file" -> "hrblock://start"
                else -> null
            }

            // Document Signing
            "docusign" -> when (action) {
                "sign" -> "docusign://sign"
                "send" -> "docusign://send"
                else -> null
            }

            // Rewards & Cashback
            "fetch_rewards" -> when (action) {
                "scan" -> "fetchrewards://scan"
                "offers" -> "fetchrewards://offers"
                else -> null
            }
            "shopkick" -> when (action) {
                "kicks" -> "shopkick://kicks"
                "offers" -> "shopkick://offers"
                else -> null
            }
            "swagbucks" -> when (action) {
                "earn" -> "swagbucks://earn"
                "surveys" -> "swagbucks://surveys"
                else -> null
            }

            // Crypto & Web3
            "metamask" -> when (action) {
                "send" -> "metamask://send?address=${param("address") ?: ""}"
                "swap" -> "metamask://swap"
                "dapp" -> "metamask://dapp/${param("url") ?: ""}"
                else -> null
            }
            "trust_wallet" -> when (action) {
                "send" -> "trust://send?asset=${param("asset") ?: ""}&address=${param("address") ?: ""}"
                "swap" -> "trust://swap"
                "dapp" -> "trust://browser?url=${encode(param("url") ?: "")}"
                else -> null
            }
            "phantom" -> when (action) {
                "browse" -> "phantom://browse/${param("url") ?: ""}"
                else -> null
            }
            "ledger_live" -> when (action) {
                "portfolio" -> "ledgerlive://portfolio"
                "receive" -> "ledgerlive://receive"
                "send" -> "ledgerlive://send"
                else -> null
            }
            "uniswap" -> when (action) {
                "swap" -> "uniswap://swap"
                else -> null
            }

            // Password Managers
            "one_password" -> when (action) {
                "search" -> "onepassword://search/${encode(param("query") ?: "")}"
                "generate" -> "onepassword://generate-password"
                else -> null
            }
            "dashlane" -> when (action) {
                "search" -> "dashlane://search?query=${encode(param("query") ?: "")}"
                "generate" -> "dashlane://generate-password"
                else -> null
            }

            // Car Marketplace
            "carvana" -> when (action) {
                "search" -> "https://www.carvana.com/cars?search=${encode(param("query") ?: "")}"
                else -> null
            }
            "autotrader" -> when (action) {
                "search" -> "https://www.autotrader.com/cars-for-sale?searchRadius=50&keyword=${encode(param("query") ?: "")}"
                else -> null
            }
            "cargurus" -> when (action) {
                "search" -> "https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?searchQuery=${encode(param("query") ?: "")}"
                else -> null
            }

            // Transit & Flight Tracking
            "transit_app" -> when (action) {
                "directions" -> "transit://directions?to=${encode(param("destination") ?: "")}"
                "nearby" -> "transit://nearby"
                else -> null
            }

            // Radio & Podcasts
            "tunein" -> when (action) {
                "search" -> "tunein://search?query=${encode(param("query") ?: "")}"
                "station" -> "tunein://station/${param("id") ?: ""}"
                "live" -> "tunein://live"
                else -> null
            }
            "siriusxm" -> when (action) {
                "channel" -> "siriusxm://channel/${param("channel") ?: ""}"
                "search" -> "siriusxm://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "audiomack" -> when (action) {
                "search" -> "audiomack://search?q=${encode(param("query") ?: "")}"
                "play" -> "audiomack://play/${param("id") ?: ""}"
                else -> null
            }
            "podcast_addict" -> when (action) {
                "search" -> "podcastaddict://search/${encode(param("query") ?: "")}"
                else -> null
            }

            // Weather (more)
            "windy" -> when (action) {
                "location" -> "https://www.windy.com/${param("lat") ?: ""}/${param("lon") ?: ""}"
                "radar" -> "https://www.windy.com/-Radar-radar"
                else -> null
            }
            "weather_underground" -> when (action) {
                "forecast" -> "https://www.wunderground.com/weather/${encode(param("location") ?: "")}"
                "radar" -> "https://www.wunderground.com/wundermap"
                else -> null
            }

            // Email Clients
            "spark_email" -> when (action) {
                "compose" -> "readdle-spark://compose?subject=${encode(param("subject") ?: "")}&recipient=${param("to") ?: ""}"
                else -> null
            }
            "edison_mail" -> when (action) {
                "compose" -> "edison-mail://compose?to=${param("to") ?: ""}&subject=${encode(param("subject") ?: "")}"
                else -> null
            }

            // Language Learning (more)
            "busuu" -> when (action) {
                "learn" -> "busuu://learn?language=${param("language") ?: ""}"
                else -> null
            }
            "memrise" -> when (action) {
                "learn" -> "memrise://learn?language=${param("language") ?: ""}"
                "review" -> "memrise://review"
                else -> null
            }
            "hellotalk" -> when (action) {
                "search" -> "hellotalk://search?language=${param("language") ?: ""}"
                else -> null
            }
            "tandem" -> when (action) {
                "search" -> "tandem://search?language=${param("language") ?: ""}"
                else -> null
            }

            // Meditation & Wellness
            "insight_timer" -> when (action) {
                "meditate" -> "insight://meditate"
                "timer" -> "insight://timer?duration=${param("minutes") ?: "10"}"
                "search" -> "insight://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "talkspace" -> when (action) {
                "chat" -> "talkspace://chat"
                "session" -> "talkspace://session"
                else -> null
            }
            "waking_up" -> when (action) {
                "meditate" -> "wakingup://meditate"
                "daily" -> "wakingup://daily"
                else -> null
            }

            // Parenting & Kids
            "babycenter" -> when (action) {
                "week" -> "babycenter://pregnancy/week/${param("week") ?: ""}"
                "articles" -> "babycenter://articles"
                else -> null
            }
            "pbs_kids" -> when (action) {
                "games" -> "pbskids://games"
                "videos" -> "pbskids://videos"
                else -> null
            }
            "abcmouse" -> when (action) {
                "learn" -> "abcmouse://learn"
                else -> null
            }
            "nick_jr" -> when (action) {
                "videos" -> "nickjr://videos"
                "games" -> "nickjr://games"
                else -> null
            }
            "the_bump" -> when (action) {
                "week" -> "thebump://pregnancy/week/${param("week") ?: ""}"
                else -> null
            }
            "peanut" -> when (action) {
                "groups" -> "peanut://groups"
                "feed" -> "peanut://feed"
                else -> null
            }

            // Religious
            "muslim_pro" -> when (action) {
                "prayer" -> "muslimpro://prayer"
                "quran" -> "muslimpro://quran"
                "qibla" -> "muslimpro://qibla"
                else -> null
            }
            "quran_app" -> when (action) {
                "surah" -> "quran://surah/${param("number") ?: "1"}"
                "ayah" -> "quran://surah/${param("surah") ?: "1"}/ayah/${param("ayah") ?: "1"}"
                "search" -> "quran://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "bible_app" -> when (action) {
                "verse" -> "youversion://bible?reference=${encode(param("reference") ?: "")}"
                "plan" -> "youversion://reading-plans"
                "search" -> "youversion://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "pray" -> when (action) {
                "daily" -> "pray://daily"
                "stories" -> "pray://stories"
                else -> null
            }

            // Bills & Splitting
            "splitwise" -> when (action) {
                "add" -> "splitwise://add-expense"
                "groups" -> "splitwise://groups"
                "balances" -> "splitwise://balances"
                else -> null
            }
            "google_one" -> when (action) {
                "storage" -> "googleone://storage"
                "backup" -> "googleone://backup"
                else -> null
            }

            // Habits
            "habitica" -> when (action) {
                "tasks" -> "habitica://tasks"
                "habits" -> "habitica://habits"
                "dailies" -> "habitica://dailies"
                else -> null
            }

            // Cloud Storage (more)
            "box" -> when (action) {
                "search" -> "box://search?q=${encode(param("query") ?: "")}"
                "upload" -> "box://upload"
                "folder" -> "box://folder/${param("id") ?: "0"}"
                else -> null
            }
            "mega" -> when (action) {
                "upload" -> "mega://upload"
                "search" -> "mega://search?q=${encode(param("query") ?: "")}"
                else -> null
            }

            // Home & Furniture
            "wayfair" -> when (action) {
                "search" -> "wayfair://search?query=${encode(param("query") ?: "")}"
                "category" -> "https://www.wayfair.com/keyword.php?keyword=${encode(param("category") ?: "")}"
                else -> null
            }
            "ikea" -> when (action) {
                "search" -> "https://www.ikea.com/us/en/search/?q=${encode(param("query") ?: "")}"
                "product" -> "https://www.ikea.com/us/en/p/-${param("id") ?: ""}"
                else -> null
            }
            "houzz" -> when (action) {
                "search" -> "houzz://search?q=${encode(param("query") ?: "")}"
                "photos" -> "houzz://photos"
                else -> null
            }

            // Fast Food & Restaurant
            "chick_fil_a" -> when (action) {
                "order" -> "chickfila://order"
                "rewards" -> "chickfila://rewards"
                "menu" -> "chickfila://menu"
                else -> null
            }
            "chipotle" -> when (action) {
                "order" -> "chipotle://order"
                "rewards" -> "chipotle://rewards"
                "menu" -> "https://www.chipotle.com/menu"
                else -> null
            }
            "dominos" -> when (action) {
                "order" -> "dominos://order"
                "tracker" -> "dominos://tracker"
                "menu" -> "https://www.dominos.com/menu"
                else -> null
            }
            "burger_king" -> when (action) {
                "order" -> "bk://order"
                "deals" -> "bk://deals"
                "menu" -> "https://www.bk.com/menu"
                else -> null
            }
            "subway" -> when (action) {
                "order" -> "subway://order"
                "rewards" -> "subway://rewards"
                else -> null
            }
            "dunkin" -> when (action) {
                "order" -> "dunkin://order"
                "rewards" -> "dunkin://rewards"
                "menu" -> "https://www.dunkindonuts.com/en/menu"
                else -> null
            }
            "papa_johns" -> when (action) {
                "order" -> "papajohns://order"
                "deals" -> "papajohns://deals"
                else -> null
            }
            "pizza_hut" -> when (action) {
                "order" -> "pizzahut://order"
                "menu" -> "https://www.pizzahut.com/menu"
                "deals" -> "pizzahut://deals"
                else -> null
            }
            "wendys" -> when (action) {
                "order" -> "wendys://order"
                "deals" -> "wendys://deals"
                else -> null
            }
            "taco_bell" -> when (action) {
                "order" -> "tacobell://order"
                "rewards" -> "tacobell://rewards"
                else -> null
            }
            "popeyes" -> when (action) {
                "order" -> "popeyes://order"
                else -> null
            }
            "panera" -> when (action) {
                "order" -> "panerabread://order"
                "menu" -> "https://www.panerabread.com/en-us/menu.html"
                "rewards" -> "panerabread://rewards"
                else -> null
            }
            else -> null
        }
    }

    private fun buildDeepLinkPart5(
        app: String,
        action: String,
        param: (String) -> String?,
        encode: (String) -> String
    ): String? {
        return when (app) {
            // Airlines (more)
            "jetblue" -> when (action) {
                "book" -> "https://www.jetblue.com/booking"
                "checkin" -> "jetblue://checkin"
                "status" -> "jetblue://flight-status?flight=${param("flight") ?: ""}"
                else -> null
            }
            "british_airways" -> when (action) {
                "book" -> "https://www.britishairways.com/travel/book"
                "checkin" -> "ba://checkin"
                "status" -> "ba://flight-status"
                else -> null
            }
            "spirit_airlines" -> when (action) {
                "book" -> "https://www.spirit.com/book"
                "checkin" -> "spirit://checkin"
                else -> null
            }
            "frontier_airlines" -> when (action) {
                "book" -> "https://www.flyfrontier.com/booking"
                "checkin" -> "frontier://checkin"
                else -> null
            }
            "emirates" -> when (action) {
                "book" -> "https://www.emirates.com/flights/book"
                "checkin" -> "emirates://checkin"
                "status" -> "emirates://flight-status?flight=${param("flight") ?: ""}"
                else -> null
            }
            "turkish_airlines" -> when (action) {
                "book" -> "https://www.turkishairlines.com/en-int/flights/"
                "checkin" -> "thy://checkin"
                else -> null
            }
            "lufthansa" -> when (action) {
                "book" -> "https://www.lufthansa.com/us/en/flight-search"
                "checkin" -> "lufthansa://checkin"
                else -> null
            }
            "qatar_airways" -> when (action) {
                "book" -> "https://www.qatarairways.com/en/booking.html"
                "checkin" -> "qatarairways://checkin"
                else -> null
            }

            // Travel (more)
            "vrbo" -> when (action) {
                "search" -> "vrbo://search?q=${encode(param("query") ?: param("destination") ?: "")}"
                "property" -> "vrbo://property/${param("id") ?: ""}"
                else -> null
            }
            "hostelworld" -> when (action) {
                "search" -> "https://www.hostelworld.com/s?q=${encode(param("destination") ?: "")}"
                else -> null
            }
            "skyscanner" -> when (action) {
                "flights" -> "skyscanner://flights?origin=${param("from") ?: ""}&destination=${param("to") ?: ""}"
                "hotels" -> "skyscanner://hotels?destination=${encode(param("destination") ?: "")}"
                else -> null
            }
            "google_flights" -> when (action) {
                "search" -> "https://www.google.com/travel/flights?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "agoda" -> when (action) {
                "search" -> "agoda://search?destination=${encode(param("destination") ?: "")}"
                "hotel" -> "agoda://hotel/${param("id") ?: ""}"
                else -> null
            }
            "hotels_com" -> when (action) {
                "search" -> "https://www.hotels.com/search.do?q-destination=${encode(param("destination") ?: "")}"
                else -> null
            }

            // Retail & Shopping
            "best_buy" -> when (action) {
                "search" -> "bestbuy://search?query=${encode(param("query") ?: "")}"
                "product" -> "https://www.bestbuy.com/site/searchpage.jsp?st=${encode(param("query") ?: "")}"
                "deals" -> "bestbuy://deals"
                else -> null
            }
            "home_depot" -> when (action) {
                "search" -> "homedepot://search?q=${encode(param("query") ?: "")}"
                "product" -> "https://www.homedepot.com/s/${encode(param("query") ?: "")}"
                else -> null
            }
            "lowes" -> when (action) {
                "search" -> "lowes://search?q=${encode(param("query") ?: "")}"
                "product" -> "https://www.lowes.com/search?searchTerm=${encode(param("query") ?: "")}"
                else -> null
            }
            "nike" -> when (action) {
                "search" -> "nike://search?q=${encode(param("query") ?: "")}"
                "product" -> "nike://product/${param("id") ?: ""}"
                "wishlist" -> "nike://wishlist"
                else -> null
            }
            "adidas" -> when (action) {
                "search" -> "adidas://search?q=${encode(param("query") ?: "")}"
                "product" -> "adidas://product/${param("id") ?: ""}"
                else -> null
            }
            "sephora" -> when (action) {
                "search" -> "sephora://search?q=${encode(param("query") ?: "")}"
                "product" -> "sephora://product/${param("id") ?: ""}"
                "offers" -> "sephora://offers"
                else -> null
            }
            "ulta" -> when (action) {
                "search" -> "ulta://search?q=${encode(param("query") ?: "")}"
                "offers" -> "ulta://offers"
                else -> null
            }
            "macys" -> when (action) {
                "search" -> "macys://search?q=${encode(param("query") ?: "")}"
                "deals" -> "macys://deals"
                else -> null
            }
            "nordstrom" -> when (action) {
                "search" -> "nordstrom://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "zara" -> when (action) {
                "search" -> "https://www.zara.com/us/en/search?searchTerm=${encode(param("query") ?: "")}"
                else -> null
            }
            "h_and_m" -> when (action) {
                "search" -> "https://www2.hm.com/en_us/search-results.html?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "uniqlo" -> when (action) {
                "search" -> "https://www.uniqlo.com/us/en/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "gap" -> when (action) {
                "search" -> "https://www.gap.com/browse/search.do?searchText=${encode(param("query") ?: "")}"
                else -> null
            }
            "old_navy" -> when (action) {
                "search" -> "https://oldnavy.gap.com/browse/search.do?searchText=${encode(param("query") ?: "")}"
                else -> null
            }
            "asos" -> when (action) {
                "search" -> "asos://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "fashion_nova" -> when (action) {
                "search" -> "https://www.fashionnova.com/search?q=${encode(param("query") ?: "")}"
                else -> null
            }

            // Shipping & Tracking
            "fedex" -> when (action) {
                "track" -> "fedex://track?number=${param("tracking") ?: ""}"
                "ship" -> "https://www.fedex.com/shipping/shipment/package"
                "locations" -> "fedex://locations"
                else -> null
            }
            "ups" -> when (action) {
                "track" -> "ups://track?trackingNumber=${param("tracking") ?: ""}"
                "ship" -> "https://www.ups.com/ship"
                "locations" -> "ups://locations"
                else -> null
            }
            "usps" -> when (action) {
                "track" -> "usps://track?trackingId=${param("tracking") ?: ""}"
                "locations" -> "https://tools.usps.com/find-location.htm"
                else -> null
            }
            "dhl" -> when (action) {
                "track" -> "dhl://track?shipmentId=${param("tracking") ?: ""}"
                "ship" -> "https://www.dhl.com/en/express/shipping.html"
                else -> null
            }
            "package_tracker" -> when (action) {
                "track" -> "seventeentrack://track?number=${param("tracking") ?: ""}"
                else -> null
            }

            // Social (more)
            "bluesky" -> when (action) {
                "post" -> "bluesky://compose"
                "profile" -> "bluesky://profile/${param("handle") ?: ""}"
                "search" -> "bluesky://search?q=${encode(param("query") ?: "")}"
                else -> null
            }

            // Parking & EV
            "spothero" -> when (action) {
                "search" -> "spothero://search?destination=${encode(param("location") ?: "")}"
                else -> null
            }
            "chargepoint" -> when (action) {
                "search" -> "chargepoint://search?location=${encode(param("location") ?: "")}"
                "station" -> "chargepoint://station/${param("id") ?: ""}"
                else -> null
            }
            "plugshare" -> when (action) {
                "search" -> "plugshare://search?location=${encode(param("location") ?: "")}"
                else -> null
            }

            // Video Communication
            "loom" -> when (action) {
                "record" -> "loom://record"
                "video" -> "loom://video/${param("id") ?: ""}"
                else -> null
            }
            "marco_polo" -> when (action) {
                "send" -> "marcopolo://send"
                else -> null
            }

            // Education (more)
            "google_classroom" -> when (action) {
                "class" -> "classroom://class/${param("id") ?: ""}"
                "assignments" -> "classroom://assignments"
                else -> null
            }
            "canvas_student" -> when (action) {
                "courses" -> "canvas-student://courses"
                "assignments" -> "canvas-student://assignments"
                "grades" -> "canvas-student://grades"
                else -> null
            }
            "remind" -> when (action) {
                "message" -> "remind://message"
                "class" -> "remind://class/${param("id") ?: ""}"
                else -> null
            }
            "classdojo" -> when (action) {
                "class" -> "classdojo://class"
                "messages" -> "classdojo://messages"
                else -> null
            }
            "chegg" -> when (action) {
                "search" -> "chegg://search?q=${encode(param("query") ?: "")}"
                "textbooks" -> "chegg://textbooks"
                else -> null
            }
            "socratic" -> when (action) {
                "ask" -> "socratic://ask?q=${encode(param("query") ?: "")}"
                else -> null
            }

            // Gaming
            "minecraft" -> when (action) {
                "play" -> "minecraft://play"
                else -> null
            }
            "pubg_mobile" -> when (action) {
                "play" -> "pubgmobile://play"
                else -> null
            }
            "call_of_duty_mobile" -> when (action) {
                "play" -> "codm://play"
                else -> null
            }
            "genshin_impact" -> when (action) {
                "play" -> "genshinimpact://play"
                else -> null
            }
            "candy_crush" -> when (action) {
                "play" -> "candycrush://play"
                else -> null
            }
            "clash_of_clans" -> when (action) {
                "play" -> "clashofclans://play"
                else -> null
            }
            "clash_royale" -> when (action) {
                "play" -> "clashroyale://play"
                else -> null
            }
            "brawl_stars" -> when (action) {
                "play" -> "brawlstars://play"
                else -> null
            }
            "pokemon_go" -> when (action) {
                "play" -> "pokemongo://play"
                else -> null
            }
            "coin_master" -> when (action) {
                "play" -> "coinmaster://play"
                else -> null
            }

            // Banking (international)
            "n26" -> when (action) {
                "transfer" -> "n26://transfer"
                "spaces" -> "n26://spaces"
                "transactions" -> "n26://transactions"
                else -> null
            }
            "monzo" -> when (action) {
                "pay" -> "monzo://pay"
                "pots" -> "monzo://pots"
                "transactions" -> "monzo://transactions"
                else -> null
            }
            "nubank" -> when (action) {
                "pix" -> "nubank://pix"
                "transfer" -> "nubank://transfer"
                else -> null
            }
            "starling" -> when (action) {
                "pay" -> "starling://pay"
                "spaces" -> "starling://spaces"
                else -> null
            }

            // Car Rental
            "zipcar" -> when (action) {
                "search" -> "zipcar://search?location=${encode(param("location") ?: "")}"
                "reserve" -> "zipcar://reserve"
                else -> null
            }
            "hertz" -> when (action) {
                "search" -> "https://www.hertz.com/rentacar/reservation/?location=${encode(param("location") ?: "")}"
                else -> null
            }
            "enterprise" -> when (action) {
                "search" -> "https://www.enterprise.com/en/car-rental/locations/${encode(param("location") ?: "")}.html"
                else -> null
            }

            // VPN (more)
            "surfshark" -> when (action) {
                "connect" -> "surfshark://connect"
                "server" -> "surfshark://connect?country=${param("country") ?: ""}"
                else -> null
            }
            "cyberghost" -> when (action) {
                "connect" -> "cyberghost://connect"
                else -> null
            }

            // Printing
            "hp_smart" -> when (action) {
                "print" -> "hpsmart://print"
                "scan" -> "hpsmart://scan"
                else -> null
            }

            // Fitness (more)
            "nike_training" -> when (action) {
                "workout" -> "niketraining://workout"
                "plan" -> "niketraining://plan"
                "browse" -> "niketraining://browse"
                else -> null
            }
            "sweat" -> when (action) {
                "workout" -> "sweat://workout"
                "planner" -> "sweat://planner"
                else -> null
            }

            // Music Creation
            "bandlab" -> when (action) {
                "create" -> "bandlab://create"
                "feed" -> "bandlab://feed"
                else -> null
            }

            // Scanner & PDF
            "genius_scan" -> when (action) {
                "scan" -> "geniusscan://scan"
                else -> null
            }

            // Budgeting
            "rocket_money" -> when (action) {
                "bills" -> "rocketmoney://bills"
                "budgets" -> "rocketmoney://budgets"
                "subscriptions" -> "rocketmoney://subscriptions"
                else -> null
            }

            // Mental Health
            "cerebral" -> when (action) {
                "session" -> "cerebral://session"
                "prescriptions" -> "cerebral://prescriptions"
                else -> null
            }

            // Period Tracker
            "clue" -> when (action) {
                "track" -> "clue://track"
                "calendar" -> "clue://calendar"
                else -> null
            }

            // Home Security (more)
            "arlo" -> when (action) {
                "cameras" -> "arlo://cameras"
                "live" -> "arlo://live"
                else -> null
            }
            "blink" -> when (action) {
                "cameras" -> "blink://cameras"
                "live" -> "blink://live"
                else -> null
            }
            "simplisafe" -> when (action) {
                "arm" -> "simplisafe://arm"
                "cameras" -> "simplisafe://cameras"
                else -> null
            }
            "adt" -> when (action) {
                "arm" -> "adt://arm"
                "cameras" -> "adt://cameras"
                else -> null
            }

            // Grocery (more)
            "instacart_shopper" -> when (action) {
                "shop" -> "instacart-shopper://shop"
                else -> null
            }
            "shipt" -> when (action) {
                "shop" -> "shipt://shop"
                "search" -> "shipt://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "publix" -> when (action) {
                "list" -> "publix://shopping-list"
                "deals" -> "publix://deals"
                else -> null
            }
            "trader_joes" -> when (action) {
                "store" -> "traderjoes://store"
                else -> null
            }
            "safeway" -> when (action) {
                "deals" -> "safeway://deals"
                "list" -> "safeway://shopping-list"
                else -> null
            }

            // Loyalty & Finance
            "chime_credit" -> when (action) {
                "score" -> "chime://credit-score"
                else -> null
            }
            "stash" -> when (action) {
                "invest" -> "stash://invest"
                "portfolio" -> "stash://portfolio"
                else -> null
            }

            // Audiobooks
            "google_play_audiobooks" -> when (action) {
                "search" -> "googlebooks://search?q=${encode(param("query") ?: "")}"
                "library" -> "googlebooks://library"
                else -> null
            }

            // Horoscope
            "co_star" -> when (action) {
                "horoscope" -> "costar://horoscope"
                "chart" -> "costar://chart"
                else -> null
            }
            "the_pattern" -> when (action) {
                "today" -> "thepattern://today"
                else -> null
            }

            // Booking & Appointments
            "vagaro" -> when (action) {
                "book" -> "vagaro://book"
                "search" -> "vagaro://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "booksy" -> when (action) {
                "book" -> "booksy://book"
                "search" -> "booksy://search?q=${encode(param("query") ?: "")}"
                else -> null
            }

            // Kids Learning
            "abc_kids" -> when (action) {
                "learn" -> "abckids://learn"
                else -> null
            }
            "youtube_kids_app" -> when (action) {
                "search" -> "youtubekids://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "duolingo_abc" -> when (action) {
                "learn" -> "duolingoabc://learn"
                else -> null
            }

            // Surveys
            "google_opinion_rewards" -> when (action) {
                "surveys" -> "googleopinionrewards://surveys"
                else -> null
            }

            // Ride (more)
            "freenow" -> when (action) {
                "ride" -> "freenow://ride?destination=${encode(param("destination") ?: "")}"
                else -> null
            }
            "ola" -> when (action) {
                "ride" -> "olacabs://ride?destination=${encode(param("destination") ?: "")}"
                else -> null
            }
            "indriver" -> when (action) {
                "ride" -> "indriver://ride"
                else -> null
            }

            // Ticketing & Events (more)
            "ticketmaster" -> when (action) {
                "search" -> "ticketmaster://search?q=${encode(param("query") ?: "")}"
                "event" -> "ticketmaster://event/${param("id") ?: ""}"
                else -> null
            }
            "stubhub" -> when (action) {
                "search" -> "stubhub://search?q=${encode(param("query") ?: "")}"
                "event" -> "stubhub://event/${param("id") ?: ""}"
                else -> null
            }
            "seatgeek" -> when (action) {
                "search" -> "seatgeek://search?q=${encode(param("query") ?: "")}"
                "event" -> "seatgeek://event/${param("id") ?: ""}"
                else -> null
            }
            "dice" -> when (action) {
                "search" -> "dice://search?q=${encode(param("query") ?: "")}"
                else -> null
            }

            // Habit & Focus
            "focus_keeper" -> when (action) {
                "start" -> "focuskeeper://start"
                else -> null
            }
            "fabulous" -> when (action) {
                "routine" -> "fabulous://routine"
                else -> null
            }
            "productive" -> when (action) {
                "habits" -> "productive://habits"
                else -> null
            }

            // Photo Printing
            "shutterfly" -> when (action) {
                "upload" -> "shutterfly://upload"
                "prints" -> "shutterfly://prints"
                else -> null
            }
            "snapfish" -> when (action) {
                "upload" -> "snapfish://upload"
                "prints" -> "snapfish://prints"
                else -> null
            }

            // Pet Services (more)
            "wag" -> when (action) {
                "book" -> "wag://book"
                "walks" -> "wag://walks"
                else -> null
            }
            "petco" -> when (action) {
                "search" -> "petco://search?q=${encode(param("query") ?: "")}"
                "vet" -> "petco://vet"
                else -> null
            }

            // Communication (more)
            "telegram_x" -> when (action) {
                "chat" -> "telegramx://chat?user=${param("username") ?: ""}"
                else -> null
            }
            "imo" -> when (action) {
                "call" -> "imo://call"
                else -> null
            }
            "botim" -> when (action) {
                "call" -> "botim://call"
                else -> null
            }
            "zangi" -> when (action) {
                "call" -> "zangi://call"
                else -> null
            }

            // News Aggregator
            "smartnews" -> when (action) {
                "search" -> "smartnews://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "inshorts" -> when (action) {
                "read" -> "inshorts://read"
                else -> null
            }
            "ground_news" -> when (action) {
                "search" -> "groundnews://search?q=${encode(param("query") ?: "")}"
                else -> null
            }

            // Finance Tracker
            "personal_capital" -> when (action) {
                "dashboard" -> "personalcapital://dashboard"
                "investments" -> "personalcapital://investments"
                else -> null
            }

            // Coupons (more)
            "honey" -> when (action) {
                "deals" -> "honey://deals"
                "offers" -> "honey://offers"
                else -> null
            }
            "flipp" -> when (action) {
                "flyers" -> "flipp://flyers"
                "search" -> "flipp://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            else -> null
        }
    }

    private fun buildDeepLinkPart6(
        app: String,
        action: String,
        param: (String) -> String?,
        encode: (String) -> String
    ): String? {
        return when (app) {
            // Fast Food & Restaurants (more)
            "kfc" -> when (action) {
                "order" -> "kfc://order"
                "menu" -> "kfc://menu"
                "deals" -> "kfc://deals"
                else -> null
            }
            "wingstop" -> when (action) {
                "order" -> "wingstop://order"
                "menu" -> "wingstop://menu"
                else -> null
            }
            "sonic" -> when (action) {
                "order" -> "sonic://order"
                "deals" -> "sonic://deals"
                else -> null
            }
            "arbys" -> when (action) {
                "order" -> "arbys://order"
                "menu" -> "arbys://menu"
                "deals" -> "arbys://deals"
                else -> null
            }
            "panda_express" -> when (action) {
                "order" -> "pandaexpress://order"
                "menu" -> "pandaexpress://menu"
                else -> null
            }
            "buffalo_wild_wings" -> when (action) {
                "order" -> "bww://order"
                "menu" -> "bww://menu"
                "deals" -> "bww://deals"
                else -> null
            }
            "olive_garden" -> when (action) {
                "order" -> "olivegarden://order"
                "menu" -> "olivegarden://menu"
                "reservations" -> "olivegarden://reservations"
                else -> null
            }
            "ihop" -> when (action) {
                "order" -> "ihop://order"
                "menu" -> "ihop://menu"
                else -> null
            }
            "jack_in_the_box" -> when (action) {
                "order" -> "jackinthebox://order"
                "deals" -> "jackinthebox://deals"
                else -> null
            }
            "whataburger" -> when (action) {
                "order" -> "whataburger://order"
                "menu" -> "whataburger://menu"
                else -> null
            }
            "nandos" -> when (action) {
                "order" -> "nandos://order"
                "menu" -> "nandos://menu"
                "rewards" -> "nandos://rewards"
                else -> null
            }
            "five_guys" -> when (action) {
                "order" -> "fiveguys://order"
                "menu" -> "fiveguys://menu"
                else -> null
            }
            "cookpad" -> when (action) {
                "search" -> "cookpad://search?q=${encode(param("query") ?: "")}"
                "recipe" -> "cookpad://recipe/${param("id") ?: ""}"
                "trending" -> "cookpad://trending"
                else -> null
            }
            "allrecipes" -> when (action) {
                "search" -> "allrecipes://search?q=${encode(param("query") ?: "")}"
                "recipe" -> "allrecipes://recipe/${param("id") ?: ""}"
                else -> null
            }
            "tasty" -> when (action) {
                "search" -> "tasty://search?q=${encode(param("query") ?: "")}"
                "trending" -> "tasty://trending"
                else -> null
            }

            // Shopping & Retail (more)
            "zappos" -> when (action) {
                "search" -> "zappos://product/search?term=${encode(param("query") ?: "")}"
                "product" -> "zappos://product/${param("id") ?: ""}"
                else -> null
            }
            "footlocker" -> when (action) {
                "search" -> "footlocker://search?q=${encode(param("query") ?: "")}"
                "product" -> "footlocker://product/${param("id") ?: ""}"
                else -> null
            }
            "kohls" -> when (action) {
                "search" -> "kohls://search?q=${encode(param("query") ?: "")}"
                "deals" -> "kohls://deals"
                "coupons" -> "kohls://coupons"
                else -> null
            }
            "jcpenney" -> when (action) {
                "search" -> "jcpenney://search?q=${encode(param("query") ?: "")}"
                "deals" -> "jcpenney://deals"
                "coupons" -> "jcpenney://coupons"
                else -> null
            }
            "bath_body_works" -> when (action) {
                "search" -> "bbw://search?q=${encode(param("query") ?: "")}"
                "deals" -> "bbw://deals"
                else -> null
            }
            "urban_outfitters" -> when (action) {
                "search" -> "urbanoutfitters://search?q=${encode(param("query") ?: "")}"
                "product" -> "urbanoutfitters://product/${param("id") ?: ""}"
                else -> null
            }
            "forever_21" -> when (action) {
                "search" -> "forever21://search?q=${encode(param("query") ?: "")}"
                "deals" -> "forever21://deals"
                else -> null
            }
            "lululemon" -> when (action) {
                "search" -> "lululemon://search?q=${encode(param("query") ?: "")}"
                "product" -> "lululemon://product/${param("id") ?: ""}"
                else -> null
            }
            "under_armour" -> when (action) {
                "search" -> "underarmour://search?q=${encode(param("query") ?: "")}"
                "product" -> "underarmour://product/${param("id") ?: ""}"
                else -> null
            }
            "crocs" -> when (action) {
                "search" -> "crocs://search?q=${encode(param("query") ?: "")}"
                "product" -> "crocs://product/${param("id") ?: ""}"
                else -> null
            }
            "new_balance" -> when (action) {
                "search" -> "newbalance://search?q=${encode(param("query") ?: "")}"
                "product" -> "newbalance://product/${param("id") ?: ""}"
                else -> null
            }
            "puma" -> when (action) {
                "search" -> "puma://search?q=${encode(param("query") ?: "")}"
                "product" -> "puma://product/${param("id") ?: ""}"
                else -> null
            }
            "converse" -> when (action) {
                "search" -> "converse://search?q=${encode(param("query") ?: "")}"
                "product" -> "converse://product/${param("id") ?: ""}"
                else -> null
            }
            "tjmaxx" -> when (action) {
                "search" -> "tjmaxx://search?q=${encode(param("query") ?: "")}"
                "deals" -> "tjmaxx://deals"
                else -> null
            }
            "dicks_sporting" -> when (action) {
                "search" -> "dickssportinggoods://search?q=${encode(param("query") ?: "")}"
                "product" -> "dickssportinggoods://product/${param("id") ?: ""}"
                else -> null
            }
            "pottery_barn" -> when (action) {
                "search" -> "potterybarn://search?q=${encode(param("query") ?: "")}"
                "product" -> "potterybarn://product/${param("id") ?: ""}"
                else -> null
            }
            "anthropologie" -> when (action) {
                "search" -> "anthropologie://search?q=${encode(param("query") ?: "")}"
                "product" -> "anthropologie://product/${param("id") ?: ""}"
                else -> null
            }
            "west_elm" -> when (action) {
                "search" -> "westelm://search?q=${encode(param("query") ?: "")}"
                "product" -> "westelm://product/${param("id") ?: ""}"
                else -> null
            }
            "aldo" -> when (action) {
                "search" -> "aldo://search?q=${encode(param("query") ?: "")}"
                "product" -> "aldo://product/${param("id") ?: ""}"
                else -> null
            }

            // Finance & Banking (more)
            "klarna" -> when (action) {
                "open" -> "klarna://"
                "pay" -> "klarna://pay"
                "history" -> "klarna://history"
                else -> null
            }
            "afterpay" -> when (action) {
                "open" -> "afterpay://"
                "shop" -> "afterpay://shop"
                "orders" -> "afterpay://orders"
                else -> null
            }
            "affirm" -> when (action) {
                "open" -> "affirm://"
                "payments" -> "affirm://payments"
                else -> null
            }
            "ally_bank" -> when (action) {
                "open" -> "allybank://"
                "accounts" -> "allybank://accounts"
                "transfer" -> "allybank://transfer"
                else -> null
            }
            "discover" -> when (action) {
                "open" -> "discover://"
                "account" -> "discover://account"
                "rewards" -> "discover://rewards"
                else -> null
            }
            "barclays" -> when (action) {
                "open" -> "barclays://"
                "accounts" -> "barclays://accounts"
                else -> null
            }
            "hsbc" -> when (action) {
                "open" -> "hsbc://"
                "accounts" -> "hsbc://accounts"
                else -> null
            }
            "payoneer" -> when (action) {
                "open" -> "payoneer://"
                "balance" -> "payoneer://balance"
                else -> null
            }
            "marcus" -> when (action) {
                "open" -> "marcus://"
                "savings" -> "marcus://savings"
                else -> null
            }
            "root_insurance" -> when (action) {
                "open" -> "root://"
                "quote" -> "root://quote"
                "policy" -> "root://policy"
                else -> null
            }

            // Streaming & TV (more)
            "sling_tv" -> when (action) {
                "watch" -> "sling://watch"
                "guide" -> "sling://guide"
                "search" -> "sling://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "fubo" -> when (action) {
                "watch" -> "fubo://watch"
                "guide" -> "fubo://guide"
                "sports" -> "fubo://sports"
                else -> null
            }
            "philo" -> when (action) {
                "watch" -> "philo://watch"
                "guide" -> "philo://guide"
                else -> null
            }
            "dazn" -> when (action) {
                "watch" -> "dazn://watch"
                "schedule" -> "dazn://schedule"
                "search" -> "dazn://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "espn_plus" -> when (action) {
                "watch" -> "sportscenter://watch"
                "scores" -> "sportscenter://scores"
                "search" -> "sportscenter://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "britbox" -> when (action) {
                "watch" -> "britbox://watch"
                "search" -> "britbox://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "rumble" -> when (action) {
                "watch" -> "rumble://video/${param("id") ?: ""}"
                "search" -> "rumble://search?q=${encode(param("query") ?: "")}"
                "channel" -> "rumble://channel/${param("name") ?: ""}"
                else -> null
            }
            "viki" -> when (action) {
                "watch" -> "viki://video/${param("id") ?: ""}"
                "search" -> "viki://search?q=${encode(param("query") ?: "")}"
                "explore" -> "viki://explore"
                else -> null
            }
            "kick" -> when (action) {
                "watch" -> "kick://channel/${param("name") ?: ""}"
                "search" -> "kick://search?q=${encode(param("query") ?: "")}"
                else -> null
            }

            // Communication (more)
            "kik" -> when (action) {
                "chat" -> "kik://chat/${param("username") ?: ""}"
                "group" -> "kik://group/${param("id") ?: ""}"
                "scan" -> "kik://scan"
                else -> null
            }
            "threema" -> when (action) {
                "chat" -> "threema://compose?id=${param("id") ?: ""}"
                "contact" -> "threema://contact/${param("id") ?: ""}"
                else -> null
            }
            "element" -> when (action) {
                "room" -> "element://room/${param("id") ?: ""}"
                "user" -> "element://user/${param("id") ?: ""}"
                "open" -> "element://"
                else -> null
            }
            "guilded" -> when (action) {
                "server" -> "guilded://server/${param("id") ?: ""}"
                "channel" -> "guilded://channel/${param("id") ?: ""}"
                "open" -> "guilded://"
                else -> null
            }
            "telegram_premium" -> when (action) {
                "chat" -> "tg://resolve?domain=${param("username") ?: ""}"
                "open" -> "tg://"
                else -> null
            }

            // Travel & Transport (more)
            "trivago" -> when (action) {
                "search" -> "trivago://search?destination=${encode(param("destination") ?: "")}"
                "hotel" -> "trivago://hotel/${param("id") ?: ""}"
                else -> null
            }
            "rome2rio" -> when (action) {
                "search" -> "rome2rio://search?from=${encode(param("origin") ?: "")}&to=${encode(param("destination") ?: "")}"
                else -> null
            }
            "maps_me" -> when (action) {
                "search" -> "mapsme://search?q=${encode(param("query") ?: "")}"
                "navigate" -> "mapsme://route?end_lat=${param("latitude") ?: ""}&end_lon=${param("longitude") ?: ""}"
                "directions" -> "mapsme://route?end_lat=${param("latitude") ?: ""}&end_lon=${param("longitude") ?: ""}"
                else -> null
            }
            "omio" -> when (action) {
                "search" -> "omio://search?from=${encode(param("origin") ?: "")}&to=${encode(param("destination") ?: "")}"
                else -> null
            }
            "flixbus" -> when (action) {
                "search" -> "flixbus://search?from=${encode(param("origin") ?: "")}&to=${encode(param("destination") ?: "")}"
                "booking" -> "flixbus://booking/${param("id") ?: ""}"
                else -> null
            }
            "amtrak" -> when (action) {
                "search" -> "amtrak://search?from=${encode(param("origin") ?: "")}&to=${encode(param("destination") ?: "")}"
                "trip" -> "amtrak://trip/${param("id") ?: ""}"
                "stations" -> "amtrak://stations"
                else -> null
            }
            "greyhound" -> when (action) {
                "search" -> "greyhound://search?from=${encode(param("origin") ?: "")}&to=${encode(param("destination") ?: "")}"
                "trip" -> "greyhound://trip/${param("id") ?: ""}"
                else -> null
            }

            // Health & Fitness (more)
            "medisafe" -> when (action) {
                "reminders" -> "medisafe://reminders"
                "add_medication" -> "medisafe://add"
                "open" -> "medisafe://"
                else -> null
            }
            "lose_it" -> when (action) {
                "log_food" -> "loseit://log/food"
                "log_weight" -> "loseit://log/weight"
                "dashboard" -> "loseit://dashboard"
                else -> null
            }
            "zero_fasting" -> when (action) {
                "start_fast" -> "zero://start"
                "end_fast" -> "zero://end"
                "history" -> "zero://history"
                else -> null
            }
            "pacer" -> when (action) {
                "steps" -> "pacer://steps"
                "walk" -> "pacer://walk"
                "history" -> "pacer://history"
                else -> null
            }
            "ada_health" -> when (action) {
                "assessment" -> "ada://assessment"
                "symptoms" -> "ada://symptoms"
                "open" -> "ada://"
                else -> null
            }

            // Freelance & Jobs (more)
            "upwork" -> when (action) {
                "search" -> "upwork://search?q=${encode(param("query") ?: "")}"
                "jobs" -> "upwork://jobs"
                "messages" -> "upwork://messages"
                "proposals" -> "upwork://proposals"
                else -> null
            }
            "fiverr" -> when (action) {
                "search" -> "fiverr://search?q=${encode(param("query") ?: "")}"
                "gig" -> "fiverr://gig/${param("id") ?: ""}"
                "inbox" -> "fiverr://inbox"
                "orders" -> "fiverr://orders"
                else -> null
            }
            "monster" -> when (action) {
                "search" -> "monster://search?q=${encode(param("query") ?: "")}"
                "jobs" -> "monster://jobs"
                "applied" -> "monster://applied"
                else -> null
            }
            "snagajob" -> when (action) {
                "search" -> "snagajob://search?q=${encode(param("query") ?: "")}"
                "jobs" -> "snagajob://jobs"
                else -> null
            }
            "toptal" -> when (action) {
                "open" -> "toptal://"
                "talent" -> "toptal://talent"
                else -> null
            }

            // Sports (more)
            "yahoo_sports" -> when (action) {
                "scores" -> "ysp://scores"
                "team" -> "ysp://team/${param("id") ?: ""}"
                "news" -> "ysp://news"
                else -> null
            }
            "bleacher_report" -> when (action) {
                "article" -> "teamstream://article/${param("id") ?: ""}"
                "scores" -> "teamstream://scores"
                "team" -> "teamstream://team/${param("id") ?: ""}"
                else -> null
            }
            "sofascore" -> when (action) {
                "match" -> "sofascore://match/${param("id") ?: ""}"
                "team" -> "sofascore://team/${param("id") ?: ""}"
                "scores" -> "sofascore://scores"
                else -> null
            }
            "fotmob" -> when (action) {
                "match" -> "fotmob://match/${param("id") ?: ""}"
                "team" -> "fotmob://team/${param("id") ?: ""}"
                "league" -> "fotmob://league/${param("id") ?: ""}"
                else -> null
            }
            "livescore" -> when (action) {
                "scores" -> "livescore://scores"
                "match" -> "livescore://match/${param("id") ?: ""}"
                else -> null
            }
            "onefootball" -> when (action) {
                "match" -> "onefootball://match/${param("id") ?: ""}"
                "team" -> "onefootball://team/${param("id") ?: ""}"
                "news" -> "onefootball://news"
                else -> null
            }
            "garmin_connect" -> when (action) {
                "activities" -> "gcm://activities"
                "activity" -> "gcm://activity/${param("id") ?: ""}"
                "dashboard" -> "gcm://dashboard"
                else -> null
            }
            "whoop" -> when (action) {
                "strain" -> "whoop://strain"
                "recovery" -> "whoop://recovery"
                "sleep" -> "whoop://sleep"
                else -> null
            }

            // Music (more)
            "anghami" -> when (action) {
                "play" -> "anghami://play?song=${param("id") ?: ""}"
                "search" -> "anghami://search?q=${encode(param("query") ?: "")}"
                "playlist" -> "anghami://playlist/${param("id") ?: ""}"
                else -> null
            }
            "boomplay" -> when (action) {
                "play" -> "boomplay://play?id=${param("id") ?: ""}"
                "search" -> "boomplay://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "gaana" -> when (action) {
                "play" -> "gaana://play?id=${param("id") ?: ""}"
                "search" -> "gaana://search?q=${encode(param("query") ?: "")}"
                "playlist" -> "gaana://playlist/${param("id") ?: ""}"
                else -> null
            }
            "jiosaavn" -> when (action) {
                "play" -> "jiosaavn://play?id=${param("id") ?: ""}"
                "search" -> "jiosaavn://search?q=${encode(param("query") ?: "")}"
                "playlist" -> "jiosaavn://playlist/${param("id") ?: ""}"
                else -> null
            }

            // Photography & Editing (more)
            "b612" -> when (action) {
                "camera" -> "b612://camera"
                "effects" -> "b612://effects"
                else -> null
            }
            "snow" -> when (action) {
                "camera" -> "snow://camera"
                "effects" -> "snow://effects"
                else -> null
            }
            "prequel" -> when (action) {
                "camera" -> "prequel://camera"
                "effects" -> "prequel://effects"
                "edit" -> "prequel://edit"
                else -> null
            }
            "afterlight" -> when (action) {
                "edit" -> "afterlight://edit"
                "camera" -> "afterlight://camera"
                else -> null
            }
            "darkroom" -> when (action) {
                "edit" -> "darkroom://edit"
                "open" -> "darkroom://"
                else -> null
            }
            "retrica" -> when (action) {
                "camera" -> "retrica://camera"
                "filters" -> "retrica://filters"
                else -> null
            }
            "foodie_camera" -> when (action) {
                "camera" -> "foodie://camera"
                "filters" -> "foodie://filters"
                else -> null
            }

            // Kids & Education (more)
            "scratch" -> when (action) {
                "project" -> "https://scratch.mit.edu/projects/${param("id") ?: ""}"
                "explore" -> "https://scratch.mit.edu/explore/projects/all"
                "create" -> "https://scratch.mit.edu/projects/editor"
                else -> null
            }
            "tynker" -> when (action) {
                "open" -> "tynker://"
                "course" -> "tynker://course/${param("id") ?: ""}"
                else -> null
            }
            "prodigy_math" -> when (action) {
                "play" -> "prodigy://play"
                "open" -> "prodigy://"
                else -> null
            }
            "epic_reading" -> when (action) {
                "read" -> "epic://read"
                "book" -> "epic://book/${param("id") ?: ""}"
                "explore" -> "epic://explore"
                else -> null
            }
            "toca_boca" -> when (action) {
                "play" -> "tocalifeworld://play"
                "open" -> "tocalifeworld://"
                else -> null
            }
            "lego" -> when (action) {
                "open" -> "lego://"
                "sets" -> "lego://sets"
                else -> null
            }

            // Productivity (more)
            "airtable" -> when (action) {
                "base" -> "airtable://base/${param("id") ?: ""}"
                "view" -> "airtable://view/${param("id") ?: ""}"
                "open" -> "airtable://"
                else -> null
            }
            "coda" -> when (action) {
                "doc" -> "coda://doc/${param("id") ?: ""}"
                "open" -> "coda://"
                else -> null
            }
            "craft" -> when (action) {
                "doc" -> "craftdocs://doc/${param("id") ?: ""}"
                "open" -> "craftdocs://"
                "new" -> "craftdocs://new"
                else -> null
            }
            "superhuman" -> when (action) {
                "compose" -> "superhuman://compose?to=${encode(param("email") ?: "")}"
                "inbox" -> "superhuman://inbox"
                "open" -> "superhuman://"
                else -> null
            }
            "fantastical" -> when (action) {
                "add" -> "x-fantastical3://parse?sentence=${encode(param("event") ?: "")}"
                "show" -> "x-fantastical3://show/calendar"
                "today" -> "x-fantastical3://show/today"
                else -> null
            }
            "things_3" -> when (action) {
                "add" -> "things:///add?title=${encode(param("title") ?: "")}"
                "show" -> "things:///show?id=${param("list") ?: "inbox"}"
                "today" -> "things:///show?id=today"
                else -> null
            }

            // Utilities (more)
            "speedtest" -> when (action) {
                "run" -> "speedtest://speedtest"
                "results" -> "speedtest://results"
                else -> null
            }
            "malwarebytes" -> when (action) {
                "scan" -> "malwarebytes://scan"
                "open" -> "malwarebytes://"
                else -> null
            }
            "hotspot_shield" -> when (action) {
                "connect" -> "hotspotshield://connect"
                "disconnect" -> "hotspotshield://disconnect"
                else -> null
            }

            // Real Estate (more)
            "hotpads" -> when (action) {
                "search" -> "hotpads://search?q=${encode(param("query") ?: "")}"
                "listing" -> "hotpads://listing/${param("id") ?: ""}"
                else -> null
            }
            "compass_real_estate" -> when (action) {
                "search" -> "compass://search?q=${encode(param("query") ?: "")}"
                "listing" -> "compass://listing/${param("id") ?: ""}"
                else -> null
            }
            "opendoor" -> when (action) {
                "search" -> "opendoor://search?q=${encode(param("query") ?: "")}"
                "listing" -> "opendoor://listing/${param("id") ?: ""}"
                "sell" -> "opendoor://sell"
                else -> null
            }

            // Automotive (more)
            "carfax" -> when (action) {
                "search" -> "carfax://search?q=${encode(param("query") ?: "")}"
                "report" -> "carfax://report/${param("vin") ?: ""}"
                else -> null
            }
            "cars_com" -> when (action) {
                "search" -> "carscom://search?q=${encode(param("query") ?: "")}"
                "listing" -> "carscom://listing/${param("id") ?: ""}"
                else -> null
            }
            "edmunds" -> when (action) {
                "search" -> "edmunds://search?q=${encode(param("query") ?: "")}"
                "vehicle" -> "edmunds://vehicle/${param("id") ?: ""}"
                "appraise" -> "edmunds://appraise"
                else -> null
            }
            "kelley_blue_book" -> when (action) {
                "search" -> "kbb://search?q=${encode(param("query") ?: "")}"
                "value" -> "kbb://value"
                "vehicle" -> "kbb://vehicle/${param("id") ?: ""}"
                else -> null
            }

            // News (more)
            "inoreader" -> when (action) {
                "feed" -> "inoreader://feed"
                "article" -> "inoreader://article/${param("id") ?: ""}"
                "search" -> "inoreader://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "yahoo_news" -> when (action) {
                "article" -> "yahoo://article/${param("id") ?: ""}"
                "search" -> "yahoo://search?q=${encode(param("query") ?: "")}"
                "trending" -> "yahoo://trending"
                else -> null
            }
            "news_republic" -> when (action) {
                "article" -> "newsrepublic://article/${param("id") ?: ""}"
                "trending" -> "newsrepublic://trending"
                else -> null
            }
            "ap_news" -> when (action) {
                "article" -> "apnews://article/${param("id") ?: ""}"
                "top_stories" -> "apnews://top"
                "search" -> "apnews://search?q=${encode(param("query") ?: "")}"
                else -> null
            }

            else -> null
        }
    }

    // ========================================================================
    // Part 7: Food/Dining, Streaming, Games, Finance, Shopping, Education
    // ========================================================================
    private fun buildDeepLinkPart7(
        app: String,
        action: String,
        param: (String) -> String?,
        encode: (String) -> String
    ): String? {
        return when (app) {
            // Food & Dining
            "opentable" -> when (action) {
                "search" -> "https://www.opentable.com/s?term=${encode(param("query") ?: "")}"
                "restaurant" -> "https://www.opentable.com/r/${param("id") ?: ""}"
                "reserve" -> "https://www.opentable.com/r/${param("id") ?: ""}?covers=${param("party_size") ?: "2"}"
                else -> null
            }
            "resy" -> when (action) {
                "search" -> "https://resy.com/cities?query=${encode(param("query") ?: "")}"
                "restaurant" -> "https://resy.com/cities/ny/${param("slug") ?: ""}"
                else -> null
            }
            "deliveroo" -> when (action) {
                "search" -> "https://deliveroo.co.uk/restaurants/search?query=${encode(param("query") ?: "")}"
                "restaurant" -> "https://deliveroo.co.uk/menu/${param("slug") ?: ""}"
                "order" -> "https://deliveroo.co.uk/menu/${param("slug") ?: ""}"
                else -> null
            }
            "foodpanda" -> when (action) {
                "search" -> "https://www.foodpanda.com/restaurants?q=${encode(param("query") ?: "")}"
                "restaurant" -> "https://www.foodpanda.com/restaurant/${param("id") ?: ""}"
                else -> null
            }
            "just_eat" -> when (action) {
                "search" -> "https://www.just-eat.co.uk/area/${encode(param("postcode") ?: "")}"
                "restaurant" -> "https://www.just-eat.co.uk/restaurants-${param("slug") ?: ""}"
                else -> null
            }
            "seamless" -> when (action) {
                "search" -> "https://www.seamless.com/search/${encode(param("query") ?: "")}"
                "restaurant" -> "https://www.seamless.com/menu/${param("slug") ?: ""}"
                else -> null
            }
            "caviar" -> when (action) {
                "search" -> "https://www.trycaviar.com/search?term=${encode(param("query") ?: "")}"
                "restaurant" -> "https://www.trycaviar.com/${param("slug") ?: ""}"
                else -> null
            }
            "slice" -> when (action) {
                "search" -> "https://slicelife.com/restaurants?q=${encode(param("query") ?: "")}"
                "restaurant" -> "https://slicelife.com/restaurants/${param("id") ?: ""}"
                "order" -> "https://slicelife.com/restaurants/${param("id") ?: ""}/menu"
                else -> null
            }
            "shake_shack" -> when (action) {
                "menu" -> "https://www.shakeshack.com/menu"
                "locations" -> "https://www.shakeshack.com/locations"
                "order" -> "https://order.shakeshack.com"
                else -> null
            }
            "little_caesars" -> when (action) {
                "menu" -> "https://littlecaesars.com/en-us/menu"
                "order" -> "https://littlecaesars.com/en-us/order/pickup"
                "locations" -> "https://littlecaesars.com/en-us/store-locator"
                else -> null
            }
            "jersey_mikes" -> when (action) {
                "menu" -> "https://www.jerseymikes.com/menu"
                "order" -> "https://www.jerseymikes.com/order"
                "locations" -> "https://www.jerseymikes.com/locations"
                else -> null
            }
            "crumbl" -> when (action) {
                "menu" -> "https://crumblcookies.com/menu"
                "locations" -> "https://crumblcookies.com/stores"
                "order" -> "https://crumblcookies.com/order"
                else -> null
            }
            "sweetgreen" -> when (action) {
                "menu" -> "https://www.sweetgreen.com/menu"
                "order" -> "https://order.sweetgreen.com"
                "locations" -> "https://www.sweetgreen.com/locations"
                else -> null
            }
            "cava" -> when (action) {
                "menu" -> "https://cava.com/menu"
                "order" -> "https://order.cava.com"
                "locations" -> "https://cava.com/locations"
                else -> null
            }
            "culvers" -> when (action) {
                "menu" -> "https://www.culvers.com/menu"
                "locations" -> "https://www.culvers.com/locator/view-all-locations"
                "flavor_of_day" -> "https://www.culvers.com/flavor-of-the-day"
                else -> null
            }
            "raising_canes" -> when (action) {
                "menu" -> "https://www.raisingcanes.com/menu"
                "locations" -> "https://www.raisingcanes.com/find-a-location"
                else -> null
            }

            // Streaming & Entertainment
            "vimeo" -> when (action) {
                "video" -> "vimeo://video/${param("id") ?: ""}"
                "search" -> "vimeo://search?q=${encode(param("query") ?: "")}"
                "channel" -> "vimeo://channels/${param("id") ?: ""}"
                "user" -> "vimeo://users/${param("id") ?: ""}"
                else -> null
            }
            "bilibili" -> when (action) {
                "video" -> "bilibili://video/${param("id") ?: ""}"
                "search" -> "bilibili://search?keyword=${encode(param("query") ?: "")}"
                "user" -> "bilibili://space/${param("id") ?: ""}"
                "live" -> "bilibili://live/${param("room_id") ?: ""}"
                else -> null
            }
            "iqiyi" -> when (action) {
                "video" -> "iqiyi://video/${param("id") ?: ""}"
                "search" -> "iqiyi://search?keyword=${encode(param("query") ?: "")}"
                else -> null
            }
            "viu" -> when (action) {
                "video" -> "https://www.viu.com/ott/watch/${param("id") ?: ""}"
                "search" -> "https://www.viu.com/ott/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "zee5" -> when (action) {
                "video" -> "zee5://content/${param("id") ?: ""}"
                "search" -> "zee5://search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "jio_cinema" -> when (action) {
                "video" -> "https://www.jiocinema.com/watch/${param("id") ?: ""}"
                "search" -> "https://www.jiocinema.com/search/${encode(param("query") ?: "")}"
                else -> null
            }
            "sonyliv" -> when (action) {
                "video" -> "https://www.sonyliv.com/shows/${param("id") ?: ""}"
                "search" -> "https://www.sonyliv.com/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "bigo_live" -> when (action) {
                "live" -> "https://www.bigo.tv/${param("user_id") ?: ""}"
                "search" -> "https://www.bigo.tv/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "likee" -> when (action) {
                "video" -> "https://likee.video/@${param("user") ?: ""}/${param("id") ?: ""}"
                "profile" -> "https://likee.video/@${param("user") ?: ""}"
                else -> null
            }
            "kwai" -> when (action) {
                "video" -> "https://www.kwai.com/video/${param("id") ?: ""}"
                "profile" -> "https://www.kwai.com/@${param("user") ?: ""}"
                else -> null
            }
            "triller" -> when (action) {
                "video" -> "https://triller.co/v/${param("id") ?: ""}"
                "profile" -> "https://triller.co/@${param("user") ?: ""}"
                else -> null
            }
            "caffeine" -> when (action) {
                "live" -> "https://www.caffeine.tv/${param("user") ?: ""}"
                else -> null
            }

            // Games
            "chess_com" -> when (action) {
                "play" -> "https://www.chess.com/play/online"
                "puzzle" -> "https://www.chess.com/puzzles"
                "profile" -> "https://www.chess.com/member/${param("username") ?: ""}"
                "learn" -> "https://www.chess.com/lessons"
                else -> null
            }
            "lichess" -> when (action) {
                "play" -> "https://lichess.org/"
                "puzzle" -> "https://lichess.org/training"
                "profile" -> "https://lichess.org/@/${param("username") ?: ""}"
                "game" -> "https://lichess.org/${param("id") ?: ""}"
                "tv" -> "https://lichess.org/tv"
                else -> null
            }
            "wordle" -> when (action) {
                "play" -> "https://www.nytimes.com/games/wordle/index.html"
                else -> null
            }

            // Finance & Banking
            "n26" -> when (action) {
                "open" -> "n26://main"
                "transfer" -> "n26://transfer"
                else -> null
            }
            "varo" -> when (action) {
                "open" -> "https://www.varomoney.com"
                "transfer" -> "https://www.varomoney.com/transfer"
                else -> null
            }
            "dave" -> when (action) {
                "open" -> "https://dave.com"
                "advance" -> "https://dave.com/advance"
                else -> null
            }
            "greenlight" -> when (action) {
                "open" -> "https://www.greenlight.com"
                else -> null
            }
            "step" -> when (action) {
                "open" -> "https://step.com"
                else -> null
            }
            "wealthfront" -> when (action) {
                "open" -> "https://www.wealthfront.com"
                "invest" -> "https://www.wealthfront.com/investing"
                "cash" -> "https://www.wealthfront.com/cash"
                else -> null
            }
            "betterment" -> when (action) {
                "open" -> "https://www.betterment.com"
                "invest" -> "https://www.betterment.com/investing"
                else -> null
            }
            "m1_finance" -> when (action) {
                "open" -> "https://www.m1finance.com"
                "invest" -> "https://www.m1finance.com/invest"
                "borrow" -> "https://www.m1finance.com/borrow"
                else -> null
            }

            // Shopping & Marketplace
            "stockx" -> when (action) {
                "search" -> "https://stockx.com/search?s=${encode(param("query") ?: "")}"
                "product" -> "https://stockx.com/${param("slug") ?: ""}"
                "trending" -> "https://stockx.com/trending-sneakers"
                else -> null
            }
            "goat" -> when (action) {
                "search" -> "https://www.goat.com/search?query=${encode(param("query") ?: "")}"
                "product" -> "https://www.goat.com/sneakers/${param("slug") ?: ""}"
                else -> null
            }
            "grailed" -> when (action) {
                "search" -> "https://www.grailed.com/shop?query=${encode(param("query") ?: "")}"
                "listing" -> "https://www.grailed.com/listings/${param("id") ?: ""}"
                "designer" -> "https://www.grailed.com/designers/${param("slug") ?: ""}"
                else -> null
            }
            "reverb" -> when (action) {
                "search" -> "https://reverb.com/marketplace?query=${encode(param("query") ?: "")}"
                "listing" -> "https://reverb.com/item/${param("id") ?: ""}"
                "shop" -> "https://reverb.com/shop/${param("slug") ?: ""}"
                else -> null
            }
            "whatnot" -> when (action) {
                "live" -> "https://www.whatnot.com/live/${param("id") ?: ""}"
                "search" -> "https://www.whatnot.com/search?q=${encode(param("query") ?: "")}"
                "category" -> "https://www.whatnot.com/category/${param("slug") ?: ""}"
                else -> null
            }
            "decluttr" -> when (action) {
                "sell" -> "https://www.decluttr.com/sell"
                "buy" -> "https://www.decluttr.com/buy"
                "search" -> "https://www.decluttr.com/buy?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "dollar_tree" -> when (action) {
                "search" -> "https://www.dollartree.com/searchresults?Ntt=${encode(param("query") ?: "")}"
                "locations" -> "https://www.dollartree.com/locations"
                else -> null
            }
            "dollar_general" -> when (action) {
                "search" -> "https://www.dollargeneral.com/search?q=${encode(param("query") ?: "")}"
                "coupons" -> "https://www.dollargeneral.com/coupons"
                "locations" -> "https://www.dollargeneral.com/store-locator"
                else -> null
            }
            "burlington" -> when (action) {
                "search" -> "https://www.burlington.com/search?q=${encode(param("query") ?: "")}"
                "locations" -> "https://www.burlington.com/stores"
                else -> null
            }
            "big_lots" -> when (action) {
                "search" -> "https://www.biglots.com/search?q=${encode(param("query") ?: "")}"
                "locations" -> "https://www.biglots.com/store-locator"
                "deals" -> "https://www.biglots.com/deals"
                else -> null
            }

            // Education
            "kahoot" -> when (action) {
                "play" -> "https://kahoot.it/"
                "create" -> "https://create.kahoot.it/"
                "discover" -> "https://create.kahoot.it/discover"
                else -> null
            }
            "blinkist" -> when (action) {
                "book" -> "blinkist://book/${param("id") ?: ""}"
                "search" -> "https://www.blinkist.com/en/nc/search?q=${encode(param("query") ?: "")}"
                "categories" -> "https://www.blinkist.com/en/nc/categories"
                else -> null
            }
            "brilliant" -> when (action) {
                "course" -> "https://brilliant.org/courses/${param("slug") ?: ""}"
                "daily" -> "https://brilliant.org/daily-problems/"
                "home" -> "https://brilliant.org/home/"
                else -> null
            }
            "codecademy" -> when (action) {
                "course" -> "https://www.codecademy.com/learn/${param("slug") ?: ""}"
                "catalog" -> "https://www.codecademy.com/catalog"
                "search" -> "https://www.codecademy.com/search?query=${encode(param("query") ?: "")}"
                else -> null
            }
            "sololearn" -> when (action) {
                "course" -> "https://www.sololearn.com/learning/${param("slug") ?: ""}"
                "profile" -> "https://www.sololearn.com/profile/${param("id") ?: ""}"
                else -> null
            }
            "mimo" -> when (action) {
                "course" -> "https://getmimo.com/courses/${param("slug") ?: ""}"
                else -> null
            }
            "elevate" -> when (action) {
                "open" -> "https://www.elevateapp.com"
                "train" -> "https://www.elevateapp.com"
                else -> null
            }
            "lumosity" -> when (action) {
                "open" -> "https://www.lumosity.com"
                "train" -> "https://www.lumosity.com/brain-games"
                else -> null
            }
            "chegg_study" -> when (action) {
                "search" -> "https://www.chegg.com/homework-help/search?q=${encode(param("query") ?: "")}"
                "textbook" -> "https://www.chegg.com/textbooks/${param("isbn") ?: ""}"
                else -> null
            }
            "wolfram_alpha" -> when (action) {
                "query" -> "https://www.wolframalpha.com/input?i=${encode(param("query") ?: "")}"
                else -> null
            }
            "symbolab" -> when (action) {
                "solve" -> "https://www.symbolab.com/solver/step-by-step/${encode(param("equation") ?: "")}"
                else -> null
            }
            "mathway" -> when (action) {
                "solve" -> "https://www.mathway.com/popular-problems"
                else -> null
            }
            "grammarly" -> when (action) {
                "open" -> "https://app.grammarly.com"
                "new_doc" -> "https://app.grammarly.com/docs/new"
                else -> null
            }

            else -> null
        }
    }

    // ========================================================================
    // Part 8: Health, Travel, Communication, Music, Sports, Utilities,
    //         Parenting, Smart Home, Meditation, Podcasts, Crypto, Work, VPN, Weather
    // ========================================================================
    private fun buildDeepLinkPart8(
        app: String,
        action: String,
        param: (String) -> String?,
        encode: (String) -> String
    ): String? {
        return when (app) {
            // Health & Wellness
            "bettersleep" -> when (action) {
                "open" -> "https://www.bettersleep.com"
                "sounds" -> "https://www.bettersleep.com/sounds"
                else -> null
            }
            "mindbody" -> when (action) {
                "search" -> "https://www.mindbodyonline.com/explore?q=${encode(param("query") ?: "")}"
                "class" -> "https://www.mindbodyonline.com/explore/class/${param("id") ?: ""}"
                else -> null
            }
            "classpass" -> when (action) {
                "search" -> "https://classpass.com/search?q=${encode(param("query") ?: "")}"
                "class" -> "https://classpass.com/classes/${param("id") ?: ""}"
                "studio" -> "https://classpass.com/studios/${param("slug") ?: ""}"
                else -> null
            }
            "mayo_clinic" -> when (action) {
                "search" -> "https://www.mayoclinic.org/search/search-results?q=${encode(param("query") ?: "")}"
                "disease" -> "https://www.mayoclinic.org/diseases-conditions/${param("slug") ?: ""}"
                "symptom" -> "https://www.mayoclinic.org/symptom-checker"
                else -> null
            }
            "ovia" -> when (action) {
                "open" -> "https://www.oviahealth.com"
                else -> null
            }

            // Travel & Transportation
            "couchsurfing" -> when (action) {
                "search" -> "https://www.couchsurfing.com/people?search_query=${encode(param("query") ?: "")}"
                "place" -> "https://www.couchsurfing.com/places/${param("slug") ?: ""}"
                "events" -> "https://www.couchsurfing.com/events"
                else -> null
            }
            "rome2rio" -> when (action) {
                "search" -> "https://www.rome2rio.com/s/${encode(param("from") ?: "")}/${encode(param("to") ?: "")}"
                "map" -> "https://www.rome2rio.com/map"
                else -> null
            }
            "kiwi" -> when (action) {
                "search" -> "https://www.kiwi.com/en/search/results/${encode(param("from") ?: "")}/${encode(param("to") ?: "")}"
                "explore" -> "https://www.kiwi.com/en/search/explore/"
                else -> null
            }
            "komoot" -> when (action) {
                "route" -> "https://www.komoot.com/tour/${param("id") ?: ""}"
                "search" -> "https://www.komoot.com/discover?q=${encode(param("query") ?: "")}"
                "plan" -> "https://www.komoot.com/plan"
                else -> null
            }
            "trainline" -> when (action) {
                "search" -> "https://www.thetrainline.com/train-times/${encode(param("from") ?: "")}-to-${encode(param("to") ?: "")}"
                "buy" -> "https://www.thetrainline.com"
                else -> null
            }
            "momondo" -> when (action) {
                "flights" -> "https://www.momondo.com/flight-search/${encode(param("from") ?: "")}-${encode(param("to") ?: "")}"
                "hotels" -> "https://www.momondo.com/hotels/${encode(param("location") ?: "")}"
                else -> null
            }
            "sygic" -> when (action) {
                "navigate" -> "com.sygic.aura://coordinate|${param("lat") ?: "0"}|${param("lon") ?: "0"}|drive"
                else -> null
            }
            "cleartrip" -> when (action) {
                "flights" -> "https://www.cleartrip.com/flights"
                "hotels" -> "https://www.cleartrip.com/hotels"
                "trains" -> "https://www.cleartrip.com/trains"
                else -> null
            }
            "makemytrip" -> when (action) {
                "flights" -> "https://www.makemytrip.com/flights"
                "hotels" -> "https://www.makemytrip.com/hotels"
                "trains" -> "https://www.makemytrip.com/railways"
                "search" -> "https://www.makemytrip.com/flights/search?from=${encode(param("from") ?: "")}&to=${encode(param("to") ?: "")}"
                else -> null
            }
            "yatra" -> when (action) {
                "flights" -> "https://www.yatra.com/flights"
                "hotels" -> "https://www.yatra.com/hotels"
                "search" -> "https://www.yatra.com/air-search?from=${encode(param("from") ?: "")}&to=${encode(param("to") ?: "")}"
                else -> null
            }
            "ixigo" -> when (action) {
                "flights" -> "https://www.ixigo.com/flights"
                "trains" -> "https://www.ixigo.com/trains"
                "buses" -> "https://www.ixigo.com/buses"
                else -> null
            }
            "bird" -> when (action) {
                "ride" -> "https://www.bird.co/ride"
                "locations" -> "https://www.bird.co/locations"
                else -> null
            }
            "lime" -> when (action) {
                "ride" -> "https://www.li.me/ride"
                "locations" -> "https://www.li.me/locations"
                else -> null
            }
            "tier" -> when (action) {
                "ride" -> "https://www.tier.app/ride"
                else -> null
            }
            "free_now" -> when (action) {
                "ride" -> "https://www.free-now.com"
                else -> null
            }

            // Communication & Messaging
            "lark" -> when (action) {
                "open" -> "https://www.larksuite.com"
                "meeting" -> "https://www.larksuite.com/meetings"
                else -> null
            }
            "zalo" -> when (action) {
                "chat" -> "https://zalo.me/${param("phone") ?: ""}"
                "profile" -> "https://zalo.me/${param("id") ?: ""}"
                else -> null
            }
            "band" -> when (action) {
                "group" -> "https://band.us/band/${param("id") ?: ""}"
                "post" -> "https://band.us/band/${param("band_id") ?: ""}/post/${param("post_id") ?: ""}"
                else -> null
            }
            "mattermost" -> when (action) {
                "channel" -> "mattermost://channel/${param("team") ?: ""}/${param("channel") ?: ""}"
                else -> null
            }
            "rocket_chat" -> when (action) {
                "channel" -> "rocketchat://channel/${param("name") ?: ""}"
                "dm" -> "rocketchat://direct/${param("username") ?: ""}"
                else -> null
            }
            "wire" -> when (action) {
                "open" -> "wire://"
                "conversation" -> "wire://conversation/${param("id") ?: ""}"
                else -> null
            }
            "session" -> when (action) {
                "open" -> "https://getsession.org"
                else -> null
            }

            // Music & Audio
            "soundhound" -> when (action) {
                "identify" -> "soundhound://recognize"
                "search" -> "https://www.soundhound.com/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "smule" -> when (action) {
                "search" -> "https://www.smule.com/search?q=${encode(param("query") ?: "")}"
                "song" -> "https://www.smule.com/song/${param("id") ?: ""}"
                "profile" -> "https://www.smule.com/${param("username") ?: ""}"
                else -> null
            }
            "genius" -> when (action) {
                "search" -> "https://genius.com/search?q=${encode(param("query") ?: "")}"
                "song" -> "https://genius.com/${param("slug") ?: ""}"
                "artist" -> "https://genius.com/artists/${param("slug") ?: ""}"
                else -> null
            }
            "musixmatch" -> when (action) {
                "search" -> "https://www.musixmatch.com/search/${encode(param("query") ?: "")}"
                "lyrics" -> "https://www.musixmatch.com/lyrics/${param("artist") ?: ""}/${param("title") ?: ""}"
                else -> null
            }
            "wynk" -> when (action) {
                "search" -> "https://wynk.in/music/search/${encode(param("query") ?: "")}"
                "song" -> "https://wynk.in/music/song/${param("id") ?: ""}"
                else -> null
            }

            // Sports & Fitness
            "zwift" -> when (action) {
                "open" -> "https://www.zwift.com"
                "events" -> "https://www.zwift.com/events"
                "routes" -> "https://www.zwift.com/routes"
                else -> null
            }
            "nike_snkrs" -> when (action) {
                "feed" -> "https://www.nike.com/snkrs"
                "product" -> "https://www.nike.com/launch/t/${param("slug") ?: ""}"
                "upcoming" -> "https://www.nike.com/snkrs?type=upcoming"
                else -> null
            }
            "sofascore_live" -> when (action) {
                "match" -> "https://www.sofascore.com/match/${param("id") ?: ""}"
                "team" -> "https://www.sofascore.com/team/${param("sport") ?: "football"}/${param("slug") ?: ""}/${param("id") ?: ""}"
                "live" -> "https://www.sofascore.com/live"
                else -> null
            }
            "flashscore" -> when (action) {
                "match" -> "https://www.flashscore.com/match/${param("id") ?: ""}"
                "team" -> "https://www.flashscore.com/team/${param("slug") ?: ""}"
                "live" -> "https://www.flashscore.com"
                else -> null
            }
            "365scores" -> when (action) {
                "match" -> "https://www.365scores.com/match/${param("id") ?: ""}"
                "live" -> "https://www.365scores.com/live"
                else -> null
            }
            "bet365" -> when (action) {
                "sports" -> "https://www.bet365.com/#/AS/B1/"
                "live" -> "https://www.bet365.com/#/IP/B1"
                else -> null
            }

            // Utilities & Tools
            "adguard" -> when (action) {
                "open" -> "adguard://open"
                "filter" -> "adguard://add/${encode(param("url") ?: "")}"
                else -> null
            }
            "pushbullet" -> when (action) {
                "push" -> "https://www.pushbullet.com/#push"
                "sms" -> "https://www.pushbullet.com/#sms"
                "devices" -> "https://www.pushbullet.com/#devices"
                else -> null
            }
            "raindrop" -> when (action) {
                "search" -> "https://app.raindrop.io/search/${encode(param("query") ?: "")}"
                "collection" -> "https://app.raindrop.io/my/${param("id") ?: ""}"
                "add" -> "https://app.raindrop.io/add?link=${encode(param("url") ?: "")}"
                else -> null
            }
            "airdroid" -> when (action) {
                "open" -> "https://web.airdroid.com"
                "transfer" -> "https://web.airdroid.com/transfer"
                else -> null
            }

            // Parenting & Family
            "what_to_expect" -> when (action) {
                "search" -> "https://www.whattoexpect.com/search?q=${encode(param("query") ?: "")}"
                "week" -> "https://www.whattoexpect.com/pregnancy/week-by-week/week-${param("week") ?: ""}.aspx"
                else -> null
            }
            "tinybeans" -> when (action) {
                "open" -> "https://www.tinybeans.com"
                "journal" -> "https://www.tinybeans.com/journal"
                else -> null
            }
            "life360" -> when (action) {
                "open" -> "life360://open"
                "map" -> "life360://map"
                "circle" -> "life360://circle/${param("id") ?: ""}"
                else -> null
            }
            "cozi" -> when (action) {
                "calendar" -> "https://www.cozi.com/calendar"
                "lists" -> "https://www.cozi.com/lists"
                "meals" -> "https://www.cozi.com/meal-planner"
                else -> null
            }

            // Smart Home & IoT
            "home_assistant" -> when (action) {
                "open" -> "homeassistant://navigate/lovelace"
                "dashboard" -> "homeassistant://navigate/lovelace/${param("dashboard") ?: ""}"
                "entity" -> "homeassistant://navigate/entity/${param("entity_id") ?: ""}"
                "automation" -> "homeassistant://navigate/config/automation"
                else -> null
            }
            "tuya" -> when (action) {
                "device" -> "tuyasmart://device/${param("id") ?: ""}"
                "scene" -> "tuyasmart://scene/${param("id") ?: ""}"
                else -> null
            }
            "ecobee" -> when (action) {
                "open" -> "https://www.ecobee.com"
                "thermostat" -> "https://www.ecobee.com/thermostat"
                else -> null
            }
            "myq" -> when (action) {
                "open" -> "myq://open"
                else -> null
            }
            "govee" -> when (action) {
                "device" -> "govee://device/${param("id") ?: ""}"
                "scene" -> "govee://scene/${param("id") ?: ""}"
                else -> null
            }
            "nanoleaf" -> when (action) {
                "device" -> "nanoleaf://device/${param("id") ?: ""}"
                "scene" -> "nanoleaf://scene/${param("name") ?: ""}"
                else -> null
            }

            // Meditation & Mindfulness
            "ten_percent" -> when (action) {
                "meditate" -> "https://www.tenpercent.com/meditate"
                "course" -> "https://www.tenpercent.com/courses/${param("slug") ?: ""}"
                "teacher" -> "https://www.tenpercent.com/teachers/${param("slug") ?: ""}"
                else -> null
            }
            "meditopia" -> when (action) {
                "meditate" -> "https://meditopia.com/meditate"
                "sleep" -> "https://meditopia.com/sleep"
                else -> null
            }
            "aura_health" -> when (action) {
                "open" -> "https://www.aurahealth.io"
                "meditate" -> "https://www.aurahealth.io/meditate"
                else -> null
            }

            // Podcasts & News
            "player_fm" -> when (action) {
                "search" -> "https://player.fm/search/${encode(param("query") ?: "")}"
                "podcast" -> "https://player.fm/series/${param("slug") ?: ""}"
                "featured" -> "https://player.fm/featured"
                else -> null
            }
            "fountain" -> when (action) {
                "search" -> "https://www.fountain.fm/search?q=${encode(param("query") ?: "")}"
                "podcast" -> "https://www.fountain.fm/show/${param("slug") ?: ""}"
                "clips" -> "https://www.fountain.fm/clips"
                else -> null
            }

            // Crypto & Web3
            "opensea" -> when (action) {
                "collection" -> "https://opensea.io/collection/${param("slug") ?: ""}"
                "asset" -> "https://opensea.io/assets/${param("chain") ?: "ethereum"}/${param("contract") ?: ""}/${param("token_id") ?: ""}"
                "profile" -> "https://opensea.io/${param("address") ?: ""}"
                "explore" -> "https://opensea.io/explore-collections"
                else -> null
            }
            "dextools" -> when (action) {
                "pair" -> "https://www.dextools.io/app/en/${param("chain") ?: "ether"}/pair-explorer/${param("address") ?: ""}"
                "token" -> "https://www.dextools.io/app/en/${param("chain") ?: "ether"}/token/${param("address") ?: ""}"
                else -> null
            }
            "exodus" -> when (action) {
                "open" -> "exodus://open"
                "send" -> "exodus://send/${param("asset") ?: ""}"
                "receive" -> "exodus://receive/${param("asset") ?: ""}"
                else -> null
            }
            "kucoin" -> when (action) {
                "trade" -> "https://www.kucoin.com/trade/${param("pair") ?: "BTC-USDT"}"
                "markets" -> "https://www.kucoin.com/markets"
                else -> null
            }
            "bybit" -> when (action) {
                "trade" -> "https://www.bybit.com/trade/spot/${param("pair") ?: "BTC/USDT"}"
                "markets" -> "https://www.bybit.com/en/markets"
                else -> null
            }
            "okx" -> when (action) {
                "trade" -> "https://www.okx.com/trade-spot/${param("pair") ?: "btc-usdt"}"
                "markets" -> "https://www.okx.com/markets"
                "earn" -> "https://www.okx.com/earn"
                else -> null
            }
            "gate_io" -> when (action) {
                "trade" -> "https://www.gate.io/trade/${param("pair") ?: "BTC_USDT"}"
                "markets" -> "https://www.gate.io/marketlist"
                else -> null
            }
            "bitget" -> when (action) {
                "trade" -> "https://www.bitget.com/spot/${param("pair") ?: "BTCUSDT"}"
                "markets" -> "https://www.bitget.com/markets"
                else -> null
            }

            // Project Management & Work
            "wrike" -> when (action) {
                "open" -> "https://www.wrike.com"
                "folder" -> "https://www.wrike.com/open.htm?id=${param("id") ?: ""}"
                "task" -> "https://www.wrike.com/open.htm?id=${param("id") ?: ""}"
                else -> null
            }
            "smartsheet" -> when (action) {
                "sheet" -> "https://app.smartsheet.com/sheets/${param("id") ?: ""}"
                "dashboard" -> "https://app.smartsheet.com/dashboards/${param("id") ?: ""}"
                "report" -> "https://app.smartsheet.com/reports/${param("id") ?: ""}"
                else -> null
            }
            "linear" -> when (action) {
                "issue" -> "https://linear.app/issue/${param("id") ?: ""}"
                "project" -> "https://linear.app/project/${param("slug") ?: ""}"
                "team" -> "https://linear.app/team/${param("key") ?: ""}"
                "search" -> "https://linear.app/search?q=${encode(param("query") ?: "")}"
                else -> null
            }
            "sunsama" -> when (action) {
                "today" -> "https://app.sunsama.com/today"
                "task" -> "https://app.sunsama.com/tasks/${param("id") ?: ""}"
                else -> null
            }
            "reclaim" -> when (action) {
                "tasks" -> "https://app.reclaim.ai/tasks"
                "habits" -> "https://app.reclaim.ai/habits"
                "calendar" -> "https://app.reclaim.ai/planner"
                else -> null
            }
            "motion" -> when (action) {
                "open" -> "https://app.usemotion.com"
                "tasks" -> "https://app.usemotion.com/tasks"
                "calendar" -> "https://app.usemotion.com/calendar"
                else -> null
            }

            // VPN & Privacy
            "mullvad" -> when (action) {
                "connect" -> "mullvad://connect"
                "account" -> "https://mullvad.net/account"
                else -> null
            }
            "protonvpn" -> when (action) {
                "connect" -> "protonvpn://connect"
                "servers" -> "https://protonvpn.com/vpn-servers"
                else -> null
            }
            "windscribe" -> when (action) {
                "connect" -> "windscribe://connect"
                "account" -> "https://windscribe.com/myaccount"
                else -> null
            }
            "tunnelbear" -> when (action) {
                "connect" -> "tunnelbear://connect"
                else -> null
            }

            // Weather
            "carrot_weather" -> when (action) {
                "forecast" -> "carrotweather://forecast"
                "map" -> "carrotweather://map"
                else -> null
            }

            else -> null
        }
    }

}
