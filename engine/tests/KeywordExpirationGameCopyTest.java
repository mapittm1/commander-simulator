package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Paid keyword removal and combat power exchange must expire in their own worlds. */
public class KeywordExpirationGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("expiry" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Keyword expiry"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 5; i++)
            addCardToZone("Forest", player, ZoneType.Library);
        return game;
    }
    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Fixture stack did not settle within its bound");
    }
    private Card find(Game game, int id) {
        return game.getCardsInGame().stream().filter(c -> c.getId() == id).findFirst().orElseThrow();
    }
    private void pay(Game game, SpellAbility ability, Player player, int mana) {
        ability.setActivatingPlayer(player);
        assertTrue(ability.canPlay(), "Action admission");
        assertTrue(ability.checkRestrictions(player), "Native restrictions");
        assertTrue(ComputerUtil.playStack(ability, player, game), "Native payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getStack().resolveStack();
        settle(game);
    }
    private Card cast(Game game, String name, Player player, Card target, int mana) {
        Card card = addCardToZone(name, player, ZoneType.Hand);
        SpellAbility ability = card.getFirstSpellAbility();
        if (target != null) { ability.getTargets().add(target); assertTrue(target.canBeTargetedBy(ability)); }
        pay(game, ability, player, mana);
        return find(game, card.getId());
    }
    private void activate(Game game, Card card, Player player, Card target, int mana, ApiType api) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(player);
        if (target != null) { ability.getTargets().add(target); assertTrue(target.canBeTargetedBy(ability)); }
        pay(game, ability, player, mana);
    }
    private void nextTurn(Game game) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
    }
    private void removal(String name, boolean withStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        for (int i = 0; i < 8; i++) addCardToZone("Forest", owner, ZoneType.Battlefield);
        for (int i = 0; i < 6; i++) addCardToZone("Mountain", owner, ZoneType.Battlefield);
        for (int i = 0; i < 5; i++) addCardToZone("Plains", owner, ZoneType.Battlefield);
        for (int i = 0; i < 3; i++) addCardToZone("Island", owner, ZoneType.Battlefield);
        Card target;
        String keyword;
        String retained = null;
        if (name.equals("claws")) {
            target = addCardToZone("Serra Angel", owner, ZoneType.Battlefield);
            keyword = "Flying";
            assertTrue(target.hasKeyword(keyword));
            cast(game, "Canopy Claws", owner, target, 1);
        } else if (name.equals("landwalk")) {
            target = addCardToZone("River Boa", owner, ZoneType.Battlefield);
            keyword = "Landwalk:Island";
            assertTrue(target.hasKeyword(keyword));
            Card hammerheim = addCardToZone("Hammerheim", owner, ZoneType.Battlefield);
            activate(game, hammerheim, owner, target, 0, ApiType.Debuff);
            assertTrue(hammerheim.isTapped());
        } else if (name.equals("dragon")) {
            target = cast(game, "Canopy Dragon", owner, null, 6);
            keyword = "Trample";
            assertTrue(target.hasKeyword(keyword));
            activate(game, target, owner, null, 2, ApiType.Pump);
            retained = "Flying";
        } else if (name.equals("minotaur")) {
            target = cast(game, "Zerapa Minotaur", owner, null, 4);
            keyword = "First Strike";
            assertTrue(target.hasKeyword(keyword));
            Player opponent = game.getPlayers().get(1);
            for (int i = 0; i < 2; i++) addCardToZone("Island", opponent, ZoneType.Battlefield);
            activate(game, target, opponent, null, 2, ApiType.Debuff);
        } else {
            String card = name.equals("snitch") ? "White Knight" : name.equals("angel") ? "Iridescent Angel" : "Grizzly Bears";
            target = addCardToZone(card, owner, ZoneType.Battlefield);
            keyword = name.equals("snitch") ? "Protection from black" : name.equals("angel") ? "Protection from each color" : "Protection:Card.nonColorless:each color:Aura";
            if (name.equals("ward")) cast(game, "Spectra Ward", owner, target, 5);
            assertTrue(target.hasKeyword(keyword));
            Card snitch = cast(game, "Cephalid Snitch", owner, null, 2);
            // A blue Snitch cannot legally target a creature protected from blue.
            if (!name.equals("snitch")) cast(game, "Moonlace", owner, snitch, 1);
            activate(game, snitch, owner, target, 0, ApiType.Debuff);
            assertEquals(snitch.getZone().getZoneType(), ZoneType.Graveyard, "Sacrifice cost paid");
            if (!name.equals("snitch")) retained = name.equals("angel") ? "Protection from red" : "Protection:Card.Red:red:Aura";
        }
        assertFalse(target.hasKeyword(keyword), "Selected keyword removed before copy: " + keyword);
        if (retained != null) assertTrue(target.hasKeyword(retained), "Other active keyword remains");
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copy = copier.makeCopy();
        Card mapped = find(copy, target.getId());
        assertFalse(mapped.hasKeyword(keyword), "Removed layer copied");
        nextTurn(copy);
        assertTrue(mapped.hasKeyword(keyword), "Copied removed keyword restored: " + keyword);
        if (name.equals("dragon")) assertFalse(mapped.hasKeyword("Flying"), "Pump grant also expires");
        assertFalse(target.hasKeyword(keyword), "Source layer remains active");
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), "Selected commands mapped");
        nextTurn(game);
        assertTrue(target.hasKeyword(keyword), "Source expires independently");
    }
    private void exchange(boolean withStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player attacker = game.getPlayers().get(1);
        for (int i = 0; i < 2; i++) addCardToZone("Plains", owner, ZoneType.Battlefield);
        Card master = cast(game, "Serene Master", owner, null, 2);
        Card beast = addCardToZone("Colossal Dreadmaw", attacker, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, attacker, 2);
        game.getPhaseHandler().setCombat(new Combat(attacker));
        game.getCombat().addAttacker(beast, owner);
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.COMBAT_DECLARE_BLOCKERS, () -> settle(game)));
        settle(game);
        assertTrue(game.getCombat().isBlocking(master, beast), "Native AI declared the blocking Master");
        assertEquals(master.getNetPower(), 6, "Native block trigger exchanged power");
        assertEquals(beast.getNetPower(), 0);
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copy = copier.makeCopy();
        Card mapped = find(copy, master.getId());
        assertEquals(mapped.getNetPower(), 6);
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(copy)));
        settle(copy);
        assertEquals(mapped.getNetPower(), 0, "Copied power exchange expires at end of combat");
        assertEquals(master.getNetPower(), 6, "Source combat remains active");
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game)));
        settle(game);
        assertEquals(master.getNetPower(), 0, "Source expires independently");
    }
    @Test public void clawsWithStack() { removal("claws", true); }
    @Test public void clawsWithoutStack() { removal("claws", false); }
    @Test public void landwalkWithStack() { removal("landwalk", true); }
    @Test public void landwalkWithoutStack() { removal("landwalk", false); }
    @Test public void dragonWithStack() { removal("dragon", true); }
    @Test public void dragonWithoutStack() { removal("dragon", false); }
    @Test public void minotaurWithStack() { removal("minotaur", true); }
    @Test public void minotaurWithoutStack() { removal("minotaur", false); }
    @Test public void snitchWithStack() { removal("snitch", true); }
    @Test public void snitchWithoutStack() { removal("snitch", false); }
    @Test public void angelWithStack() { removal("angel", true); }
    @Test public void angelWithoutStack() { removal("angel", false); }
    @Test public void wardWithStack() { removal("ward", true); }
    @Test public void wardWithoutStack() { removal("ward", false); }
    @Test public void exchangeWithStack() { exchange(true); }
    @Test public void exchangeWithoutStack() { exchange(false); }
}
