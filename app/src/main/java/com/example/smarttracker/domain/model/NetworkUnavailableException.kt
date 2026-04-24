package com.example.smarttracker.domain.model

/**
 * Сеть недоступна — оборачивает java.io.IOException из Retrofit/OkHttp.
 *
 * Используется в presentation-слое для принятия решения о постановке
 * операции в офлайн-очередь. Изолирует зависимость от Retrofit:
 * ViewModel и Worker не импортируют retrofit2.*.
 */
class NetworkUnavailableException(cause: Throwable? = null) :
    Exception("Сеть недоступна", cause)
