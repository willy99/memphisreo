# Project Overview
- **Stack**: Java 21, Spring Boot 3.x, Gradle (or Maven)
- **Architecture**: Modular Monolith / Clean Architecture (Domain, Service, Web layers)

# Build & Test Commands
- **Build**: `./gradlew build -x test`
- **Test**: `./gradlew test`
- **Single Test**: `./gradlew test --tests com.example.MyTestClass.testMethod`
- **Lint/Checkstyle**: `./gradlew check`

# Robust Coding Rules
- **Null Safety**: Use `Optional<T>` for nullable return types. Never return `null` explicitly from service methods. Annotate packages with `@NonNullApi` where applicable.
- **Exceptions**: Never swallow caught exceptions. Use custom domain exceptions extending a base `RuntimeException`. Always map exceptions globally via `@ControllerAdvice`.
- **Immutability**: Favor Java `record` types for DTOs and value objects. Make fields `final` in services and components.
- **Logging**: Use SLF4J via Lombok `@Slf4j`. Never use `e.printStackTrace()` or `System.out.println`. Log with structured parameters.
- **Testing**: Every new service method requires a corresponding JUnit 5 unit test using AssertJ for assertions and Mockito for mocking.

# Execution Constraints
- Implement features in small batches (Entities/DTOs -> Services -> Controllers).
- Run `./gradlew test` after modifying any logic to verify fixes immediately.
