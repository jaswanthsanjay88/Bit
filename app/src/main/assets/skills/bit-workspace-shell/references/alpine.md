# Alpine Linux 3.21 (minirootfs, musl, ash)

Shell is `ash` unless bash was installed (BIT's bootstrap installs python3, py3-pip, bash, curl, git, ca-certificates). Write POSIX sh syntax; avoid bashisms (`[[ ]]`, arrays, `<<<`) unless `command -v bash` succeeds, then use `bash -c '...'`.

## Packages
```
apk update
apk add --no-cache <pkg> [<pkg>...]     # install
apk search -v <name>                    # find
apk info -e <pkg>                       # installed?
apk del <pkg>                           # remove
```
`community` repo is enabled by BIT. Use `--no-cache` always (no apt-style cache dir).

## Common toolchains
| Need | Install |
|---|---|
| Node.js | `apk add --no-cache nodejs npm` |
| Python + pip | `apk add --no-cache python3 py3-pip` (venv: `python3 -m venv .venv`) |
| C/C++ | `apk add --no-cache build-base` (gcc, g++, make, musl-dev) |
| Rust | `apk add --no-cache rust cargo` |
| Go | `apk add --no-cache go` |
| Java | `apk add --no-cache openjdk17` |
| git, curl, jq, ripgrep | `apk add --no-cache git curl jq ripgrep` |
| Native Python wheels | `apk add --no-cache python3-dev build-base libffi-dev openssl-dev` |

## musl caveats
- Many prebuilt glibc binaries (some pip wheels, node native addons, downloaded tools) fail with `not found` or `Error loading shared library`. Fix: install the Alpine package (`py3-numpy`, `py3-pillow`, etc.) or build from source with `build-base`.
- `pip install` of packages without musllinux wheels compiles from source: slow, raise `timeout` to 600, install `-dev` headers first.
- Alpine packages named `py3-*` install into system Python; they are visible from a venv only with `--system-site-packages`.
- Use `busybox` flags: `sed -i`, `grep -E` work; GNU-only flags (`ls --color`, `date -d`, `sed -z`) may not. Add `coreutils`/`grep`/`sed` packages if needed.
- Services: no OpenRC. Run daemons directly.
