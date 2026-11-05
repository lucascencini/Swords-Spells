package com.minidofus;

import com.minidofus.model.AiTests;
import com.minidofus.model.BattleTests;
import com.minidofus.model.CommandTests;
import com.minidofus.model.MapTests;
import com.minidofus.net.NetTests;

/** Point d'entrée des tests (voir build.sh / build.bat : commande « test »). */
public final class AllTests {

    private AllTests() { }

    public static void main(String[] args) throws Exception {
        BattleTests.run();
        MapTests.run();
        AiTests.run();
        CommandTests.run();
        NetTests.run();
        System.exit(Check.report());
    }
}
