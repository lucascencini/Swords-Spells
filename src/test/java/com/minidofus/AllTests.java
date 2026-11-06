package com.minidofus;

import com.minidofus.model.AiTests;
import com.minidofus.model.BattleTests;
import com.minidofus.model.MapTests;

/** Point d'entrée des tests (voir build.sh / build.bat : commande « test »). */
public final class AllTests {

    private AllTests() { }

    public static void main(String[] args) {
        BattleTests.run();
        MapTests.run();
        AiTests.run();
        System.exit(Check.report());
    }
}
