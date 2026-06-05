package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RandomTextControllerTest {

    private static final Set<String> EXPECTED_MESSAGES = Set.of(
            "Today is a good day to ship code.",
            "Coverage numbers are climbing.",
            "Small changes make big progress.",
            "Keep going, you are nearly there."
    );

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new RandomTextController()).build();
    }

    @Test
    void randomTextReturnsOneOfTheExpectedMessages() throws Exception {
        String body = mockMvc.perform(get("/random-text"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(EXPECTED_MESSAGES).contains(body);
    }
}


