package dev.mi6e4ka.openstore.ui.screen.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mi6e4ka.openstore.R
import androidx.core.content.edit

data class SettingsSection(
    val id: String,
    val title: Int,
    val description: Int?,
    val icon: Int,
    val type: ParamType,
    val defaultVal: String,
    val select: List<SettingsSectionSelect>? = null
)

data class SettingsSectionSelect(
    val title: String,
    val value: String,
    val isGroup: Boolean = false,
    val groupPackages: List<String> = emptyList()
)

enum class ParamType {
    SWITCH,
    SELECT,
    MULTI_SELECT
}

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val sections = listOf(
        SettingsSection(
            id = "installer_type",
            title = R.string.settings_installer_title,
            description = R.string.settings_installer_description,
            icon = R.drawable.apk_install_24px,
            type = ParamType.SELECT,
            defaultVal = "session",
            select = listOf(
                SettingsSectionSelect(
                    title = "Session",
                    value = "session"
                ),
                SettingsSectionSelect(
                    title = "Intent",
                    value = "intent"
                )
            )
        ),
        SettingsSection(
            id = "updates_sources",
            title = R.string.settings_updates_sources_title,
            description = R.string.settings_updates_sources_description,
            icon = R.drawable.ic_update_24px,
            type = ParamType.MULTI_SELECT,
            defaultVal = "dev.mi6e4ka.openstore;dev.mi6e4ka.openstore.nightly;dev.mi6e4ka.openstore.debug;ru.vk.store",
            select = listOf(
                SettingsSectionSelect(
                    title = "OpenStore",
                    value = "group_openstore",
                    isGroup = true,
                    groupPackages = listOf(
                        "dev.mi6e4ka.openstore",
                        "dev.mi6e4ka.openstore.nightly",
                        "dev.mi6e4ka.openstore.debug"
                    )
                ),
                SettingsSectionSelect(
                    title = "RuStore",
                    value = "ru.vk.store"
                ),
                SettingsSectionSelect(
                    title = stringResource(R.string.settings_source_aurora_store),
                    value = "com.aurora.store"
                ),
                SettingsSectionSelect(
                    title = "Google Play",
                    value = "com.android.vending"
                ),
                SettingsSectionSelect(
                    title = stringResource(R.string.settings_group_file_managers),
                    value = "group_filemanagers",
                    isGroup = true,
                    groupPackages = listOf(
                        "com.hihonor.filemanager",
                        "com.google.android.documentsui",
                        "com.android.documentsui",
                        "com.google.android.apps.nbu.files",
                        "com.mi.android.globalFileexplorer"
                    )
                ),
                SettingsSectionSelect(
                    title = stringResource(R.string.settings_group_browsers),
                    value = "group_browsers",
                    isGroup = true,
                    groupPackages = listOf(
                        "com.android.chrome",
                        "com.yandex.browser"
                    )
                ),
                SettingsSectionSelect(
                    title = stringResource(R.string.settings_source_manual),
                    value = "com.android.packageinstaller"
                ),
                SettingsSectionSelect(
                    title = stringResource(R.string.settings_source_unknown),
                    value = "null"
                )
            )
        ),
        SettingsSection(
            id = "updates_check_all",
            title = R.string.settings_updates_check_all_title,
            description = R.string.settings_updates_check_all_description,
            icon = R.drawable.ic_arrow_circle_up_24px,
            type = ParamType.SWITCH,
            defaultVal = ""
        ),
        SettingsSection(
            id = "app_theme",
            title = R.string.settings_app_theme_title,
            description = null,
            icon = R.drawable.ic_brightness_6_24px,
            type = ParamType.SELECT,
            defaultVal = "auto",
            select = listOf(
                SettingsSectionSelect(
                    title = "Auto",
                    value = "auto"
                ),
                SettingsSectionSelect(
                    title = "Dark",
                    value = "dark"
                ),
                SettingsSectionSelect(
                    title = "Light",
                    value = "light"
                ),
            )
        )
    )
    val context = LocalContext.current as? Activity
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        {
                            val popped = navController.popBackStack()

                            if (!popped) {
                                context?.finish()
                            }
                        }
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24px), "back")
                    }
                }
            )
        }
    ) {
        innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            items(sections) { item ->
                SettingsCard(
                    item = item
                )
            }
        }
    }
}

