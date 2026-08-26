# aws-profile

Switch AWS IAM Identity Center permission sets without touching Terraform.

```
prod-1    → standing access  (Terraform)
prod-1+   → elevated access  (TerraformElevated)
```

Both map to the same AWS CLI profile name (`prod-1`), so Terraform never needs to change.

---

## How it works

`aws-profile` reads your existing `~/.aws/config` — no duplication needed. The only thing
it needs to know is the suffix that distinguishes your elevated permission set from the
standing one (e.g. `Elevated`). Everything else (account IDs, regions, SSO sessions) comes
from the config you already have.

```
CLI alias          AWS profile       Permission set
─────────────────────────────────────────────────
prod-1             prod-1            Terraform
prod-1+            prod-1            TerraformElevated
```

`aws-profile` only edits `sso_role_name` in `~/.aws/config`. It never touches credentials,
SSO tokens, or caches.

---

## Installation

```bash
brew install lorenzo85/tap/aws-profile
```

### Fish shell

`awsp` (the interactive profile switcher) is loaded automatically — no extra steps needed.

### Zsh

Run once to enable `awsp`:

```bash
echo 'source $(brew --prefix)/share/zsh/site-functions/_awsp' >> ~/.zshrc
source ~/.zshrc
```

---

## Configuration

Run once after installing:

```bash
aws-profile init
```

This creates `~/.config/aws-profile/config.toml` with a single setting:

```toml
# Suffix appended to the standing role name to get the elevated role.
# Example: Terraform -> TerraformElevated
elevated_suffix = "Elevated"
```

Adjust the suffix to match your permission set naming convention.

---

## Usage

### Interactive profile switcher (recommended)

```bash
awsp
```

1. Select a profile from your `~/.aws/config` via fzf
2. Answer the elevated access prompt
3. `awsp` automatically runs `aws sso login` if the SSO token is missing or expired

### Standing access

```bash
aws-profile prod-1
```

```
✓ AWS profile: prod-1
✓ Account:     111111111111
✓ Access:      STANDING
✓ Permission:  Terraform
✓ Region:      eu-west-1
```

### Elevated access

```bash
aws-profile prod-1+
```

```
✓ AWS profile: prod-1
✓ Account:     111111111111
✓ Access:      ELEVATED
✓ Permission:  TerraformElevated
✓ Region:      eu-west-1
```

### List profiles

```bash
aws-profile list
aws-profile list --verbose
```

### Show current profile

```bash
aws-profile current
aws-profile current prod-1
```

### SSO login

```bash
aws-profile login prod-1
```

Runs `aws sso login --profile prod-1`. `awsp` handles this automatically.

### Reset all profiles to standing access

```bash
aws-profile reset
```

Strips the elevated suffix from every profile in `~/.aws/config`.

### Validate configuration

```bash
aws-profile validate prod-1
```

Checks that the profile exists in `~/.aws/config` and is consistent.

---

## Terraform

Terraform configuration never needs to change:

```hcl
provider "aws" {
  profile = "prod-1"
  region  = "eu-west-1"
}
```

Switch access level, then run Terraform:

```bash
aws-profile prod-1
terraform plan

aws-profile prod-1+
terraform apply
```

The profile name stays `prod-1` regardless of access level.

---

## AWS CLI

```bash
aws-profile prod-1+
aws sts get-caller-identity --profile prod-1
```

The `--profile` argument matches the account alias, with no `+`.

---

## What aws-profile touches

| File                                  | Action                                     |
|--------------------------------------|--------------------------------------------|
| `~/.aws/config`                      | Updates `sso_role_name` in place           |
| `~/.config/aws-profile/config.toml` | Read only                                  |
| `~/.aws/credentials`                 | Never touched                              |
| SSO cache                            | Never touched                              |

Writes are atomic: a temporary file is written and renamed over the target.

---

## Security

- Never stores credentials
- Never reads SSO tokens
- Only modifies `sso_role_name` in `~/.aws/config`

---

## Local build

```bash
git clone https://github.com/lorenzo85/aws-profile
cd aws-profile
./gradlew linkReleaseExecutableMacosArm64
# → build/bin/macosArm64/releaseExecutable/aws-profile.kexe
```

Copy to PATH:

```bash
cp build/bin/macosArm64/releaseExecutable/aws-profile.kexe /usr/local/bin/aws-profile
```

### macOS without full Xcode

If you have only the Command Line Tools (not the Xcode app), the Kotlin/Native linker
requires a workaround because it calls `xcodebuild -version` to detect the SDK:

```bash
mkdir -p /tmp/fake-xcode-bin
cat > /tmp/fake-xcode-bin/xcodebuild << 'SCRIPT'
#!/bin/sh
echo "Xcode 16.0"
echo "Build version 16A5230g"
SCRIPT
chmod +x /tmp/fake-xcode-bin/xcodebuild

PATH="/tmp/fake-xcode-bin:$PATH" \
SDKROOT=/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk \
./gradlew linkReleaseExecutableMacosArm64
```

### Java version

Gradle 8.14 requires Java 17–24. If your default `java` is version 25 or newer:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 24) ./gradlew macosArm64Test
```

---

## Development

```bash
./gradlew macosArm64Test      # run tests (macOS ARM64)
./gradlew check               # all enabled tests
```

---

## Releasing

Push a version tag — CI builds all four platform binaries, creates a GitHub release, and
updates the Homebrew formula automatically:

```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## Architecture

```
CLI (CliParser, Cli)
 │
 ▼
Application (ProfileSwitcher, InitService, CurrentProfileService, LoginService)
 │
 ▼
Ports (ConfigurationRepository, AwsConfigRepository, ProcessRunner)
 │
 ▼
Infrastructure
  ├── TomlConfigurationRepository  — reads ~/.config/aws-profile/config.toml
  ├── AwsConfigFileRepository      — reads/writes ~/.aws/config (atomic)
  └── NativeProcessRunner          — executes aws sso login via fork/exec
```
