# AGENTS.md

## Gradle build

このプロジェクトは Gradle Kotlin DSL の Java プロジェクトである。ビルドについては、推測で Maven や別バージョンの Gradle を使わず、以下に従うこと。

### ファイルの役割

- `build.gradle.kts`: プラグイン、依存関係、Java toolchain、Gradle タスクの正式な定義。
- `settings.gradle.kts`: ルートプロジェクト名の定義。
- `gradle.properties`: group/version と Gradle のキャッシュ・並列実行設定。
- `gradle/wrapper/gradle-wrapper.properties`: 使用する Gradle のバージョンを `9.7.0` に固定している。
- `.gradle/`: このワークスペースで使う Gradle user home とビルドキャッシュ。ソースやビルド定義ではない。手で編集せず、キャッシュ障害の明確な根拠とユーザーの許可がない限り削除しない。

### この環境固有の注意

- 必須 JDK は Java 25。`build.gradle.kts` の toolchain も 25 である。現在の JDK は `/usr/lib/jvm/java-25-openjdk-amd64`。
- リポジトリには `gradlew`、`gradlew.bat`、`gradle-wrapper.jar` がないため、`./gradlew` は実行できない。
- PATH 上にも `gradle` コマンドはない。
- 使用可能な Gradle 9.7.0 は次にある。
  `/home/slaviaaa2/.gradle/wrapper/dists/gradle-9.7.0-bin/d4tj7w02tcgubx9zk9hbippn6/gradle-9.7.0/bin/gradle`
- Codex の通常サンドボックス内では、Gradle がファイルロック用ソケットを作れず `Could not determine a usable wildcard IP` で起動に失敗する。このエラーが出たらビルド不良と判断せず、同じコマンドを権限昇格付きで再実行する。

### 標準ビルドコマンド

リポジトリルートから次を実行する。

```bash
env GRADLE_USER_HOME="$PWD/.gradle" \
  /home/slaviaaa2/.gradle/wrapper/dists/gradle-9.7.0-bin/d4tj7w02tcgubx9zk9hbippn6/gradle-9.7.0/bin/gradle \
  build --offline
```

このコマンドは Java コンパイル、リソース処理、テスト、JAR 作成まで行う。成功時の成果物は `build/libs/Fifty-1.0-SNAPSHOT.jar`。

依存関係や Gradle プラグインを変更してキャッシュに必要物がない場合だけ、まず理由を確認したうえで `--offline` を外して実行する。ネットワーク利用が必要なら権限昇格を申請する。

### その他のタスク

- コンパイルだけ確認: 上記コマンド末尾を `compileJava --offline` にする。
- Paper 開発サーバー起動: 上記コマンド末尾を `runServer` にする。長時間動作し、2 GiB のヒープを使うため、ユーザーが起動を求めた場合だけ実行する。
- コード変更後の標準検証は必ず `build --offline`。IDE の表示だけで成功と判断しない。

### 禁止事項

- Gradle を起動するためだけに `build.gradle.kts` や `gradle.properties` を変更しない。
- `.gradle/` や `build/` の生成物を実装ファイルとして扱わない。
- ビルドエラーの解消目的で、ユーザーの未コミット変更を消したり巻き戻したりしない。
