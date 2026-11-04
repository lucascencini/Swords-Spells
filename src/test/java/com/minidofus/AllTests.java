package com.minidofus;

import com.minidofus.model.BattleTests;
import com.minidofus.model.MapTests;

/** Point d'entrée des tests (voir build.sh / build.bat : commande « test »). */
public final class AllTests {

    private AllTests() { }

    public static void main(String[] args) {
        BattleTests.run();
        MapTests.run();
        System.exit(Check.report());
    }
}
