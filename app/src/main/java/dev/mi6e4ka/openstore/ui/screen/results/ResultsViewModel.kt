package dev.mi6e4ka.openstore.ui.screen.results

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.mi6e4ka.openstore.data.model.SearchResult
import dev.mi6e4ka.openstore.di.AppModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

data class Platform(
    val label: String,
    val value: String,
    val icon: ImageVector
)
data class ResultsState(
    val selectedPlatform: Platform = Platform("Mobile", "mobile", Icons.Default.PhoneAndroid),
)

class ResultsViewModel(private val query: String) : ViewModel() {
    private val _state = MutableStateFlow(ResultsState())
    val state: StateFlow<ResultsState> = _state.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchFlow: Flow<PagingData<SearchResult>> = state.flatMapLatest {
        AppModule.searchRepository.search(query, it.selectedPlatform.value).flow
    }.cachedIn(viewModelScope)

    fun selectPlatform(newPlatform: Platform) {
        _state.update { it.copy(selectedPlatform = newPlatform) }
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
