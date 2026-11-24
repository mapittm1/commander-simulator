package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.mana.Mana;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.OptionalCostValue;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Inspect actual nested filter payments, not only an affordability preview. */
public class CommanderWaterbendFilterTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("filter-0"))
                .findFirst().orElseThrow();
    }

    private Card named(Game game, String name, ZoneType zone) {
        return actor(game).getCardsIn(zone, false).stream().filter(card -> card.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private void settle(Game game) {
        GameSimulator.resolveStack(game, actor(game));
    }

    private void cast(Game game, String name, int mana) {
        SpellAbility ability = addCardToZone(name, actor(game), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(actor(game));
        assertTrue(ComputerUtil.playStack(ability, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        settle(game);
    }

    private Game setup(boolean floatGeneric, String filterName) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("filter-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Nested waterbend filter payment"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        for (int index = 0; index < 7; index++) addCardToZone("Island", actor(game), ZoneType.Battlefield);
        cast(game, "Krark-Clan Ironworks", 4);
        cast(game, filterName, 2);
        addTokens("c_a_clue_draw", 6, actor(game));
        if (floatGeneric) {
            Card wastes = addCardToZone("Wastes", actor(game), ZoneType.Battlefield);
            SpellAbility mana = wastes.getManaAbilities().get(0);
            mana.setActivatingPlayer(actor(game));
            assertTrue(ComputerUtil.playNoStack(actor(game), mana, game, false));
            assertEquals(actor(game).getManaPool().totalMana(), 1);
        }
        addCardToZone("Grizzly Bears", actor(game), ZoneType.Graveyard);
        addCardToZone("Spirit Water Revival", actor(game), ZoneType.Hand);
        assertEquals(actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isLand() && !card.isTapped()).count(), 1L);
        assertEquals(tappedArtifacts(game), 0L);
        return game;
    }

    private long tappedArtifacts(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isArtifact() && card.isTapped()).count();
    }

    private SpellAbility revival(Game game, boolean waterbend) {
        SpellAbility base = named(game, "Spirit Water Revival", ZoneType.Hand).getFirstSpellAbility();
        base.setActivatingPlayer(actor(game));
        if (!waterbend) return base;
        List<OptionalCostValue> choices = GameActionUtil.getOptionalCostValues(base).stream()
                .filter(value -> value.getType() == OptionalCost.Generic
                        && "6".equals(value.getCost().getMaxWaterbend())).toList();
        assertEquals(choices.size(), 1);
        SpellAbility chosen = GameActionUtil.addOptionalCosts(base, choices);
        chosen.setActivatingPlayer(actor(game));
        assertEquals(chosen.getPayCosts().getTotalMana().getCMC(), 9);
        return chosen;
    }

    private void nestedSacrificeFilter(int depth) {
        nestedSacrificeFilter(depth, false);
    }

    private void nestedSacrificeFilter(int depth, boolean floatGeneric) {
        nestedSacrificeFilter(depth, floatGeneric, true);
    }

    private void nestedSacrificeFilter(int depth, boolean floatGeneric, boolean waterbend) {
        nestedSacrificeFilter(depth, floatGeneric, waterbend, "Prismite");
    }

    private void nestedSacrificeFilter(int depth, boolean floatGeneric, boolean waterbend, String filterName) {
        Game original = setup(floatGeneric, filterName);
        original.copyLastState();
        Game game = original;
        for (int index = 0; index < depth; index++)
            game = new GameCopier(game).withStack(true).makeCopy(null, actor(game));
        SpellAbility chosen = revival(game, waterbend);
        assertTrue(ComputerUtil.playStack(chosen, actor(game), game),
                "A legal allocation exists: waterbend with independent artifacts, sacrifice others for Prismite and normal mana");
        SpellAbility paid = game.getStack().peekAbility();
        assertEquals(paid.isOptionalCostPaid(OptionalCost.Generic), waterbend);
        Set<SpellAbility> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<SpellAbility> pending = new ArrayDeque<>();
        pending.add(paid);
        Set<Card> manaTapSources = Collections.newSetFromMap(new IdentityHashMap<>());
        int sacrifices = 0;
        int filtersUsingSacrificeMana = 0;
        while (!pending.isEmpty()) {
            SpellAbility ability = pending.removeFirst();
            if (!visited.add(ability)) continue;
            if (ability != paid && ability.getPayCosts().hasTapCost()) manaTapSources.add(ability.getHostCard());
            for (Card sacrificed : ability.getPaidList("Sacrificed")) {
                sacrifices++;
                assertFalse(sacrificed.isTapped(),
                        "A starting-untapped artifact cannot pay a waterbend tap and nested filter mana sacrifice: "
                                + sacrificed.getName() + "; root mana=" + paid.getPayingMana().size()
                                + "; remaining taps=" + tappedArtifacts(game));
            }
            for (Mana mana : ability.getPayingMana()) {
                if (ability.getHostCard().getName().equals(filterName)
                        && mana.getSourceCard().getName().equals("Krark-Clan Ironworks"))
                    filtersUsingSacrificeMana++;
                if (mana.getManaAbility() != null && mana.getManaAbility().getSourceSA() != null)
                    pending.add(mana.getManaAbility().getSourceSA());
            }
        }
        assertTrue(filtersUsingSacrificeMana > 0, "Actually fund the selected filter with native KCI mana");
        assertTrue(sacrifices >= (floatGeneric || !filterName.equals("Prismite") ? 1 : 2), "Inspect the nested native sacrifice receipts needed for the actual initial pool");
        long waterTaps = tappedArtifacts(game) - manaTapSources.stream()
                .filter(card -> card.isArtifact() && card.isTapped() && card.isInPlay()).count();
        assertTrue(waterTaps <= 6L);
        assertEquals(paid.getPayingMana().size() + waterTaps, (waterbend ? 9L : 3L),
                "Normal mana and independent waterbend taps cover the actual total cost");
        settle(game);
        assertEquals(actor(game).isUnlimitedHandSize(), waterbend);
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), waterbend ? 7 : 2);
        assertEquals(actor(game).getCardsIn(ZoneType.Library).size(), waterbend ? 18 : 22);
        assertEquals(actor(game).getCardsIn(ZoneType.Graveyard).size(), waterbend ? 0 : 1);
        named(game, "Spirit Water Revival", ZoneType.Exile);
        if (depth > 0) {
            assertEquals(tappedArtifacts(original), 0L);
            assertEquals(actor(original).getCardsIn(ZoneType.Hand).size(), 1);
            named(original, filterName, ZoneType.Battlefield);
            named(original, "Krark-Clan Ironworks", ZoneType.Battlefield);
        }
    }

    @Test public void nestedSacrificeFilterDepth0() { nestedSacrificeFilter(0); }
    @Test public void nestedSacrificeFilterDepth1() { nestedSacrificeFilter(1); }
    @Test public void nestedSacrificeFilterDepth2() { nestedSacrificeFilter(2); }

    @Test public void floatingGenericFilterDepth0() { nestedSacrificeFilter(0, true); }
    @Test public void floatingGenericFilterDepth1() { nestedSacrificeFilter(1, true); }
    @Test public void floatingGenericFilterDepth2() { nestedSacrificeFilter(2, true); }

    @Test public void basicFilterDepth0() { nestedSacrificeFilter(0, true, false); }
    @Test public void basicFilterDepth1() { nestedSacrificeFilter(1, true, false); }
    @Test public void basicFilterDepth2() { nestedSacrificeFilter(2, true, false); }

    @Test public void signetFilterDepth0() { nestedSacrificeFilter(0, false, true, "Azorius Signet"); }
    @Test public void signetFilterDepth1() { nestedSacrificeFilter(1, false, true, "Azorius Signet"); }
    @Test public void signetFilterDepth2() { nestedSacrificeFilter(2, false, true, "Azorius Signet"); }
}
