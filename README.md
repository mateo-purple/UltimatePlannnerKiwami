# アルティメットプランナー極み

Android・iPhone・PCで使う予定管理アプリです。

## ブラウザ版

このリポジトリの `index.html` / `app.js` / `style.css` はスマホ対応の週間時間割です。曜日が横軸、時刻が縦軸。予定の登録・編集・削除、毎週の繰り返し、ローカル保存、Firebase同期に対応します。

GitHub Pages等の静的ホスティングで公開してください。GitHubの Settings → Pages で main / root を選択できます（プランとリポジトリ公開設定によっては利用不可）。

## Android Studio版

Android側は `UltimatePlannerKiwami_AndroidStudio_CloudSync_v4` としてGoogle Driveに保存済みです。従来のSQLiteデータを維持しつつ、クラウド同期設定画面を追加しました。Android Studioで更新版のフォルダを開いてください。

## Firebaseの初期設定（必須）

1. Firebase Consoleでプロジェクトを作成。
2. Authentication → Sign-in method → **メール/パスワード** を有効化。
3. Firestore Databaseを作成し、`firestore.rules` のルールを適用。テストモードで放置しないこと。
4. Firebaseの **Project ID** と **Web API Key** を確認。
5. ブラウザ版とAndroid版で同じProject ID、API Key、メールアドレス、パスワードを使ってログイン。

Firebaseの設定が済むまではブラウザ内の予定はローカルに保存され、端末間同期は動きません。現在のログイン方式はメール/パスワードで、Googleアカウントログインではありません。同期競合は更新日時が新しいデータを優先します。Web版はログイン更新トークンをlocalStorageに保存するため共用端末では使用を避けてください。

## GitHub同梱の拡張版（2026-11-09）

- **`docs/`**: 週間時間割に加え、課題の優先表示・空き時間への自動配置・JSONバックアップ・Firebase同期を備えたWeb/PWA版。GitHub Pagesでは **main /docs** を公開元にすると、この版を利用できます。
- **`app/`**: Android StudioのJavaプロジェクト一式。既存のSQLite保存データを保持するDB移行、Firebaseメール/パスワード認証、Cloud Firestore同期を追加しています。Android Studioではリポジトリのルートを開いてください。
- **`docs/README.md`**: Firebase初期設定やPWAの使い方。Webアプリの設定ではFirebase Web App ID・Auth Domainも必要です。
- **`.github/workflows/check.yml`**: WebテストとAndroidデバッグビルドのCI。

既存のルート直下の `index.html` は初期Web版として残しています。機能を追加した版は `docs/` にあります。アカウント作成にはFirebase側でメール/パスワード認証とFirestoreの準備が必要です。
