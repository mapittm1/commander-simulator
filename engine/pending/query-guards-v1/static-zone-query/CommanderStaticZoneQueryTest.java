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
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityLayer;
import forge.game.staticability.StaticAbilityMode;
import java.util.AbstractSet;
import java.util.Iterator;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Live property outcomes retain identity, timestamps, LKI and phasing across copies. */
public class CommanderStaticZoneQueryTest extends AITest {
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

    private StaticAbility add(Game game, String parameters) {
        Card host = game.getPlayers().getFirst().getCardsIn(ZoneType.Battlefield).getFirst();
        StaticAbility ability = new StaticAbility(parameters, host, host.getCurrentState());
        host.addStaticAbility(ability);
        return ability;
    }

    private String parameters() {
        return "Mode$ Continuous | AffectedDefined$ Self | AddKeyword$ Hexproof"
                + " | IsPresent$ Card.Self | PresentCompare$ GE1";
    }

    @Test(dataProvider = "copyDepths")
    public void defaultScopeAndLayerStayCorrect(int depth) {
        Game game = setup(depth);
        add(game, parameters());
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Battlefield, StaticAbilityLayer.ABILITIES));
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Battlefield, StaticAbilityLayer.COLOR));
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, null));
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Battlefield, null));
    }

    @Test(dataProvider = "copyDepths")
    public void explicitZonesAndLiveMutationRemainEffective(int depth) {
        Game game = setup(depth);
        StaticAbility ability = add(game, parameters());
        ability.putParam("AffectedZone", "Stack");
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Battlefield, StaticAbilityLayer.ABILITIES));
        ability.putParam("AffectedZone", "Battlefield,Stack");
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Battlefield, StaticAbilityLayer.ABILITIES));
        ability.removeParam("AffectedZone");
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
    }

    @Test(dataProvider = "copyDepths")
    public void relevantConditionsAreReadLive(int depth) {
        Game game = setup(depth);
        StaticAbility ability = add(game, parameters());
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Battlefield, null));
        ability.putParam("PresentCompare", "EQ0");
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Battlefield, null));
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, null));
        ability.putParam("AffectedZone", "Stack");
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, null));
        ability.putParam("PresentCompare", "GE1");
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, null));
    }

    @Test(dataProvider = "copyDepths")
    public void unknownAbilityMetadataRetainsFallback(int depth) {
        Game game = setup(depth);
        Card host = game.getPlayers().getFirst().getCardsIn(ZoneType.Battlefield).getFirst();
        StaticAbility ability = new StaticAbility(parameters(), host, host.getCurrentState()) {
            @Override public String getParamOrDefault(String name, String fallback) {
                return name.equals("AffectedZone") ? "Stack" : super.getParamOrDefault(name, fallback);
            }
        };
        assertFalse(ability.hasParam("AffectedZone"));
        host.addStaticAbility(ability);
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
    }

    @Test(dataProvider = "copyDepths")
    public void unknownModeSetRetainsItsCallback(int depth) {
        Game game = setup(depth);
        StaticAbility ability = add(game, parameters());
        int[] reads = {0};
        ability.setMode(new AbstractSet<StaticAbilityMode>() {
            @Override public Iterator<StaticAbilityMode> iterator() {
                return List.of(StaticAbilityMode.Continuous).iterator();
            }
            @Override public int size() { return 1; }
            @Override public boolean contains(Object value) {
                reads[0]++;
                return value == StaticAbilityMode.Continuous;
            }
        });
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, null));
        assertEquals(reads[0], 1);
    }

    private static final class ObservedHost extends Card {
        private int gameReads;
        private ObservedHost(Game game) { super(910000, game); }
        @Override public Game getGame() { gameReads++; return super.getGame(); }
    }

    @Test(dataProvider = "copyDepths")
    public void unknownHostRetainsConditionCallbacks(int depth) {
        Game game = setup(depth);
        Player actor = game.getPlayers().getFirst();
        ObservedHost host = new ObservedHost(game);
        host.setOwner(actor);
        host.setController(actor, game.getNextTimestamp());
        actor.getZone(ZoneType.Battlefield).add(host);
        host.addStaticAbility(new StaticAbility(parameters(), host, host.getCurrentState()));
        host.gameReads = 0;
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
        assertTrue(host.gameReads > 0, "Opaque host conditions retain their original callbacks");
    }

    @Test(dataProvider = "copyDepths")
    public void copiedNativeAbilityRetainsScopeAndLiveParameters(int depth) {
        Game original = setup(depth);
        StaticAbility ability = add(original, parameters());
        Card originalHost = ability.getHostCard();
        GameCopier copier = new GameCopier(original).withStack(true);
        Game game = copier.makeCopy(null, original.getPlayers().getFirst());
        Card host = copier.find(originalHost);
        copier.release();
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Battlefield, StaticAbilityLayer.ABILITIES));
        StaticAbility copied = host.getStaticAbilities().get(0);
        copied.putParam("AffectedZone", "Stack");
        assertTrue(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
        assertFalse(original.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
    }

    /** Observation only: irrelevant scope need not evaluate a native condition's card scan. */
    public int irrelevantConditionObservation() {
        Game game = setup(0);
        Player actor = game.getPlayers().getFirst();
        int[] reads = {0};
        Card observer = new Card(920000, game) {
            @Override public boolean hasProperty(String property, Player controller,
                    Card source, forge.game.CardTraitBase context) {
                reads[0]++;
                return super.hasProperty(property, controller, source, context);
            }
        };
        observer.setOwner(actor);
        observer.setController(actor, game.getNextTimestamp());
        actor.getZone(ZoneType.Battlefield).add(observer);
        add(game, parameters());
        reads[0] = 0;
        assertFalse(game.getAction().hasStaticAbilityAffectingZone(ZoneType.Stack, StaticAbilityLayer.ABILITIES));
        return reads[0];
    }
}
