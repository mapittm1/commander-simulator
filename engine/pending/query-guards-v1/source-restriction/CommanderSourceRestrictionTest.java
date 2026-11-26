package forge.ai;

import static org.testng.Assert.*;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.card.CardStateName;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Live property outcomes retain identity, timestamps, LKI and phasing across copies. */
public class CommanderSourceRestrictionTest extends AITest {
    @DataProvider(name = "copyDepths")
    public Object[][] copyDepths() { return new Object[][] {{0}, {1}, {2}}; }

    private Game setup(int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("property-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Property controls"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            addCardToZone("Forest", player, ZoneType.Library);
            addCardToZone("Forest", player, ZoneType.Battlefield);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().getFirst(), 5);
        game.copyLastState();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, game.getPlayers().getFirst());
            copier.release();
        }
        game.clearChangeZoneLKIInfo();
        return game;
    }

    private boolean valid(Card card, String restriction, Player actor, Card source) {
        return card.isValid(restriction, actor, source, null);
    }

    @Test(dataProvider = "copyDepths")
    public void positiveIdentityAndShortCircuit(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        Card other = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).getFirst();
        assertTrue(valid(card, "Card.Self", actor, card));
        assertTrue(valid(card, "card.Self", actor, card));
        assertFalse(valid(card, "Card.Self", actor, other));
        assertFalse(valid(card, "Card.Self+IsTriggerRemembered", actor, other));
        expectThrows(NullPointerException.class,
                () -> valid(card, "Card.Self+IsTriggerRemembered", actor, card));
    }

    @Test(dataProvider = "copyDepths")
    public void disjunctionAndNegationRemainIndependent(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        Card other = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).getFirst();
        assertTrue(card.isValid(new String[] {"Card.Self", "Card.Other"}, actor, other, null));
        assertTrue(valid(card, "!Card.Self", actor, other));
        assertFalse(valid(card, "!Card.Self", actor, card));
        assertTrue(valid(card, "Card.!Self", actor, other));
        assertFalse(valid(card, "Card.!Self", actor, card));
        assertTrue(valid(card, "Card.Other+!Self", actor, other));
    }

    @Test(dataProvider = "copyDepths")
    public void liveControllerAndLkiAreNotCached(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Player opponent = game.getPlayers().get(1);
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        Card recorded = CardCopyService.getLKICopy(card);
        assertTrue(valid(card, "Card.Self+YouCtrl", actor, card));
        card.setController(opponent, game.getNextTimestamp());
        assertFalse(valid(card, "Card.Self+YouCtrl", actor, card));
        game.addChangeZoneLKIInfo(recorded);
        assertTrue(valid(card, "Card.Self+YouCtrl", actor, card));
        game.clearChangeZoneLKIInfo();
        assertFalse(valid(card, "Card.Self+YouCtrl", actor, card));
    }

    @Test(dataProvider = "copyDepths")
    public void timestampAndPhasingRemainLive(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        Card recorded = CardCopyService.getLKICopy(card);
        card.setGameTimestamp(game.getNextTimestamp());
        assertTrue(valid(card, "Card.Self", actor, recorded));
        assertFalse(valid(card, "Card.Self+StrictlySelf", actor, recorded));
        card.setPhasedOut(actor);
        assertFalse(valid(card, "Card.Self", actor, card));
        assertTrue(valid(card, "Card.phasedOutSelf", actor, card));
        card.setPhasedOut(null);
        assertTrue(valid(card, "Card.Self", actor, card));
    }

    @Test(dataProvider = "copyDepths")
    public void subclassCallbacksAndPropertyOrderAreRetained(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card source = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        List<String> calls = new ArrayList<>();
        Card custom = new Card(900000 + depth, game) {
            @Override public boolean hasProperty(String property, Player controller,
                    Card original, forge.game.CardTraitBase ability) {
                calls.add(property);
                return true;
            }
        };
        custom.setOwner(actor);
        custom.setController(actor, game.getNextTimestamp());
        calls.clear();
        assertTrue(valid(custom, "Card.Self+Trailing", actor, source));
        assertEquals(calls, List.of("Self", "Trailing"));
        calls.clear();
        assertTrue(valid(custom, "Card.First+Self", actor, source));
        assertEquals(calls, List.of("First", "Self"));
    }

    @Test(dataProvider = "copyDepths")
    public void preparedSpellExclusionPrecedesIdentity(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        card.addAlternateState(CardStateName.PreparedSpell, false);
        assertTrue(card.setState(CardStateName.PreparedSpell, false));
        card.setZone(actor.getZone(ZoneType.Exile));
        assertTrue(card.isInZone(ZoneType.Exile));
        assertFalse(valid(card, "Card.Self", actor, card));
        assertFalse(valid(card, "Card.Self", actor, null));
        card.setState(CardStateName.Original, false);
        card.setZone(actor.getZone(ZoneType.Battlefield));
        assertTrue(valid(card, "Card.Self", actor, card));
    }

    @Test(dataProvider = "copyDepths")
    public void nullContextAndNullGameRetainFailures(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        Card other = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).getFirst();
        assertFalse(valid(card, "Card.Self", actor, null));
        expectThrows(NullPointerException.class, () -> valid(card, null, actor, card));
        card.dangerouslySetGame(null);
        try {
            expectThrows(NullPointerException.class,
                    () -> valid(card, "Card.Self", actor, other));
        } finally {
            card.dangerouslySetGame(game);
        }
    }

    @Test(dataProvider = "copyDepths")
    public void prefixAndTypeVariantsKeepOriginalPath(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        assertTrue(valid(card, "Card.SelfNativeSuffix", actor, card));
        assertTrue(valid(card, "Card.YouCtrl+Self", actor, card));
        assertTrue(valid(card, "Land.Self", actor, card));
        assertFalse(valid(card, "Creature.Self", actor, card));
    }
}
