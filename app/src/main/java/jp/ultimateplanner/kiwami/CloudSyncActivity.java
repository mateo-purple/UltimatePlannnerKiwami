package jp.ultimateplanner.kiwami;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Manual Firebase setup + email/password login. Credentials are never committed to source control. */
public class CloudSyncActivity extends Activity {
    private EditText project,key,email,password;
    private TextView status,login,register,sync,logout;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Ui.BG);build();}
    private void build(){
        LinearLayout outer=Ui.vertical(this);outer.setBackgroundColor(Ui.BG);setContentView(outer);
        LinearLayout toolbar=Ui.horizontal(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);Ui.padding(toolbar,20,14,20,14);toolbar.setBackgroundColor(Color.WHITE);
        TextView back=Ui.text(this,"‹",30,Ui.INK,true);back.setOnClickListener(v->finish());toolbar.addView(back,Ui.margin(this,42,-2,0,0,10,0));
        toolbar.addView(Ui.text(this,"端末間の同期",19,Ui.INK,true));outer.addView(toolbar);
        ScrollView scroll=new ScrollView(this);LinearLayout form=Ui.vertical(this);Ui.padding(form,20,22,20,35);scroll.addView(form);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        form.addView(Ui.text(this,"Android・PC・iPhoneで同じ予定を管理する",18,Ui.INK,true));
        Ui.attach(form,Ui.text(this,"FirebaseのAuthentication（メール/パスワード）とFirestoreを有効にしてください。Web版の設定と同じProject ID・Web API Keyを入力します。",13,Ui.MUTED),10);
        status=Ui.text(this,CloudSync.accountLabel(this),13,Ui.PURPLE,true);form.addView(status,Ui.margin(this,-1,-2,0,18,0,8));
        Ui.fieldLabel(form,"Firebase Project ID");project=Ui.field(this,"例: your-project-id",CloudSync.project(this),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);form.addView(project,Ui.lp(this,-1,-2));
        Ui.fieldLabel(form,"Firebase Web API Key");key=Ui.field(this,"AIza...",CloudSync.apiKey(this),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);form.addView(key,Ui.lp(this,-1,-2));
        Ui.fieldLabel(form,"メールアドレス");email=Ui.field(this,"name@example.com",CloudSync.email(this),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);form.addView(email,Ui.lp(this,-1,-2));
        Ui.fieldLabel(form,"パスワード");password=Ui.field(this,"6文字以上","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);form.addView(password,Ui.lp(this,-1,-2));
        login=Ui.button(this,"ログインして同期",true);login.setOnClickListener(v->authenticate(false));form.addView(login,Ui.margin(this,-1,-2,0,20,0,0));
        register=Ui.button(this,"新規アカウントを作成",false);register.setOnClickListener(v->authenticate(true));form.addView(register,Ui.margin(this,-1,-2,0,10,0,0));
        sync=Ui.button(this,"今すぐ同期",false);sync.setOnClickListener(v->{busy(true);status.setText("同期中…");CloudSync.sync(this,true,(message,ok)->finished(message,ok));});
        form.addView(sync,Ui.margin(this,-1,-2,0,15,0,0));
        logout=Ui.button(this,"ログアウト",false);logout.setOnClickListener(v->{CloudSync.logout(this);status.setText("ログアウトしました。予定は端末に残ります。");refreshButtons();});
        form.addView(logout,Ui.margin(this,-1,-2,0,9,0,0));
        Ui.attach(form,Ui.text(this,"既存の予定は消えません。初回ログイン時に端末内の予定をクラウドへアップロードします。削除も同期されます。別のアカウントへ切り替えて予定が混ざることを防ぐため、同期先アカウントの変更は制限しています。",12,Ui.MUTED),22);
        refreshButtons();
    }
    private void refreshButtons(){boolean connected=CloudSync.signedIn(this);sync.setEnabled(connected);logout.setEnabled(connected);}
    private void busy(boolean yes){login.setEnabled(!yes);register.setEnabled(!yes);sync.setEnabled(!yes&&CloudSync.signedIn(this));logout.setEnabled(!yes&&CloudSync.signedIn(this));}
    private void authenticate(boolean create){
        String mail=email.getText().toString().trim(),pass=password.getText().toString();
        if(mail.isEmpty()||pass.length()<6){status.setText("メールアドレスと6文字以上のパスワードを入力してください");return;}
        busy(true);status.setText("接続しています…");
        CloudSync.signIn(this,project.getText().toString().trim(),key.getText().toString().trim(),mail,pass,create,(message,ok)->finished(message,ok));
    }
    private void finished(String message,boolean ok){status.setText(message);busy(false);password.setText("");refreshButtons();if(ok)Toast.makeText(this,"同期しました",Toast.LENGTH_SHORT).show();}
}
