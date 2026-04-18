package com.tun.vpn

import androidx.compose.runtime.compositionLocalOf

/**
 * i18n: EN + RU.
 *
 * Компоузы читают строки через LocalStrings.current. ViewModel и другие не-compose
 * слои — через Strings.current (volatile singleton, обновляется из LanguageStore).
 */
data class TgStrings(
    val app: String,
    val tagline: String,

    // statuses
    val stDisconnected: String,
    val stConnecting: String,
    val stStartingProxy: String,
    val stAuthenticating: String,
    val stRegisteringIdentity: String, // %d/%d
    val stDtls: String,
    val stTurnAllocated: String,
    val stCaptchaSolving: String,
    val stCaptchaSolved: String,
    val stCaptchaWaiting: String, // #%d
    val stWgHandshake: String,
    val stConnected: String,
    val stDisconnecting: String,
    val stError: String,
    val stNoProfile: String,
    val stProfileIncomplete: String,
    val stProxyFailed: String,
    val stVpnFailed: String, // %s
    val stImported: String, // %s
    val stInvalidLink: String,

    // buttons
    val btnConnect: String,
    val btnConnecting: String,
    val btnDisconnect: String,
    val btnRetry: String,

    // home
    val noProfileTitle: String,
    val noProfileSub: String,

    // import
    val addTitle: String,
    val addSub: String,
    val pasteClip: String,
    val addManual: String,
    val cancel: String,
    val deleteWord: String,

    // settings
    val settings: String,
    val appearance: String,
    val themeSub: String,
    val themeAurora: String,
    val themeAuroraSub: String,
    val themeBloom: String,
    val themeBloomSub: String,
    val themePrism: String,
    val themePrismSub: String,
    val navigation: String,
    val language: String,
    val langEn: String,
    val langRu: String,
    val viewLogs: String,
    val viewLogsSub: String,
    val about: String,
    val aboutSub: String,

    // edit
    val editProfile: String,
    val secProfile: String,
    val secProxy: String,
    val secWg: String,
    val nameLabel: String,
    val turnUrl: String,
    val peerAddr: String,
    val listenAddr: String,
    val conns: String,
    val manualCaptcha: String,
    val manualCaptchaSub: String,
    val manualCaptchaToggle: String,
    val deleteProfile: String,
    val deleteConfirmTitle: String, // %s
    val deleteConfirmBody: String,

    // captcha
    val captchaTitle: String,
    val captchaStepOf: String, // "Step %d of ~%d"
    val captchaHint: String,
    val refresh: String,

    // logs
    val activity: String,
    val logsAll: String,
    val logsApp: String,
    val logsTp: String,
    val logsWg: String,
    val searchLogs: String,
    val friendly: String,
    val raw: String,
    val noLogs: String,
    val noMatchingLogs: String,
    val copiedLines: String, // %d

    // updates
    val updateAvailable: String,
    val updateTapToInstall: String,
    val updatesSection: String,
    val checkForUpdates: String,
    val checkForUpdatesSub: String,
    val upToDate: String,
    val downloadUpdate: String,

    // about
    val verPrefix: String,
    val aboutDesc: String,
    val srcProxy: String,
    val srcBridge: String,

    // friendly log messages (strings emitted from VM / TurnProxyManager)
    val fStartingTurnGate: String,
    val fReachingVk: String,
    val fVkCaptcha: String, // #%d
    val fCaptchaPassed: String,
    val fAuthenticating: String, // %d/%d
    val fSecureChannel: String,
    val fOpeningTunnel: String,
    val fConnected: String,
    val fDisconnected: String,
    val fSolvingCaptcha: String,
    val fCaptchaPassedShort: String,
)

