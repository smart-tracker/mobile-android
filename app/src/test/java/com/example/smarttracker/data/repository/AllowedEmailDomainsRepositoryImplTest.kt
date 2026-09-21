package com.example.smarttracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.smarttracker.data.remote.AuthApiService
import com.example.smarttracker.data.remote.dto.AllowedEmailDomainsResponseDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.File
import java.io.IOException

/**
 * Юнит-тесты [AllowedEmailDomainsRepositoryImpl] — цепочка источников BR-4:
 * сеть → DataStore-кэш → зашитый fallback.
 *
 * DataStore создаётся на временном файле через [PreferenceDataStoreFactory]
 * (ради этого зависимость и вынесена в конструктор) — чистый JVM, без
 * Robolectric. Персистентность проверяется честно: второй экземпляр
 * репозитория с упавшей сетью читает то, что сохранил первый.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AllowedEmailDomainsRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        dataStoreScope = CoroutineScope(dispatcher) + Job()
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            // Расширение обязательно .preferences_pb — фабрика проверяет
            File(tmpFolder.root, "test_domains.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    private fun apiReturning(domains: List<String>?): AuthApiService = mock {
        onBlocking { getAllowedEmailDomains() } doReturn AllowedEmailDomainsResponseDto(domains)
    }

    private fun apiFailing(): AuthApiService = mock {
        // doAnswer вместо doThrow: suspend-сигнатура не декларирует checked
        // IOException, и валидация Mockito отклонила бы прямой doThrow
        onBlocking { getAllowedEmailDomains() } doAnswer { throw IOException("offline") }
    }

    @Test
    fun `сеть ОК - серверный список, нормализованный trim+lowercase`() = runTest(dispatcher) {
        val repo = AllowedEmailDomainsRepositoryImpl(
            apiReturning(listOf(" Yandex.RU ", "mail.ru", "", "PetrSU.ru")), dataStore)

        assertEquals(setOf("yandex.ru", "mail.ru", "petrsu.ru"), repo.getAllowedDomains())
    }

    @Test
    fun `повторный вызов - из памяти, api дёргается один раз`() = runTest(dispatcher) {
        val api = apiReturning(listOf("yandex.ru"))
        val repo = AllowedEmailDomainsRepositoryImpl(api, dataStore)

        repo.getAllowedDomains()
        repo.getAllowedDomains()

        verify(api, times(1)).getAllowedEmailDomains()
    }

    @Test
    fun `сбой сети без кэша - зашитый fallback`() = runTest(dispatcher) {
        val repo = AllowedEmailDomainsRepositoryImpl(apiFailing(), dataStore)

        assertEquals(
            AllowedEmailDomainsRepositoryImpl.HARDCODED_RUSSIAN_DOMAINS,
            repo.getAllowedDomains(),
        )
    }

    @Test
    fun `сбой сети - последний серверный список из DataStore, не хардкод`() = runTest(dispatcher) {
        // Первый запуск: сервер отдал расширенный список (с вузовским доменом),
        // репозиторий сохранил его в DataStore
        val serverList = setOf("yandex.ru", "mail.ru", "petrsu.ru")
        AllowedEmailDomainsRepositoryImpl(
            apiReturning(serverList.toList()), dataStore).getAllowedDomains()

        // «Перезапуск без сети»: новый экземпляр (память пуста), сеть падает
        val offlineRepo = AllowedEmailDomainsRepositoryImpl(apiFailing(), dataStore)

        assertEquals(serverList, offlineRepo.getAllowedDomains())
    }

    @Test
    fun `пустой список от сервера - трактуется как сбой, fallback`() = runTest(dispatcher) {
        val repo = AllowedEmailDomainsRepositoryImpl(apiReturning(emptyList()), dataStore)

        assertEquals(
            AllowedEmailDomainsRepositoryImpl.HARDCODED_RUSSIAN_DOMAINS,
            repo.getAllowedDomains(),
        )
    }

    @Test
    fun `null вместо массива domains - fallback без исключений`() = runTest(dispatcher) {
        val repo = AllowedEmailDomainsRepositoryImpl(apiReturning(null), dataStore)

        assertEquals(
            AllowedEmailDomainsRepositoryImpl.HARDCODED_RUSSIAN_DOMAINS,
            repo.getAllowedDomains(),
        )
    }

    @Test
    fun `после сбоя сеть не мемоизируется - следующий вызов пробует снова`() = runTest(dispatcher) {
        val api = apiFailing()
        val repo = AllowedEmailDomainsRepositoryImpl(api, dataStore)

        repo.getAllowedDomains()
        repo.getAllowedDomains()

        verify(api, times(2)).getAllowedEmailDomains()
    }
}
