package com.texasprogram.app

import com.texasprogram.app.model.WarmUp
import com.texasprogram.app.model.formatWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Разминка перед рабочим весом: те же числа, что в WarmUpTests на iOS.
class WarmUpTest {
    private fun text(steps: List<WarmUp.Step>) = steps.joinToString(" ") { "${formatWeight(it.weight)}×${it.reps}" }

    @Test
    fun stepsFromBar() {
        assertEquals("20×10 40×5 60×3 80×2", text(WarmUp.steps(100.0)))
        assertEquals("20×10 55×5 85×3 112.5×2 127.5×1", text(WarmUp.steps(142.5)))
        assertEquals("20×10 30×3 40×2", text(WarmUp.steps(50.0)))
        assertEquals("", text(WarmUp.steps(34.0)))
    }

    @Test
    fun deadliftStartsWithPlates() {
        assertEquals("70×5 107.5×3 142.5×2 160×1", text(WarmUp.steps("Становая тяга", 180.0)))
    }

    @Test
    fun onlyBarbell() {
        assertTrue(WarmUp.steps("Приседания", 120.0).isNotEmpty())
        assertTrue(WarmUp.steps("Жим лёжа", 80.0).isNotEmpty())
        assertTrue(WarmUp.steps("Жим гантелей лёжа", 80.0).isEmpty())
        assertTrue(WarmUp.steps("Сгибание рук со штангой", 40.0).isEmpty())
        assertTrue(WarmUp.steps("Жим ногами", 200.0).isEmpty())
    }
}
