package com.ianzb.hypernavbar.ui.screen.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

// ── Navigation state ──────────────────────────────────────────────────
private enum class EditorLevel { APPS, ACTIVITIES, FIELDS }

// ── Option definitions ────────────────────────────────────────────────
private val MODE_LABELS = listOf("自动模式（-1）", "禁用模式（0）", "取色模式（1）", "强制沉浸模式（2）")
private val MODE_VALUES = listOf(-1, 0, 1, 2)

private val SF_SAMPLING_LABELS = listOf("自动模式（0）", "强制启用（1）", "强制禁用（255）")
private val SF_SAMPLING_VALUES = listOf(0, 1, 255)

private val DIALOG_POPUP_LABELS = listOf("禁用模式（0）", "视图采样模式（1）", "SF采样模式（2）")
private val DIALOG_POPUP_VALUES = listOf(0, 1, 2)

private val COLOR_TYPE_LABELS = listOf("默认（null）", "自动取色（1）", "自定义颜色")
private const val COLOR_TYPE_DEFAULT = 0
private const val COLOR_TYPE_AUTO = 1
private const val COLOR_TYPE_CUSTOM = 2

private const val EMPTY_JSON = """{
    "dataVersion": "999999",
    "name": "沉浸规则",
    "modules": "navigation_bar_immersive_application_config_new",
    "modifyApps": "modifyApps",
    "NBIRules": {}
}"""

// ── JSON helpers ──────────────────────────────────────────────────────
private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key, 0)

private fun parseJsonSafe(jsonStr: String): JSONObject {
    return try {
        JSONObject(jsonStr.trim().ifEmpty { EMPTY_JSON })
    } catch (_: Exception) {
        JSONObject(EMPTY_JSON)
    }
}

/**
 * Format NBIRules JSON: 4-space indent, sort by package name A-Z,
 * sort activity rules by activity name A-Z.
 */
fun formatNbiJson(jsonStr: String): String {
    return try {
        val root = JSONObject(jsonStr.trim().ifEmpty { return jsonStr })
        val nbiRules = root.optJSONObject("NBIRules") ?: return root.toString(4)
        val sortedRoot = JSONObject()
        sortedRoot.put("dataVersion", root.optString("dataVersion", "999999"))
        if (root.has("name")) sortedRoot.put("name", root.optString("name"))
        sortedRoot.put("modules", root.optString("modules", "navigation_bar_immersive_application_config_new"))
        sortedRoot.put("modifyApps", root.optString("modifyApps", "modifyApps"))
        val sortedNbi = JSONObject()
        nbiRules.keys().asSequence().sorted().forEach { pkg ->
            val app = nbiRules.optJSONObject(pkg) ?: return@forEach
            val sortedApp = JSONObject()
            if (app.has("name")) sortedApp.put("name", app.optString("name"))
            sortedApp.put("enable", app.optBoolean("enable", false))
            if (app.has("enable31")) sortedApp.put("enable31", app.optBoolean("enable31"))
            if (app.has("disableVersionCode") && !app.isNull("disableVersionCode"))
                sortedApp.put("disableVersionCode", app.optLong("disableVersionCode"))
            val activityRules = app.optJSONObject("activityRules")
            if (activityRules != null) {
                val sortedActivities = JSONObject()
                activityRules.keys().asSequence().sorted().forEach { act ->
                    sortedActivities.put(act, activityRules.opt(act))
                }
                sortedApp.put("activityRules", sortedActivities)
            }
            sortedNbi.put(pkg, sortedApp)
        }
        sortedRoot.put("NBIRules", sortedNbi)
        sortedRoot.toString(4)
    } catch (_: Exception) {
        jsonStr
    }
}

/**
 * Determine the color-type index from the JSON color value:
 *   0 = null (default), 1 = sentinel 1 (auto-detect), 2 = ARGB integer.
 */
private fun colorTypeFromValue(color: Any?): Int = when {
    color == null || color == JSONObject.NULL -> COLOR_TYPE_DEFAULT
    color is Int && color == 1 -> COLOR_TYPE_AUTO
    else -> COLOR_TYPE_CUSTOM
}

/**
 * The ARGB int stored in JSON, or 0xFF000000 (black) when no custom
 * color has been chosen yet.
 */
