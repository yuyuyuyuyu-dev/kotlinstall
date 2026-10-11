# kotlinstall

A tool that installs programs written in Kotlin straight from GitHub.

## Table of contents

- [Why it was made](#why-it-was-made)
- [What it does](#what-it-does)
- [How to use it](#how-to-use-it)
  - [Requirements](#requirements)
  - [Install](#install)
  - [Uninstall](#uninstall)
  - [Limitations](#limitations)
- [License](#license)

## Why it was made

Other programming languages can install a program straight from GitHub, like `go install github.com/user/repository@latest` or `cargo install --git https://github.com/user/repository.git`. I envied that, so I made the same thing for Kotlin.

## What it does

TODO

## How to use it

### Requirements

- macOS (Apple silicon or Intel) or Linux (x64)
- Git
- A JDK, which Gradle runs on
- Gradle, only for projects that do not have the Gradle Wrapper (`gradlew`)
- Xcode, only on macOS
- The Kotlin compiler with its `kotlinr` command, only to run the installer script

### Install

```bash
curl -fsSL https://raw.githubusercontent.com/yuyuyuyuyu-dev/kotlinstall/main/install-kotlinstall.main.kts | kotlinr -howtorun .main.kts /dev/stdin
```

The installer script builds kotlinstall from its source code and puts it in `~/.kotlinstall/bin`. Add that directory to `PATH`:

```bash
export PATH="$HOME/.kotlinstall/bin:$PATH"
```

Then `kotlinstall --help` shows what it can do:

```text
Usage: kotlinstall [<options>] <command> [<args>]...

  Install Kotlin/Native programs from Git repositories

Options:
  -h, --help  Show this message and exit

Commands:
  install    Build a Kotlin/Native Gradle project from a Git repository and install its commands
  uninstall  Remove installed packages and their commands
  list       List the installed packages and their commands
```

### Uninstall

Everything kotlinstall installs, including kotlinstall itself, is in `~/.kotlinstall`. Removing that directory removes kotlinstall together with every program it installed:

```bash
rm -rf ~/.kotlinstall
```

Then take `~/.kotlinstall/bin` out of `PATH`.

Gradle and Kotlin/Native keep their own files, such as downloaded dependencies and compilers, in `~/.gradle` and `~/.konan`. Other tools use them too, so kotlinstall leaves them alone.

### Limitations

Only Gradle projects are supported.
Kotlin Toolchain projects are not. Kotlin Toolchain is still in Alpha and under active development, and its `kotlin` command has the same name as the `kotlin` command of the Kotlin compiler, which makes it hard to tell users how to run the installer script on a machine that has Kotlin Toolchain. Working that out was more trouble than it was worth for now, so I left out Kotlin Toolchain support altogether.

Also, only Kotlin/Native programs are supported. Programs that run on the JVM are not.
Supporting them would mean deciding what to do when the version of Java that a program needs is not installed. That was too much trouble to work out, so I left out programs that run on the JVM.

## License

[MIT](LICENSE)
