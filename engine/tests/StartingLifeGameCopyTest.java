package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/** Starting life is a rules baseline, independent of current life. */
public class StartingLifeGameCopyTest extends AITest {
    private Game pod(int startingLife) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            RegisteredPlayer registered = RegisteredPlayer.forCommander(new Deck());
            registered.setStartingLife(startingLife);
            registered.setPlayer(new LobbyPlayerAi("seat" + seat, null));
            players.add(registered);
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Starting-life fixture"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        for (Player player : game.getPlayers()) {
            addCardToZone("Forest", player, ZoneType.Library);
        }
        return game;
    }

    private Card copiedCard(Game game, Card original, ZoneType zone) {
        return game.getCardsIn(zone).stream()
                .filter(card -> card.getId() == original.getId()).findFirst().orElseThrow();
    }

    @DataProvider(name = "baselines")
    public Object[][] baselines() {
        return new Object[][] {{20}, {25}, {30}, {40}, {50}};
    }

    @Test(dataProvider = "baselines")
    public void startingBaselineAndCurrentLifeRemainSeparate(int baseline) {
        Game game = pod(baseline);
        Player owner = game.getPlayers().get(0);
        owner.setLife(baseline - 3, null);
        Game copy = new GameCopier(game).makeCopy();
        for (Player original : game.getRegisteredPlayers()) {
            Player mapped = copy.getPlayer(original.getId());
            assertEquals(mapped.getStartingLife(), baseline);
            assertEquals(mapped.getRegisteredPlayer().getStartingLife(), baseline);
            assertEquals(mapped.getLife(), original.getLife());
        }
        copy.getPlayer(owner.getId()).gainLife(2, null, null);
        assertEquals(owner.getLife(), baseline - 3);
        assertEquals(copy.getPlayer(owner.getId()).getStartingLife(), baseline);
    }

    @DataProvider(name = "thresholds")
    public Object[][] thresholds() {
        return new Object[][] {
            {20, 26, false}, {20, 27, true},
            {40, 27, false}, {40, 40, false}, {40, 46, false},
            {40, 47, true}, {40, 48, true}
        };
    }

    @Test(dataProvider = "thresholds")
    public void righteousValkyrieUsesOriginalStartingLife(int baseline, int life, boolean buffed) {
        Game game = pod(baseline);
        Player owner = game.getPlayers().get(0);
        owner.setLife(life, null);
        Card valkyrie = addCard("Righteous Valkyrie", owner);
        Card bear = addCard("Grizzly Bears", owner);
        game.getAction().checkStateEffects(true);
        assertEquals(valkyrie.getNetPower(), buffed ? 4 : 2);
        assertEquals(bear.getNetPower(), buffed ? 4 : 2);
        Game copy = new GameCopier(game).makeCopy();
        assertEquals(copiedCard(copy, valkyrie, ZoneType.Battlefield).getNetPower(), buffed ? 4 : 2);
        assertEquals(copiedCard(copy, bear, ZoneType.Battlefield).getNetToughness(), buffed ? 4 : 2);
        assertEquals(copy.getPlayer(owner.getId()).getStartingLife(), baseline);
    }

    @DataProvider(name = "paidGains")
    public Object[][] paidGains() {
        return new Object[][] {{"Angel's Mercy", 4, 7, true}, {"Chaplain's Blessing", 1, 5, false}};
    }

    @Test(dataProvider = "paidGains")
    public void paidLifeGainCrossesOnlyTheCorrectThreshold(String spell, int mana, int gain, boolean buffed) {
        Game game = pod(40);
        Player owner = game.getPlayers().get(0);
        Card valkyrie = addCard("Righteous Valkyrie", owner);
        Card bear = addCard("Grizzly Bears", owner);
        addCards("Plains", mana, owner);
        Card originalSpell = addCardToZone(spell, owner, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        Game copy = new GameCopier(game).makeCopy();
        Player mapped = copy.getPlayer(owner.getId());
        assertEquals(copiedCard(copy, valkyrie, ZoneType.Battlefield).getNetPower(), 2);
        SpellAbility ability = copiedCard(copy, originalSpell, ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(mapped);
        assertTrue(ability.canPlay());
        assertTrue(ComputerUtil.playStack(ability, mapped, copy));
        assertEquals(ability.getPayingMana().size(), mana);
        copy.getStack().resolveStack();
        copy.getAction().checkStateEffects(true);
        assertEquals(mapped.getLife(), 40 + gain);
        assertEquals(mapped.getStartingLife(), 40);
        assertEquals(copiedCard(copy, valkyrie, ZoneType.Battlefield).getNetPower(), buffed ? 4 : 2);
        assertEquals(copiedCard(copy, bear, ZoneType.Battlefield).getNetPower(), buffed ? 4 : 2);
        assertEquals(copiedCard(copy, originalSpell, ZoneType.Graveyard).getName(), spell);
        assertEquals(owner.getLife(), 40);
        assertEquals(bear.getNetPower(), 2);
    }

    @Test
    public void eliminatedPlayersKeepStartingLifeAcrossRepeatedCopies() {
        Game game = pod(40);
        Player lost = game.getPlayers().get(1);
        lost.setLife(0, null);
        game.getAction().checkStateEffects(true);
        assertTrue(lost.hasLost());
        Game copy = new GameCopier(game).makeCopy();
        Game again = new GameCopier(copy).makeCopy();
        Player mapped = again.getPlayer(lost.getId());
        assertEquals(mapped.getStartingLife(), 40);
        assertEquals(mapped.getRegisteredPlayer().getStartingLife(), 40);
        assertEquals(mapped.getLife(), 0);
        assertTrue(mapped.hasLost());
        assertEquals(again.getPlayers().size(), 3);
    }

    @Test
    public void liveBaselineOverrideDoesNotRewriteRegistrationOrCurrentLife() {
        Game game = pod(40);
        Player owner = game.getPlayers().get(0);
        owner.setStartingLife(30);
        owner.setLife(35, null);
        Game copy = new GameCopier(game).makeCopy();
        Player mapped = copy.getPlayer(owner.getId());
        assertEquals(mapped.getStartingLife(), 30);
        assertEquals(mapped.getRegisteredPlayer().getStartingLife(), 40);
        assertEquals(mapped.getLife(), 35);
    }
}
