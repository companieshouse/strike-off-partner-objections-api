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

## Code Quality Rules

Before completing any task:

- Follow existing project patterns.
- Follow all SonarQube rules.
- Do not suppress SonarQube warnings.

## SonarQube Rules

- Minimise cognitive complexity.
- Prefer early returns.
- Avoid nested conditionals.
- Avoid duplicated code.
- Avoid magic numbers.
- Avoid unnecessary object creation.
- Avoid methods longer than 50 lines.
- Avoid classes with multiple responsibilities.
- Handle null values safely.
- Avoid Optional.get().
- Prefer constants for repeated values.


# Java Coding Standards
Standards should be applied to all new code, including new classes, methods, and tests. Existing code should be refactored to meet these standards when it is modified.

## Java Coding Standards

### General

- Follow existing project patterns and package structure.
- Use meaningful class, method and variable names.
- Use Java 21 features where appropriate.
- Prefer composition to inheritance.
- Keep classes focused on a single responsibility.
- Use generics for type safety.

### Formatting

- Use IntelliJ default formatting rules.
- Use 4 spaces for indentation.
- Use braces for all control statements.
- Keep code readable and maintainable.

### Implementation

- Always use @Override when overriding methods.
- Access static members using the class name.
- Avoid magic numbers; use named constants.
- Minimise cognitive complexity.
- Prefer early returns over nested conditionals.
- Prefer immutable objects where practical.

### Exception Handling

- Do not ignore exceptions.
- Catch specific exceptions rather than Exception.
- Provide meaningful error messages.
- Follow existing project exception handling patterns.

### Collections

- Use appropriate collection types.
- Prefer immutable collections where practical.
- Avoid raw types.

### Documentation

- Add Javadoc for public classes and public APIs where required by project conventions.
- Keep documentation concise and focused on intent.


## Testing

Only create tests when asked for

When generating tests:

- Cover happy paths.
- Cover validation failures.
- Cover exception paths.
- Cover boundary conditions.
- Cover null inputs.
- Cover all public methods.

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