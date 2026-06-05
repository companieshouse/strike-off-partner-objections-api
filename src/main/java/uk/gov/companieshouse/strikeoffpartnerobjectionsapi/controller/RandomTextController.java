package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RandomTextController {

    private static final List<String> MESSAGES = List.of(
            "Today is a good day to ship code.",
            "Coverage numbers are climbing.",
            "Small changes make big progress.",
            "Keep going, you are nearly there."
    );

    @GetMapping("/random-text")
    public String randomText() {
        int index = ThreadLocalRandom.current().nextInt(MESSAGES.size());
        return MESSAGES.get(index);
    }
}

