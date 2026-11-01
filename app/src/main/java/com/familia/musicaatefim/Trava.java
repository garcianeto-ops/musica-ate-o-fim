package com.familia.musicaatefim;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Camadas por cima da tela:
 *  - trava: bloqueia o toque, mostra "ESPERE" sobre a área do vídeo e a barra de progresso;
 *  - aviso de fim: faixa verde no mesmo lugar dizendo que pode escolher outra música
 *    (não bloqueia o toque).
 */
final class Trava {

    interface Saida {
        void liberar();
    }

    private static final long JANELA_VOLUME_MS = 2000;
    private static final long AVISO_FIM_MAX_MS = 5000;

    private final Context base;
    private final Saida saida;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable esconderFimRun = new Runnable() {
        @Override
        public void run() {
            esconderFim();
        }
    };

    private Context c;
    private WindowManager wm;
    private float dp;

    private Raiz raiz;
    private LinearLayout conteudo;
    private ImageView foto;
    private View veu;
    private Bitmap arteAtual;
    private boolean cobrirAtual;
    private View espaco;
    private TextView espere;
    private TextView titulo;
    private TextView falta;
    private Barra barra;
    private boolean visivel;

    private FrameLayout fimRaiz;
    private TextView fimTexto;
    private boolean fimVisivel;

    Trava(Context base, Saida saida) {
        this.base = base;
        this.saida = saida;
    }

    boolean visivel() {
        return visivel;
    }

