package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.MemoryRepository

/**
 * Phase 18 — standalone Global-Memory management VM. Backs [GlobalMemoryPage].
 *
 * Mirrors what the in-conversation `memory_tool` already supports (US2 / FR-011): the agent
 * (or any assistant using global memory) writes into [MemoryRepository.GLOBAL_MEMORY_ID]; this
 * page exposes those shared rows for direct view/edit/delete so the user no longer has to
 * pick an assistant to manage global memories. The underlying sink is the same one
 * injected at prompt build (`MemoryRepository`), so edits here surface in the very next
 * conversation turn — on either surface (chat or Telegram), per FR-018 parity.
 */
class GlobalMemoryVM(
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    val memories: StateFlow<List<AssistantMemory>> =
        memoryRepository.getGlobalMemoriesFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addMemory(content: String) {
        viewModelScope.launch {
            memoryRepository.addMemory(
                assistantId = MemoryRepository.GLOBAL_MEMORY_ID,
                content = content,
            )
        }
    }

    fun updateMemory(memory: AssistantMemory) {
        viewModelScope.launch {
            runCatching {
                memoryRepository.updateContent(id = memory.id, content = memory.content)
            }
        }
    }

    fun deleteMemory(memory: AssistantMemory) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id = memory.id)
        }
    }
}