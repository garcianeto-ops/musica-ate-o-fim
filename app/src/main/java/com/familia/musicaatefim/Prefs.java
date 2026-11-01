package com.familia.musicaatefim;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String NOME = "config";
    static final String ATIVO = "ativo";
    static final String PAUSAR = "pausarNoFim";
    static final String MOSTRAR_MSG = "mostrarMensagem";
    static final String MSG = "mensagem";
    static final String MSG_FIM = "mensagemFim";
    static final String SOM_FIM = "somFim";

    static final String MSG_PADRAO = "ESPERE";
    static final String MSG_FIM_PADRAO = "PODE ESCOLHER OUTRA MÚSICA";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NOME, Context.MODE_PRIVATE);
    }

    static boolean ativo(Context c) {
        return get(c).getBoolean(ATIVO, true);
    }

    static boolean pausar(Context c) {
        return get(c).getBoolean(PAUSAR, true);
    }

    static boolean mostrarMsg(Context c) {
        return get(c).getBoolean(MOSTRAR_MSG, true);
    }

    static final String NOME_ICONE = "nomeIcone";

    static String nomeIcone(Context c) {
        return texto(c, NOME_ICONE, "Minhas músicas");
    }

    static final String POR_TEMPO = "porTempo";
    static final String SEGUNDOS = "segundos";
    static final int SEGUNDOS_PADRAO = 60;

    /** false = trava até o fim da música; true = trava por um número de segundos. */
    static boolean porTempo(Context c) {
        return get(c).getBoolean(POR_TEMPO, false);
    }

    static int segundos(Context c) {
        return Math.max(5, get(c).getInt(SEGUNDOS, SEGUNDOS_PADRAO));
    }

    static final String COBRIR = "cobrirVideo";

    static boolean cobrir(Context c) {
        return get(c).getBoolean(COBRIR, true);
    }

    static boolean somFim(Context c) {
        return get(c).getBoolean(SOM_FIM, false);
    }

    static String msg(Context c) {
        return texto(c, MSG, MSG_PADRAO);
    }

    static String msgFim(Context c) {
        return texto(c, MSG_FIM, MSG_FIM_PADRAO);
    }

    private static String texto(Context c, String chave, String padrao) {
        String s = get(c).getString(chave, padrao);
        return (s == null || s.trim().isEmpty()) ? padrao : s.trim();
    }
}
