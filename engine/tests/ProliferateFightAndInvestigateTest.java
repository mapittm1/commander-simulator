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
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ProliferateFightAndInvestigateTest extends AITest {
    private record Pod(Game game, Player actor, Player opponent) {}

    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Coverage gaps"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        return new Pod(game, actor, game.getPlayers().get(1));
    }

    private ZoneType zone(Pod pod, Card card) {
        return pod.game.getZoneOf(pod.game.getCardState(card)).getZoneType();
    }

    @Test
    public void smellFearProliferatesBeforeBothCreaturesDealFightDamage() {
        verifySmellFear(0);
    }

    @Test
    public void smellFearStillProliferatesWhenOnlyOneTargetRemains() {
        verifySmellFear(1);
    }

    @Test
    public void smellFearDoesNotProliferateWhenBothTargetsAreGone() {
        verifySmellFear(2);
    }

    private void verifySmellFear(int removed) {
        Pod pod = pod();
        addCardToZone("Forest", pod.actor, ZoneType.Battlefield);
        addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        Card own = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Battlefield);
        Card enemy = addCardToZone("Rumbling Baloth", pod.opponent, ZoneType.Battlefield);
        Card artifact = addCardToZone("Everflowing Chalice", pod.opponent, ZoneType.Battlefield);
        own.setCounters(CounterEnumType.P1P1, 1);
        artifact.setCounters(CounterEnumType.CHARGE, 2);
        artifact.setCounters(CounterEnumType.OIL, 1);
        pod.opponent.setPoisonCounters(1, pod.actor);
        pod.actor.dangerouslySetController(new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override
            public <T extends GameEntity> List<T> chooseEntitiesForEffect(FCollectionView<T> choices,
                    int min, int max, DelayedReveal delayed, SpellAbility ability, String title,
                    Player related, Map<String, Object> params) {
                if (ability.getApi() == ApiType.Proliferate) {
                    return choices.stream().filter(choice -> choice.equals(own)
                            || choice.equals(artifact) || choice.equals(pod.opponent)).toList();
                }
                return super.chooseEntitiesForEffect(choices, min, max, delayed, ability, title, related, params);
            }
        });
        pod.game.getAction().checkStateEffects(true);
        Card card = addCardToZone("Smell Fear", pod.actor, ZoneType.Hand);
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getSubAbility().getTargets().add(own);
        spell.getSubAbility().getSubAbility().getTargets().add(enemy);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, spell, null));
        if (removed > 0) {
            pod.game.getAction().moveToGraveyard(enemy, null);
        }
        if (removed > 1) {
            pod.game.getAction().moveToGraveyard(own, null);
        }

        pod.game.getStack().resolveStack();
        pod.game.getAction().checkStateEffects(true);
        assertEquals(pod.opponent.getPoisonCounters(), removed == 2 ? 1 : 2);
        assertEquals(artifact.getCounters(CounterEnumType.CHARGE), removed == 2 ? 2 : 3);
        assertEquals(artifact.getCounters(CounterEnumType.OIL), removed == 2 ? 1 : 2);
        assertEquals(zone(pod, enemy), ZoneType.Graveyard);
        assertEquals(zone(pod, own), removed == 1 ? ZoneType.Battlefield : ZoneType.Graveyard);
        if (removed == 1) {
            assertEquals(own.getCounters(CounterEnumType.P1P1), 2);
            assertEquals(own.getDamage(), 0);
        }
    }

    @Test
    public void hardEvidenceCreatesSeparateClueThatCanBePaidAndSacrificed() {
        Pod pod = pod();
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        }
        Card draw = addCardToZone("Forest", pod.actor, ZoneType.Library);
        Card evidence = addCardToZone("Hard Evidence", pod.actor, ZoneType.Hand);
        SpellAbility spell = evidence.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, spell, null));
        pod.game.getStack().resolveStack();
        List<Card> tokens = pod.actor.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).toList();
        assertEquals(tokens.size(), 2);
        Card crab = tokens.stream().filter(card -> card.getType().hasCreatureType("Crab")).findFirst().orElseThrow();
        assertEquals(crab.getNetPower(), 0);
        assertEquals(crab.getNetToughness(), 3);
        Card clue = tokens.stream().filter(card -> card.getType().hasSubtype("Clue")).findFirst().orElseThrow();
        assertTrue(clue.isArtifact());
        SpellAbility investigate = clue.getNonManaAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.Draw).findFirst().orElseThrow();
        investigate.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, investigate, null));
        assertEquals(zone(pod, draw), ZoneType.Library);
        pod.game.getStack().resolveStack();
        assertEquals(zone(pod, draw), ZoneType.Hand);
        assertEquals(zone(pod, crab), ZoneType.Battlefield);
        assertTrue(pod.actor.getCardsIn(ZoneType.Battlefield).stream().noneMatch(card -> card.getId() == clue.getId()));
    }
}