    private void preparar() {
        if (wm != null) return;
        c = base;
        if (Build.VERSION.SDK_INT >= 30) {
            DisplayManager dm = base.getSystemService(DisplayManager.class);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            c = base.createWindowContext(d, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        dp = c.getResources().getDisplayMetrics().density;
    }

    private GradientDrawable fundo(int cor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(28 * dp);
        return g;
    }

    private TextView aviso(int cor) {
        TextView tv = new TextView(c);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(48);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        int p = (int) (24 * dp);
        tv.setPadding(p, (int) (36 * dp), p, (int) (36 * dp));
        tv.setBackground(fundo(cor));
        return tv;
    }

    private FrameLayout.LayoutParams lpAviso() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        int m = (int) (16 * dp);
        lp.setMargins(m, (int) (90 * dp), m, m);
        return lp;
    }

    private void criarTrava() {
        preparar();
        raiz = new Raiz(c);
        raiz.setBackgroundColor(0x55000000);
        // Engole todos os toques: nada chega ao YouTube Music por baixo.
        raiz.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                return true;
            }
        });

        // Coluna: aviso "ESPERE" em cima (área do vídeo), espaço, painel de progresso embaixo.
        // Capa da música ao fundo (só no modo que esconde o vídeo), com um véu escuro por cima.
        foto = new ImageView(c);
        foto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        raiz.addView(foto, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        veu = new View(c);
        veu.setBackgroundColor(0x8C000000);
        raiz.addView(veu, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        conteudo = new LinearLayout(c);
        conteudo.setOrientation(LinearLayout.VERTICAL);
        raiz.addView(conteudo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        espere = aviso(0xE61B2A49);
        espere.setShadowLayer(14 * dp, 0, 0, 0xE6000000);
        conteudo.addView(espere);
        espaco = new View(c);
        conteudo.addView(espaco, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout painel = new LinearLayout(c);
        painel.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (22 * dp);
        painel.setPadding(p, p, p, p);
        painel.setBackground(fundo(0xE61B2A49));

        titulo = new TextView(c);
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(22);
        titulo.setMaxLines(2);
        titulo.setEllipsize(TextUtils.TruncateAt.END);
        painel.addView(titulo);

        barra = new Barra(c);
        LinearLayout.LayoutParams lpBarra = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (30 * dp));
        lpBarra.topMargin = (int) (16 * dp);
        painel.addView(barra, lpBarra);

        falta = new TextView(c);
        falta.setTextColor(0xFFDDE6F5);
        falta.setTextSize(18);
        LinearLayout.LayoutParams lpFalta = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpFalta.topMargin = (int) (10 * dp);
        painel.addView(falta, lpFalta);

        LinearLayout.LayoutParams lpPainel = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = (int) (16 * dp);
        lpPainel.setMargins(m, m, m, (int) (40 * dp));
        conteudo.addView(painel, lpPainel);
    }

    /** cobrir = fundo opaco e "ESPERE" ocupando toda a parte de cima: o vídeo some, ele só ouve. */
    private void aplicarModo(boolean cobrir, boolean mostrarMensagem) {
        cobrirAtual = cobrir;
        int m = (int) (16 * dp);
        if (cobrir) {
            raiz.setBackgroundColor(0xFF0F1A2E);
            foto.setVisibility(View.VISIBLE);
            veu.setVisibility(arteAtual != null ? View.VISIBLE : View.GONE);
            espere.setBackground(fundo(arteAtual != null ? 0x40000000 : 0xE61B2A49));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            lp.setMargins(m, (int) (40 * dp), m, 0);
            espere.setLayoutParams(lp);
            espere.setAutoSizeTextTypeUniformWithConfiguration(28, 110, 2, TypedValue.COMPLEX_UNIT_SP);
            espaco.setVisibility(View.GONE);
            espere.setVisibility(View.VISIBLE);
            espere.setAlpha(mostrarMensagem ? 1f : 0f);
        } else {
            raiz.setBackgroundColor(0x55000000);
            foto.setVisibility(View.GONE);
            veu.setVisibility(View.GONE);
            espere.setBackground(fundo(0xE61B2A49));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(m, (int) (90 * dp), m, 0);
            espere.setLayoutParams(lp);
            espere.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
            espere.setTextSize(48);
            espaco.setVisibility(View.VISIBLE);
            espere.setAlpha(1f);
            espere.setVisibility(mostrarMensagem ? View.VISIBLE : View.GONE);
        }
    }

    private void criarFim() {
        preparar();
        fimRaiz = new FrameLayout(c);
        fimTexto = aviso(0xFF2E7D32);
        fimRaiz.addView(fimTexto, lpAviso());
    }

    void mostrar(String mensagem, boolean mostrarMensagem, boolean cobrir) {
        esconderFim();
        if (visivel) return;
        try {
            if (raiz == null) criarTrava();
            espere.setText(mensagem);
            aplicarModo(cobrir, mostrarMensagem);
            // Janela com foco (sem FLAG_NOT_FOCUSABLE) para receber os botões de volume e o "voltar".
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            if (Build.VERSION.SDK_INT >= 28) {
                // Cobre também a área do entalhe da câmera quando o vídeo está em tela cheia (deitado).
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            raiz.upEm = 0;
            wm.addView(raiz, lp);
            visivel = true;
        } catch (Exception e) {
            visivel = false;
        }
    }

    void esconder() {
        if (!visivel) return;
        visivel = false;
        try {
            wm.removeView(raiz);
        } catch (Exception ignored) {
        }
    }

    /** Faixa verde no lugar do "ESPERE". Não bloqueia o toque: ele já pode escolher a próxima. */
    void mostrarFim(String mensagem) {
        h.removeCallbacks(esconderFimRun);
        try {
            if (fimRaiz == null) criarFim();
            fimTexto.setText(mensagem);
            if (!fimVisivel) {
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.START;
                // Até 0.8 o Android deixa o toque passar para o app de baixo.
                lp.alpha = 0.8f;
                wm.addView(fimRaiz, lp);
                fimVisivel = true;
            }
            h.postDelayed(esconderFimRun, AVISO_FIM_MAX_MS);
        } catch (Exception e) {
            fimVisivel = false;
        }
    }

    void esconderFim() {
        h.removeCallbacks(esconderFimRun);
        if (!fimVisivel) return;
        fimVisivel = false;
        try {
            wm.removeView(fimRaiz);
        } catch (Exception ignored) {
        }
    }

    /** Troca a capa de fundo quando o YouTube Music envia (ou atualiza) a imagem da música. */
    void definirArte(Bitmap b) {
        if (raiz == null || b == arteAtual) return;
        arteAtual = b;
        foto.setImageBitmap(b);
        if (cobrirAtual) {
            veu.setVisibility(b != null ? View.VISIBLE : View.GONE);
            espere.setBackground(fundo(b != null ? 0x40000000 : 0xE61B2A49));
        }
    }

    void atualizar(String nome, long pos, long dur) {
        if (!visivel) return;
        titulo.setText(nome == null ? "" : nome);
        float f = dur > 0 ? Math.min(1f, Math.max(0f, pos / (float) dur)) : 0f;
        barra.definir(f);
        falta.setText("Faltam " + formatar(Math.max(0, dur - pos)));
    }

    private static String formatar(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Raiz da trava: recebe os botões físicos. Volume + e depois volume − (em até 2 s) destrava. */
    final class Raiz extends FrameLayout {
        long upEm;

        Raiz(Context ctx) {
            super(ctx);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            int k = e.getKeyCode();
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                long agora = SystemClock.uptimeMillis();
                if (k == KeyEvent.KEYCODE_VOLUME_UP) {
                    upEm = agora;
                } else if (k == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (upEm > 0 && agora - upEm <= JANELA_VOLUME_MS) {
                        upEm = 0;
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                saida.liberar();
                            }
                        });
                    }
                    upEm = 0;
                } else {
                    upEm = 0;
                }
            }
            // Bloqueia "voltar" e "menu" enquanto travado; o volume continua funcionando normalmente.
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU) return true;
            return super.dispatchKeyEvent(e);
        }
    }

    /** Barra de progresso grande e arredondada. */
    static final class Barra extends View {
        private final Paint fundo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frente = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float fracao;

        Barra(Context c) {
            super(c);
            fundo.setColor(0x40FFFFFF);
            frente.setColor(0xFF7FD3A8);
        }

        void definir(float f) {
            fracao = f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float hh = getHeight();
            float raio = hh / 2f;
            r.set(0, 0, w, hh);
            canvas.drawRoundRect(r, raio, raio, fundo);
            float cheio = Math.max(hh, w * fracao);
            r.set(0, 0, cheio, hh);
            canvas.drawRoundRect(r, raio, raio, frente);
        }
    }
}
