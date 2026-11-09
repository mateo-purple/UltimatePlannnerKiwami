import {normalize} from './core.js';
/** Firebase browser SDK is loaded only after configuration so offline-only mode needs no network. */
const VERSION='13.2.0';
const CDN='https://www.gstatic.com/firebasejs/';
export class CloudClient {
  constructor(onUser,onRemote,onStatus){this.onUser=onUser;this.onRemote=onRemote;this.onStatus=onStatus;this.user=null;this.unsubscribe=null;this.working=false;}
  async initialize(config){
    if(!config.apiKey||!config.projectId||!config.appId) {this.onStatus('端末内のみ');return;}
    const [appMod,authMod,storeMod]=await Promise.all(['firebase-app','firebase-auth','firebase-firestore'].map(p=>import(`${CDN}${VERSION}/${p}.js`)));
    this.authApi=authMod;this.storeApi=storeMod;
    const app=appMod.initializeApp(config);
    this.auth=authMod.getAuth(app);this.store=storeMod.getFirestore(app);
    authMod.onAuthStateChanged(this.auth,user=>this.setUser(user),error=>this.onStatus('認証エラー: '+error.code));
    this.onStatus('ログイン待ち');
  }
  setUser(user){
    if(this.unsubscribe){this.unsubscribe();this.unsubscribe=null;}
    this.user=user;this.onUser(user);
    if(!user){this.onStatus('未ログイン');return;}
    const collection=this.storeApi.collection(this.store,'users',user.uid,'entries');
    this.unsubscribe=this.storeApi.onSnapshot(collection,snapshot=>{
      const values=snapshot.docs.map(d=>normalize({...d.data(),syncId:d.id}));
      this.onRemote(values);
      this.onStatus(snapshot.metadata.fromCache?'オフラインキャッシュ':'同期済み');
    },error=>this.onStatus(`同期失敗: ${error.code || error.message}`));
    this.onStatus('同期中…');
  }
  async login(email,password,create=false){
    if(!this.auth)throw Error('Firebase設定を保存してからページを再読み込みしてください');
    const fn=create?this.authApi.createUserWithEmailAndPassword:this.authApi.signInWithEmailAndPassword;
    await fn(this.auth,email,password);
  }
  async logout(){if(this.auth)await this.authApi.signOut(this.auth);}
  async push(entry){
    if(!this.user)return;
    const doc=this.storeApi.doc(this.store,'users',this.user.uid,'entries',entry.syncId);
    await this.storeApi.setDoc(doc,normalize(entry));
  }
  async pushMany(entries){
    if(!this.user||entries.length===0)return;
    const outcomes=await Promise.allSettled(entries.map(e=>this.push(e)));
    const failed=outcomes.filter(x=>x.status==='rejected');
    if(failed.length)this.onStatus(`${failed.length}件の同期を保留中（再接続時に再試行）`);
  }
}
