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
import forge.game.card.CardProperty;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Live property outcomes retain identity, timestamps, LKI and phasing across copies. */
public class CommanderExactPropertyTest extends AITest {
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

    private boolean matches(Card card, String property, Player controller, Card source) {
        return CardProperty.cardHasProperty(card, property, controller, source, null);
    }

    @Test(dataProvider = "copyDepths")
    public void identityAndOther(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        Card other = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).getFirst();
        assertTrue(matches(card, "Self", actor, card));
        assertTrue(matches(card, "StrictlySelf", actor, card));
        assertFalse(matches(card, "Other", actor, card));
        assertFalse(matches(card, "StrictlyOther", actor, card));
        assertFalse(matches(card, "Self", actor, other));
        assertFalse(matches(card, "StrictlySelf", actor, other));
        assertTrue(matches(card, "Other", actor, other));
        assertTrue(matches(card, "StrictlyOther", actor, other));
    }

    @Test(dataProvider = "copyDepths")
    public void liveControllerAndRecordedLki(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Player opponent = game.getPlayers().get(1);
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        Card recorded = CardCopyService.getLKICopy(card);
        assertTrue(matches(card, "YouCtrl", actor, card));
        card.setController(opponent, game.getNextTimestamp());
        assertSame(game.getChangeZoneLKIInfo(card), card);
        assertFalse(matches(card, "YouCtrl", actor, card));
        assertTrue(matches(card, "YouDontCtrl", actor, card));
        assertTrue(matches(card, "YouCtrl", opponent, card));
        game.addChangeZoneLKIInfo(recorded);
        assertSame(game.getChangeZoneLKIInfo(card), recorded);
        assertTrue(matches(card, "YouCtrl", actor, card));
        assertFalse(matches(card, "YouDontCtrl", actor, card));
        game.clearChangeZoneLKIInfo();
        assertTrue(matches(card, "YouCtrl", opponent, card));
    }

    @Test(dataProvider = "copyDepths")
    public void timestampDistinguishesStrictIdentity(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        Card recorded = CardCopyService.getLKICopy(card);
        card.setGameTimestamp(game.getNextTimestamp());
        assertTrue(matches(card, "Self", actor, recorded));
        assertFalse(matches(card, "StrictlySelf", actor, recorded));
        assertFalse(matches(card, "Other", actor, recorded));
        assertTrue(matches(card, "StrictlyOther", actor, recorded));
    }

    @Test(dataProvider = "copyDepths")
    public void phasingPrecedesExactDispatch(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        card.setPhasedOut(actor);
        assertFalse(matches(card, "Self", actor, card));
        assertFalse(matches(card, "YouCtrl", actor, card));
        assertTrue(matches(card, "phasedOutSelf", actor, card));
        assertTrue(matches(card, "phasedOutYouCtrl", actor, card));
        card.setPhasedOut(null);
        assertTrue(matches(card, "Self", actor, card));
        assertTrue(matches(card, "YouCtrl", actor, card));
    }

    @Test(dataProvider = "copyDepths")
    public void prefixVariantsRetainNativeFallback(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        assertTrue(matches(card, "SelfNativeSuffix", actor, card));
        assertTrue(matches(card, "StrictlySelfNativeSuffix", actor, card));
        assertFalse(matches(card, "OtherNativeSuffix", actor, card));
        assertFalse(matches(card, "StrictlyOtherNativeSuffix", actor, card));
        assertTrue(matches(card, "YouCtrlNativeSuffix", actor, card));
        assertFalse(matches(card, "YouDontCtrlNativeSuffix", actor, card));
    }

    @Test(dataProvider = "copyDepths")
    public void nullContextRetainsNativeIdentityResults(int depth) {
        Game game = setup(depth);
        Card card = game.getPlayers().getFirst().getCardsIn(ZoneType.Battlefield).getFirst();
        assertFalse(matches(card, "Self", null, null));
        assertFalse(matches(card, "StrictlySelf", null, null));
        assertTrue(matches(card, "Other", null, null));
        assertTrue(matches(card, "StrictlyOther", null, null));
        assertFalse(matches(card, "YouCtrl", null, null));
        assertTrue(matches(card, "YouDontCtrl", null, null));
    }

    @Test(dataProvider = "copyDepths")
    public void nullPropertyStillFails(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        Card card = actor.getCardsIn(ZoneType.Battlefield).getFirst();
        expectThrows(NullPointerException.class, () -> matches(card, null, actor, card));
    }
}