private val EN = TgStrings(
    app = "TurnGate",
    tagline = "VPN through VK TURN proxy",

    stDisconnected = "Ready to connect",
    stConnecting = "Establishing tunnel…",
    stStartingProxy = "Starting proxy…",
    stAuthenticating = "Authenticating with VK…",
    stRegisteringIdentity = "Registering identity %d/%d…",
    stDtls = "Establishing secure channel…",
    stTurnAllocated = "Opening WireGuard tunnel…",
    stCaptchaSolving = "Solving captcha…",
    stCaptchaSolved = "Captcha solved, continuing…",
    stCaptchaWaiting = "Waiting for captcha #%d…",
    stWgHandshake = "WireGuard handshake…",
    stConnected = "Protected",
    stDisconnecting = "Closing tunnel…",
    stError = "Connection failed",
    stNoProfile = "No profile selected",
    stProfileIncomplete = "Profile incomplete",
    stProxyFailed = "Proxy failed to start",
    stVpnFailed = "VPN failed: %s",
    stImported = "Imported: %s",
    stInvalidLink = "Invalid config link",

    btnConnect = "Connect",
    btnConnecting = "Connecting…",
    btnDisconnect = "Disconnect",
    btnRetry = "Try again",

    noProfileTitle = "No profile yet",
    noProfileSub = "Paste a turnbridge:// link to get started",

    addTitle = "Add profile",
    addSub = "From a turnbridge:// link or manually.",
    pasteClip = "Paste from clipboard",
    addManual = "Configure manually",
    cancel = "Cancel",
    deleteWord = "Delete",

    settings = "Settings",
    appearance = "Appearance",
    themeSub = "Choose how TurnGate looks",
    themeAurora = "Aurora",
    themeAuroraSub = "Violet · magenta",
    themeBloom = "Bloom",
    themeBloomSub = "Coral · lime",
    themePrism = "Prism",
    themePrismSub = "Pink · electric",
    navigation = "Navigation",
    language = "Language",
    langEn = "English",
    langRu = "Русский",
    viewLogs = "Activity & logs",
    viewLogsSub = "See what's happening under the hood",
    about = "About TurnGate",
    aboutSub = "Version, credits, source",

    editProfile = "Edit profile",
    secProfile = "Profile",
    secProxy = "Proxy settings",
    secWg = "WireGuard config",
    nameLabel = "Name",
    turnUrl = "TURN server URL",
    peerAddr = "Peer address",
    listenAddr = "Listen address",
    conns = "Parallel connections",
    manualCaptcha = "Always solve captcha manually",
    manualCaptchaSub = "Skip the automatic solver",
    manualCaptchaToggle = "Solve captcha manually",
    deleteProfile = "Delete profile",
    deleteConfirmTitle = "Delete \"%s\"?",
    deleteConfirmBody = "This cannot be undone.",

    captchaTitle = "Quick check",
    captchaStepOf = "Step %d of ~%d",
    captchaHint = "VK may show several checks in a row. Sit tight.",
    refresh = "Refresh",

    activity = "Activity",
    logsAll = "All",
    logsApp = "App",
    logsTp = "Proxy",
    logsWg = "WireGuard",
    searchLogs = "Search…",
    friendly = "Friendly",
    raw = "Raw",
    noLogs = "Nothing yet — connect to start streaming activity.",
    noMatchingLogs = "No matching logs",
    copiedLines = "Copied %d lines",

    updateAvailable = "Update available",
    updateTapToInstall = "tap to install",
    updatesSection = "Updates",
    checkForUpdates = "Check for updates",
    checkForUpdatesSub = "Tap to query GitHub releases",
    upToDate = "You're on the latest version",
    downloadUpdate = "Download",

    verPrefix = "Version",
    aboutDesc = "TurnGate routes WireGuard traffic through VK's TURN servers. Open source, no telemetry.",
    srcProxy = "vk-turn-proxy on GitHub",
    srcBridge = "TurnBridge on GitHub",

    fStartingTurnGate = "Starting TurnGate",
    fReachingVk = "Reaching VK servers",
    fVkCaptcha = "VK asked for a check (#%d)",
    fCaptchaPassed = "Check passed — continuing",
    fAuthenticating = "Authenticating (%d/%d)",
    fSecureChannel = "Establishing secure channel",
    fOpeningTunnel = "Opening WireGuard tunnel",
    fConnected = "Connected — you are protected",
    fDisconnected = "VPN disconnected",
    fSolvingCaptcha = "Solving captcha automatically",
    fCaptchaPassedShort = "Captcha passed",
)

