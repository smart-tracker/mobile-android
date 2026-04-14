package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.UserPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit-тесты маппера RegisterRequest → RegisterRequestDto.
 *
 * Покрывает:
 * - Маппинг goal_ids (BUG-1: пустой список для EXPLORING/OTHER нарушает minItems:1)
 * - Приоритет явного списка roleIds над purpose
 * - Формат birthDate (ISO yyyy-MM-dd)
 * - Преобразование gender в строку нижнего регистра
 */
class RegisterRequestDtoTest {

    private fun makeRequest(
        purpose: UserPurpose = UserPurpose.ATHLETE,
        roleIds: List<Int> = emptyList(),
        gender: Gender = Gender.MALE,
    ) = RegisterRequest(
        firstName       = "Ivan",
        username        = "ivan_test",
        birthDate       = LocalDate.of(2000, 5, 4),
        gender          = gender,
        purpose         = purpose,
        roleIds         = roleIds,
        email           = "ivan@example.com",
        password        = "Secret1234",
        confirmPassword = "Secret1234",
    )

    // ── goal_ids маппинг ──────────────────────────────────────────────────────

    @Test
    fun `ATHLETE purpose с пустым roleIds даёт goalIds = listOf(1)`() {
        val dto = makeRequest(purpose = UserPurpose.ATHLETE).toDto()
        assertEquals(listOf(1), dto.goalIds)
    }

    @Test
    fun `TRAINER purpose с пустым roleIds даёт goalIds = listOf(2)`() {
        val dto = makeRequest(purpose = UserPurpose.TRAINER).toDto()
        assertEquals(listOf(2), dto.goalIds)
    }

    @Test
    fun `CLUB_OWNER purpose с пустым roleIds даёт goalIds = listOf(3)`() {
        val dto = makeRequest(purpose = UserPurpose.CLUB_OWNER).toDto()
        assertEquals(listOf(3), dto.goalIds)
    }

    @Test
    fun `EXPLORING purpose с пустым roleIds даёт goalIds = listOf(1) (дефолт)`() {
        // Исправление BUG-1: вместо пустого списка (HTTP 422) отправляем goal_id=1.
        val dto = makeRequest(purpose = UserPurpose.EXPLORING).toDto()
        assertEquals(listOf(1), dto.goalIds)
    }

    @Test
    fun `OTHER purpose с пустым roleIds даёт goalIds = listOf(1) (дефолт)`() {
        // Исправление BUG-1: вместо пустого списка (HTTP 422) отправляем goal_id=1.
        val dto = makeRequest(purpose = UserPurpose.OTHER).toDto()
        assertEquals(listOf(1), dto.goalIds)
    }

    @Test
    fun `явный roleIds имеет приоритет над purpose`() {
        val dto = makeRequest(purpose = UserPurpose.ATHLETE, roleIds = listOf(2, 3)).toDto()
        assertEquals(listOf(2, 3), dto.goalIds)
    }

    @Test
    fun `явный roleIds приоритетнее даже если purpose даёт другое значение`() {
        val dto = makeRequest(purpose = UserPurpose.TRAINER, roleIds = listOf(1)).toDto()
        assertEquals(listOf(1), dto.goalIds)
    }

    // ── birthDate формат ──────────────────────────────────────────────────────

    @Test
    fun `birthDate сериализуется в формат yyyy-MM-dd`() {
        val dto = makeRequest().toDto()
        // LocalDate.toString() гарантирует ISO-8601: "2000-05-04"
        assertEquals("2000-05-04", dto.birthDate)
    }

    @Test
    fun `birthDate однозначно и двухзначно для месяца и дня`() {
        val request = makeRequest().copy(birthDate = LocalDate.of(1999, 1, 3))
        val dto = request.toDto()
        assertEquals("1999-01-03", dto.birthDate)
    }

    // ── gender форматирование ─────────────────────────────────────────────────

    @Test
    fun `MALE gender преобразуется в строку lowercase male`() {
        val dto = makeRequest(gender = Gender.MALE).toDto()
        assertEquals("male", dto.gender)
    }

    @Test
    fun `FEMALE gender преобразуется в строку lowercase female`() {
        val dto = makeRequest(gender = Gender.FEMALE).toDto()
        assertEquals("female", dto.gender)
    }

    // ── поле username → SerializedName nickname ───────────────────────────────

    @Test
    fun `username из domain попадает в поле username DTO (серализуется как nickname)`() {
        val dto = makeRequest().toDto()
        // В DTO поле называется `username` (со @SerializedName("nickname"))
        // Gson при сериализации отправит "nickname": "ivan_test" на сервер
        assertEquals("ivan_test", dto.username)
    }
}
