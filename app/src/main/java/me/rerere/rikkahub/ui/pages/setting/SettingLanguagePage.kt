package me.rerere.rikkahub.ui.pages.setting

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

private data class LanguageOption(val tag: String, val labelRes: Int)

private val LANGUAGE_OPTIONS = listOf(
    LanguageOption("en", R.string.language_english),
    LanguageOption("fr", R.string.language_french),
    LanguageOption("de", R.string.language_german),
    LanguageOption("it", R.string.language_italian),
    LanguageOption("ja", R.string.language_japanese),
    LanguageOption("ko", R.string.language_korean),
    LanguageOption("zh-CN", R.string.language_simplified_chinese),
    LanguageOption("zh-TW", R.string.language_traditional_chinese),
    LanguageOption("es", R.string.language_spanish),
    LanguageOption("ar", R.string.language_arabic),
    LanguageOption("fa", R.string.language_persian),
    LanguageOption("ur", R.string.language_urdu),
    LanguageOption("in", R.string.language_indonesian),
)

@Composable
fun SettingLanguagePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentLanguage = settings.appLanguage

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_language_title))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    LANGUAGE_OPTIONS.forEach { option ->
                        item(
                            onClick = {
                                vm.setAppLanguage(option.tag)
                                (context as? Activity)?.recreate()
                            },
                            headlineContent = {
                                Text(stringResource(option.labelRes))
                            },
                            trailingContent = {
                                if (option.tag == currentLanguage) {
                                    Icon(
                                        imageVector = HugeIcons.Tick01,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
