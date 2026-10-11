# kotlinstall

Kotlinで作ったものをGitHubから直接インストールするツール。

## 目次

TODO

## なぜ作られたのか

`go install https://github.com/user/repository.git` や `cargo install --git https://github.com/user/repository.git` のように作ったものをGitHubから直接インストールできる他のプログラミング言語が羨ましかったので、それのKotlin版を作りました。

## 何をするのか

TODO

## どのように使うのか

### 前提条件

TODO

### インストール

```bash
TODO: kotlinstall本体のインストールコマンド
ヘルプもここに載せる
```

### アンインストール

```bash
TODO: kotlinstall本体のアンインストール方法
```

### 制約

Gradleプロジェクトのみ対応しています。
TODO: Kotlin Toolchainはまだ開発中で `kotlin` というコマンド名の移行も完了していないしKotlinスクリプトの実行もまだサポートしていなくて、Kotlin Toolchainがインストールされている環境でのインストールスクリプトの実行方法の案内を考えるのがめんどくさかったからKotlin Toolchainのサポートを丸ごと外した、という経緯をいい感じに説明する。要事実確認。

また、サポートしているのはKotlin/Nativeのもののみで、JVMのものはサポート外です。
JVMのものをサポートしようとすると実行に必要なバージョンのJavaがインストールされていない時にどうするかを考える必要が出てきて、それを考えるのがめんどくさかったのでJVMで動くものはサポート外にしました。

### ライセンス

TODO
