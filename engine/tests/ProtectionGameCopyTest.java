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
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/** Temporary protection expires independently in each world. */
public class ProtectionGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("protection" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Protection fixture"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 4; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private void pay(Game game, SpellAbility ability, Player owner, int mana) {
        ability.setActivatingPlayer(owner);
        assertTrue(ability.canPlay(), "Native action admission");
        assertTrue(ComputerUtil.playStack(ability, owner, game), "Native action payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getAction().checkStateEffects(true);
    }

    private Card cast(Game game, String name, Player owner, int mana) {
        Card card = addCardToZone(name, owner, ZoneType.Hand);
        pay(game, card.getFirstSpellAbility(), owner, mana);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        return find(game, card.getId());
    }

    private Card find(Game game, int identity) {
        return game.getCardsInGame().stream().filter(card -> card.getId() == identity)
                .findFirst().orElseThrow();
    }

    private SpellAbility activated(Card card, ApiType api, Player owner) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        return ability;
    }

    private Game snapshot(Game game) {
        GameCopier copier = new GameCopier(game).withStack(true);
        Game copy = copier.makeCopy();
        return copy;
    }

    private void triggers(Game game) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }



    /** Run native phase hooks, not individual counter-reset methods. No priority is offered. */
    private void nextTurn(Game game) {
        int previousTurn = game.getPhaseHandler().getTurn();
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(game)), "Native turn advance");
        settle(game);
        assertEquals(game.getPhaseHandler().getTurn(), previousTurn + 1);
        assertEquals(game.getPhaseHandler().getPhase(), PhaseType.MAIN1);
    }

    private void settle(Game game) {
        triggers(game);
        int guard = 0;
        while (!game.getStack().isEmpty()) {
            assertTrue(++guard < 40, "Bounded fixture trigger resolution");
            game.getStack().resolveStack();
            triggers(game);
        }
    }

    private void round(Game game, int ownerId) {
        for (int seat = 0; seat < 4; seat++) {
            nextTurn(game);
        }
        assertEquals(game.getPhaseHandler().getPlayerTurn().getId(), ownerId);
    }



    private void advance(Game game, PhaseType phase) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(phase, () -> settle(game)));
        settle(game);
    }

    private Player prepare(Game game, String land, int count) {
        Player owner = game.getPlayers().get(0);
        for (int i = 0; i < count; i++) addCardToZone(land, owner, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, owner, 1);
        return owner;
    }

    private void targeted(Game game, String name, Player owner, Card target, int mana) {
        Card spell = addCardToZone(name, owner, ZoneType.Hand);
        spell.getFirstSpellAbility().getTargets().add(target);
        pay(game, spell.getFirstSpellAbility(), owner, mana);
        game.getStack().resolveStack();
        settle(game);
    }

    private void protection(String name, boolean withStack) {
        Game game = pod();
        Player owner = prepare(game, "Plains", 8);
        Card bear = addCardToZone("Silvercoat Lion", owner, ZoneType.Battlefield);
        Card knight = addCardToZone("White Knight", owner, ZoneType.Battlefield);
        boolean playerEffect = name.equals("Seht's Tiger");
        if (name.equals("Shelter")) targeted(game, name, owner, bear, 2);
        else { cast(game, name, owner, playerEffect ? 4 : 1); settle(game); }
        assertTrue(playerEffect ? owner.hasKeyword(forge.game.keyword.Keyword.PROTECTION)
                : bear.hasKeyword(forge.game.keyword.Keyword.PROTECTION), "Protection granted before copy");
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copy = copier.makeCopy();
        Card copiedBear = find(copy, bear.getId());
        Card copiedKnight = find(copy, knight.getId());
        Player copiedOwner = copy.getPlayers().get(0);
        assertTrue(playerEffect ? copiedOwner.hasKeyword(forge.game.keyword.Keyword.PROTECTION)
                : copiedBear.hasKeyword(forge.game.keyword.Keyword.PROTECTION), "Active protection copied");
        nextTurn(copy);
        assertFalse(playerEffect ? copiedOwner.hasKeyword(forge.game.keyword.Keyword.PROTECTION)
                : copiedBear.hasKeyword(forge.game.keyword.Keyword.PROTECTION), "Copied protection expires");
        assertTrue(copiedKnight.hasKeyword("Protection from black"), "Intrinsic protection survives");
        assertTrue(playerEffect ? owner.hasKeyword(forge.game.keyword.Keyword.PROTECTION)
                : bear.hasKeyword(forge.game.keyword.Keyword.PROTECTION), "Source duration remains active");
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), "All selected cleanup commands mapped");
        nextTurn(game);
        assertFalse(playerEffect ? owner.hasKeyword(forge.game.keyword.Keyword.PROTECTION)
                : bear.hasKeyword(forge.game.keyword.Keyword.PROTECTION), "Source protection expires independently");
    }
    @Test public void shelterWithStack() { protection("Shelter", true); }
    @Test public void shelterWithoutStack() { protection("Shelter", false); }
    @Test public void braveWithStack() { protection("Brave the Elements", true); }
    @Test public void braveWithoutStack() { protection("Brave the Elements", false); }
    @Test public void tigerWithStack() { protection("Seht's Tiger", true); }
    @Test public void tigerWithoutStack() { protection("Seht's Tiger", false); }
}