@Composable
fun SettingsCard(
    item: SettingsSection
) {

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var showDialog by remember { mutableStateOf(false) }

    var currentValue by remember {
        mutableStateOf<Any>(
            if (item.type == ParamType.SWITCH) prefs.getBoolean(item.id, false)
            else prefs.getString(item.id, item.defaultVal) ?: item.defaultVal
        )
    }

    val description = if (item.description != null && item.description != 0) {
        stringResource(item.description)
    } else if (item.type != ParamType.SWITCH) {
        val strVal = currentValue as? String ?: ""
        val selectedValues = strVal.split(";").filter { it.isNotEmpty() }.toSet()
        item.select?.filter { entry ->
            if (entry.isGroup) {
                entry.groupPackages.any { it in selectedValues }
            } else {
                entry.value in selectedValues
            }
        }?.joinToString(", ") { it.title } ?: ""
    } else ""

    Card (
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .clickable{
                    if (item.type == ParamType.SWITCH) {
                        val newValue = !(currentValue as Boolean)
                        prefs.edit { putBoolean(item.id, newValue) }
                        currentValue = newValue
                    } else {
                        showDialog = true
                    }
                }
                .padding(15.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Icon(
                painterResource(item.icon),
                "icon",
                modifier = Modifier.size(32.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(item.title), fontWeight = FontWeight.Medium)
                if (description.isNotEmpty()) {
                    Text(description, color = Color.Gray, fontWeight = FontWeight.Normal)
                }
            }
            if (item.type == ParamType.SWITCH) {
                Switch(
                    currentValue as Boolean,
                    { v ->
                        prefs.edit { putBoolean(item.id, v) }
                        currentValue = v
                    }
                )
            }
        }
        if (showDialog) {
            val selectedPackages = remember { 
                mutableStateListOf<String>().apply {
                    val currentStr = currentValue as? String ?: ""
                    addAll(currentStr.split(";").filter { it.isNotEmpty() })
                }
            }
            
            var selectedVal by remember { mutableStateOf(currentValue as? String) }
            
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(item.title)) },
                text = {
                    LazyColumn {
                        items(item.select ?: listOf()) { entry ->
                            val isChecked = if (entry.isGroup) {
                                entry.groupPackages.all { it in selectedPackages }
                            } else {
                                entry.value in selectedPackages
                            }
                            
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.type == ParamType.MULTI_SELECT) {
                                            if (entry.isGroup) {
                                                if (isChecked) {
                                                    selectedPackages.removeAll(entry.groupPackages)
                                                } else {
                                                    entry.groupPackages.forEach { 
                                                        if (it !in selectedPackages) selectedPackages.add(it)
                                                    }
                                                }
                                            } else {
                                                if (isChecked) {
                                                    selectedPackages.remove(entry.value)
                                                } else {
                                                    selectedPackages.add(entry.value)
                                                }
                                            }
                                        } else {
                                            selectedVal = entry.value
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.type == ParamType.SELECT) {
                                    RadioButton(
                                        selected = selectedVal == entry.value,
                                        onClick = { selectedVal = entry.value }
                                    )
                                } else {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = null // Click handled by Row
                                    )
                                }

                                Text(
                                    entry.title,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newValue = if (item.type == ParamType.MULTI_SELECT) {
                            selectedPackages.joinToString(";")
                        } else {
                            selectedVal ?: item.defaultVal
                        }
                        prefs.edit { putString(item.id, newValue) }
                        currentValue = newValue
                        showDialog = false
                    }) {
                        Text(stringResource(R.string.settings_save_button))
                    }
                }
            )
        }
    }
}
