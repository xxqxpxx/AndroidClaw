# Contributing to AndroidClaw

Thank you for your interest in contributing! We welcome issues, feature requests, and pull requests.

## Getting Started

1. **Fork the repository** and clone your fork
2. **Create a feature branch**: `git checkout -b feature/your-feature-name`
3. **Make your changes** and commit with clear messages
4. **Push to your fork** and submit a pull request

## Development Setup

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 35

### Running the Project

1. Clone and open in Android Studio
2. Create `local.properties` with:
   ```properties
   sdk.dir=/path/to/android/sdk
   anthropic.api.key=your-api-key-here
   ```
3. Build and run on emulator or device

## Code Style

- **Kotlin**: Follow [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
- **Format**: Run `./gradlew ktlintFormat` before committing
- **Tests**: Add unit tests for new features

## Commit Messages

Use clear, descriptive commit messages:

- `feat: add new device action` (new feature)
- `fix: resolve crash on device disconnect` (bug fix)
- `docs: update README setup instructions` (documentation)
- `chore: upgrade dependencies` (maintenance)

## Reporting Issues

When filing a bug report, include:

- Android OS version
- Device model
- Steps to reproduce
- Expected vs actual behavior
- Relevant logs/screenshots

## Branching Strategy

- `main` — stable releases
- `develop` — integration branch for features
- `feature/*` — individual feature branches

## Pull Request Process

1. Update documentation if needed
2. Add tests for new functionality
3. Ensure all CI checks pass
4. Request review from maintainers
5. Squash commits before merging (optional)

## License

By contributing, you agree your code will be licensed under the MIT License.

## Questions?

Open a GitHub Discussion or reach out to the maintainers.

Happy coding! 🚀
