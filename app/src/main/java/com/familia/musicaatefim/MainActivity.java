package com.familia.musicaatefim;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.text.InputType;
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
    private ImageView fotoView;
    private static final int PEDIR_FOTO = 10;

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

        // Foto escolhida no próprio celular (fica guardada só dentro do app).
        fotoView = new ImageView(this);
        fotoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fotoView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        fotoView.setClipToOutline(true);
        int tam = (int) (170 * dp);
        LinearLayout.LayoutParams lpFoto = new LinearLayout.LayoutParams(tam, tam);
        lpFoto.gravity = Gravity.CENTER_HORIZONTAL;
        lpFoto.topMargin = (int) (12 * dp);
        col.addView(fotoView, lpFoto);
        mostrarFoto();

        col.addView(botao("Escolher foto (da galeria)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(i, "Escolher foto"), PEDIR_FOTO);
            }
        }));
        col.addView(texto("Nome embaixo do ícone:", 15, false));
        col.addView(campo(Prefs.NOME_ICONE, Prefs.nomeIcone(this)));
        col.addView(botao("Criar ícone com a foto na tela inicial (abre o YouTube Music)",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        criarAtalho();
                    }
                }));
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

        col.addView(texto("Quanto tempo travar", 20, true));
        final RadioGroup grupo = new RadioGroup(this);
        final RadioButton rbFim = new RadioButton(this);
        rbFim.setText("Até o fim da música");
        rbFim.setTextSize(16);
        rbFim.setId(View.generateViewId());
        final RadioButton rbTempo = new RadioButton(this);
        rbTempo.setText("Por um tempo determinado (em segundos)");
        rbTempo.setTextSize(16);
        rbTempo.setId(View.generateViewId());
        grupo.addView(rbFim);
        grupo.addView(rbTempo);
        grupo.check(Prefs.porTempo(this) ? rbTempo.getId() : rbFim.getId());
        col.addView(grupo);

        final EditText segundos = new EditText(this);
        segundos.setInputType(InputType.TYPE_CLASS_NUMBER);
        segundos.setTextSize(18);
        segundos.setHint("Segundos (mínimo 5)");
        segundos.setText(String.valueOf(Prefs.segundos(this)));
        segundos.setEnabled(Prefs.porTempo(this));
        segundos.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                int v;
                try {
                    v = Integer.parseInt(s.toString().trim());
                } catch (NumberFormatException e) {
                    return;
                }
                Prefs.get(MainActivity.this).edit().putInt(Prefs.SEGUNDOS, Math.max(5, v)).apply();
            }
        });
        col.addView(segundos);
        col.addView(texto("No modo por tempo, se a música for mais curta, a tela destrava no fim dela.",
                14, false));

        grupo.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) {
                boolean tempo = id == rbTempo.getId();
                segundos.setEnabled(tempo);
                Prefs.get(MainActivity.this).edit().putBoolean(Prefs.POR_TEMPO, tempo).apply();
            }
        });

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

    private File arquivoFoto() {
        return new File(getFilesDir(), "foto.jpg");
    }

    private void aviso(String t) {
        Toast.makeText(this, t, Toast.LENGTH_LONG).show();
    }

    private void mostrarFoto() {
        Bitmap b = BitmapFactory.decodeFile(arquivoFoto().getPath());
        if (b != null) {
            fotoView.setImageBitmap(b);
            fotoView.setBackground(null);
        } else {
            fotoView.setImageDrawable(null);
            fotoView.setBackgroundColor(0xFFE0E7FF);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PEDIR_FOTO || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Bitmap b = decodificar(data.getData());
            Bitmap q = quadrado(b, 512);
            FileOutputStream out = new FileOutputStream(arquivoFoto());
            try {
                q.compress(Bitmap.CompressFormat.JPEG, 92, out);
            } finally {
                out.close();
            }
            mostrarFoto();
            aviso("Foto salva. Agora toque em \"Criar ícone com a foto\".");
        } catch (Exception e) {
            aviso("Não consegui abrir essa foto. Tente outra.");
        }
    }

    /** Abre a foto já na posição certa (respeitando a rotação) e reduzida para no máximo ~1024 px. */
    private Bitmap decodificar(Uri uri) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(src, new ImageDecoder.OnHeaderDecodedListener() {
                @Override
                public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                            ImageDecoder.Source s) {
                    int w = info.getSize().getWidth();
                    int h = info.getSize().getHeight();
                    int menor = Math.min(w, h);
                    if (menor > 1024) {
                        float f = 1024f / menor;
                        decoder.setTargetSize(Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f)));
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                }
            });
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(uri);
        try {
            BitmapFactory.decodeStream(in, null, o);
        } finally {
            if (in != null) in.close();
        }
        int amostra = 1;
        while (Math.min(o.outWidth, o.outHeight) / (amostra * 2) >= 1024) amostra *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = amostra;
        in = getContentResolver().openInputStream(uri);
        try {
            Bitmap b = BitmapFactory.decodeStream(in, null, o2);
            if (b == null) throw new Exception("foto inválida");
            return b;
        } finally {
            if (in != null) in.close();
        }
    }

    /** Recorte quadrado, puxado um pouco para cima (onde normalmente fica o rosto). */
    private static Bitmap quadrado(Bitmap b, int lado) {
        int w = b.getWidth();
        int h = b.getHeight();
        int s = Math.min(w, h);
        int x = (w - s) / 2;
        int y = (int) ((h - s) * 0.2f);
        Bitmap q = Bitmap.createBitmap(b, x, y, s, s);
        return Bitmap.createScaledBitmap(q, lado, lado, true);
    }

    /** Ícone na tela inicial com a foto, que abre direto o YouTube Music. */
    private void criarAtalho() {
        Bitmap foto = BitmapFactory.decodeFile(arquivoFoto().getPath());
        if (foto == null) {
            aviso("Escolha a foto primeiro.");
            return;
        }
        Intent yt = getPackageManager().getLaunchIntentForPackage(VigiaMusicaService.YT_MUSIC);
        if (yt == null) {
            aviso("YouTube Music não encontrado neste celular.");
            return;
        }
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            aviso("Este celular não permite criar ícones assim.");
            return;
        }
        if (yt.getAction() == null) yt.setAction(Intent.ACTION_MAIN);
        Icon icone = Icon.createWithAdaptiveBitmap(Bitmap.createScaledBitmap(foto, 432, 432, true));
        ShortcutInfo si = new ShortcutInfo.Builder(this, "foto-" + System.currentTimeMillis())
                .setShortLabel(Prefs.nomeIcone(this))
                .setIcon(icone)
                .setIntent(yt)
                .build();
        sm.requestPinShortcut(si, null);
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
