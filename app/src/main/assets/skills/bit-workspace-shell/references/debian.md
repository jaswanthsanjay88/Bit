# Debian/Ubuntu (BIT default: Ubuntu 24.04 LTS, glibc, bash)

Shell is `bash -l`. BIT pre-patches apt/dpkg for PRoot (root sandbox user, `force-unsafe-io`, no pipelining, IPv4). Do not undo these.

## Packages
```
apt-get update                                           # once per session, before first install
apt-get install -y --no-install-recommends <pkg> ...     # install
apt-cache search <name> | head -20                       # find
dpkg -s <pkg> | head -3                                  # installed?
apt-get remove -y <pkg>
```
Always `apt-get`, not `apt` (stable output, script-safe). Always `-y`. Non-interactive env vars are already set. If an install is interrupted: `dpkg --configure -a`, then retry.

## Common toolchains
| Need | Install |
|---|---|
| Node.js | `apt-get install -y nodejs npm` (Ubuntu 24.04 ships Node 18; for newer, ask before using NodeSource) |
| Python | `apt-get install -y python3 python3-pip python3-venv` |
| C/C++ | `apt-get install -y build-essential` |
| Rust | `apt-get install -y rustc cargo` |
| Go | `apt-get install -y golang-go` |
| Java | `apt-get install -y default-jdk-headless` |
| git, curl, jq, ripgrep | `apt-get install -y git curl jq ripgrep` |
| Python native deps | `apt-get install -y python3-dev build-essential libffi-dev libssl-dev` |

## Python (PEP 668)
Global `pip install` fails with `externally-managed-environment`. Use `/opt/bit-env` if it exists, else a project venv:
```
cd proj && python3 -m venv .venv && .venv/bin/pip install -q -r requirements.txt && .venv/bin/python -m pytest -q
```
Call `.venv/bin/python` / `.venv/bin/pip` directly instead of `source activate`, since each call is a fresh shell.

## PRoot caveats
- `systemctl`, `service`, `invoke-rc.d` are no-op stubs; `policy-rc.d` denies service starts. After installing a service package (nginx, postgres, redis), run the binary directly in the same call as your test and kill it afterward.
- `apt-get` may feel slow on mobile data; set `timeout` 300+ for large installs and install only what the task needs.
- Prefer glibc-based prebuilt binaries/wheels here; they generally just work.
- If `dpkg` reports a broken `status` file, BIT's patcher repairs it on next launch; otherwise retry once before escalating.
