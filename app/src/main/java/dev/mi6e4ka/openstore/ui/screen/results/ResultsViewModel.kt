package dev.mi6e4ka.openstore.ui.screen.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.mi6e4ka.openstore.data.model.SearchResult
import dev.mi6e4ka.openstore.di.AppModule
import kotlinx.coroutines.flow.Flow

class ResultsViewModel(private val query: String) : ViewModel() {

    val searchFlow: Flow<PagingData<SearchResult>> =
        AppModule.searchRepository.search(query).flow.cachedIn(viewModelScope)

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
