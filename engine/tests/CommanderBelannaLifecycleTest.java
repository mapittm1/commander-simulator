package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/** Native paid control/copy transitions, with supplied surveil and reveal choices. */
public class CommanderBelannaLifecycleTest extends AITest {
    @DataProvider(name = "artifactTop")
    public Object[][] artifactTop() { return new Object[][] {{true}, {false}}; }

    @Test(dataProvider = "artifactTop")
    public void borrowedSourceUsesItsActivatorRatherThanItsOwner(boolean artifact) {
        Scene s = scene(artifact, true, false);
        assertSame(s.host.getOwner(), s.other);
        assertSame(s.host.getController(), s.actor);
        activate(s);
        s.game.getStack().resolveStack();
        assertOutcome(s, artifact ? 1 : 0, artifact ? 0 : 2, 1);
        assertEquals(s.other.getNumDrawnThisTurn(), 0);
    }

    @Test(dataProvider = "artifactTop")
    public void controlChangeOnStackDoesNotRedirectTheAbility(boolean artifact) {
        Scene s = scene(artifact, false, false);
        activate(s);
        assertEquals(s.game.getStack().size(), 1);
        cast(s, s.other, "Ray of Command", s.host);
        s.game.getStack().resolveStack();
        assertSame(s.host.getController(), s.other);
        assertSame(s.host.getOwner(), s.actor);
        assertEquals(s.game.getStack().size(), 1);
        assertSame(s.game.getStack().peekAbility().getActivatingPlayer(), s.actor);
        s.game.getStack().resolveStack();
        assertOutcome(s, artifact ? 1 : 0, artifact ? 0 : 2, 1);
        assertEquals(s.other.getNumDrawnThisTurn(), 0);
    }

    @Test(dataProvider = "artifactTop")
    public void copiedActivationKeepsEachResolutionSeparate(boolean useAbundance) {
        Scene s = scene(true, false, useAbundance);
        Card engine = addCardToZone("Lithoform Engine", s.actor, ZoneType.Battlefield);
        s.game.getAction().checkStateEffects(true);
        activate(s);
        SpellAbility original = s.game.getStack().peekAbility();
        SpellAbility copy = engine.getSpellAbilities().stream()
                .filter(sa -> sa.getApi() == ApiType.CopySpellAbility
                        && sa.getParamOrDefault("TargetType", "").contains("Activated"))
                .findFirst().orElseThrow();
        copy.setActivatingPlayer(s.actor);
        copy.getTargets().add(original);
        assertTrue(ComputerUtil.playStack(copy, s.actor, s.game));
        assertTrue(engine.isTapped());
        assertEquals(s.game.getStack().size(), 2);
        s.game.getStack().resolveStack();
        assertEquals(s.game.getStack().size(), 2, "The copy is placed above the retained original");
        s.game.getStack().resolveStack();
        assertEquals(s.game.getStack().size(), 1);
        assertOutcome(s, 1, useAbundance ? 2 : 0, 1);
        s.game.getStack().resolveStack();
        // The next card is a nonartifact: it must not reuse the copy's reveal or draw.
        assertOutcome(s, 1, useAbundance ? 4 : 2, 2);
        assertEquals(s.actor.getNumDrawnThisTurn(), useAbundance ? 0 : 1);
        assertTrue(s.game.getStack().isEmpty());
    }

    @Test
    public void aLaterActivationStoresANewDrawCount() {
        Scene s = scene(true, false, false);
        activate(s);
        s.game.getStack().resolveStack();
        assertOutcome(s, 1, 0, 1);
        cast(s, s.actor, "Twiddle", s.host);
        s.game.getStack().resolveStack();
        assertFalse(s.host.isTapped());
        activate(s);
        s.game.getStack().resolveStack();
        assertOutcome(s, 1, 2, 2);
        assertEquals(s.actor.getNumDrawnThisTurn(), 1);
        assertTrue(s.game.getStack().isEmpty());
    }