private val RU = TgStrings(
    app = "TurnGate",
    tagline = "VPN через VK TURN-прокси",

    stDisconnected = "Готов к подключению",
    stConnecting = "Устанавливаем туннель…",
    stStartingProxy = "Запуск прокси…",
    stAuthenticating = "Авторизация в VK…",
    stRegisteringIdentity = "Регистрация идентичности %d/%d…",
    stDtls = "Устанавливаем безопасный канал…",
    stTurnAllocated = "Открываем туннель WireGuard…",
    stCaptchaSolving = "Проходим капчу…",
    stCaptchaSolved = "Капча пройдена, продолжаем…",
    stCaptchaWaiting = "Ожидание капчи №%d…",
    stWgHandshake = "Рукопожатие WireGuard…",
    stConnected = "Защищено",
    stDisconnecting = "Закрываем туннель…",
    stError = "Не удалось подключиться",
    stNoProfile = "Профиль не выбран",
    stProfileIncomplete = "Профиль не заполнен",
    stProxyFailed = "Прокси не запустился",
    stVpnFailed = "Ошибка VPN: %s",
    stImported = "Импортирован: %s",
    stInvalidLink = "Неверная ссылка",

    btnConnect = "Подключить",
    btnConnecting = "Подключаем…",
    btnDisconnect = "Отключить",
    btnRetry = "Ещё раз",

    noProfileTitle = "Пока нет профилей",
    noProfileSub = "Вставьте ссылку turnbridge://, чтобы начать",

    addTitle = "Добавить профиль",
    addSub = "Из ссылки turnbridge:// или вручную.",
    pasteClip = "Вставить из буфера",
    addManual = "Настроить вручную",
    cancel = "Отмена",
    deleteWord = "Удалить",

    settings = "Настройки",
    appearance = "Оформление",
    themeSub = "Выберите вид TurnGate",
    themeAurora = "Aurora",
    themeAuroraSub = "Фиолетовый · маджента",
    themeBloom = "Bloom",
    themeBloomSub = "Коралл · лайм",
    themePrism = "Prism",
    themePrismSub = "Розовый · электрик",
    navigation = "Навигация",
    language = "Язык",
    langEn = "English",
    langRu = "Русский",
    viewLogs = "Активность и логи",
    viewLogsSub = "Посмотреть, что происходит внутри",
    about = "О приложении",
    aboutSub = "Версия, источники, благодарности",

    editProfile = "Редактировать профиль",
    secProfile = "Профиль",
    secProxy = "Настройки прокси",
    secWg = "Конфиг WireGuard",
    nameLabel = "Название",
    turnUrl = "URL TURN-сервера",
    peerAddr = "Адрес пира",
    listenAddr = "Локальный адрес",
    conns = "Параллельные соединения",
    manualCaptcha = "Всегда решать капчу вручную",
    manualCaptchaSub = "Пропустить автоматический решатель",
    manualCaptchaToggle = "Решать капчу вручную",
    deleteProfile = "Удалить профиль",
    deleteConfirmTitle = "Удалить \"%s\"?",
    deleteConfirmBody = "Это действие необратимо.",

    captchaTitle = "Быстрая проверка",
    captchaStepOf = "Шаг %d из ~%d",
    captchaHint = "VK может показать несколько проверок подряд. Это нормально.",
    refresh = "Обновить",

    activity = "Активность",
    logsAll = "Все",
    logsApp = "Приложение",
    logsTp = "Прокси",
    logsWg = "WireGuard",
    searchLogs = "Поиск…",
    friendly = "Кратко",
    raw = "Сырые",
    noLogs = "Ничего ещё — подключитесь, чтобы увидеть активность.",
    noMatchingLogs = "Нет подходящих записей",
    copiedLines = "Скопировано строк: %d",

    updateAvailable = "Доступно обновление",
    updateTapToInstall = "нажмите для установки",
    updatesSection = "Обновления",
    checkForUpdates = "Проверить обновления",
    checkForUpdatesSub = "Нажмите, чтобы запросить GitHub releases",
    upToDate = "У вас последняя версия",
    downloadUpdate = "Скачать",

    verPrefix = "Версия",
    aboutDesc = "TurnGate направляет трафик WireGuard через TURN-серверы VK. Открытый код, без телеметрии.",
    srcProxy = "vk-turn-proxy на GitHub",
    srcBridge = "TurnBridge на GitHub",

    fStartingTurnGate = "Запускаем TurnGate",
    fReachingVk = "Подключаемся к серверам VK",
    fVkCaptcha = "VK просит пройти проверку (#%d)",
    fCaptchaPassed = "Проверка пройдена — продолжаем",
    fAuthenticating = "Авторизация (%d/%d)",
    fSecureChannel = "Устанавливаем безопасный канал",
    fOpeningTunnel = "Открываем туннель WireGuard",
    fConnected = "Подключено — вы защищены",
    fDisconnected = "VPN отключён",
    fSolvingCaptcha = "Решаем капчу автоматически",
    fCaptchaPassedShort = "Капча пройдена",
)

/**
 * Глобально доступный синглтон текущих строк — используется из ViewModel и
 * других не-compose слоёв. Обновляется из [LanguageStore].
 */
object Strings {
    @Volatile
    var current: TgStrings = EN
        private set

    fun setLang(lang: String) {
        current = when (lang) {
            "ru" -> RU
            else -> EN
        }
    }
}

/**
 * Compose CompositionLocal для чтения строк внутри @Composable функций.
 * Обновляется при смене языка (ключ перезаписывается в MainActivity).
 */
val LocalStrings = compositionLocalOf<TgStrings> { EN }

/** Возвращает набор строк по коду языка. */
fun stringsFor(lang: String): TgStrings = when (lang) {
    "ru" -> RU
    else -> EN
}