private fun argbFromValue(color: Any?): Int = when (color) {
    is Int -> color
    else -> 0xFF000000.toInt()
}

// ── Main editor sheet ─────────────────────────────────────────────────
@Composable
fun JsonRuleEditorSheet(
    show: Boolean,
    jsonInput: String,
    onJsonChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    // --- work-copy of the JSON, rebuilt whenever we confirm an edit ---
    var editingJsonStr by remember { mutableStateOf(jsonInput) }

    // sync when sheet is first opened (or jsonInput changes externally)
    if (editingJsonStr != jsonInput) {
        editingJsonStr = jsonInput
    }

    val root = remember(editingJsonStr) { parseJsonSafe(editingJsonStr) }
    val nbiRules = remember(root) {
        root.optJSONObject("NBIRules") ?: JSONObject().also { root.put("NBIRules", it) }
    }

    // --- navigation ---
    var currentLevel by remember { mutableStateOf(EditorLevel.APPS) }
    var selectedPackage by remember { mutableStateOf("") }
    var selectedActivity by remember { mutableStateOf("") }

    // --- dialogs ---
    var showAddAppDialog by remember { mutableStateOf(false) }
    var showAddActivityDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf("") } // pkg or "pkg::activity"

    // --- add-app fields ---
    var newPkg by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    // --- add-activity fields ---
    var newActName by remember { mutableStateOf("") }

    /** Return a mutable copy of the activity JSON. Null if not found. */
    fun getActivityJson(): JSONObject? {
        val app = nbiRules.optJSONObject(selectedPackage) ?: return null
        val rules = app.optJSONObject("activityRules") ?: return null
        return rules.optJSONObject(selectedActivity)
    }

    /** Fetch a field from the current activity, or null. */
    /** Write back to the working JSON and update the string. */
    fun saveRoot() {
        editingJsonStr = root.toString(2)
        // Immediately propagate changes to parent
        onJsonChange(editingJsonStr)
    }

    fun deleteApp(pkg: String) {
        nbiRules.remove(pkg)
        saveRoot()
    }

    fun deleteActivity(pkg: String, activity: String) {
        val app = nbiRules.optJSONObject(pkg) ?: return
        val rules = app.optJSONObject("activityRules") ?: return
        rules.remove(activity)
        if (rules.length() == 0) app.remove("activityRules")
        saveRoot()
    }

    fun updateActivityField(key: String, value: Any?) {
        val act = getActivityJson() ?: return
        if (value == null) {
            act.remove(key)
        } else {
            act.put(key, value)
        }
        saveRoot()
    }

    // ── UI ────────────────────────────────────────────────────────────
    WindowBottomSheet(
        title = when (currentLevel) {
            EditorLevel.APPS -> "可视化规则编辑"
            EditorLevel.ACTIVITIES -> "Activity 列表"
            EditorLevel.FIELDS -> "编辑 Activity 规则"
        },
        show = show,
        onDismissRequest = onDismiss,
        startAction = {
            IconButton(onClick = {
                when (currentLevel) {
                    EditorLevel.ACTIVITIES -> currentLevel = EditorLevel.APPS
                    EditorLevel.FIELDS -> currentLevel = EditorLevel.ACTIVITIES
                    EditorLevel.APPS -> onDismiss()
                }
            }) {
                Icon(
                    MiuixIcons.Close,
                    contentDescription = if (currentLevel == EditorLevel.APPS) "取消" else "返回",
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            IconButton(onClick = {
                when (currentLevel) {
                    EditorLevel.FIELDS -> {
                        // Save changes and go back to activity list
                        currentLevel = EditorLevel.ACTIVITIES
                    }
                    EditorLevel.ACTIVITIES -> {
                        // Go back to app list
                        currentLevel = EditorLevel.APPS
                    }
                    EditorLevel.APPS -> {
                        // Format and save, then dismiss
                        onJsonChange(formatNbiJson(editingJsonStr))
                        onDismiss()
                    }
                }
            }) {
                Icon(MiuixIcons.Ok, "保存", tint = MiuixTheme.colorScheme.onBackground)
            }
        },
    ) {
        // Pre-compute derived values to avoid @Composable context issues inside LazyColumn
        val pkgKeys = nbiRules.keys().asSequence().toList().sorted()
        val appForSelected = nbiRules.optJSONObject(selectedPackage)
        val activityRules = appForSelected?.optJSONObject("activityRules") ?: JSONObject()
        val actKeys = activityRules.keys().asSequence().toList().sorted()
        val currentAct = getActivityJson() ?: JSONObject()

        LazyColumn(
            modifier = Modifier.fillMaxWidth().scrollEndHaptic().overScrollVertical(),
        ) {
            when (currentLevel) {

                // ── App list ──────────────────────────────────────────
                EditorLevel.APPS -> {
                    item {
                        SmallTitle(
                            text = "应用列表（${nbiRules.length()}）",
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    item {
                        TextButton(
                            text = "添加应用",
                            onClick = {
                                newPkg = ""
                                newName = ""
                                showAddAppDialog = true
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }

                    items(pkgKeys) { pkg ->
                        val app = nbiRules.optJSONObject(pkg)
                        val name = app?.optString("name", "") ?: ""
                        val actCount = app?.optJSONObject("activityRules")?.length() ?: 0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                            onClick = {
                                selectedPackage = pkg
                                currentLevel = EditorLevel.ACTIVITIES
                            },
                            showIndication = true,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicComponent(
                                    title = pkg,
                                    summary = buildString {
                                        if (name.isNotEmpty()) append(name).append(" · ")
                                        append("$actCount 个 Activity")
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { showDeleteConfirm = pkg }) {
                                    Icon(
                                        MiuixIcons.Delete,
                                        contentDescription = "删除",
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(
                            Modifier.padding(
                                bottom = WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues()
                                        .calculateBottomPadding(),
                            )
                        )
                    }
                }

                // ── Activity list ─────────────────────────────────────
                EditorLevel.ACTIVITIES -> {
                    item {
                        SmallTitle(
                            text = selectedPackage,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    // App name editor
                    item {
                        val appName = appForSelected?.optString("name", "") ?: ""
                        var isEditingName by remember { mutableStateOf(false) }
                        var nameField by remember(appName) { mutableStateOf(appName) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                            onClick = { isEditingName = true },
                            showIndication = true,
                        ) {
                            if (isEditingName) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    TextField(
                                        value = nameField,
                                        onValueChange = { nameField = it },
                                        label = "应用名称",
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TextButton(
                                            text = "取消",
                                            onClick = { isEditingName = false },
                                            enabled = true,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        TextButton(
                                            text = "保存",
                                            onClick = {
                                                appForSelected?.put("name", nameField.trim())
                                                saveRoot()
                                                isEditingName = false
                                            },
                                            colors = ButtonDefaults.textButtonColorsPrimary(),
                                            enabled = true,
                                        )
                                    }
                                }
                            } else {
                                BasicComponent(
                                    title = "应用名称",
                                    summary = if (appName.isNotEmpty()) "$appName（点击编辑）" else "未设置（点击编辑）",
                                )
                            }
                        }
                    }
                    item {
                        TextButton(
                            text = "添加 Activity",
                            onClick = {
                                newActName = ""
                                showAddActivityDialog = true
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }

                    items(actKeys) { actName ->
                        val actJson = activityRules.optJSONObject(actName)
                        val mode = actJson?.optIntOrNull("mode")
                        val modeLabel = mode?.let { m ->
                            MODE_LABELS.getOrNull(MODE_VALUES.indexOf(m))
                        } ?: "—"
                        val color = actJson?.opt("color")
                        val colorLabel = when (colorTypeFromValue(color)) {
                            COLOR_TYPE_AUTO -> "取色:自动"
                            COLOR_TYPE_CUSTOM -> "取色:#${(argbFromValue(color) and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()}"
                            else -> "取色:默认"
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                            onClick = {
                                selectedActivity = actName
                                currentLevel = EditorLevel.FIELDS
                            },
                            showIndication = true,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicComponent(
                                    title = actName,
                                    summary = "$modeLabel · $colorLabel",
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    showDeleteConfirm = "$selectedPackage::$actName"
                                }) {
                                    Icon(
                                        MiuixIcons.Delete,
                                        contentDescription = "删除",
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (actKeys.isEmpty()) {
                        item {
                            Text(
                                text = "暂无 Activity 规则，请点击上方添加",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                            )
                        }
                    }

                    item {
                        Spacer(
                            Modifier.padding(
                                bottom = WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues()
                                        .calculateBottomPadding(),
                            )
                        )
                    }
                }

                // ── Field editor ──────────────────────────────────────
                EditorLevel.FIELDS -> {
                    val act = currentAct

                    val mode = act.optIntOrNull("mode") ?: -1
                    val modeIdx = MODE_VALUES.indexOf(mode).coerceAtLeast(0)

                    val color = act.opt("color")
                    val colorType = colorTypeFromValue(color)
                    val argb = argbFromValue(color)

                    val sfMode = act.optIntOrNull("sf_sampling_mode") ?: 0
                    val sfIdx = SF_SAMPLING_VALUES.indexOf(sfMode).coerceAtLeast(0)

                    val dialogMode = act.optIntOrNull("dialogMode") ?: 1
                    val dialogIdx = DIALOG_POPUP_VALUES.indexOf(dialogMode).coerceAtLeast(0)

                    val popupModeVal = act.optIntOrNull("popupMode") ?: 1
                    val popupIdx = DIALOG_POPUP_VALUES.indexOf(popupModeVal).coerceAtLeast(0)

                    val appNavDisabled = act.optInt("appNavColorDisabled", 0) == 1

                    item {
                        SmallTitle(
                            text = "$selectedPackage / $selectedActivity",
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    // ── mode spinner ──
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                        ) {
                            WindowDropdownPreference(
                                title = "沉浸模式（mode）",
                                summary = MODE_LABELS[modeIdx],
                                items = MODE_LABELS,
                                selectedIndex = modeIdx,
                                onSelectedIndexChange = { updateActivityField("mode", MODE_VALUES[it]) },
                            )
                        }
                    }

                    // ── color ──
                    item {
                        var pickerColor by remember(argb) { mutableStateOf(Color(argb)) }
                        val aVal = (argb ushr 24) and 0xFF
                        val rVal = (argb ushr 16) and 0xFF
                        val gVal = (argb ushr 8) and 0xFF
                        val bVal = argb and 0xFF
                        var rgbaInput by remember(argb) { mutableStateOf("$rVal, $gVal, $bVal, $aVal") }
                        var hexInput by remember(argb) {
                            mutableStateOf("#${rVal.toString(16).padStart(2, '0')}${gVal.toString(16).padStart(2, '0')}${bVal.toString(16).padStart(2, '0')}${aVal.toString(16).padStart(2, '0')}".uppercase())
                        }
                        var showColorPicker by remember(colorType) { mutableStateOf(colorType == COLOR_TYPE_CUSTOM) }

                        /** Sync both inputs and picker from ARGB int */
                        fun syncFromArgb(newArgb: Int) {
                            pickerColor = Color(newArgb)
                            updateActivityField("color", newArgb)
                            val r = (newArgb ushr 16) and 0xFF
                            val g = (newArgb ushr 8) and 0xFF
                            val b = newArgb and 0xFF
                            val a = (newArgb ushr 24) and 0xFF
                            rgbaInput = "$r, $g, $b, $a"
                            hexInput = "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}${a.toString(16).padStart(2, '0')}".uppercase()
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                        ) {
                            Column {
                                WindowDropdownPreference(
                                    title = "导航栏颜色（color）",
                                    summary = when (colorType) {
                                        COLOR_TYPE_DEFAULT -> "默认（null）"
                                        COLOR_TYPE_AUTO -> "自动取色（1）"
                                        else -> "自定义颜色"
                                    },
                                    items = COLOR_TYPE_LABELS,
                                    selectedIndex = colorType,
                                    onSelectedIndexChange = { i ->
                                        when (i) {
                                            COLOR_TYPE_DEFAULT -> updateActivityField("color", JSONObject.NULL)
                                            COLOR_TYPE_AUTO -> updateActivityField("color", 1)
                                            COLOR_TYPE_CUSTOM -> updateActivityField("color", 0xFF000000.toInt())
                                        }
                                        showColorPicker = (i == COLOR_TYPE_CUSTOM)
                                    },
                                )
                                // Custom color editor - animated appearance
                                AnimatedVisibility(
                                    visible = showColorPicker && colorType == COLOR_TYPE_CUSTOM,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    Column {
                                        // Color preview + ARGB display
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(pickerColor),
                                            )
                                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                                Text(
                                                    text = "ARGB: $argb",
                                                    style = MiuixTheme.textStyles.body2,
                                                    color = MiuixTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    text = "R: $rVal  G: $gVal  B: $bVal  A: $aVal",
                                                    style = MiuixTheme.textStyles.footnote2,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                )
                                            }
                                        }
                                        // RGBA input field
                                        TextField(
                                            value = rgbaInput,
                                            onValueChange = { v ->
                                                rgbaInput = v
                                                val parts = v.split(",").map { it.trim() }
                                                if (parts.size == 4) {
                                                    val nr = parts[0].toIntOrNull()?.coerceIn(0, 255) ?: return@TextField
                                                    val ng = parts[1].toIntOrNull()?.coerceIn(0, 255) ?: return@TextField
                                                    val nb = parts[2].toIntOrNull()?.coerceIn(0, 255) ?: return@TextField
                                                    val na = parts[3].toIntOrNull()?.coerceIn(0, 255) ?: return@TextField
                                                    val newArgb = (na shl 24) or (nr shl 16) or (ng shl 8) or nb
                                                    syncFromArgb(newArgb)
                                                }
                                            },
                                            label = "RGBA（格式：R, G, B, A）",
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 4.dp),
                                        )
                                        // Hex input field
                                        TextField(
                                            value = hexInput,
                                            onValueChange = { v ->
                                                hexInput = v
                                                val trimmed = v.trim()
                                                if (trimmed.startsWith("#") && (trimmed.length == 7 || trimmed.length == 9)) {
                                                    try {
                                                        val hex = trimmed.substring(1)
                                                        val r = hex.substring(0, 2).toInt(16)
                                                        val g = hex.substring(2, 4).toInt(16)
                                                        val b = hex.substring(4, 6).toInt(16)
                                                        val a = if (hex.length == 8) hex.substring(6, 8).toInt(16) else 255
                                                        val newArgb = (a shl 24) or (r shl 16) or (g shl 8) or b
                                                        syncFromArgb(newArgb)
                                                    } catch (_: Exception) {}
                                                }
                                            },
                                            label = "HEX（格式：#RRGGBB / #RRGGBBAA）",
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 4.dp),
                                        )
                                        // ColorPicker
                                        ColorPicker(
                                            color = pickerColor,
                                            onColorChanged = { newColor ->
                                                syncFromArgb(newColor.toArgb())
                                            },
                                            colorSpace = ColorSpace.HSV,
                                            showPreview = false,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── sf_sampling_mode spinner ──
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                        ) {
                            WindowDropdownPreference(
                                title = "SF采样模式（sf_sampling_mode）",
                                summary = SF_SAMPLING_LABELS[sfIdx],
                                items = SF_SAMPLING_LABELS,
                                selectedIndex = sfIdx,
                                onSelectedIndexChange = { updateActivityField("sf_sampling_mode", SF_SAMPLING_VALUES[it]) },
                            )
                        }
                    }

                    // ── dialogMode spinner ──
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                        ) {
                            WindowDropdownPreference(
                                title = "对话框模式（dialogMode）",
                                summary = DIALOG_POPUP_LABELS[dialogIdx],
                                items = DIALOG_POPUP_LABELS,
                                selectedIndex = dialogIdx,
                                onSelectedIndexChange = { updateActivityField("dialogMode", DIALOG_POPUP_VALUES[it]) },
                            )
                        }
                    }

                    // ── popupMode spinner ──
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                        ) {
                            WindowDropdownPreference(
                                title = "弹窗模式（popupMode）",
                                summary = DIALOG_POPUP_LABELS[popupIdx],
                                items = DIALOG_POPUP_LABELS,
                                selectedIndex = popupIdx,
                                onSelectedIndexChange = { updateActivityField("popupMode", DIALOG_POPUP_VALUES[it]) },
                            )
                        }
                    }

                    // ── appNavColorDisabled ──
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp),
                        ) {
                            SwitchPreference(
                                checked = appNavDisabled,
                                onCheckedChange = { checked ->
                                    updateActivityField("appNavColorDisabled", if (checked) 1 else 0)
                                },
                                title = "禁用应用导航栏颜色",
                                summary = "appNavColorDisabled",
                            )
                        }
                    }

                    // ── delete activity ──
                    item {
                        TextButton(
                            text = "删除此 Activity",
                            onClick = {
                                showDeleteConfirm = "$selectedPackage::$selectedActivity"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }

                    item {
                        Spacer(
                            Modifier.padding(
                                bottom = WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues()
                                        .calculateBottomPadding(),
                            )
                        )
                    }
                }
            }
        }
    }

    // ── Add App dialog ─────────────────────────────────────────────────
    WindowDialog(
        title = "添加应用",
        show = showAddAppDialog,
        onDismissRequest = { showAddAppDialog = false },
    ) {
        TextField(
            modifier = Modifier.padding(vertical = 4.dp),
            value = newPkg,
            onValueChange = { newPkg = it },
            label = "应用包名",
            singleLine = true,
        )
        TextField(
            modifier = Modifier.padding(vertical = 4.dp),
            value = newName,
            onValueChange = { newName = it },
            label = "应用名称（可选）",
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = "取消",
                onClick = { showAddAppDialog = false },
            )
            Spacer(Modifier.width(12.dp))
            TextButton(
                text = "确定",
                onClick = {
                    val pkg = newPkg.trim()
                    if (pkg.isEmpty()) return@TextButton
                    if (!nbiRules.has(pkg)) {
                        val newApp = JSONObject()
                        newApp.put("name", newName.trim())
                        newApp.put("enable", true)
                        newApp.put("activityRules", JSONObject())
                        nbiRules.put(pkg, newApp)
                        saveRoot()
                    }
                    showAddAppDialog = false
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    // ── Add Activity dialog ────────────────────────────────────────────
    WindowDialog(
        title = "添加 Activity",
        show = showAddActivityDialog,
        onDismissRequest = { showAddActivityDialog = false },
    ) {
        TextField(
            modifier = Modifier.padding(vertical = 4.dp),
            value = newActName,
            onValueChange = { newActName = it },
            label = "Activity 名称（如 com.example.MainActivity 或 *）",
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = "取消",
                onClick = { showAddActivityDialog = false },
            )
            Spacer(Modifier.width(12.dp))
            TextButton(
                text = "确定",
                onClick = {
                    val actName = newActName.trim()
                    if (actName.isEmpty()) return@TextButton
                    val app = nbiRules.optJSONObject(selectedPackage)
                    val rules = app?.optJSONObject("activityRules")
                        ?: JSONObject().also { app?.put("activityRules", it) }
                    if (!rules.has(actName)) {
                        val newAct = JSONObject()
                        newAct.put("mode", 1)
                        newAct.put("color", 1)
                        newAct.put("sf_sampling_mode", 0)
                        newAct.put("dialogMode", 1)
                        newAct.put("popupMode", 1)
                        newAct.put("appNavColorDisabled", 0)
                        rules.put(actName, newAct)
                        saveRoot()
                    }
                    showAddActivityDialog = false
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    // ── Delete confirmation ────────────────────────────────────────────
    WindowDialog(
        title = "确认删除",
        summary = "确定要删除此条目吗？此操作不可撤销。",
        show = showDeleteConfirm.isNotEmpty(),
        onDismissRequest = { showDeleteConfirm = "" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = "取消",
                onClick = { showDeleteConfirm = "" },
            )
            Spacer(Modifier.width(12.dp))
            TextButton(
                text = "删除",
                onClick = {
                    val target = showDeleteConfirm
                    if (target.contains("::")) {
                        val parts = target.split("::")
                        deleteActivity(parts[0], parts[1])
                        if (currentLevel == EditorLevel.FIELDS) {
                            currentLevel = EditorLevel.ACTIVITIES
                        }
                    } else {
                        deleteApp(target)
                        if (currentLevel != EditorLevel.APPS && selectedPackage == target) {
                            currentLevel = EditorLevel.APPS
                        }
                    }
                    showDeleteConfirm = ""
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

