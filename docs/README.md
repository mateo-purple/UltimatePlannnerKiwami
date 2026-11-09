# ブラウザ版 アルティメットプランナー極み

Android / iPhone / Windows / Mac で使える、週間予定表型のWebアプリです。デスクトップ・スマホ画面に対応します。

## 起動

`docs/` を静的ホスティングしてください。GitHub Pages では **Settings → Pages → Deploy from a branch → `main` / `/docs`** を選択します。GitHub Freeの非公開リポジトリではPagesを公開できないため、その場合はFirebase Hostingなどの静的ホスティングを利用するか、公開リポジトリへ変更する必要があります。Pages上のサイトは原則インターネットからアクセスできます。

開発時は `python -m http.server 8765 --directory docs` で起動し、ブラウザで `http://localhost:8765` を開きます。HTMLファイルをダブルクリックした `file://` 起動はES Modulesが制限される場合があります。

## Firebaseの設定

1. [Firebase Console](https://console.firebase.google.com/) でプロジェクトを作成。
2. **Authentication → Sign-in method → メール/パスワード** を有効にする。
3. **Firestore Database** を作成し、リポジトリ直下の `firestore.rules` の内容を **ルール** に貼り付けて公開する（公開ルールにはしない）。
4. **プロジェクトの設定 → マイアプリ** でWebアプリを追加し、`firebaseConfig` の `apiKey`、`projectId`、`appId`、`authDomain` を控える。
5. Web版の **設定 → クラウド同期** に4項目を入力。再読み込み後に **アカウント → 新規登録 / ログイン** を使う。
6. Android版の **設定 → 端末間の同期** に同じ `projectId` と `apiKey` を入力し、同じメール・パスワードでログインする。
7. GitHub Pagesのドメインを使う場合は、Authenticationの **Settings → Authorized domains** に `mateo-purple.github.io` を追加する（Firebase設定により必要な場合）。

Firebaseプロジェクト未設定でも、予定はブラウザ内のローカルストレージに保存されます。別のブラウザや端末へ自動共有されるのはFirebase設定・ログイン後です。

## 画面・機能

- 7曜日×24時間の週間グリッド。前週 / 翌週、今日、予定ブロックのタップ編集。
- 授業・バイトなどの**毎週繰り返し**と、普通の予定。複数日にまたがる予定も日ごとに表示。
- タスクの期限・作業時間・進捗。固定予定を避けた**空き時間への自動割当**（表示のみ、予定として保存しない）。
- モバイル向け横スクロール。ブラウザからホーム画面に追加できるPWA（初回オンラインで読み込む）。
- JSON形式でデータのバックアップ・復元。
- Firestoreの更新通知で、ログインした他のブラウザにも予定を反映。
- 通信不可時のローカル編集と復帰後の送信。削除はtombstoneとして同期し、別端末で勝手に復活することを防止。

## データ形式と制約

クラウドのドキュメントは `users/{firebaseAuth.uid}/entries/{syncId}`。`syncId` はUUID、タイムスタンプはUNIXミリ秒です。変更の競合時は `updatedMs` の大きい側を採用します。端末時計のずれが大きい場合や同時編集には注意してください。

Web版ではAndroid端末の日本語OCR・Android通知は実行できません。また、端末内保存はブラウザデータ削除で消えます。重要な予定はログインして同期するかJSONバックアップを取ってください。

## テスト

`node --test docs/tests/core.test.mjs` （リポジトリルートから）。
