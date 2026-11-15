package com.texasprogram.app

import com.texasprogram.app.service.EquipmentKind
import com.texasprogram.app.service.EquipmentScan
import com.texasprogram.app.service.EquipmentScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentScannerTest {
    private val incline = EquipmentKind.Label("Incline press", listOf("Upper chest"), listOf("Front deltoids", "Triceps"))
    private val shoulders = EquipmentKind.Label("Shoulder press", listOf("Deltoids"), listOf("Triceps"))
    private val catalog = listOf(
        EquipmentKind("incline_press", "", incline, incline),
        EquipmentKind("shoulder_press", "", shoulders, shoulders)
    )

    @Test
    fun resultUsesMusclesFromCatalog() {
        val result = EquipmentScanner.parse(
            """{"status":"identified","candidates":["incline_press"],"observation":"Handle path","muscles":["untrusted"]}""",
            catalog
        )
        assertEquals(EquipmentScan.Status.IDENTIFIED, result.status)
        assertEquals(listOf("Upper chest"), result.candidates.single().en.primary)
    }

    @Test
    fun ambiguousPhotoRequestsSideView() {
        val result = EquipmentScanner.parse(
            """{"status":"side_view","candidates":["incline_press","shoulder_press"],"observation":"Handles unclear"}""",
            catalog
        )
        assertEquals(EquipmentScan.Status.SIDE_VIEW, result.status)
        assertEquals(listOf("incline_press", "shoulder_press"), result.candidates.map { it.id })
    }

    @Test
    fun unknownClassificationDoesNotDisplayACandidate() {
        val result = EquipmentScanner.parse(
            """{"status":"unknown","candidates":["incline_press"],"observation":"No machine"}""",
            catalog
        )
        assertEquals(EquipmentScan.Status.UNKNOWN, result.status)
        assertTrue(result.candidates.isEmpty())
    }
}
