package com.qualityinspect.backend.action;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the corrective-action state machine. */
class ActionStateMachineTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> transitions() {
        try {
            Field f = ActionService.class.getDeclaredField("ALLOWED_TRANSITIONS");
            f.setAccessible(true);
            return (Map<String, Set<String>>) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void open_canMoveToInProgressOrCancelled() {
        Set<String> next = transitions().get("OPEN");
        assertTrue(next.contains("IN_PROGRESS"));
        assertTrue(next.contains("CANCELLED"));
        assertFalse(next.contains("COMPLETED"));
    }

    @Test
    void inProgress_canCompleteAwaitingOrCancel() {
        Set<String> next = transitions().get("IN_PROGRESS");
        assertTrue(next.contains("AWAITING_REINSPECTION"));
        assertTrue(next.contains("COMPLETED"));
        assertTrue(next.contains("CANCELLED"));
    }

    @Test
    void awaitingReinspection_canCompleteOrGoBack() {
        Set<String> next = transitions().get("AWAITING_REINSPECTION");
        assertTrue(next.contains("COMPLETED"));
        assertTrue(next.contains("IN_PROGRESS"));
    }

    @Test
    void completed_isTerminal() {
        assertTrue(transitions().get("COMPLETED").isEmpty());
    }

    @Test
    void cancelled_isTerminal() {
        assertTrue(transitions().get("CANCELLED").isEmpty());
    }
}
