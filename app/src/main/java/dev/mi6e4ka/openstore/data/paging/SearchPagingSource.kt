package dev.mi6e4ka.openstore.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.mi6e4ka.openstore.data.api.ApiService
import dev.mi6e4ka.openstore.data.model.SearchResult

class SearchPagingSource(
    private val api: ApiService,
    private val query: String
) : PagingSource<Int, SearchResult>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchResult> {
        return try {
            val page = params.key ?: 0
            val response = api.search(
                pageSize = params.loadSize,
                query = query,
                pageNumber = page
            )
            LoadResult.Page(
                data = response.body.content,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (response.body.content.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SearchResult>): Int? {
        return state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }
    }
}
