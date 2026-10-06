# Copilot Development Standards

## Technology Stack

- Java 21
- Spring Boot 4
- Maven
- JUnit 5
- Mockito
- JaCoCo
- SonarQube
- Apache Kafka
- MongoDB

## Code Quality Principles

Before completing any task:

- Follow existing project patterns.
- Follow all SonarQube rules.
- Do not suppress SonarQube warnings.
- Review and refactor holistically—don't just fix one thing in isolation; look for similar issues throughout the codebase and apply solutions consistently.

## SonarQube Compliance

- Minimise cognitive complexity.
- Prefer early returns to avoid nested conditionals.
- Avoid duplicated code.
- Avoid magic numbers and strings; use named constants.
- Avoid unnecessary object creation.
- Avoid methods longer than 50 lines.
- Avoid classes with multiple responsibilities.
- Handle null values safely.
- Avoid Optional.get().
- Prefer constants for repeated values.

## Java Coding Standards

Standards should be applied to all new code, including new classes, methods, and tests. Existing code should be refactored to meet these standards when it is modified.

### General

- Follow existing project patterns and package structure.
- Use meaningful class, method and variable names.
- Use Java 21 features where appropriate.
- Prefer composition to inheritance.
- Keep classes focused on a single responsibility.
- Use generics for type safety.
- Code should be self-documenting; well-named constants and variables eliminate the need for comments explaining magic values.

### Formatting

- Use IntelliJ default formatting rules.
- Use 4 spaces for indentation.
- Use braces for all control statements.
- Keep code readable and maintainable.

### Implementation

- Always use @Override when overriding methods.
- Access static members using the class name.
- Avoid magic numbers and strings; use named constants.
- Minimise cognitive complexity.
- Prefer early returns over nested conditionals.
- Prefer immutable objects where practical.
- Break long methods into smaller, focused ones.

### Import Management

- Always use proper imports; never use fully qualified class names in code (e.g., avoid `java.util.concurrent.atomic.AtomicReference<>()`).
- Import classes that are used, even if used only once.
- Use static imports for utility methods and constants.
- Proper imports improve readability and follow IDE conventions.

### Exception Handling

- Do not ignore exceptions or silently swallow them; don't catch exceptions just to log them and continue.
- Catch only specific exceptions that you explicitly handle, not generic `Exception`.
- Let system failures propagate to enable upstream error handling, retry logic, and audit trails.
- Distinguish between expected exceptions (e.g., NOT_FOUND when an optional resource is deleted—catch) and unexpected ones (e.g., PERSISTENCE_FAILURE—propagate).
- Provide meaningful error messages.
- Follow existing project exception handling patterns.

### Collections

- Use appropriate collection types.
- Prefer immutable collections where practical.
- Avoid raw types.

### Documentation

- Add Javadoc for public classes and public APIs where required by project conventions.
- Keep documentation concise and focused on intent.

## Code Reusability & DRY Principle

### Constants and Magic Values

- Any hardcoded value used more than once should become a named constant.
- This applies to strings, numbers, URIs, status codes, and identifiers.
- Constants should explain their purpose, not just their value (e.g., `VALID_COMPANY_NUMBER` is better than `COMPANY_NUM`).
- Constants make the intent clear and reduce maintenance burden.

### Test Data Centralisation

- Use `@BeforeEach` to initialise common test objects (e.g., `requestDto`, `mappedDocument`).
- Avoid recreating the same test objects in every test method.
- Share test data across related tests to reduce duplication.

### Helper Methods

- When test patterns repeat, create helper methods immediately (e.g., object creation, mock setup).
- Extract helper methods for complex operations in production code.
- Prevents SonarQube violations and makes code maintainable.
- Apply helper patterns consistently across all related test classes.

## Data Design

### Immutable Objects Over Generic Types

- Don't use `BiConsumer<String, String>` for callbacks that need rich context.
- Create dedicated immutable classes (e.g., `CallbackResult`) with named fields.
- This makes code self-documenting and prevents parameter confusion.
- Enables handlers to make intelligent decisions based on attempt metadata.

### Callbacks and Context

- Pass attempt numbers, correlation IDs, and failure reasons to handlers.
- Use 1-based numbering for user-facing retry counts.
- Handlers need to know: "Was this the first attempt or a retry?" and "How many attempts did it take?"
- This enables intelligent retry policies and proper audit trail recording.

## Audit Trail & Data Integrity

- Persist all callback attempts (success and failure) to the database.
- Include attempt number, correlation ID, and timestamp for each outcome.
- Never lose results due to exception swallowing or missing persistence.
- A complete audit trail is non-negotiable for compliance, debugging, and retry logic.

## Concurrency & Thread Safety

- Use immutable payload objects during async callback execution.
- Prevent race conditions with proper synchronisation (e.g., `synchronized` blocks around executor state changes).
- Ensure graceful shutdown of executors with timeout protection.

## Testing

Only create tests when asked for.

When generating tests:

- Cover happy paths.
- Cover validation failures.
- Cover exception paths.
- Cover boundary conditions.
- Cover null inputs.
- Cover all public methods.
- Keep tests maintainable with consistent naming (verb_scenario_outcome).
- Group related tests together.
- Share setup code to reduce duplication.
- Use existing project patterns for test structure.
- Lambda assertions: lambdas in `assertThatThrownBy()` should contain **only one invocation** that could throw; extract helper method calls outside the lambda and pass as variables.
- Use parametrised tests when applicable to avoid code repetition and test multiple scenarios with different inputs.
- Parametrised tests (using `@ParameterizedTest`, `@ValueSource`, `@CsvSource`, etc.) reduce duplication and improve maintainability.

Generate sufficient tests to reasonably achieve:
- Line Coverage >= 80%
- Branch Coverage >= 80%


If coverage is below target, create additional tests.

## Final Verification

Before considering a task complete:

- Ensure code compiles.
- Ensure tests pass.
- Review for SonarQube issues.

Only provide the final implementation once all checks have been completed.