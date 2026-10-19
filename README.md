# kotlinstall

A tool that installs programs written in Kotlin directly from GitHub repositories, like `go install` for Go and `cargo install --git` for Rust.

## Table of contents

- [Why it was made](#why-it-was-made)
- [What it does](#what-it-does)
- [How to use](#how-to-use)
- [Limitations](#limitations)
- [License](#license)

## Why it was made

I once needed a small command line tool that starts fast, and the choice came down to Kotlin/Native or Rust.
Rust has `cargo install --git https://github.com/user/repository.git`, which installs a program directly from GitHub. Kotlin had nothing like it, so I chose Rust.

But I still love Kotlin and could not give up on it, so I made kotlinstall.

## What it does

It installs programs written in Kotlin directly from GitHub.
It clones the source code into a temporary directory and builds it, copies the result to `~/.kotlinstall/packages/<package name>/`, links `~/.kotlinstall/bin/<command name>` to it with a symbolic link, and then removes the whole temporary directory.

## How to use

### Requirements

- macOS or Linux
- Git
- A JDK, which the installer script and `kotlinstall install` need to build programs. Tested with JDK 25.
- Xcode, only on macOS. The Command Line Tools alone might be enough, but I have not tested that.
- The Kotlin compiler with its `kotlinr` command, only to run the installer script

### Install kotlinstall

```bash
curl -fsSL https://raw.githubusercontent.com/yuyuyuyuyu-dev/kotlinstall/main/install-kotlinstall.main.kts | kotlinr -howtorun .main.kts /dev/stdin
```

The installer script builds kotlinstall from its source code and puts it in `~/.kotlinstall/bin`. Add that directory to `PATH`:

```bash
export PATH="$HOME/.kotlinstall/bin:$PATH"
```

### Install a program

```bash
kotlinstall install https://github.com/user/repository.git
```

This installs the commands of the program into `~/.kotlinstall/bin`.

### Other commands

See the help:

```bash
kotlinstall --help
```

### Uninstall kotlinstall

Everything kotlinstall installs, including kotlinstall itself, is in `~/.kotlinstall`. Removing that directory removes kotlinstall together with every program it installed:

```bash
rm -rf ~/.kotlinstall
```

Then take `~/.kotlinstall/bin` out of `PATH`.

Gradle and Kotlin/Native keep their own files, such as downloaded dependencies and compilers, in `~/.gradle` and `~/.konan`. Other tools use them too, so kotlinstall leaves them alone.

## Limitations

Only Gradle projects are supported. Kotlin Toolchain projects are not.
Kotlin Toolchain is still in Alpha, and its `kotlin` command has the same name as the `kotlin` command of the Kotlin compiler. To support it, I would have to think about how to tell people to run the installer script on a machine that has Kotlin Toolchain. I did not feel like thinking about that, so this first version leaves Kotlin Toolchain out.

Also, only Kotlin/Native programs are supported. Programs that run on the JVM are not.
To support them, I would have to think about what to do when the version of Java that a program needs is not installed. I did not feel like thinking about that either, so this first version leaves them out.

## License

[MIT](LICENSE)
