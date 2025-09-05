package dev.mi6e4ka.openstore.ui.screen.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.mi6e4ka.openstore.data.model.SearchResult
import dev.mi6e4ka.openstore.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ResultsState(
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null
)

class ResultsViewModel(private val query: String) : ViewModel() {
    private val _state = MutableStateFlow(ResultsState())
    val state: StateFlow<ResultsState> = _state.asStateFlow()

    fun getSearchFlow(): Flow<PagingData<SearchResult>> {
        return AppModule.searchRepository.search(query).flow.cachedIn(viewModelScope)
    }
}

class ResultsViewModelFactory(
    private val query: String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResultsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ResultsViewModel(query) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
