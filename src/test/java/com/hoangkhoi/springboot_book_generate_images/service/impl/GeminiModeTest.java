package com.hoangkhoi.springboot_book_generate_images.service.impl;

import com.hoangkhoi.springboot_book_generate_images.model.ImageTurn;
import com.hoangkhoi.springboot_book_generate_images.service.GeminiClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;


class GeminiModeTest {

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {"gemini.mode=simulate", "app.data-dir=target/test-data",
            "gemini.simulate.text-delay=1ms", "gemini.simulate.image-delay=1ms"})
    class SimulateMode {

        @Autowired
        private GeminiClient client;

        @Test
        void theSimulateClientIsUsed() {
            assertThat(client).isInstanceOf(SimulateGeminiClient.class);
        }

        @Test
        void itDrawsARealPngSoTheBrowserCanRenderIt() {
            String chain = client.openImageContext("watercolour");
            byte[] png = client.generateImage(chain, "a shy mole").image();

            assertThat(png).hasSizeGreaterThan(100);
            assertThat(new byte[] {png[0], png[1], png[2], png[3]})
                    .isEqualTo(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        }

        /** Each picture must hand back the id the next one continues from. */
        @Test
        void itAdvancesTheImageConversation() {
            String chain = client.openImageContext("watercolour");
            ImageTurn first = client.generateImage(chain, "a shy mole");
            ImageTurn second = client.generateImage(first.interactionId(), "a river rat");

            assertThat(first.interactionId()).isNotEqualTo(chain);
            assertThat(second.interactionId()).isNotEqualTo(first.interactionId());
        }

        @Test
        void itRespectsTheTwoCharacterShapeThePipelineExpects() {
            assertThat(client.generateCharacters("ctx", "watercolour")).hasSize(2);
            assertThat(client.generateChapters("ctx", "watercolour", java.util.List.of("The Mole")))
                    .hasSize(1);
        }
    }
}
