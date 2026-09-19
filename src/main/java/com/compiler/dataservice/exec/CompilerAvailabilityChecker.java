package com.compiler.dataservice.exec;

import com.compiler.dataservice.domain.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * On startup, checks that each language's compiler/interpreter is on PATH.
 * These binaries must be installed in the Dockerfile (there is no daemon
 * here to install them into on demand); this only reports what's missing so
 * a bad image is obvious in the logs instead of surfacing as a generic 500
 * on the first real request.
 */
@Component
public class CompilerAvailabilityChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompilerAvailabilityChecker.class);

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();
        for (Language language : Language.values()) {
            for (String binary : language.getRequiredBinaries()) {
                if (!isOnPath(binary)) {
                    missing.add(language + " needs '" + binary + "'");
                }
            }
        }

        if (missing.isEmpty()) {
            log.info("All language toolchains present: {}", (Object) Language.values());
        } else {
            log.warn("Missing toolchains, these languages will fail at request time until the " +
                    "Dockerfile installs them: {}", missing);
        }
    }

    private boolean isOnPath(String binary) {
        try {
            Process process = new ProcessBuilder("sh", "-c", "command -v " + binary).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
