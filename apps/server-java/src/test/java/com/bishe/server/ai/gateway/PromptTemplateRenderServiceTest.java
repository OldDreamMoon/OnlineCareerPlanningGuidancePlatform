package com.bishe.server.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateRenderServiceTest {

    private final PromptTemplateRenderService service = new PromptTemplateRenderService(new ObjectMapper());

    @Test
    void renderForRuntime_shouldTreatMissingOptionalVariablesAsEmptyString() {
        PromptTemplateRenderService.RenderResult result = service.renderForRuntime(
                "targetRole={{targetRole}}\nresumeContext={{resumeContext}}",
                """
                        {
                          "targetRole": {
                            "required": true,
                            "sampleValue": "Backend Engineer"
                          },
                          "resumeContext": {
                            "required": false,
                            "defaultValue": ""
                          }
                        }
                        """,
                Map.of("targetRole", "Backend Engineer")
        );

        assertThat(result.renderedContent()).isEqualTo("targetRole=Backend Engineer\nresumeContext=");
        assertThat(result.missingVariables()).isEmpty();
    }
}
