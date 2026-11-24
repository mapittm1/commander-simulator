package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.card.CardStateName;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Normal paid prepared entry, copied state/face and independent leaves-play cleanup. */
public class PreparedTestSupport extends AITest {
    protected Player actor(Game game) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("prepared-0")).findFirst().orElseThrow();
    }

    protected Card creature(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Infirmary Healer")).findFirst().orElseThrow();
    }

    protected void settle(Game game) {
        GameSimulator.resolveStack(game, actor(game));
    }

    protected Game prepared() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("prepared-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Prepared snapshot state"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (int index = 0; index < 6; index++) addCardToZone("Forest", player, ZoneType.Battlefield);
        }
        Player player = actor(game);
        Card healer = addCardToZone("Infirmary Healer", player, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player, 5);
        SpellAbility spell = healer.getFirstSpellAbility();
        spell.setActivatingPlayer(player);
        assertTrue(spell.canPlay() && spell.checkRestrictions(spell.getHostCard(), player));
        assertTrue(ComputerUtil.playStack(spell, player, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        settle(game);
        assertEquals(player.getLandsInPlay().stream().filter(Card::isTapped).count(), 2L);
        check(game);
        game.copyLastState();
        return game;
    }

    protected void check(Game game) {
        Card healer = creature(game);
        assertTrue(healer.isPrepared(), "Native printed replacement makes the creature prepared");
        Card effect = healer.getPrepared();
        Card spell = healer.getPreparedSpell();
        assertNotNull(effect);
        assertNotNull(spell);
        assertSame(effect.getGame(), game, "Prepared effect belongs to this world");
        assertSame(spell.getGame(), game, "Prepared spell belongs to this world");
        assertSame(effect.getOwner(), actor(game));
        assertSame(spell.getOwner(), actor(game));
        assertEquals(spell.getCurrentStateName(), CardStateName.PreparedSpell);
        assertEquals(spell.getName(), "Stream of Life");
        assertTrue(actor(game).getCardsIn(ZoneType.Command).stream().anyMatch(card -> card == effect));
        assertTrue(actor(game).getCardsIn(ZoneType.Exile).stream().anyMatch(card -> card == spell));
    }

    protected void leave(Game game) {
        Card healer = creature(game);
        Card effect = healer.getPrepared();
        Card spell = healer.getPreparedSpell();
        game.getAction().moveToHand(healer, null);
        settle(game);
        assertFalse(healer.isPrepared(), "Native leaves-play callback clears preparation");
        assertTrue(actor(game).getCardsIn(ZoneType.Command).stream().noneMatch(card -> card == effect));
        assertTrue(actor(game).getCardsIn(ZoneType.Exile).stream().noneMatch(card -> card == spell));
    }

    protected void copies(int depth, int leaves) {
        Game original = prepared();
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        check(copy);
        if (leaves == 1) {
            leave(copy);
            check(original);
        } else if (leaves == 2) {
            leave(original);
            check(copy);
        }
    }

}
