package com.qualityinspect.backend.defect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the defect state-machine transitions. */
class DefectStateMachineTest {

    @Test
    void openToClosed_isAllowed() {
        assertTrue(DefectService.isValidTransition("OPEN", "CLOSED"));
    }

    @Test
    void reopenedToClosed_isAllowed() {
        assertTrue(DefectService.isValidTransition("REOPENED", "CLOSED"));
    }

    @Test
    void closedToReopened_isAllowed() {
        assertTrue(DefectService.isValidTransition("CLOSED", "REOPENED"));
    }

    @Test
    void openToReopened_isBlocked() {
        assertFalse(DefectService.isValidTransition("OPEN", "REOPENED"));
    }

    @Test
    void closedToOpen_isBlocked() {
        assertFalse(DefectService.isValidTransition("CLOSED", "OPEN"));
    }

    @Test
    void reopenedToOpen_isBlocked() {
        assertFalse(DefectService.isValidTransition("REOPENED", "OPEN"));
    }

    @Test
    void sameStatus_isBlocked() {
        assertFalse(DefectService.isValidTransition("OPEN", "OPEN"));
        assertFalse(DefectService.isValidTransition("CLOSED", "CLOSED"));
    }
}
