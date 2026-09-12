# グローバルスキル: 日本語（マルチバイト）パス環境での Gradle ビルド失敗の解決

## 目的
プロジェクトの絶対パスに日本語（マルチバイト文字）が含まれる環境で、Gradle ビルドが失敗する問題を確実に回避し、ビルドを成功させるための汎用手順を提供する。

## 適用条件（このスキルを使うべき状況）
以下のいずれかに該当する場合に適用する。

* ビルドログに `Invalid file path` が出力される。
* `./gradlew` 実行時に `An unexpected error occurred while trying to open file .../gradle-wrapper.jar` が出力される。
* プロジェクトの絶対パスに日本語・全角文字・マルチバイト文字が含まれている。
* `gradle` コマンドがシステムにインストールされていない。

## 根本原因
* Gradle 内部の `FileUtils.canonicalize()` がマルチバイト文字を含むパスを正しく解決できず、`Invalid file path` を送出する。
* シンボリックリンクによる ASCII パス回避は無効。`getCanonicalPath()` がリンク先の実体パス（日本語）へ解決してしまうため。

## 手順（グローバル・プラクティス）

### 1. プロジェクトを ASCII のみのパスへコピー
```bash
mkdir -p /tmp/eev_ascii
cp -r <日本語パスのプロジェクト>/. /tmp/eev_ascii/
```

### 2. Gradle Wrapper の jar を ASCII パスへ退避
```bash
mkdir -p /tmp/eevbuild
cp gradle/wrapper/gradle-wrapper.jar /tmp/eevbuild/
```

### 3. Wrapper を `java -classpath` で直接起動してビルド
```bash
cd /tmp/eev_ascii && java -Dorg.gradle.appname=gradlew \
  -classpath /tmp/eevbuild/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain :app:compileDebugKotlin --console=plain
```

* タスク名（`:app:compileDebugKotlin` など）は目的に応じて変更する。
* `--console=plain` を付与すると CI・ログ取得時に出力が安定する。

## 注意点
* この手順は「ビルド確認・成果物生成」を目的とした回避策である。恒久対策としては、プロジェクト自体を ASCII のみのパス配下に配置することが望ましい。
* コピー先（`/tmp/eev_ascii`）は一時領域のため、OS 再起動等で消える可能性がある。必要に応じて再コピーする。
* コピー後にソースを編集した場合は、再度コピーし直すか、編集対象のみ同期すること。

## 検証
* ビルドログに `BUILD SUCCESSFUL` が出力されることを確認する。
* 失敗時は `Invalid file path` が再発していないか、コピー先パスが ASCII のみであるかを確認する。
