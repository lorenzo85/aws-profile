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
```

```
prod-1
prod-2
staging-1
```

```bash
aws-profile list --verbose
```

```
prod-1      111111111111    eu-west-1
prod-2      222222222222    eu-west-1
staging-1   333333333333    eu-central-1
```

### Show current profile

```bash
aws-profile current prod-1
```

```
Profile:     prod-1
Account:     111111111111
Access:      ELEVATED
Permission:  TerraformElevated
Region:      eu-west-1
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

| File                           | Action              |
|-------------------------------|---------------------|
| `~/.aws/config`               | Upserts `[profile <alias>]` |
| `~/.config/aws-profile/config.toml` | Read only        |
| `~/.aws/credentials`          | Never touched       |
| SSO cache                     | Never touched       |

Writes are atomic: a temporary file is written and renamed over the target.

---

## Security

- Never stores credentials
- Never reads SSO tokens
- Only modifies `~/.aws/config`
- Account IDs stay in your local config (treat it as sensitive)

---

## Development

Requirements: JDK 17+ (for Gradle), Kotlin/Native toolchain (downloaded automatically).

```bash
git clone https://github.com/argol/aws-profile
cd aws-profile

./gradlew test          # run all tests (native)
./gradlew build         # compile all targets
./gradlew check         # tests + verification
```

Build a specific target:

```bash
./gradlew linkReleaseExecutableMacosArm64
# → build/bin/macosArm64/releaseExecutable/aws-profile.kexe
```

---

## Release

Tag and push — GitHub Actions does the rest:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The release workflow:
1. Builds native binaries on 4 platform/arch combinations
2. Packages each into a `.tar.gz`
3. Generates `checksums.txt`
4. Creates a GitHub Release with all artifacts
5. Updates `argol/homebrew-tap` with the new formula

---

## GitHub secrets required

| Secret | Purpose |
|--------|---------|
| `HOMEBREW_TAP_TOKEN` | Fine-grained PAT with **Contents: write** on `argol/homebrew-tap` only |

Create a fine-grained Personal Access Token at **GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens**:

- Resource owner: `argol`
- Repository access: **Only selected repositories** → `homebrew-tap`
- Permissions → Contents: **Read and write**

Add it as a repository secret in `argol/aws-profile`: **Settings → Secrets and variables → Actions → New repository secret**, name `HOMEBREW_TAP_TOKEN`.

Rotate it annually or whenever it is suspected to be compromised. The token only has access to the tap repository.

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
