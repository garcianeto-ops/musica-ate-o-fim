package com.familia.musicaatefim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Camada transparente por cima da tela que bloqueia o toque e mostra o progresso da música. */
final class Trava {

    interface Saida {
        void liberar();
    }

    private static final long TEMPO_SAIDA_MS = 5000;

    private final Context base;
    private final Saida saida;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable sair = new Runnable() {
        @Override
        public void run() {
            saida.liberar();
        }
    };

    private WindowManager wm;
    private FrameLayout raiz;
    private TextView titulo;
    private TextView falta;
    private Barra barra;
    private boolean visivel;

    Trava(Context base, Saida saida) {
        this.base = base;
        this.saida = saida;
    }

    boolean visivel() {
        return visivel;
    }

    private void criar() {
        Context c = base;
        if (Build.VERSION.SDK_INT >= 30) {
            DisplayManager dm = base.getSystemService(DisplayManager.class);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            c = base.createWindowContext(d, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        float dp = c.getResources().getDisplayMetrics().density;

        raiz = new FrameLayout(c);
        raiz.setBackgroundColor(0x55000000);
        // Engole todos os toques: nada chega ao YouTube Music por baixo.
        raiz.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                return true;
            }
        });

        LinearLayout painel = new LinearLayout(c);
        painel.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (22 * dp);
        painel.setPadding(p, p, p, p);
        GradientDrawable fundo = new GradientDrawable();
        fundo.setColor(0xE61B2A49);
        fundo.setCornerRadius(28 * dp);
        painel.setBackground(fundo);

        titulo = new TextView(c);
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(22);
        titulo.setMaxLines(2);
        titulo.setEllipsize(TextUtils.TruncateAt.END);
        painel.addView(titulo);

        barra = new Barra(c, dp);
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

        FrameLayout.LayoutParams lpPainel = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int m = (int) (16 * dp);
        lpPainel.setMargins(m, m, m, (int) (56 * dp));
        raiz.addView(painel, lpPainel);

        // Saída secreta para os pais: segurar o canto superior direito por 5 segundos.
        View canto = new View(c);
        canto.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    h.removeCallbacks(sair);
                    h.postDelayed(sair, TEMPO_SAIDA_MS);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    h.removeCallbacks(sair);
                }
                return true;
            }
        });
        int lado = (int) (110 * dp);
        raiz.addView(canto, new FrameLayout.LayoutParams(lado, lado, Gravity.TOP | Gravity.END));
    }

    void mostrar() {
        if (visivel) return;
        try {
            if (raiz == null) criar();
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(raiz, lp);
            visivel = true;
        } catch (Exception e) {
            visivel = false;
        }
    }

    void esconder() {
        h.removeCallbacks(sair);
        if (!visivel) return;
        visivel = false;
        try {
            wm.removeView(raiz);
        } catch (Exception ignored) {
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

    /** Barra de progresso grande e arredondada. */
    static final class Barra extends View {
        private final Paint fundo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frente = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float fracao;

        Barra(Context c, float dp) {
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
