# ブラウザ版 アルティメットプランナー極み

Android / iPhone / Windows / Mac で使える、週間予定表型のWebアプリです。デスクトップ・スマホ画面に対応します。

## 更新時の注意

Cloudflare Workersの静的ファイル更新は自動ではありません。GitHubへpushしたあと、最新版の `docs/` フォルダをCloudflareに再アップロードしてください。既存のブラウザデータを消さずに更新できます。PWAの旧画面が残るときは強制再読み込みを行ってください。

## 起動

`docs/` を静的ホスティングしてください。GitHub Pages では **Settings → Pages → Deploy from a branch → `main` / `/docs`** を選択します。GitHub Freeの非公開リポジトリではPagesを公開できないため、その場合はFirebase Hostingなどの静的ホスティングを利用するか、公開リポジトリへ変更する必要があります。Pages上のサイトは原則インターネットからアクセスできます。

開発時は `python -m http.server 8765 --directory docs` で起動し、ブラウザで `http://localhost:8765` を開きます。HTMLファイルをダブルクリックした `file://` 起動はES Modulesが制限される場合があります。

## Firebaseの設定（管理者向け）

Firebaseプロジェクト `ultimateplannerkiwami` は接続済みです。`docs/firebase-config.js` は公開用のFirebaseクライアント設定であり、秘密鍵ではありません。**サービスアカウントのJSONや管理者秘密鍵をここへ追加しないでください。**

- Firebase Authenticationのメール/パスワード認証とCloud Firestoreを有効にしてください。
- リポジトリの `firestore.rules` と同等のユーザー本人のみ許可するルールをFirestoreへ公開してください。
- Web版は起動時にFirebaseへ自動接続します。利用者がProject IDやAPIキーを入力する必要はありません。
- Web版右上の「アカウント」から登録・ログインし、Android版でも同じアカウントでログインしてください。
- Cloudflare Workersの静的アセット公開先: `https://ultimateplannerkiwami.iloveshiroganenoel.workers.dev/`。新しいWeb版を反映するには `docs/` のファイルを再アップロードする必要があります。GitHubの更新だけでは自動公開されません。
- 既存ブラウザのログイン・予定データは、同じURLで再公開すれば維持されます。ブラウザデータの削除は避けてください。

Firebase Web APIキーは公開可能なクライアント識別子ですが、**Firestore Security RulesとAuthenticationが必須**です。公開したサイトに管理者認証情報を含めないでください。

## 画面・機能

- 7曜日×24時間の週間グリッド。前週 / 翌週、今日、予定ブロックのタップ編集。
- 授業・バイトなどの**毎週繰り返し**と、普通の予定。複数日にまたがる予定も日ごとに表示。
- タスクの期限・作業時間・進捗。固定予定を避けた**空き時間への自動割当**（表示のみ、予定として保存しない）。
- モバイル向け横スクロール。ブラウザからホーム画面に追加できるPWA（初回オンラインで読み込む）。
- JSON形式でデータのバックアップ・復元（Android版と共通形式、ファイル上限8MB）。
- Firestoreの更新通知で、ログインした他のブラウザにも予定を反映。
- 通信不可時にも以前ログインしたユーザーの端末内予定を表示し、編集できます。再接続後に同期します。削除はtombstoneとして同期し、別端末で勝手に復活することを防止。

## データ形式と制約

クラウドのドキュメントは `users/{firebaseAuth.uid}/entries/{syncId}`。`syncId` はUUID、タイムスタンプはUNIXミリ秒です。変更の競合時は `updatedMs` の大きい側を採用します。端末時計のずれが大きい場合や同時編集には注意してください。

Web版ではAndroid端末の日本語OCR・Android通知は実行できません。また、端末内保存はブラウザデータ削除で消えます。重要な予定はログインして同期するかJSONバックアップを取ってください。

## テスト

`node --test docs/tests/core.test.mjs` （リポジトリルートから）。