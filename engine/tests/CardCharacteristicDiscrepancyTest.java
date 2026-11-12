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

public class CardCharacteristicDiscrepancyTest extends AITest {
    private record Pod(Game game, Player actor, Player opponent) {}

    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Card characteristics"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        return new Pod(game, actor, game.getPlayers().get(1));
    }

    private void cast(Pod pod, Card card) {
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, spell, null));
        assertEquals(pod.game.getStack().peekAbility().getHostCard().getId(), card.getId());
    }

    @Test
    public void lostOrderUsesOnePlusChosenOpponentsLiveCreatureCount() {
        Pod pod = pod();
        pod.actor.dangerouslySetController(new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override
            public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> options,
                    DelayedReveal delayed, SpellAbility ability, String title, boolean optional,
                    Player targetedPlayer, Map<String, Object> params) {
                if (ability.getHostCard().getName().equals("Lost Order of Jarkeld")) {
                    return options.stream().filter(option -> option.equals(pod.opponent))
                            .findFirst().orElseThrow();
                }
                return super.chooseSingleEntityForEffect(options, delayed, ability, title, optional, targetedPlayer, params);
            }
        });
        for (int index = 0; index < 4; index++) {
            addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        }
        Card bear = addCardToZone("Grizzly Bears", pod.opponent, ZoneType.Battlefield);
        addCardToZone("Runeclaw Bear", pod.opponent, ZoneType.Battlefield);
        addCardToZone("Silvercoat Lion", pod.game.getPlayers().get(2), ZoneType.Battlefield);
        Card knight = addCardToZone("Lost Order of Jarkeld", pod.actor, ZoneType.Hand);
        pod.game.getAction().checkStateEffects(true);
        assertEquals(knight.getNetPower(), 1);
        assertEquals(knight.getNetToughness(), 1);
        cast(pod, knight);
        pod.game.getStack().resolveStack();
        Card entrant = pod.game.getCardState(knight);
        assertTrue(entrant.isInPlay());
        assertEquals(entrant.getNetPower(), 3);
        assertEquals(entrant.getNetToughness(), 3);
        pod.game.getAction().moveToGraveyard(bear, null);
        pod.game.getAction().checkStateEffects(true);
        assertEquals(entrant.getNetPower(), 2);
        assertEquals(entrant.getNetToughness(), 2);
        Card dead = pod.game.getAction().moveToGraveyard(entrant, null);
        assertEquals(dead.getNetPower(), 1);
        assertEquals(dead.getNetToughness(), 1);
    }

    @Test
    public void hybridPhyrexianLukkaCanPayManaAndEnterAtFullLoyalty() {
        verifyLukka(false);
    }

    @Test
    public void hybridPhyrexianLukkaCanPayLifeAndEnterCompleated() {
        verifyLukka(true);
    }

    private void verifyLukka(boolean payLife) {
        Pod pod = pod();
        addCardToZone("Mountain", pod.actor, ZoneType.Battlefield);
        addCardToZone("Forest", pod.actor, ZoneType.Battlefield);
        addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        if (!payLife) {
            addCardToZone("Mountain", pod.actor, ZoneType.Battlefield);
        }
        Card card = addCardToZone("Lukka, Bound to Ruin", pod.actor, ZoneType.Hand);
        assertEquals(card.getCMC(), 5);
        int life = pod.actor.getLife();
        cast(pod, card);
        SpellAbility paid = pod.game.getStack().peekAbility();
        assertEquals(paid.getPayingMana().size(), payLife ? 4 : 5);
        assertEquals(pod.actor.getLife(), life - (payLife ? 2 : 0));
        pod.game.getStack().resolveStack();
        Card entrant = pod.game.getCardState(card);
        assertTrue(entrant.isInPlay());
        assertEquals(entrant.getCounters(CounterEnumType.LOYALTY), payLife ? 3 : 5);
    }

    @Test
    public void sasayaRetainsItsManaValueWhenFlippedByItsPaidAbility() {
        Pod pod = pod();
        Card sasaya = addCardToZone("Sasaya, Orochi Ascendant", pod.actor, ZoneType.Battlefield);
        for (int index = 0; index < 7; index++) {
            addCardToZone("Forest", pod.actor, ZoneType.Hand);
        }
        pod.game.getAction().checkStateEffects(true);
        SpellAbility flip = sasaya.getNonManaAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.SetState).findFirst().orElseThrow();
        flip.setActivatingPlayer(pod.actor);
        assertEquals(sasaya.getCMC(), 3);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, flip, null));
        pod.game.getStack().resolveStack();
        assertTrue(sasaya.isFlipped() && sasaya.isEnchantment());
        assertEquals(sasaya.getName(), "Sasaya's Essence");
        assertEquals(sasaya.getCMC(), 3);
        assertTrue(sasaya.getColor().hasGreen());
        Card returned = pod.game.getAction().moveToHand(sasaya, null);
        assertEquals(returned.getName(), "Sasaya, Orochi Ascendant");
        assertEquals(returned.getCMC(), 3);
    }

    @Test
    public void arlinnTransformsWithoutReplacingItsActualLoyaltyCounters() {
        Pod pod = pod();
        addCardToZone("Mountain", pod.actor, ZoneType.Battlefield);
        addCardToZone("Forest", pod.actor, ZoneType.Battlefield);
        addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        Card arlinn = addCardToZone("Arlinn Kord", pod.actor, ZoneType.Hand);
        cast(pod, arlinn);
        pod.game.getStack().resolveStack();
        Card planeswalker = pod.game.getCardState(arlinn);
        assertEquals(planeswalker.getCounters(CounterEnumType.LOYALTY), 3);
        SpellAbility transform = planeswalker.getNonManaAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.Token).findFirst().orElseThrow();
        transform.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, transform, null));
        pod.game.getStack().resolveStack();
        assertEquals(planeswalker.getName(), "Arlinn, Embraced by the Moon");
        assertEquals(planeswalker.getCounters(CounterEnumType.LOYALTY), 3);
        assertEquals(planeswalker.getCMC(), 4);
        assertTrue(pod.actor.getCardsIn(ZoneType.Battlefield).stream()
                .anyMatch(card -> card.isToken() && card.getType().hasCreatureType("Wolf")));
    }
}
