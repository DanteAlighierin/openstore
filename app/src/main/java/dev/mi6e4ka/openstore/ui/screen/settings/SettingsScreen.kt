package dev.mi6e4ka.openstore.ui.screen.settings

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
import androidx.compose.material3.ElevatedCard
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
    val description: Int,
    val icon: Int,
    val type: ParamType,
    val defaultVal: String,
    val select: List<SettingsSectionSelect>? = null
)

data class SettingsSectionSelect(
    val title: String,
    val value: String
)

enum class ParamType {
    SWITCH,
    SELECT,
    MULTI_SELECT
}

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
            defaultVal = "",
            select = listOf(
                SettingsSectionSelect(
                    title = "OpenStore",
                    value = "dev.mi6e4ka.openstore"
                ),
                SettingsSectionSelect(
                    title = "RuStore",
                    value = "ru.vk.store"
                ),
                SettingsSectionSelect(
                    title = "Google Play",
                    value = "com.android.vending"
                ),
                SettingsSectionSelect(
                    title = "Manual Installed",
                    value = "com.android.packageinstaller"
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
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        {navController.popBackStack()}
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

    var booleanSelect by remember { mutableStateOf(
        if (item.type == ParamType.SWITCH) prefs.getBoolean(item.id, false) else false
    )}

    Card (
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .clickable{
                    if (item.type != ParamType.SWITCH) {
                        showDialog = true
                    } else {
                        prefs.edit { putBoolean(item.id, !booleanSelect) }
                        booleanSelect = !booleanSelect
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
                Text(stringResource(item.description), color = Color.Gray, fontWeight = FontWeight.Normal)
            }
            if (item.type == ParamType.SWITCH) {

                Switch(
                    booleanSelect,
                    {
                        v -> prefs.edit { putBoolean(item.id, v) }; booleanSelect = v
                    }
                )
            }
        }
        if (showDialog) {
            val selected = remember { mutableStateListOf<SettingsSectionSelect>() }
            if (item.type == ParamType.MULTI_SELECT) {
                selected.apply { addAll(prefs.getString(item.id, item.defaultVal)!!.split(";").mapNotNull {
                        v ->
                    item.select?.firstOrNull { it.value == v }
                })
                }
            }
            var selectedVal by remember { mutableStateOf(prefs.getString(item.id, item.defaultVal)) }
            AlertDialog(
                onDismissRequest = { showDialog = false },

                title = {
                    Text(stringResource(item.title))
                },

                text = {
                    LazyColumn {
                        items(item.select?:listOf()) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.type == ParamType.MULTI_SELECT) {
                                            if (entry in selected) {
                                                selected.remove(entry)
                                            } else {
                                                selected.add(entry)
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
                                        checked = entry in selected,
                                        onCheckedChange = {
                                            if (it) selected.add(entry)
                                            else selected.remove(entry)
                                        }
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
                        if (item.type == ParamType.MULTI_SELECT) {
                            prefs.edit { putString(item.id, selected.joinToString(";") { it.value }) }

                        } else {
                            prefs.edit { putString(item.id, selectedVal) }
                        }
                        showDialog = false
                    }) {
                        Text(stringResource(R.string.settings_save_button))
                    }
                }
            )
        }
    }


}
