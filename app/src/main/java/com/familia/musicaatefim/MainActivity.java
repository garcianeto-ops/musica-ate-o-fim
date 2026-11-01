package com.familia.musicaatefim;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.service.notification.NotificationListenerService;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status1;
    private TextView status2;
    private float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * dp);
        col.setPadding(p, p, p, p);
        sv.addView(col);

        col.addView(texto("Música até o Fim", 26, true));
        col.addView(texto("Quando uma música começa no YouTube Music, a tela fica travada até ela terminar. "
                + "No fim, a tela destrava sozinha para escolher a próxima.", 16, false));

        col.addView(texto("Passo 1", 20, true));
        col.addView(botao("Permitir acesso às notificações", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        }));
        status1 = texto("", 16, false);
        col.addView(status1);
        col.addView(texto("Se a opção aparecer bloqueada (cinza): abra Configurações > Apps > "
                + "Música até o Fim > ⋮ (três pontinhos no canto) > Permitir configurações restritas. "
                + "Depois volte aqui e tente de novo.", 14, false));

        col.addView(texto("Passo 2", 20, true));
        col.addView(botao("Permitir sobrepor outros apps", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        }));
        status2 = texto("", 16, false);
        col.addView(status2);

        col.addView(texto("Ajustes", 20, true));
        col.addView(chave("Proteção ligada", Prefs.ATIVO, Prefs.ativo(this)));
        col.addView(chave("Pausar no fim de cada música (para ele escolher a próxima; a escolhida toca sozinha)",
                Prefs.PAUSAR, Prefs.pausar(this)));

        col.addView(texto("Mensagens na tela", 20, true));
        col.addView(chave("Esconder o vídeo por completo (ele só ouve)",
                Prefs.COBRIR, Prefs.cobrir(this)));
        col.addView(chave("Mostrar mensagem sobre o vídeo enquanto toca",
                Prefs.MOSTRAR_MSG, Prefs.mostrarMsg(this)));
        col.addView(texto("Mensagem enquanto a música toca:", 15, false));
        col.addView(campo(Prefs.MSG, Prefs.msg(this)));
        col.addView(texto("Mensagem quando a música termina (faixa verde):", 15, false));
        col.addView(campo(Prefs.MSG_FIM, Prefs.msgFim(this)));
        col.addView(chave("Tocar um aviso sonoro quando a música terminar",
                Prefs.SOM_FIM, Prefs.somFim(this)));

        col.addView(texto("Para destravar durante uma música (só para você): aperte VOLUME + "
                + "e logo em seguida VOLUME − (em até 2 segundos).", 16, true));
        col.addView(texto("Dica: ative a Fixação de tela (Configurações > Segurança > Fixar app) e fixe o "
                + "YouTube Music. Assim ele não sai do app nem abre a barra de notificações.", 14, false));

        col.addView(botao("Abrir YouTube Music", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = getPackageManager().getLaunchIntentForPackage(VigiaMusicaService.YT_MUSIC);
                if (i != null) startActivity(i);
            }
        }));

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean n = notificacoesOk();
        boolean o = Settings.canDrawOverlays(this);
        status1.setText(n ? "✅ Concedido" : "❌ Ainda não concedido");
        status2.setText(o ? "✅ Concedido" : "❌ Ainda não concedido");
        if (n) {
            try {
                NotificationListenerService.requestRebind(new ComponentName(this, VigiaMusicaService.class));
            } catch (Exception ignored) {
            }
        }
    }

    private boolean notificacoesOk() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    private TextView texto(String t, int sp, boolean negrito) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(sp);
        tv.setTextColor(0xFF1F2937);
        if (negrito) tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (12 * dp);
        tv.setLayoutParams(lp);
        return tv;
    }

    private Button botao(String t, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(t);
        bt.setAllCaps(false);
        bt.setOnClickListener(l);
        return bt;
    }

    private EditText campo(final String prefKey, String valor) {
        EditText et = new EditText(this);
        et.setText(valor);
        et.setTextSize(18);
        et.setSingleLine(true);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                Prefs.get(MainActivity.this).edit().putString(prefKey, s.toString()).apply();
            }
        });
        return et;
    }

    private Switch chave(String t, final String prefKey, boolean valor) {
        Switch s = new Switch(this);
        s.setText(t);
        s.setTextSize(16);
        s.setChecked(valor);
        int v = (int) (10 * dp);
        s.setPadding(0, v, 0, v);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.get(MainActivity.this).edit().putBoolean(prefKey, on).apply();
            }
        });
        return s;
    }
}
