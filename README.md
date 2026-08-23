# aws-profile

Switch AWS IAM Identity Center permission sets without touching Terraform.

```
prod-1    → standing access  (Terraform)
prod-1+   → elevated access  (TerraformElevated)
```

Both map to the same AWS CLI profile name (`prod-1`), so Terraform never needs to change.

---

## How it works

```
CLI alias          AWS profile       Permission set
─────────────────────────────────────────────────
prod-1             prod-1            Terraform
prod-1+            prod-1            TerraformElevated
```

`aws-profile` only edits `~/.aws/config`. It never touches credentials, SSO tokens, or caches.

---

## Installation

```bash
brew install argol/tap/aws-profile
```

---

## Configuration

Create `~/.config/aws-profile/config.toml`:

```toml
[sso]
session = "company"

[permission_sets]
standing = "Terraform"
elevated = "TerraformElevated"

[accounts.prod-1]
account_id = "111111111111"
region = "eu-west-1"

[accounts.prod-2]
account_id = "222222222222"
region = "eu-west-1"

[accounts.staging-1]
account_id = "333333333333"
region = "eu-central-1"
```

---

## Usage

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

### List accounts

```bash
aws-profile list
aws-profile list --verbose
```

### Show current profile

```bash
aws-profile current prod-1
```

### SSO login

```bash
aws-profile login prod-1
```

This runs `aws sso login --profile prod-1`. Only needed when the SSO session expires.

### Validate configuration

```bash
aws-profile validate prod-1
```

Checks that the local config and AWS profile are consistent, without authenticating.

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

| File                                  | Action                      |
|--------------------------------------|-----------------------------|
| `~/.aws/config`                      | Upserts `[profile <alias>]` |
| `~/.config/aws-profile/config.toml` | Read only                   |
| `~/.aws/credentials`                 | Never touched               |
| SSO cache                            | Never touched               |

Writes are atomic: a temporary file is written and renamed over the target.

---

## Security

- Never stores credentials
- Never reads SSO tokens
- Only modifies `~/.aws/config`
- Account IDs stay in your local config (treat it as sensitive)

---

## Local testing (without Homebrew)

Build the binary directly from source and run it:

```bash
git clone https://github.com/argol/aws-profile
cd aws-profile

./gradlew linkReleaseExecutableMacosArm64
```

The binary is placed at:

```
build/bin/macosArm64/releaseExecutable/aws-profile.kexe
```

Copy it to your PATH:

```bash
cp build/bin/macosArm64/releaseExecutable/aws-profile.kexe /usr/local/bin/aws-profile
```

Or run it directly:

```bash
./build/bin/macosArm64/releaseExecutable/aws-profile.kexe --version
./build/bin/macosArm64/releaseExecutable/aws-profile.kexe --help
```

Then create your config:

```bash
mkdir -p ~/.config/aws-profile
cat > ~/.config/aws-profile/config.toml << 'EOF'
[sso]
session = "your-sso-session"

[permission_sets]
standing = "Terraform"
elevated = "TerraformElevated"

[accounts.prod-1]
account_id = "111111111111"
region = "eu-west-1"
EOF
```

And test:

```bash
aws-profile prod-1
aws-profile prod-1+
aws-profile list
aws-profile current prod-1
```

### macOS without full Xcode

If you have only the Command Line Tools (not the Xcode app), the Kotlin/Native linker
requires a workaround because it calls `xcodebuild -version` to detect the SDK:

```bash
# One-time setup: create a stub xcodebuild
mkdir -p /tmp/fake-xcode-bin
cat > /tmp/fake-xcode-bin/xcodebuild << 'SCRIPT'
#!/bin/sh
echo "Xcode 16.0"
echo "Build version 16A5230g"
SCRIPT
chmod +x /tmp/fake-xcode-bin/xcodebuild

# Build and test with the stub in PATH
PATH="/tmp/fake-xcode-bin:$PATH" \
SDKROOT=/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk \
./gradlew macosArm64Test

PATH="/tmp/fake-xcode-bin:$PATH" \
SDKROOT=/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk \
./gradlew linkReleaseExecutableMacosArm64
```

If you have the full Xcode app installed, just run `./gradlew` normally without the prefix.

### Java version

Gradle 8.14 requires Java 17–24. If your default `java` is version 25 or newer, point
Gradle at an older JDK:

```bash
# Temporary override for one command
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew macosArm64Test

# Or set permanently in gradle.properties (already done in this repo for Java 24)
# org.gradle.java.home=/path/to/jdk
```

---

## Development

```bash
./gradlew macosArm64Test      # run tests on the current machine (macOS ARM64)
./gradlew linuxX64Test        # run tests when on Linux x64
./gradlew check               # all enabled tests
./gradlew build               # compile all targets
```

Build a specific target:

```bash
./gradlew linkReleaseExecutableMacosArm64
# → build/bin/macosArm64/releaseExecutable/aws-profile.kexe
```

---

## Publishing to GitHub

### 1. Create the repositories

```bash
# From the aws-profile directory
gh repo create argol/aws-profile \
  --public \
  --source=. \
  --remote=origin \
  --push

# From the homebrew-tap directory
cd ../homebrew-tap
gh repo create argol/homebrew-tap \
  --public \
  --source=. \
  --remote=origin \
  --push
```

### 2. Add the required secret

Go to **`argol/aws-profile` → Settings → Secrets and variables → Actions** and create:

| Secret name | Value |
|-------------|-------|
| `HOMEBREW_TAP_TOKEN` | Fine-grained PAT — see below |

Create the token at **GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens**:

- Resource owner: `argol`
- Repository access: **Only selected repositories** → `homebrew-tap`
- Permissions → Contents: **Read and write**

This token is the only credential the release workflow needs. It has no access to any
other repository. Rotate it annually or on suspected compromise.

### 3. Make the first release

```bash
git tag v0.1.0
git push origin v0.1.0
```

GitHub Actions then runs automatically:

```
git tag v0.1.0
      ↓
GitHub Actions (4 parallel jobs)
      ├── macos-14       → aws-profile-darwin-arm64.tar.gz
      ├── macos-13       → aws-profile-darwin-amd64.tar.gz
      ├── ubuntu-latest  → aws-profile-linux-amd64.tar.gz
      └── ubuntu-24-arm  → aws-profile-linux-arm64.tar.gz
      ↓
GitHub Release (with checksums.txt)
      ↓
argol/homebrew-tap updated automatically
      ↓
brew install argol/tap/aws-profile
```

### 4. Install via Homebrew

```bash
brew install argol/tap/aws-profile
aws-profile --version
```

---

## Updating Homebrew for a new release

No manual steps are needed. Every time you push a new tag (`v0.2.0`, `v1.0.0`, etc.),
the release workflow recalculates the SHA-256 checksums from the fresh artifacts and
commits the updated formula to `argol/homebrew-tap`. Homebrew users get the new version
on their next `brew upgrade`.

---

## Architecture

```
CLI (CliParser, Cli)
 │
 ▼
Application (ProfileSwitcher, AccountResolver, CurrentProfileService, LoginService)
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

The domain and application layers have no knowledge of TOML, file paths, or the AWS CLI.
Swapping the config format or the account source requires no changes to the CLI or
business logic.
