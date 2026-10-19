# Contributing

Thanks for taking the time to improve this sample! Bug reports, documentation fixes and new examples of Vault features
are all welcome.

## Ground rules

- **No real secrets, ever.** Not in code, tests, issues, screenshots or logs. Demo values must be obviously fake and
  labeled as such (e.g. `demo-...`, `...-NOT-A-REAL-SECRET`). Redact tokens, role IDs and secret IDs before sharing
  output.
- Keep the sample **simple and focused**: every addition should teach something about using Vault from Spring Boot.
- Security-relevant shortcuts (dev mode, root token, unauthenticated demo endpoints) must stay clearly documented as
  such.
- Report vulnerabilities privately - see [SECURITY.md](SECURITY.md).

## Development setup

Requirements: JDK 21, Docker (with Compose). Maven is provided by the wrapper.

```bash
./mvnw verify            # unit + integration tests (Testcontainers: Vault + PostgreSQL)
./mvnw verify -DskipITs  # unit tests only

docker compose up -d && ./scripts/vault-init.sh
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

If you change `scripts/vault-init.sh`, keep it POSIX `sh` compatible (it also runs inside the Alpine-based Vault image
during the integration test) and run ShellCheck:

```bash
docker run --rm -v "$PWD/scripts:/mnt:ro" koalaman/shellcheck:stable --shell=sh /mnt/vault-init.sh
```

## Coding conventions

- Follow the existing style and [.editorconfig](.editorconfig) (4 spaces for Java, 2 for YAML/HCL, max. 120 columns).
- Constructor injection, no field injection. Prefer `@ConfigurationProperties` over `@Value` except where the sample
  deliberately demonstrates `@Value`.
- Never log secret values; log key names or masked values at most.
- Policies in `vault/policies/` stay least-privilege - if the app needs a new path, grant exactly that path and
  explain why in a comment.
- Add or update tests: unit tests for logic, `VaultSampleApplicationIT` for behavior that involves Vault.
- Update the README when behavior, configuration or versions change.

## Pull requests

1. Fork the repository and create a branch from `main` (`feature/...` or `fix/...`).
2. Make focused commits with clear messages (imperative mood, e.g. "Add Kubernetes auth example").
3. Make sure `./mvnw verify` passes locally; CI runs the same build plus ShellCheck and a Docker image build.
4. Open a pull request and fill in the template. Describe what the change demonstrates and how you tested it.

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
