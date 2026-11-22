package forge.ai;

import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.card.CardCollection;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Follow-up research: non-type choices on resolving spells and raw stack card state. */
public class CommanderChoiceContextTest extends AITest {
    @Test public void colorsOnAStackCardSurviveTheCopy() {
        Pod pod = pod(); Card card = stackCard(pod, "Persecute"); card.setChosenColors(List.of("Blue"));
        forge.ai.simulation.GameCopier copier = new forge.ai.simulation.GameCopier(pod.game);
        Game game = copier.makeCopy(null, pod.actor); Card copy = copier.find(card);
        assertSame(copy.getGame(), game); assertTrue(card.hasChosenColor());
        assertEquals(copy.getChosenColors(), card.getChosenColors());
    }
    @Test public void namedCardsOnAStackCardSurviveTheCopy() {
        Pod pod = pod(); Card card = stackCard(pod, "Demonic Consultation"); card.setNamedCards(List.of("Forest"));
        forge.ai.simulation.GameCopier copier = new forge.ai.simulation.GameCopier(pod.game);
        copier.makeCopy(null, pod.actor); Card copy = copier.find(card);
        assertTrue(card.hasNamedCard()); assertEquals(copy.getNamedCards(), card.getNamedCards());
    }
    /** Explicit raw state contract, not an added printed Persecute ability. */
    @Test public void aChosenPlayerOnAStackCardMapsIntoTheCopiedGame() {
        Pod pod = pod(); Card card = stackCard(pod, "Persecute"); card.setChosenPlayer(pod.enemy);
        forge.ai.simulation.GameCopier copier = new forge.ai.simulation.GameCopier(pod.game);
        Game game = copier.makeCopy(null, pod.actor); Player chosen = copier.find(card).getChosenPlayer();
        assertNotNull(chosen); assertNotSame(chosen, pod.enemy); assertSame(chosen.getGame(), game);
        assertSame(chosen, copier.find(pod.enemy));
    }
    @Test public void suppliedPersecuteDiscardsOnlyTheChosenColor() { persecute(false); }
    @Test public void persecutesChosenColorSurvivesAMidResolutionCopy() { persecute(true); }

