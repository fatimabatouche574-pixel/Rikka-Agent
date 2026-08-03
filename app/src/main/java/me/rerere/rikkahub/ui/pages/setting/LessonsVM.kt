package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.lesson.Lesson
import me.rerere.rikkahub.data.lesson.LessonRepository

/**
 * Phase 21 / US4 — VM backing [LessonsPage]. Lists lessons for the *currently-selected*
 * assistant (the active one); delete propagates via [LessonRepository.delete].
 *
 * The active assistant id is the same value Settings → "current assistant" points at, so
 * the page reflects what the user is actually talking to. Disabling `enableLessons` on the
 * assistant stops new captures but never deletes existing lessons (spec assumption /
 * contract §6), so the list is repopulated even when the toggle is off.
 */
class LessonsVM(
    private val settingsStore: SettingsStore,
    private val lessonRepository: LessonRepository,
) : ViewModel() {

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    init {
        viewModelScope.launch {
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val settings = settingsStore.settingsFlow.value
            val assistantId = settings.assistantId.toString()
            _lessons.value = lessonRepository.lessonsFor(assistantId)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            lessonRepository.delete(id)
            refresh()
        }
    }
}