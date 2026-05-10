# Security Policy

## Reporting Security Vulnerabilities

**Please do NOT file a public GitHub issue for security vulnerabilities.**

If you discover a security issue, please email the maintainers privately at:

- Ahmed Abdul Fatah: [ahmedafatah95@gmail.com](mailto:ahmedafatah95@gmail.com)

Include:

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

We will respond within 48 hours and work with you on a fix.

## Security Best Practices

### For Contributors

- **Never commit API keys, tokens, or credentials**
- Use `local.properties` (`.gitignored`) for local secrets
- Use environment variables for CI/CD
- Never hardcode secrets in code
- Use `.example` template files for required configs

### For Users

- **Never share your API keys** in bug reports or GitHub issues
- Store `local.properties` safely (never commit it)
- Use strong passwords for keystore files
- Enable 2FA on accounts with sensitive credentials
- Regularly rotate API keys

### For Maintainers

- Run git history checks before releases: `git log --all -S "sk-ant\|AIza\|password"`
- Use tools like [git-secrets](https://github.com/awslabs/git-secrets) or [TruffleHog](https://github.com/trufflesecurity/truffleHog)
- Review all dependency updates for known CVEs
- Use signed commits: `git commit -S`

## Supported Versions

| Version | Status  |
| ------- | ------- |
| 0.1.0   | Current |

Older versions may not receive security updates.

## Security Checklist

Before each release:

- [ ] No API keys in git history
- [ ] No credentials in source code
- [ ] Dependencies are up-to-date
- [ ] No high/critical CVEs in dependencies
- [ ] Firebase rules are restrictive (if using)
- [ ] All `.example` files are in repo, actual config files are `.gitignored`