    @Test public void choicesSurviveAHandCopy() { zoneChoices(ZoneType.Hand); }
    @Test public void choicesSurviveAStackCopy() { zoneChoices(ZoneType.Stack); }
    @Test public void choicesSurviveABattlefieldCopy() { zoneChoices(ZoneType.Battlefield); }
    @Test public void choicesSurviveAGraveyardCopy() { zoneChoices(ZoneType.Graveyard); }
    @Test public void choicesSurviveAnExileCopy() { zoneChoices(ZoneType.Exile); }
    @Test public void choicesSurviveALibraryCopy() { zoneChoices(ZoneType.Library); }
    @Test public void theChosenModeSurvivesAStackCopy() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenMode("first");
        assertEquals(copy(p,c).getChosenMode(),"first");
    }
    @Test public void theChosenDirectionSurvivesAStackCopy() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenDirection(forge.game.Direction.Left);
        assertEquals(copy(p,c).getChosenDirection(),forge.game.Direction.Left);
    }
    @Test public void theChosenParitySurvivesAStackCopy() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenEvenOdd(forge.game.EvenOdd.Odd);
        assertEquals(copy(p,c).getChosenEvenOdd(),forge.game.EvenOdd.Odd);
    }
    @Test public void theChosenSectorSurvivesAStackCopy() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenSector("A");
        assertEquals(copy(p,c).getChosenSector(),"A");
    }
    @Test public void chosenCardsMapIntoTheirCopiedWorld() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"), target=addCardToZone("Grizzly Bears",p.enemy,ZoneType.Battlefield);
        c.setChosenCards(new CardCollection(target));
        forge.ai.simulation.GameCopier copier=new forge.ai.simulation.GameCopier(p.game); Game game=copier.makeCopy(null,p.actor);
        Card result=copier.find(c).getChosenCard(); assertNotNull(result);
        assertNotSame(result,target); assertSame(result.getGame(),game); assertSame(result,copier.find(target));
    }
    @Test public void chosenGroupsMapPlayersAndCardsWithoutSharingTheirContainers() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"), target=addCardToZone("Grizzly Bears",p.enemy,ZoneType.Battlefield);
        c.addToChosenMap(p.enemy,new CardCollection(target));
        forge.ai.simulation.GameCopier copier=new forge.ai.simulation.GameCopier(p.game); Game game=copier.makeCopy(null,p.actor);
        Card result=copier.find(c); Player enemy=copier.find(p.enemy);
        assertTrue(result.getChosenMap().containsKey(enemy));
        assertSame(result.getChosenMap().get(enemy).get(0).getGame(),game);
        assertSame(result.getChosenMap().get(enemy).get(0),copier.find(target));
        result.getChosenMap().get(enemy).clear(); assertEquals(c.getChosenMap().get(p.enemy).size(),1);
    }
    @Test public void aHistoricalChosenCardKeepsItsOldTimestampAndChoices() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"), target=addCardToZone("Grizzly Bears",p.enemy,ZoneType.Battlefield);
        target.setChosenPlayer(p.actor); target.setChosenMode("before"); target.setChosenCards(new CardCollection(target)); c.setChosenCards(new CardCollection(target));
        Card moved=p.game.getAction().moveToGraveyard(target,null); assertNotEquals(moved.getGameTimestamp(),target.getGameTimestamp());
        forge.ai.simulation.GameCopier copier=new forge.ai.simulation.GameCopier(p.game); Game game=copier.makeCopy(null,p.actor);
        Card remembered=copier.find(c).getChosenCard(); assertNotNull(remembered);
        assertSame(remembered.getGame(),game); assertNotSame(remembered,copier.find(moved));
        assertEquals(remembered.getGameTimestamp(),target.getGameTimestamp());
        assertSame(remembered.getChosenPlayer(),copier.find(p.actor)); assertEquals(remembered.getChosenMode(),"before");
        assertSame(remembered.getChosenCard(),remembered);
    }
    @Test public void nativeLastKnownInformationKeepsChoicesAndSecretPresentation() {
        Pod p=pod(); Card c=addCardToZone("Grizzly Bears",p.actor,ZoneType.Battlefield);
        c.setChosenPlayer(p.actor); c.setSecretChosenPlayer(p.enemy);
        c.setChosenType("Human"); c.setSecretChosenType("Elf"); c.setChosenMode("before");
        Card result=forge.game.card.CardCopyService.getLKICopy(c);
        assertSame(result.getChosenPlayer(),p.enemy); assertEquals(result.getChosenType(),"Elf");
        assertEquals(result.getChosenMode(),"before"); assertSame(result.getView().getChosenPlayer(),p.actor.getView());
        assertEquals(result.getView().getChosenType(),"Human");
    }
    @Test public void aSecretPlayerDoesNotBecomePublicInTheCopy() {
        Pod p=pod(); Card c=addCardToZone("Grizzly Bears",p.actor,ZoneType.Battlefield); c.setSecretChosenPlayer(p.enemy);
        assertNull(c.getView().getChosenPlayer()); Card result=copy(p,c);
        assertNotNull(result.getChosenPlayer()); assertNull(result.getView().getChosenPlayer());
    }
    @Test public void theLastPublicPlayerChoiceIsPreservedAfterASecretChange() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenPlayer(p.actor); c.setSecretChosenPlayer(p.enemy);
        forge.ai.simulation.GameCopier copier=new forge.ai.simulation.GameCopier(p.game); copier.makeCopy(null,p.actor);
        Card result=copier.find(c); assertSame(result.getChosenPlayer(),copier.find(p.enemy));
        assertSame(result.getView().getChosenPlayer(),copier.find(p.actor).getView());
    }
    @Test public void theLastPublicTypeChoiceIsPreservedAfterASecretChange() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenType("Human"); c.setSecretChosenType("Elf");
        Card result=copy(p,c); assertEquals(result.getChosenType(),"Elf"); assertEquals(result.getView().getChosenType(),"Human");
    }
    @Test public void aSecretNumberRemainsSecretInTheCopy() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenNumber(42,true);
        Card result=copy(p,c); assertEquals(result.getChosenNumber(),Integer.valueOf(42));
        assertEquals(result.getView().getChosenNumber(),c.getView().getChosenNumber());
    }
    @Test public void existingTypeChoicesAreRetained() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenType("Human"); c.setChosenType2("Wizard");
        Card result=copy(p,c); assertEquals(result.getChosenType(),"Human"); assertEquals(result.getChosenType2(),"Wizard");
    }
    @Test public void theChosenCommanderColorIsRetained() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenCommanderColor(MagicColor.BLUE);
        assertEquals(copy(p,c).getChosenCommanderColor(),MagicColor.BLUE);
    }
    @Test public void copiedColorContainersAreIndependent() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenColors(new ArrayList<>(List.of("Blue")));
        Card result=copy(p,c); assertEquals(result.getChosenColors(),c.getChosenColors());
        ((List<String>)result.getChosenColors()).add("Red"); assertEquals(c.getChosenColors(),List.of("Blue"));
        assertEquals(result.getView().getChosenColors(),List.of("Blue","Red"));
        assertEquals(c.getView().getChosenColors(),List.of("Blue"));
    }
    @Test public void copiedNameContainersAreIndependent() {
        Pod p=pod(); Card c=stackCard(p,"Demonic Consultation"); c.setNamedCards(new ArrayList<>(List.of("Forest")));
        Card result=copy(p,c); assertEquals(result.getNamedCards(),c.getNamedCards());
        result.getNamedCards().add("Island"); assertEquals(c.getNamedCards(),List.of("Forest"));
        assertEquals(result.getView().getNamedCard(),List.of("Forest","Island"));
        assertEquals(c.getView().getNamedCard(),List.of("Forest"));
    }
    @Test public void eliminatedChosenPlayersRemainMappedParticipants() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenPlayer(p.enemy); p.enemy.concede();
        forge.ai.simulation.GameCopier copier=new forge.ai.simulation.GameCopier(p.game); Game game=copier.makeCopy(null,p.actor);
        Player chosen=copier.find(c).getChosenPlayer(); assertNotNull(chosen); assertNotSame(chosen,p.enemy);
        assertSame(chosen.getGame(),game); assertSame(chosen,copier.find(p.enemy)); assertTrue(chosen.hasLost());
    }
    @Test public void emptyChoiceStateDoesNotAcquireValues() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"), result=copy(p,c);
        assertFalse(result.hasChosenColor()); assertFalse(result.hasNamedCard()); assertNull(result.getChosenPlayer());
        assertTrue(result.getChosenCards().isEmpty()); assertTrue(result.getChosenMap().isEmpty());
        assertNull(result.getChosenDirection()); assertFalse(result.hasChosenEvenOdd()); assertFalse(result.hasChosenNumber());
    }
    /** Combined raw-state contracts complement the independent fields and real Persecute witnesses. */
    private void zoneChoices(ZoneType zone) {
        Pod p=pod(); Card c=zone==ZoneType.Stack?stackCard(p,"Persecute"):addCardToZone("Persecute",p.actor,zone);
        Card target=addCardToZone("Grizzly Bears",p.enemy,ZoneType.Hand);
        c.setChosenColors(List.of("Blue")); c.setNamedCards(List.of("Forest")); c.setChosenPlayer(p.enemy);
        c.setChosenCards(new CardCollection(target)); c.addToChosenMap(p.enemy,new CardCollection(target));
        c.setChosenMode("first"); c.setChosenSector("A"); c.setChosenDirection(forge.game.Direction.Left);
        c.setChosenEvenOdd(forge.game.EvenOdd.Odd); c.setChosenType("Human"); c.setChosenType2("Wizard"); c.setChosenNumber(7);
        forge.ai.simulation.GameCopier copier=new forge.ai.simulation.GameCopier(p.game); copier.makeCopy(null,p.actor); Card result=copier.find(c);
        assertEquals(result.getChosenColors(),c.getChosenColors()); assertEquals(result.getNamedCards(),c.getNamedCards());
        assertSame(result.getChosenPlayer(),copier.find(p.enemy)); assertSame(result.getChosenCard(),copier.find(target));
        assertSame(result.getChosenMap().get(copier.find(p.enemy)).get(0),copier.find(target));
        assertEquals(result.getChosenMode(),"first"); assertEquals(result.getChosenSector(),"A");
        assertEquals(result.getChosenDirection(),forge.game.Direction.Left); assertEquals(result.getChosenEvenOdd(),forge.game.EvenOdd.Odd);
        assertEquals(result.getChosenType(),"Human"); assertEquals(result.getChosenType2(),"Wizard"); assertEquals(result.getChosenNumber(),Integer.valueOf(7));
    }
    private Card copy(Pod p,Card c) {
        forge.ai.simulation.GameCopier copier=new forge.ai.simulation.GameCopier(p.game); copier.makeCopy(null,p.actor); return copier.find(c);
    }

    @Test public void revealingAPlayerInTheCopyDoesNotRevealTheOriginalSecret() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenPlayer(p.actor); c.setSecretChosenPlayer(p.enemy);
        forge.ai.simulation.GameCopier copier=new forge.ai.simulation.GameCopier(p.game); copier.makeCopy(null,p.actor);
        Card result=copier.find(c); assertSame(result.getView().getChosenPlayer(),copier.find(p.actor).getView());
        result.revealChosenPlayer(); assertSame(result.getView().getChosenPlayer(),copier.find(p.enemy).getView());
        assertSame(c.getView().getChosenPlayer(),p.actor.getView()); assertSame(c.getChosenPlayer(),p.enemy);
    }
    @Test public void revealingATypeInTheCopyDoesNotRevealTheOriginalSecret() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenType("Human"); c.setSecretChosenType("Elf");
        Card result=copy(p,c); assertEquals(result.getView().getChosenType(),"Human");
        result.revealChosenType(); assertEquals(result.getView().getChosenType(),"Elf");
        assertEquals(c.getView().getChosenType(),"Human"); assertEquals(c.getChosenType(),"Elf");
    }
    @Test public void selfReferencingChoiceGroupsRetainTheirCopiedIdentity() {
        Pod p=pod(); Card c=stackCard(p,"Persecute"); c.setChosenCards(new CardCollection(c));
        c.addToChosenMap(p.actor,new CardCollection(c));
        forge.ai.simulation.GameCopier copier=new forge.ai.simulation.GameCopier(p.game); copier.makeCopy(null,p.actor);
        Card result=copier.find(c); assertSame(result.getChosenCard(),result);
        assertSame(result.getChosenMap().get(copier.find(p.actor)).get(0),result);
        assertSame(c.getChosenCard(),c); assertSame(c.getChosenMap().get(p.actor).get(0),c);
    }

    private void persecute(boolean inspectCopy) {
        Pod pod = pod(); Card spell = addCardToZone("Persecute", pod.actor, ZoneType.Hand);
        Card blue = addCardToZone("Counterspell", pod.enemy, ZoneType.Hand);
        Card green = addCardToZone("Grizzly Bears", pod.enemy, ZoneType.Hand);
        SpellAbility action = spell.getFirstSpellAbility(); action.setActivatingPlayer(pod.actor);
        action.getSubAbility().getTargets().add(pod.enemy);
        boolean[] observed = {false}; AiController brain = ((PlayerControllerAi) pod.actor.getController()).getAi();
        PlayerControllerAi controller = new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override public AiController getAi() { return brain; }
            @Override public ColorSet chooseColors(String message, SpellAbility choice, int min, int max, ColorSet options) {
                assertTrue((options.getColor() & MagicColor.BLUE) != 0); return ColorSet.fromMask(MagicColor.BLUE);
            }
            @Override public void reveal(CardCollectionView cards, ZoneType zone, Player owner, String prefix, boolean suffix) {
                if (zone == ZoneType.Hand && owner == pod.enemy && pod.game.getStack().isResolving()) {
                    observed[0] = true; Card source = action.getHostCard();
                    assertTrue(source.isInZone(ZoneType.Stack)); assertTrue(source.hasChosenColor());
                    if (inspectCopy) {
                        forge.ai.simulation.GameCopier copier = new forge.ai.simulation.GameCopier(pod.game);
                        copier.makeCopy(null, pod.actor);
                        assertEquals(copier.find(source).getChosenColors(), source.getChosenColors(),
                                "A resolving spell's chosen color must survive a game copy");
                    }
                }
                super.reveal(cards, zone, owner, prefix, suffix);
            }
        };
        pod.actor.runWithController(() -> {
            assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, action, null)); settle(pod.game);
        }, controller);
        assertTrue(observed[0]); assertTrue(pod.game.getCardState(blue).isInZone(ZoneType.Graveyard));
        assertTrue(green.isInZone(ZoneType.Hand)); assertTrue(pod.game.getCardState(spell).isInZone(ZoneType.Graveyard));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
        assertTrue(pod.actor.getLandsInPlay().stream().allMatch(Card::isTapped));
    }
    private Card stackCard(Pod pod, String name) {
        Card card = addCardToZone(name, pod.actor, ZoneType.Hand);
        pod.actor.getZone(ZoneType.Hand).remove(card); pod.game.getStackZone().add(card); return card;
    }
    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("choice-context-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Spell choice context copying"));
        game.setNoGUIUser(); game.setAge(GameStage.Play); Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 4; i++) addCardToZone("Swamp", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2); game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        return new Pod(game, actor, game.getPlayers().get(1));
    }
    private void settle(Game game) {
        for (int i = 0; i < 40; i++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return; game.getStack().resolveStack();
        }
        throw new AssertionError("Choice context stack did not settle");
    }
    private record Pod(Game game, Player actor, Player enemy) { }
}
