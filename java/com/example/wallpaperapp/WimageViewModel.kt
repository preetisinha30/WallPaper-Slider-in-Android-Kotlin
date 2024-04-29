package com.example.wallpaperapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch


class WimageViewModel(private val repository: wImageRepository) : ViewModel() {

    // Using LiveData and caching what allWords returns has several benefits:
    // - We can put an observer on the data (instead of polling for changes) and only update the
    //   the UI when the data actually changes.
    // - Repository is completely separated from the UI through the ViewModel.
    val allWimages: LiveData<List<Wimages>> = repository.allWimages.asLiveData()

    /**
     * Launching a new coroutine to insert the data in a non-blocking way
     */
    fun insert(wimages: Wimages) = viewModelScope.launch {
        repository.insert(wimages)
    }

    fun delete(wimages: Wimages) = viewModelScope.launch {
        repository.delete(wimages)
    }
}

class WimageViewModelFactory(private val repository: wImageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WimageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WimageViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}