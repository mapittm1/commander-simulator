package forge.ai;

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
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Shared supplied pod setup and native paid morph; contains no test methods. */
public class InspectionTestSupport extends AITest {
    protected boolean known(Player observer, Card card) {
        return AiCardMemory.isRememberedCard(observer, card, AiCardMemory.MemorySet.REVEALED_CARDS);
    }

    protected void assertPublic(Card card, String name) {
        assertTrue(card.isFaceDown());
        assertEquals(card.getName(), name);
        assertEquals(card.getNetPower(), 2);
        assertEquals(card.getNetToughness(), 2);
    }

    protected Pod pod(String name, int mana) {
        return pod(name, mana, "Willbender");
    }

    protected Pod pod(String name, int mana, String secret) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("inspection-admission-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Inspection admission research"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player owner = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        for (int index = 0; index < mana; index++) addCardToZone(mana == 3 ? "Plains" : "Island", actor, ZoneType.Battlefield);
        for (int index = 0; index < 3; index++) addCardToZone("Island", owner, ZoneType.Battlefield);
        Card host = addCardToZone(name, actor, ZoneType.Battlefield);
        host.setSickness(false);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, owner, 2);
        Card source = addCardToZone(secret, owner, ZoneType.Hand);
        SpellAbility morph = source.getAllPossibleAbilities(owner, false).stream()
                .filter(ability -> ability.isSpell() && ability.isCastFaceDown()).findFirst().orElseThrow();
        morph.setActivatingPlayer(owner);
        assertTrue(morph.canPlay());
        assertTrue(ComputerUtil.playStack(morph, owner, game));
        assertEquals(morph.getPayingMana().size(), 3);
        settle(game);
        Card hidden = game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getId() == source.getId()).findFirst().orElseThrow();
        assertTrue(hidden.isFaceDown());
        assertFalse(known(actor, hidden));
        assertTrue(hidden.getView().canBeShownTo(actor.getView()), "The public face-down object is visible");
        assertFalse(hidden.getView().canFaceDownBeShownTo(actor.getView()), "Its concealed face is not visible");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        SpellAbility action = host.getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.LookAt).findFirst().orElseThrow();
        action.setActivatingPlayer(actor);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return new Pod(game, actor, host, hidden, action, hidden.getName());
    }

    protected void settle(Game game) {
        for (int step = 0; step < 40; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Inspection admission research stack did not settle");
    }

    protected static final class Pod {
        final Game game;
        final Player actor;
        final Card host;
        final Card hidden;
        final SpellAbility action;
        final String publicName;

        Pod(Game game, Player actor, Card host, Card hidden, SpellAbility action, String publicName) {
            this.game = game;
            this.actor = actor;
            this.host = host;
            this.hidden = hidden;
            this.action = action;
            this.publicName = publicName;
        }
    }
}