    private Scene scene(boolean artifact, boolean borrowed, boolean abundance) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + i, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Belanna lifecycle"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player other = game.getPlayers().get(2);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (int i = 0; i < 8; i++) addCardToZone("Mountain", actor, ZoneType.Battlefield);
        for (int i = 0; i < 4; i++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
            addCardToZone("Island", other, ZoneType.Battlefield);
            addCardToZone("Forest", other, ZoneType.Library);
        }
        addCardToZone(artifact ? "Sol Ring" : "Grizzly Bears", actor, ZoneType.Library);
        addCardToZone("Grizzly Bears", actor, ZoneType.Library);
        addCardToZone("Island", actor, ZoneType.Library);
        addCardToZone("Swamp", actor, ZoneType.Library);
        Card host = addCardToZone("B'Elanna Torres, Scrapper", borrowed ? other : actor, ZoneType.Battlefield);
        if (abundance) addCardToZone("Abundance", actor, ZoneType.Battlefield);
        Scene s = new Scene(game, actor, other, host);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public ImmutablePair<CardCollection, CardCollection> arrangeForSurveil(CardCollection cards) {
                s.surveils++;
                assertEquals(cards.size(), 2);
                return ImmutablePair.of(new CardCollection(cards), new CardCollection());
            }
            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                if (ability.getHostCard().getId() == host.getId() && ability.getApi() == ApiType.PeekAndReveal) {
                    s.reveals++;
                    assertEquals(card.getId(), actor.getCardsIn(ZoneType.Library).get(0).getId());
                    return true;
                }
                return super.confirmAction(ability, mode, message, options, card, params);
            }
            @Override
            public boolean confirmReplacementEffect(ReplacementEffect effect, SpellAbility action,
                    GameEntity affected, String question) {
                if (effect.getHostCard().getName().equals("Abundance")) return true;
                return super.confirmReplacementEffect(effect, action, affected, question);
            }
            @Override
            public SpellAbility chooseSingleSpellForEffect(List<SpellAbility> spells, SpellAbility action,
                    String title, Map<String, Object> params) {
                if (action.getHostCard().getName().equals("Abundance")) {
                    return spells.stream().filter(sa -> "Card.nonLand".equals(sa.getParam("Valid"))).findFirst().orElseThrow();
                }
                return super.chooseSingleSpellForEffect(spells, action, title, params);
            }
        });
        game.getAction().checkStateEffects(true);
        if (borrowed) {
            cast(s, actor, "Act of Treason", host);
            game.getStack().resolveStack();
            assertSame(host.getController(), actor);
        } else host.setSickness(false);
        game.getTriggerHandler().resetActiveTriggers();
        return s;
    }

    private void activate(Scene s) {
        SpellAbility ability = s.host.getSpellAbilities().stream()
                .filter(sa -> sa.getApi() == ApiType.Surveil).findFirst().orElseThrow();
        ability.setActivatingPlayer(s.actor);
        s.game.copyLastState();
        assertTrue(ComputerUtil.playStack(ability, s.actor, s.game));
        assertTrue(s.host.isTapped());
    }

    private void cast(Scene s, Player caster, String name, Card target) {
        SpellAbility spell = addCardToZone(name, caster, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(caster);
        spell.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(spell, caster, s.game), name + " must be paid");
    }

    private void assertOutcome(Scene s, int hand, int counters, int resolutions) {
        assertEquals(s.actor.getCardsIn(ZoneType.Hand).size(), hand);
        assertEquals(s.host.getCounters(CounterEnumType.P1P1), counters);
        assertEquals(s.surveils, resolutions);
        assertEquals(s.reveals, resolutions);
        assertFalse(s.host.getRemembered().iterator().hasNext());
    }

    private static final class Scene {
        final Game game;
        final Player actor;
        final Player other;
        final Card host;
        int surveils;
        int reveals;
        Scene(Game game, Player actor, Player other, Card host) {
            this.game = game; this.actor = actor; this.other = other; this.host = host;
        }
    }
}
