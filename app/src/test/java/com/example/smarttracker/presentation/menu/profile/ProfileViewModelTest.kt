package com.example.smarttracker.presentation.menu.profile

import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.domain.model.User
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.time.LocalDate

/**
 * Unit-тесты ProfileViewModel.
 *
 * Покрывает:
 *  - Успешную загрузку профиля: форматирование полей для UI
 *    (username с «@», дата «ДД.ММ.ГГГГ», пол по-русски, вес/рост целыми)
 *  - Дату последней тренировки — максимум по полю date из истории
 *  - Ошибку загрузки профиля → errorMessage, isLoading=false
 *  - Ошибку загрузки истории — НЕ ломает отображение профиля
 *  - refreshFromCache: тихое обновление полей + инкремент photoKey
 *    только при смене photoUrl (форсирует Coil перезагрузить картинку)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var workoutRepository: WorkoutRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testUser(photoUrl: String? = "https://api/photo1.png") = User(
        id = 1,
        firstName = "Иван",
        lastName = "Петров",
        middleName = null,
        username = "ivan123",
        email = "ivan@yandex.ru",
        birthDate = LocalDate.of(1990, 1, 15),
        gender = Gender.MALE,
        weight = 72.0f,
        height = 180.0f,
        photoUrl = photoUrl,
    )

    private fun historyItem(date: LocalDate) = TrainingHistoryItem(
        trainingId = "t-${date}",
        typeActivId = 1,
        date = date,
        timeStart = null,
        timeEnd = null,
        kilocalories = null,
        distanceM = null,
        avgSpeed = null,
        elevationGain = null,
    )

    /** ViewModel создаётся ПОСЛЕ стабов: init сразу запускает loadProfile(). */
    private fun createViewModel(
        userResult: Result<User> = Result.success(testUser()),
        historyResult: Result<List<TrainingHistoryItem>> = Result.success(emptyList()),
    ): ProfileViewModel {
        authRepository = mock {
            onBlocking { getUserInfo() } doReturn userResult
        }
        workoutRepository = mock {
            onBlocking { getTrainingHistory() } doReturn historyResult
        }
        return ProfileViewModel(authRepository, workoutRepository)
    }

    // ── Загрузка профиля ──────────────────────────────────────────────────────

    @Test
    fun `успешная загрузка - поля отформатированы для UI`() = runTest {
        val vm = createViewModel()

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNull(s.errorMessage)
        assertEquals("Иван", s.firstName)
        assertEquals("@ivan123", s.username)
        assertEquals("15.01.1990", s.birthDate)
        assertEquals("Мужской", s.gender)
        assertEquals("72", s.weight)
        assertEquals("180", s.height)
    }

    @Test
    fun `дата последней тренировки - максимум по date из истории`() = runTest {
        val vm = createViewModel(
            historyResult = Result.success(
                listOf(
                    historyItem(LocalDate.of(2026, 5, 10)),
                    historyItem(LocalDate.of(2026, 6, 1)),
                    historyItem(LocalDate.of(2026, 3, 2)),
                )
            )
        )

        assertEquals("01.06.2026", vm.state.value.lastTrainingDate)
    }

    @Test
    fun `пустая история - lastTrainingDate null`() = runTest {
        val vm = createViewModel(historyResult = Result.success(emptyList()))

        assertNull(vm.state.value.lastTrainingDate)
    }

    @Test
    fun `ошибка загрузки профиля - errorMessage и isLoading false`() = runTest {
        val vm = createViewModel(userResult = Result.failure(Exception("Сеть недоступна")))

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals("Сеть недоступна", s.errorMessage)
    }

    @Test
    fun `ошибка истории не ломает профиль`() = runTest {
        val vm = createViewModel(historyResult = Result.failure(Exception("500")))

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNull(s.errorMessage)
        assertEquals("Иван", s.firstName)
        assertNull(s.lastTrainingDate)
    }

    // ── refreshFromCache ──────────────────────────────────────────────────────

    @Test
    fun `refreshFromCache обновляет поля без индикатора загрузки`() = runTest {
        val vm = createViewModel()
        authRepository.stub {
            onBlocking { getUserInfo() } doReturn
                Result.success(testUser().copy(firstName = "Пётр"))
        }

        vm.refreshFromCache()

        val s = vm.state.value
        assertEquals("Пётр", s.firstName)
        assertFalse(s.isLoading)
    }

    @Test
    fun `refreshFromCache - смена photoUrl инкрементирует photoKey`() = runTest {
        val vm = createViewModel()
        val keyBefore = vm.state.value.photoKey
        authRepository.stub {
            onBlocking { getUserInfo() } doReturn
                Result.success(testUser(photoUrl = "https://api/photo2.png"))
        }

        vm.refreshFromCache()

        assertEquals(keyBefore + 1, vm.state.value.photoKey)
    }

    @Test
    fun `refreshFromCache - тот же photoUrl не меняет photoKey`() = runTest {
        val vm = createViewModel()
        val keyBefore = vm.state.value.photoKey

        vm.refreshFromCache()

        assertEquals(keyBefore, vm.state.value.photoKey)
    }

    @Test
    fun `refreshFromCache - ошибка не затирает показанные данные`() = runTest {
        val vm = createViewModel()
        authRepository.stub {
            onBlocking { getUserInfo() } doReturn Result.failure(Exception("offline"))
        }

        vm.refreshFromCache()

        val s = vm.state.value
        assertEquals("Иван", s.firstName)
        assertNull(s.errorMessage)
        assertNotNull(s.username)
    }
}
