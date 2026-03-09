package com.platform.ai;

import com.platform.engine.ComponentLoader;
import javax.inject.Inject;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates the full AI → generate → validate → compile → deploy pipeline.
 *
 * Steps:
 *   1. Build prompt
 *   2. Call AI provider
 *   3. Validate the response (has class, implements contract)
 *   4. Compile via ComponentLoader
 *   5. If compile fails → send error back to AI and retry (up to 3 times)
 *   6. On success → component is live
 */
@Stateless
public class AIGateway {

    private static final Logger LOG = Logger.getLogger(AIGateway.class.getName());
    private static final int MAX_ATTEMPTS = 3;

    // Adapter registry — add new providers here
    private static final Map<AIProvider, AIProviderAdapter> ADAPTERS = new HashMap<>();
    static {
        ADAPTERS.put(AIProvider.CLAUDE,  new ClaudeAdapter());
        ADAPTERS.put(AIProvider.GROQ,    new GroqAdapter());
        ADAPTERS.put(AIProvider.GEMINI,  new GeminiAdapter());
    }

    @EJB
    private ComponentLoader componentLoader;

    @Inject
    private PromptBuilder promptBuilder;

    public GenerationResult generate(String name, String version,
                                     String description, AIProvider provider,
                                     String apiKey) {

        AIProviderAdapter adapter = ADAPTERS.get(provider);
        if (adapter == null) {
            return GenerationResult.failure("Unknown AI provider: " + provider, 0);
        }

        String systemPrompt  = promptBuilder.buildSystemPrompt();
        String userPrompt    = promptBuilder.buildUserPrompt(name, version, description);
        String lastCode      = null;
        String lastError     = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            LOG.info("AI generation attempt " + attempt + "/" + MAX_ATTEMPTS
                    + " for component: " + name + " using " + provider);
            try {
                // Build prompt — on retry, include previous error
                String prompt = (attempt == 1)
                        ? userPrompt
                        : promptBuilder.buildRetryPrompt(name, version, description,
                                                          lastCode, lastError);

                // Call AI
                String rawResponse = adapter.generate(systemPrompt, prompt, apiKey);

                // Sanitize — strip markdown fences if AI included them
                String sourceCode = sanitize(rawResponse);
                lastCode = sourceCode;

                // Validate structure
                ValidationResult validation = validate(name, sourceCode);
                if (!validation.isValid()) {
                    lastError = "Validation failed: " + validation.getError();
                    LOG.warning("Attempt " + attempt + " validation failed: " + lastError);
                    continue;
                }

                // Compile and deploy
                ComponentLoader.CompileResult compileResult =
                        componentLoader.compileSource(name, sourceCode);

                if (!compileResult.isSuccess()) {
                    lastError = "Compile error: " + compileResult.getErrorMessage();
                    LOG.warning("Attempt " + attempt + " compile failed: " + lastError);
                    continue;
                }

                // Success
                LOG.info("Component generated and deployed: " + name + " (attempt " + attempt + ")");
                return GenerationResult.success(name, sourceCode, attempt);

            } catch (Exception e) {
                lastError = e.getMessage();
                LOG.log(Level.WARNING, "Attempt " + attempt + " threw exception", e);
            }
        }

        // All attempts exhausted
        return GenerationResult.failure(lastError, MAX_ATTEMPTS);
    }

    // ── Sanitize AI response ──────────────────────────────────

    private String sanitize(String raw) {
        if (raw == null) return "";

        // Strip ```java ... ``` or ``` ... ``` fences
        raw = raw.replaceAll("(?s)```java\\s*", "").replaceAll("(?s)```\\s*", "");

        // Trim whitespace
        return raw.trim();
    }

    // ── Validate structure ────────────────────────────────────

    private ValidationResult validate(String expectedClassName, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank())
            return ValidationResult.invalid("Source code is empty");

        if (!sourceCode.contains("package com.platform.components"))
            return ValidationResult.invalid("Missing package declaration: com.platform.components");

        if (!sourceCode.contains("class " + expectedClassName))
            return ValidationResult.invalid("Class name must be: " + expectedClassName);

        if (!sourceCode.contains("extends AbstractEventComponent"))
            return ValidationResult.invalid("Class must extend AbstractEventComponent");

        if (!sourceCode.contains("getMetadata()"))
            return ValidationResult.invalid("Missing method: getMetadata()");

        if (!sourceCode.contains("accept("))
            return ValidationResult.invalid("Missing method: accept()");

        if (!sourceCode.contains("execute("))
            return ValidationResult.invalid("Missing method: execute()");

        return ValidationResult.valid();
    }

    // ── Inner result classes ──────────────────────────────────

    public static class GenerationResult {
        private final boolean success;
        private final String  componentName;
        private final String  sourceCode;
        private final int     attempts;
        private final String  errorMessage;

        private GenerationResult(boolean success, String componentName,
                                  String sourceCode, int attempts, String errorMessage) {
            this.success       = success;
            this.componentName = componentName;
            this.sourceCode    = sourceCode;
            this.attempts      = attempts;
            this.errorMessage  = errorMessage;
        }

        public static GenerationResult success(String name, String source, int attempts) {
            return new GenerationResult(true, name, source, attempts, null);
        }

        public static GenerationResult failure(String error, int attempts) {
            return new GenerationResult(false, null, null, attempts, error);
        }

        public boolean isSuccess()       { return success; }
        public String getComponentName() { return componentName; }
        public String getSourceCode()    { return sourceCode; }
        public int getAttempts()         { return attempts; }
        public String getErrorMessage()  { return errorMessage; }
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String  error;

        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        static ValidationResult valid()              { return new ValidationResult(true, null); }
        static ValidationResult invalid(String msg)  { return new ValidationResult(false, msg); }

        boolean isValid() { return valid; }
        String getError() { return error; }
    }
}
