package com.recbox.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Temporary local session store. Replace with DataStore + backend auth in production. */
class SessionRepository {
    private val _phone = MutableStateFlow<String?>(null)
    val phone: StateFlow<String?> = _phone.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    fun setPhone(phone: String) { _phone.value = phone }
    fun completeAuthentication() { _isAuthenticated.value = true }
    fun clear() { _phone.value = null; _isAuthenticated.value = false }
}
