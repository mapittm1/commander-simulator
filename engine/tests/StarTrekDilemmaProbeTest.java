package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameActionUtil;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.PlaySpellAbility;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Exercise actual modal casting, payment, and the Star Trek dilemma trigger. */
public class StarTrekDilemmaProbeTest extends AITest {
    private record Setup(Game game, Player actor, Card adjudicator) {
        void collectTriggers() {
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }
    }

    private Setup setup() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Star Trek Dilemma"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public List<AbilitySub> chooseModeForAbility(SpellAbility spell, List<AbilitySub> options,
                    int min, int num, boolean allowRepeat) {
                AbilitySub mode = options.stream().filter(option -> option.getApi() == ApiType.PumpAll || option.getApi() == ApiType.PutCounter)
                        .findFirst().orElseThrow();
                return new ArrayList<>(List.of(mode));
            }
        });
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        Card adjudicator = addCardToZone("Melkotian Adjudicator", actor, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", game.getPlayers().get(1), ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        return new Setup(game, actor, adjudicator);
    }

    private SpellAbility charm(Setup setup) {
        Card card = addCardToZone("Boros Charm", setup.actor, ZoneType.Hand);
        SpellAbility ability = card.getFirstSpellAbility();
        ability.setActivatingPlayer(setup.actor);
        return ability;
    }

    @Test
    public void failedModalCastLeavesDilemmaHistoryAndStackEmpty() {
        Setup setup = setup();
        addCardToZone("Mountain", setup.actor, ZoneType.Battlefield);
        SpellAbility ability = charm(setup);

        assertFalse(ComputerUtil.handlePlayingSpellAbility(setup.actor, ability, null));
        assertTrue(setup.actor.getCardsIn(ZoneType.Hand).contains(ability.getHostCard()));
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 0);
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 0);
    }

    @Test
    public void successfulModalCastCountsOnceAndFiresBeforeTheSpell() {
        Setup setup = setup();
        addCardToZone("Mountain", setup.actor, ZoneType.Battlefield);
        addCardToZone("Plains", setup.actor, ZoneType.Battlefield);
        SpellAbility ability = charm(setup);

        assertTrue(ComputerUtil.handlePlayingSpellAbility(setup.actor, ability, null));
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 2);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == setup.adjudicator);
        setup.game.getStack().resolveStack();
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void failedCastPreservesAPreviousSuccessfulDilemma() {
        Setup setup = setup();
        addCardToZone("Mountain", setup.actor, ZoneType.Battlefield);
        addCardToZone("Plains", setup.actor, ZoneType.Battlefield);
        SpellAbility paid = charm(setup);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(setup.actor, paid, null));
        setup.collectTriggers();
        setup.game.getStack().resolveStack();
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 1);

        SpellAbility unpaid = charm(setup);
        assertFalse(ComputerUtil.handlePlayingSpellAbility(setup.actor, unpaid, null));
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 1);
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void entwineChoosesAllModesAndFacesOneDilemma() {
        Setup setup = setup();
        for (int index = 0; index < 9; index++) {
            addCardToZone("Forest", setup.actor, ZoneType.Battlefield);
        }
        Card tooth = addCardToZone("Tooth and Nail", setup.actor, ZoneType.Hand);
        SpellAbility base = tooth.getFirstSpellAbility();
        base.setActivatingPlayer(setup.actor);
        var optionalCosts = GameActionUtil.getOptionalCostValues(base);
        assertEquals(optionalCosts.size(), 1);
        SpellAbility entwined = GameActionUtil.addOptionalCosts(base, optionalCosts);
        assertTrue(entwined.isEntwine());

        assertTrue(ComputerUtil.handlePlayingSpellAbility(setup.actor, entwined, null));
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 2);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == setup.adjudicator);
        setup.game.getStack().resolveStack();
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 1);
        assertTrue(setup.game.getStack().peekAbility().getSubAbility().getSubAbility() != null);
    }

    @Test
    public void sharedPlayEntryCountsAFreeModalCast() {
        Setup setup = setup();
        SpellAbility spell = charm(setup);
        PlaySpellAbility.playSaWithoutPayingManaCost(setup.actor.getController(), spell, true);
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 2);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == setup.adjudicator);
    }

    @Test
    public void pendingDilemmaSettlesOnceAcrossAbilityCopies() {
        Setup setup = setup();
        SpellAbility original = charm(setup);
        assertTrue(CharmEffect.makeChoices(original, true));
        SpellAbility stagedCopy = original.copy(original.getHostCard(), false);
        stagedCopy.commitDilemma();
        original.rollbackDilemma();
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 1);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == setup.adjudicator);
    }

    @Test
    public void modalEnterTriggerFacesOneDilemma() {
        Setup setup = setup();
        Card knight = addCardToZone("Knight of Autumn", setup.actor, ZoneType.Hand);
        Card permanent = setup.game.getAction().moveTo(ZoneType.Battlefield, knight, null, AbilityKey.newMap());
        setup.collectTriggers();
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 2);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == setup.adjudicator);
        setup.game.getStack().resolveStack();
        setup.game.getStack().resolveStack();
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(permanent.getCounters(CounterEnumType.P1P1), 2);
    }

    @Test
    public void aiPlayEffectFacesOneDilemmaOnFreeModalCast() {
        Setup setup = setup();
        SpellAbility spell = charm(setup).copyWithNoManaCost(setup.actor);
        spell.putParam("WithoutManaCost", "True");
        spell.getPayCosts().setMandatory(true);
        assertTrue(setup.actor.getController().playSaFromPlayEffect(spell));
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 2);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == setup.adjudicator);
    }

    @Test
    public void cascadeResolvesATargetedModalSpellAndItsDilemma() {
        Setup setup = setup();
        Player opponent = setup.game.getPlayers().get(1);
        opponent.setLife(4, null);
        Card charm = addCardToZone("Boros Charm", setup.actor, ZoneType.Library);
        List<Boolean> castResults = new ArrayList<>();
        addCardToZone("Island", setup.actor, ZoneType.Battlefield);
        addCardToZone("Plains", setup.actor, ZoneType.Battlefield);
        addCardToZone("Plains", setup.actor, ZoneType.Battlefield);
        setup.actor.dangerouslySetController(new PlayerControllerAi(setup.game, setup.actor, setup.actor.getLobbyPlayer()) {
            @Override
            public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> options,
                    DelayedReveal delayed, SpellAbility ability, String title, boolean optional,
                    Player targetedPlayer, Map<String, Object> params) {
                if (ability.getApi() == ApiType.Play) {
                    return options.stream().filter(option -> option.equals(charm)).findFirst().orElseThrow();
                }
                return super.chooseSingleEntityForEffect(options, delayed, ability, title, optional, targetedPlayer, params);
            }

            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode, String message,
                    List<String> options, Card shown, Map<String, Object> params) {
                if (ability.getApi() == ApiType.Play && shown == charm) {
                    return true;
                }
                return super.confirmAction(ability, mode, message, options, shown, params);
            }

            @Override
            public boolean playSaFromPlayEffect(SpellAbility spell) {
                // The test controller accepted the optional cast and supplied its legal mode and target.
                // Use Forge's mandatory branch to exercise rules execution without a second strategic veto.
                if (spell.getHostCard().equals(charm)) {
                    spell.getPayCosts().setMandatory(true);
                }
                boolean cast = super.playSaFromPlayEffect(spell);
                castResults.add(cast);
                return cast;
            }

            @Override
            public List<AbilitySub> chooseModeForAbility(SpellAbility spell, List<AbilitySub> options,
                    int min, int num, boolean allowRepeat) {
                AbilitySub damage = options.stream().filter(mode -> mode.getApi() == ApiType.DealDamage)
                        .findFirst().orElseThrow();
                damage.resetTargets();
                damage.getTargets().add(opponent);
                return new ArrayList<>(List.of(damage));
            }
        });
        Card plea = addCardToZone("Ardent Plea", setup.actor, ZoneType.Hand);
        SpellAbility spell = plea.getFirstSpellAbility();
        spell.setActivatingPlayer(setup.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(setup.actor, spell, null));
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 2);
        setup.game.getStack().resolveStack();
        setup.collectTriggers();
        assertEquals(castResults, List.of(true), "cascade cast result, card zone " + charm.getZone());
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == setup.adjudicator);
        setup.game.getStack().resolveStack();
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(setup.game.getStack().peekAbility().getHostCard().getName(), "Boros Charm");
        setup.game.getStack().resolveStack();
        assertEquals(opponent.getLife(), 0);
    }

    @Test
    public void sharedCastCancellationAtTargetSelectionRestoresDilemma() {
        Setup setup = setup();
        addCardToZone("Mountain", setup.actor, ZoneType.Battlefield);
        addCardToZone("Plains", setup.actor, ZoneType.Battlefield);
        List<ApiType> canceled = new ArrayList<>();
        PlayerControllerAi controller = new PlayerControllerAi(setup.game, setup.actor, setup.actor.getLobbyPlayer()) {
            @Override
            public List<AbilitySub> chooseModeForAbility(SpellAbility spell, List<AbilitySub> options,
                    int min, int num, boolean allowRepeat) {
                AbilitySub damage = options.stream().filter(mode -> mode.getApi() == ApiType.DealDamage)
                        .findFirst().orElseThrow();
                return new ArrayList<>(List.of(damage));
            }

            @Override
            public boolean chooseTargetsFor(SpellAbility ability) {
                canceled.add(ability.getApi());
                return false;
            }
        };
        setup.actor.dangerouslySetController(controller);
        SpellAbility spell = charm(setup);
        assertFalse(PlaySpellAbility.playSpellAbility(controller, setup.actor, spell));
        assertEquals(canceled, List.of(ApiType.DealDamage));
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 0);
        assertTrue(setup.actor.getCardsIn(ZoneType.Hand).contains(spell.getHostCard()));
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 0);
    }

    @Test
    public void randomModalTriggerAlsoTriggersChiraExactlyOnce() {
        Setup setup = setup();
        Card chira = addCardToZone("Chira, All In", setup.actor, ZoneType.Battlefield);
        Card merriment = addCardToZone("Outlaws' Merriment", setup.actor, ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true);
        setup.game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, setup.actor, 1);
        setup.game.getTriggerHandler().resetActiveTriggers();
        setup.game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(setup.actor), false);
        setup.collectTriggers();
        setup.collectTriggers();

        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        assertEquals(setup.game.getStack().size(), 3);
        setup.game.getStack().resolveStack();
        setup.game.getStack().resolveStack();
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == merriment);
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(chira.getCounters(CounterEnumType.P1P1), 1);
        List<Card> treasures = setup.actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isToken() && card.getType().hasSubtype("Treasure")).toList();
        assertEquals(treasures.size(), 1);
        assertTrue(treasures.get(0).isTapped());
    }

    @Test
    public void preselectedModeKeepsItsTargetAndFacesOneDilemma() {
        Setup setup = setup();
        addCardToZone("Mountain", setup.actor, ZoneType.Battlefield);
        addCardToZone("Plains", setup.actor, ZoneType.Battlefield);
        Player opponent = setup.game.getPlayers().get(1);
        int life = opponent.getLife();
        SpellAbility spell = charm(setup);
        AbilitySub damage = spell.getAdditionalAbilityList("Choices").stream()
                .filter(mode -> mode.getApi() == ApiType.DealDamage).findFirst().orElseThrow();
        CharmEffect.chainAbilities(spell, new ArrayList<>(List.of(damage)));
        spell.getSubAbility().getTargets().add(opponent);

        assertTrue(ComputerUtil.playStack(spell, setup.actor, setup.game));
        assertEquals(setup.actor.getDilemmasFacedThisTurn(), 1);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 2);
        setup.game.getStack().resolveStack();
        assertEquals(setup.adjudicator.getCounters(CounterEnumType.P1P1), 1);
        setup.game.getStack().resolveStack();
        assertEquals(opponent.getLife(), life - 4);
    }
}
