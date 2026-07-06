package com.bupt.ecommerce.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DigitalHumanServiceTest {

    @Test
    void shouldSelectAvatarAndVoice() {
        DigitalHumanService service = new DigitalHumanService();

        DigitalHumanService.DigitalHumanConfig selected =
                service.select("guide_youth", "female_custom");

        assertEquals("guide_youth", service.current().avatarId());
        assertEquals("female_custom", service.current().voiceId());
        assertEquals(selected, service.current());
    }

    @Test
    void unknownAvatarShouldBeRejected() {
        DigitalHumanService service = new DigitalHumanService();

        assertThrows(IllegalArgumentException.class, () -> service.select("missing", null));
    }
}
