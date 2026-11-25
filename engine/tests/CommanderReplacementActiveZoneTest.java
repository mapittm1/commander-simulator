package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementHandler;
import forge.game.replacement.ReplacementLayer;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.Spell;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Explicit native active zones must not change meaning under performance mode. */
public class CommanderReplacementActiveZoneTest extends AITest {
    private Player actor(Game game) {
        return game.getRegisteredPlayers().stream().filter(p -> p.getName().equals("zone-0")).findFirst().orElseThrow();
    }
    private Card land(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Forest")).findFirst().orElseThrow();
    }
    private Game board(ZoneType zone, ReplacementType event) { return board(zone, zone, event); }
    private Game board(ZoneType zone, ZoneType active, ReplacementType event) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("zone-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Explicit replacement active zones"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int i = 0; i < 12; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        Card source = addCardToZone("Sol Ring", actor(game), zone);
        ReplacementEffect effect = ReplacementHandler.parseReplacement("Event$ " + event + " | ActiveZones$ " + active
                + " | ValidCard$ Card.YouCtrl | Layer$ CantHappen", source, true);
        source.addReplacementEffect(effect);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        game.getAction().checkStaticAbilities(false); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        return game;
    }
    private void blocked(Game game, ReplacementType event) {
        Card target = land(game);
        SpellAbility mana = target.getManaAbilities().get(0); mana.setActivatingPlayer(actor(game));
        Map<AbilityKey, Object> params = AbilityKey.mapFromAffected(target);
        params.put(AbilityKey.Player, actor(game)); params.put(AbilityKey.Activator, actor(game));
        params.put(AbilityKey.AbilityMana, mana); params.put(AbilityKey.Mana, "G"); params.put(AbilityKey.IsCombat, false);
        assertEquals(game.getReplacementHandler().getReplacementList(event, params, ReplacementLayer.CantHappen).size(), 1,
                "Explicit active zone must remain eligible; performance=" + Spell.isPerformanceMode() + "; event=" + event);
        if (event == ReplacementType.Tap) assertFalse(target.canTap());
        if (event == ReplacementType.Untap) { target.setTapped(true); assertFalse(target.canUntap(actor(game), false)); target.setTapped(false); }
        assertEquals(actor(game).getManaPool().totalMana(), 0); assertEquals(actor(game).getLife(), 100);
    }
    private void check(ZoneType zone, ReplacementType event, int depth) {
        boolean previous = Spell.isPerformanceMode();
        try {
            Spell.setPerformanceMode(false);
            Game original = board(zone, event); blocked(original, event);
            Game game = original;
            for (int i = 0; i < depth; i++) {
                GameCopier copier = new GameCopier(game).withStack(true); game = copier.makeCopy(null, actor(game)); copier.release();
            }
            blocked(game, event);
            Spell.setPerformanceMode(true); blocked(game, event);
        } finally { Spell.setPerformanceMode(previous); }
    }
    private void inactive(ZoneType zone, ZoneType active, ReplacementType event, int depth) {
        boolean previous = Spell.isPerformanceMode();
        try {
            Spell.setPerformanceMode(false);
            Game game = board(zone, active, event);
            for (int i = 0; i < depth; i++) {
                GameCopier copier = new GameCopier(game).withStack(true); game = copier.makeCopy(null, actor(game)); copier.release();
            }
            for (boolean performance : List.of(false, true)) {
                Spell.setPerformanceMode(performance);
                Card target = land(game);
                SpellAbility mana = target.getManaAbilities().get(0); mana.setActivatingPlayer(actor(game));
                Map<AbilityKey, Object> params = AbilityKey.mapFromAffected(target);
                params.put(AbilityKey.Player, actor(game)); params.put(AbilityKey.Activator, actor(game));
                params.put(AbilityKey.AbilityMana, mana); params.put(AbilityKey.Mana, "G"); params.put(AbilityKey.IsCombat, false);
                assertTrue(game.getReplacementHandler().getReplacementList(event, params, ReplacementLayer.CantHappen).isEmpty(),
                        "An inactive source cannot replace this event");
                if (event == ReplacementType.Tap) assertTrue(target.canTap());
                if (event == ReplacementType.Untap) { target.setTapped(true); assertTrue(target.canUntap(actor(game), false)); target.setTapped(false); }
                assertEquals(actor(game).getManaPool().totalMana(), 0);
            }
        } finally { Spell.setPerformanceMode(previous); }
    }
    @Test public void battlefieldTapDepth0() { check(ZoneType.Battlefield, ReplacementType.Tap, 0); }
    @Test public void battlefieldTapDepth1() { check(ZoneType.Battlefield, ReplacementType.Tap, 1); }
    @Test public void battlefieldTapDepth2() { check(ZoneType.Battlefield, ReplacementType.Tap, 2); }
    @Test public void battlefieldUntapDepth0() { check(ZoneType.Battlefield, ReplacementType.Untap, 0); }
    @Test public void battlefieldUntapDepth1() { check(ZoneType.Battlefield, ReplacementType.Untap, 1); }
    @Test public void battlefieldUntapDepth2() { check(ZoneType.Battlefield, ReplacementType.Untap, 2); }
    @Test public void battlefieldProduceManaDepth0() { check(ZoneType.Battlefield, ReplacementType.ProduceMana, 0); }
    @Test public void battlefieldProduceManaDepth1() { check(ZoneType.Battlefield, ReplacementType.ProduceMana, 1); }
    @Test public void battlefieldProduceManaDepth2() { check(ZoneType.Battlefield, ReplacementType.ProduceMana, 2); }
    @Test public void commandTapDepth0() { check(ZoneType.Command, ReplacementType.Tap, 0); }
    @Test public void commandTapDepth1() { check(ZoneType.Command, ReplacementType.Tap, 1); }
    @Test public void commandTapDepth2() { check(ZoneType.Command, ReplacementType.Tap, 2); }
    @Test public void commandUntapDepth0() { check(ZoneType.Command, ReplacementType.Untap, 0); }
    @Test public void commandUntapDepth1() { check(ZoneType.Command, ReplacementType.Untap, 1); }
    @Test public void commandUntapDepth2() { check(ZoneType.Command, ReplacementType.Untap, 2); }
    @Test public void commandProduceManaDepth0() { check(ZoneType.Command, ReplacementType.ProduceMana, 0); }
    @Test public void commandProduceManaDepth1() { check(ZoneType.Command, ReplacementType.ProduceMana, 1); }
    @Test public void commandProduceManaDepth2() { check(ZoneType.Command, ReplacementType.ProduceMana, 2); }
    @Test public void graveyardTapDepth0() { check(ZoneType.Graveyard, ReplacementType.Tap, 0); }
    @Test public void graveyardTapDepth1() { check(ZoneType.Graveyard, ReplacementType.Tap, 1); }
    @Test public void graveyardTapDepth2() { check(ZoneType.Graveyard, ReplacementType.Tap, 2); }
    @Test public void graveyardUntapDepth0() { check(ZoneType.Graveyard, ReplacementType.Untap, 0); }
    @Test public void graveyardUntapDepth1() { check(ZoneType.Graveyard, ReplacementType.Untap, 1); }
    @Test public void graveyardUntapDepth2() { check(ZoneType.Graveyard, ReplacementType.Untap, 2); }
    @Test public void graveyardProduceManaDepth0() { check(ZoneType.Graveyard, ReplacementType.ProduceMana, 0); }
    @Test public void graveyardProduceManaDepth1() { check(ZoneType.Graveyard, ReplacementType.ProduceMana, 1); }
    @Test public void graveyardProduceManaDepth2() { check(ZoneType.Graveyard, ReplacementType.ProduceMana, 2); }
    @Test public void handTapDepth0() { check(ZoneType.Hand, ReplacementType.Tap, 0); }
    @Test public void handTapDepth1() { check(ZoneType.Hand, ReplacementType.Tap, 1); }
    @Test public void handTapDepth2() { check(ZoneType.Hand, ReplacementType.Tap, 2); }
    @Test public void handUntapDepth0() { check(ZoneType.Hand, ReplacementType.Untap, 0); }
    @Test public void handUntapDepth1() { check(ZoneType.Hand, ReplacementType.Untap, 1); }
    @Test public void handUntapDepth2() { check(ZoneType.Hand, ReplacementType.Untap, 2); }
    @Test public void handProduceManaDepth0() { check(ZoneType.Hand, ReplacementType.ProduceMana, 0); }
    @Test public void handProduceManaDepth1() { check(ZoneType.Hand, ReplacementType.ProduceMana, 1); }
    @Test public void handProduceManaDepth2() { check(ZoneType.Hand, ReplacementType.ProduceMana, 2); }
    @Test public void libraryTapDepth0() { check(ZoneType.Library, ReplacementType.Tap, 0); }
    @Test public void libraryTapDepth1() { check(ZoneType.Library, ReplacementType.Tap, 1); }
    @Test public void libraryTapDepth2() { check(ZoneType.Library, ReplacementType.Tap, 2); }
    @Test public void libraryUntapDepth0() { check(ZoneType.Library, ReplacementType.Untap, 0); }
    @Test public void libraryUntapDepth1() { check(ZoneType.Library, ReplacementType.Untap, 1); }
    @Test public void libraryUntapDepth2() { check(ZoneType.Library, ReplacementType.Untap, 2); }
    @Test public void libraryProduceManaDepth0() { check(ZoneType.Library, ReplacementType.ProduceMana, 0); }
    @Test public void libraryProduceManaDepth1() { check(ZoneType.Library, ReplacementType.ProduceMana, 1); }
    @Test public void libraryProduceManaDepth2() { check(ZoneType.Library, ReplacementType.ProduceMana, 2); }
    @Test public void exileTapDepth0() { check(ZoneType.Exile, ReplacementType.Tap, 0); }
    @Test public void exileTapDepth1() { check(ZoneType.Exile, ReplacementType.Tap, 1); }
    @Test public void exileTapDepth2() { check(ZoneType.Exile, ReplacementType.Tap, 2); }
    @Test public void exileUntapDepth0() { check(ZoneType.Exile, ReplacementType.Untap, 0); }
    @Test public void exileUntapDepth1() { check(ZoneType.Exile, ReplacementType.Untap, 1); }
    @Test public void exileUntapDepth2() { check(ZoneType.Exile, ReplacementType.Untap, 2); }
    @Test public void exileProduceManaDepth0() { check(ZoneType.Exile, ReplacementType.ProduceMana, 0); }
    @Test public void exileProduceManaDepth1() { check(ZoneType.Exile, ReplacementType.ProduceMana, 1); }
    @Test public void exileProduceManaDepth2() { check(ZoneType.Exile, ReplacementType.ProduceMana, 2); }
    @Test public void graveyardInactiveTapDepth0() { inactive(ZoneType.Graveyard, ZoneType.Battlefield, ReplacementType.Tap, 0); }
    @Test public void graveyardInactiveTapDepth1() { inactive(ZoneType.Graveyard, ZoneType.Battlefield, ReplacementType.Tap, 1); }
    @Test public void graveyardInactiveTapDepth2() { inactive(ZoneType.Graveyard, ZoneType.Battlefield, ReplacementType.Tap, 2); }
    @Test public void graveyardInactiveUntapDepth0() { inactive(ZoneType.Graveyard, ZoneType.Battlefield, ReplacementType.Untap, 0); }
    @Test public void graveyardInactiveUntapDepth1() { inactive(ZoneType.Graveyard, ZoneType.Battlefield, ReplacementType.Untap, 1); }
    @Test public void graveyardInactiveUntapDepth2() { inactive(ZoneType.Graveyard, ZoneType.Battlefield, ReplacementType.Untap, 2); }
    @Test public void graveyardInactiveProduceManaDepth0() { inactive(ZoneType.Graveyard, ZoneType.Battlefield, ReplacementType.ProduceMana, 0); }
    @Test public void graveyardInactiveProduceManaDepth1() { inactive(ZoneType.Graveyard, ZoneType.Battlefield, ReplacementType.ProduceMana, 1); }
    @Test public void graveyardInactiveProduceManaDepth2() { inactive(ZoneType.Graveyard, ZoneType.Battlefield, ReplacementType.ProduceMana, 2); }
    @Test public void battlefieldInactiveTapDepth0() { inactive(ZoneType.Battlefield, ZoneType.Graveyard, ReplacementType.Tap, 0); }
    @Test public void battlefieldInactiveTapDepth1() { inactive(ZoneType.Battlefield, ZoneType.Graveyard, ReplacementType.Tap, 1); }
    @Test public void battlefieldInactiveTapDepth2() { inactive(ZoneType.Battlefield, ZoneType.Graveyard, ReplacementType.Tap, 2); }
    @Test public void battlefieldInactiveUntapDepth0() { inactive(ZoneType.Battlefield, ZoneType.Graveyard, ReplacementType.Untap, 0); }
    @Test public void battlefieldInactiveUntapDepth1() { inactive(ZoneType.Battlefield, ZoneType.Graveyard, ReplacementType.Untap, 1); }
    @Test public void battlefieldInactiveUntapDepth2() { inactive(ZoneType.Battlefield, ZoneType.Graveyard, ReplacementType.Untap, 2); }
    @Test public void battlefieldInactiveProduceManaDepth0() { inactive(ZoneType.Battlefield, ZoneType.Graveyard, ReplacementType.ProduceMana, 0); }
    @Test public void battlefieldInactiveProduceManaDepth1() { inactive(ZoneType.Battlefield, ZoneType.Graveyard, ReplacementType.ProduceMana, 1); }
    @Test public void battlefieldInactiveProduceManaDepth2() { inactive(ZoneType.Battlefield, ZoneType.Graveyard, ReplacementType.ProduceMana, 2); }
}
