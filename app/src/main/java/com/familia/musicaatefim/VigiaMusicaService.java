package com.familia.musicaatefim;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

import java.util.List;

/**
 * Acompanha o YouTube Music. Quando uma música começa a tocar, trava o toque na tela;
 * quando ela termina, destrava (e pausa a próxima, se configurado) para a criança escolher outra.
 */
public class VigiaMusicaService extends NotificationListenerService {

    static final String YT_MUSIC = "com.google.android.apps.youtube.music";

    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaSessionManager msm;
    private MediaController controle;
    private Trava trava;

    private String faixaAtual;
    private String faixaLiberada;
    private String faixaSuprimida;
    private long suprimirAte;
    private long ultimaPos = -1;
    private long limiteTrava;
    private boolean aguardandoEscolha;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessoes =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> lista) {
                    escolherSessao(lista);
                }
            };

    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            avaliar();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            avaliar();
        }

        @Override
        public void onSessionDestroyed() {
            soltarControle();
            liberar();
            trava.esconderFim();
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String k) {
                    avaliar();
                }
            };

    private final Runnable tique = new Runnable() {
        @Override
        public void run() {
            tique();
        }
    };

    private final Runnable tocarSeParado = new Runnable() {
        @Override
        public void run() {
            if (controle == null) return;
            PlaybackState st = controle.getPlaybackState();
            int e = st == null ? PlaybackState.STATE_NONE : st.getState();
            if (e == PlaybackState.STATE_PAUSED || e == PlaybackState.STATE_STOPPED
                    || e == PlaybackState.STATE_NONE) {
                tocar();
            }
        }
    };

    private final Runnable liberarSeParado = new Runnable() {
        @Override
        public void run() {
            if (!estaTocando()) liberar();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        trava = new Trava(this, new Trava.Saida() {
            @Override
            public void liberar() {
                // Saída dos pais: libera esta música e não trava de novo até a próxima.
                faixaLiberada = faixaAtual;
                VigiaMusicaService.this.liberar();
            }
        });
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefsListener);
        msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        ComponentName cn = new ComponentName(this, VigiaMusicaService.class);
        try {
            msm.addOnActiveSessionsChangedListener(sessoes, cn, h);
            escolherSessao(msm.getActiveSessions(cn));
        } catch (SecurityException ignored) {
            // Acesso às notificações ainda não concedido.
        }
    }

    @Override
    public void onListenerDisconnected() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
        if (msm != null) {
            try {
                msm.removeOnActiveSessionsChangedListener(sessoes);
            } catch (Exception ignored) {
            }
        }
        soltarControle();
        liberar();
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        liberar();
        super.onDestroy();
    }

    private void escolherSessao(List<MediaController> lista) {
        MediaController achado = null;
        if (lista != null) {
            for (MediaController c : lista) {
                if (YT_MUSIC.equals(c.getPackageName())) {
                    achado = c;
                    break;
                }
            }
        }
        if (achado == null) return;
        if (controle != null && controle.getSessionToken().equals(achado.getSessionToken())) return;
        soltarControle();
        controle = achado;
        controle.registerCallback(cb, h);
        avaliar();
    }

    private void soltarControle() {
        if (controle != null) {
            try {
                controle.unregisterCallback(cb);
            } catch (Exception ignored) {
            }
            controle = null;
        }
    }

    private static String chave(MediaMetadata m) {
        if (m == null) return null;
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null) return null;
        String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        return t + "|" + (a == null ? "" : a);
    }

    private static long posicao(PlaybackState st) {
        if (st == null) return 0;
        long p = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING && st.getLastPositionUpdateTime() > 0) {
            long passou = SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime();
            p += (long) (passou * st.getPlaybackSpeed());
        }
        return Math.max(0, p);
    }

    private boolean estaTocando() {
        if (controle == null) return false;
        PlaybackState st = controle.getPlaybackState();
        if (st == null) return false;
        int e = st.getState();
        return e == PlaybackState.STATE_PLAYING
                || e == PlaybackState.STATE_BUFFERING
                || e == PlaybackState.STATE_CONNECTING;
    }

    private void pausar() {
        if (controle != null) {
            try {
                controle.getTransportControls().pause();
            } catch (Exception ignored) {
            }
        }
    }

    private void tocar() {
        if (controle != null) {
            try {
                controle.getTransportControls().play();
            } catch (Exception ignored) {
            }
        }
    }

    /** A música travada terminou: destrava, avisa e, se configurado, pausa a próxima que começou sozinha. */
    private void fimDaMusica() {
        liberar();
        trava.mostrarFim(Prefs.msgFim(this));
        if (Prefs.somFim(this)) tocarSom();
        if (Prefs.pausar(this)) {
            aguardandoEscolha = true;
            faixaSuprimida = faixaAtual;
            suprimirAte = SystemClock.elapsedRealtime() + 4000;
            pausar();
        } else {
            aguardandoEscolha = false;
        }
    }

    private void tocarSom() {
        try {
            Ringtone r = RingtoneManager.getRingtone(this,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
        } catch (Exception ignored) {
        }
    }

    private void avaliar() {
        if (controle == null || !Prefs.ativo(this)) {
            liberar();
            if (trava != null) trava.esconderFim();
            return;
        }
        MediaMetadata md = controle.getMetadata();
        PlaybackState st = controle.getPlaybackState();
        String k = chave(md);
        int estado = st == null ? PlaybackState.STATE_NONE : st.getState();

        if (k != null && !k.equals(faixaAtual)) {
            boolean estavaTravado = trava.visivel();
            faixaAtual = k;
            faixaLiberada = null;
            ultimaPos = -1;
            if (estavaTravado) {
                // Com a tela travada ninguém conseguiu trocar a música: ela terminou e a próxima entrou.
                fimDaMusica();
            } else if (aguardandoEscolha && !k.equals(faixaSuprimida)) {
                // Ele escolheu outra música: toca direto, sem precisar apertar play.
                aguardandoEscolha = false;
                h.removeCallbacks(tocarSeParado);
                h.postDelayed(tocarSeParado, 400);
                h.postDelayed(tocarSeParado, 1500);
            }
        }

        long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = posicao(st);
        long agora = SystemClock.elapsedRealtime();

        if (estado == PlaybackState.STATE_PLAYING) {
            h.removeCallbacks(liberarSeParado);
            if (aguardandoEscolha && k != null && k.equals(faixaSuprimida)) {
                if (agora < suprimirAte) {
                    // A próxima da fila começou sozinha: pausa e espera ele escolher.
                    pausar();
                    return;
                }
                // Passou o tempo: foi ele quem apertou play nessa música.
                aguardandoEscolha = false;
            }
            if (k != null && k.equals(faixaLiberada)) {
                liberar();
                return;
            }
            if (!trava.visivel() && dur > 0 && dur - pos > 2000) {
                travar(dur, pos);
            }
        } else if (estado == PlaybackState.STATE_BUFFERING || estado == PlaybackState.STATE_CONNECTING) {
            // Carregando: mantém como está.
        } else if (trava.visivel()) {
            // Pausou ou parou por conta própria (fim da fila, fone desconectado...): destrava em seguida.
            h.removeCallbacks(liberarSeParado);
            h.postDelayed(liberarSeParado, 2500);
        }
    }

    private void travar(long dur, long pos) {
        if (!Settings.canDrawOverlays(this)) return;
        h.removeCallbacks(tocarSeParado);
        trava.mostrar(Prefs.msg(this), Prefs.mostrarMsg(this), Prefs.cobrir(this));
        if (!trava.visivel()) return;
        ultimaPos = -1;
        limiteTrava = SystemClock.elapsedRealtime() + (dur - pos) + 20000;
        h.removeCallbacks(tique);
        h.post(tique);
    }

    private void liberar() {
        h.removeCallbacks(tique);
        h.removeCallbacks(liberarSeParado);
        if (trava != null) trava.esconder();
    }

    private void tique() {
        if (trava == null || !trava.visivel() || controle == null) return;
        MediaMetadata md = controle.getMetadata();
        PlaybackState st = controle.getPlaybackState();
        long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = posicao(st);

        // Modo "repetir esta música": a posição volta ao início sem trocar de faixa.
        if (ultimaPos >= 0 && dur > 0 && ultimaPos > dur - 5000 && pos < 3000) {
            ultimaPos = -1;
            fimDaMusica();
            return;
        }
        ultimaPos = pos;

        // Segurança: nunca fica travado muito além do tempo da música.
        if (SystemClock.elapsedRealtime() > limiteTrava) {
            liberar();
            return;
        }

        String nome = md == null ? "" : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        trava.atualizar(nome, pos, dur);
        h.postDelayed(tique, 500);
    }
}
