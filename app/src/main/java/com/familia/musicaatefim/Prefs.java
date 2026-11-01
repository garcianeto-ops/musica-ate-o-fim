package com.familia.musicaatefim;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String NOME = "config";
    static final String ATIVO = "ativo";
    static final String PAUSAR = "pausarNoFim";

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
}
