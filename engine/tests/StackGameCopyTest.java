package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static org.testng.Assert.*;

/** Paid stack state must be restored, not cast again from printed abilities. */
public class StackGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Stack fixture"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        for (Player player : game.getPlayers()) {
            addCardToZone("Forest", player, ZoneType.Library);
        }
        return game;
    }

    private SpellAbility spell(Game game, String name, Player owner) {
        SpellAbility ability = addCardToZone(name, owner, ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(owner);
        return ability;
    }

    private void pay(Game game, SpellAbility ability, int mana) {
        assertTrue(ability.canPlay(), ability.toString());
        assertTrue(ComputerUtil.playStack(ability, ability.getActivatingPlayer(), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
    }

    private Game snapshot(Game game) { return new GameCopier(game).withStack(true).makeCopy(); }

    @Test
    public void paidManaAndSourceReferencesSurviveCopy() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Mountain", owner);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(game.getPlayers().get(1));
        pay(game, bolt, 1);
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 1);
        SpellAbility copied = copy.getStack().peekAbility();
        assertFalse(copied.getHostCard().isLKI());
        assertEquals(copied.getPayingMana().size(), 1);
        assertSame(copied.getActivatingPlayer().getGame(), copy);
        assertSame(copied.getPayingMana().get(0).getSourceCard().getGame(), copy);
        assertSame(copied.getPayingMana().get(0).getPlayer().getGame(), copy);
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayers().get(1).getLife(), 37);
        assertEquals(game.getPlayers().get(1).getLife(), 40);
        assertEquals(game.getStack().size(), 1);
    }

    @Test
    public void snapshotDoesNotCountAnAlreadyCastSpellAgain() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Mountain", owner);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(game.getPlayers().get(1));
        pay(game, bolt, 1);
        assertEquals(owner.getSpellsCastThisTurn(), 1);
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 1);
        assertEquals(copy.getPlayer(owner.getId()).getSpellsCastThisTurn(), 1);
    }

    @Test
    public void paidXSurvivesCopyAndResolution() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Mountain", 4, owner);
        SpellAbility fireball = spell(game, "Fireball", owner);
        fireball.setXManaCostPaid(3);
        fireball.getTargets().add(game.getPlayers().get(1));
        pay(game, fireball, 4);
        assertEquals(game.getStack().peekAbility().getXManaCostPaid().intValue(), 3);
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 1);
        assertEquals(copy.getStack().peekAbility().getXManaCostPaid().intValue(), 3);
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayers().get(1).getLife(), 37);
        assertEquals(game.getPlayers().get(1).getLife(), 40);
    }

    @Test
    public void paidKickerSurvivesCopy() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Mountain", 5, owner);
        SpellAbility burst = spell(game, "Burst Lightning", owner);
        burst = GameActionUtil.addOptionalCosts(burst, GameActionUtil.getOptionalCostValues(burst).stream()
                .filter(cost -> cost.getType() == OptionalCost.Kicker1).collect(Collectors.toList()));
        burst.getTargets().add(game.getPlayers().get(1));
        pay(game, burst, 5);
        assertTrue(game.getStack().peekAbility().isKicked());
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 1);
        assertTrue(copy.getStack().peekAbility().isKicked());
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayers().get(1).getLife(), 36);
    }

    @Test
    public void counterspellTargetsCopiedPaidSpell() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCard("Mountain", owner);
        addCards("Island", 2, opponent);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(opponent);
        pay(game, bolt, 1);
        SpellAbility counter = spell(game, "Counterspell", opponent);
        counter.getTargets().add(game.getStack().peekAbility());
        pay(game, counter, 2);
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 2);
        SpellAbility target = copy.getStack().peekAbility().getTargets().getFirstTargetedSpell();
        assertNotNull(target);
        assertSame(target.getHostCard().getGame(), copy);
        copy.getStack().resolveStack();
        assertTrue(copy.getStack().isEmpty());
        assertEquals(copy.getPlayer(opponent.getId()).getLife(), 40);
        assertEquals(copy.getCardsIn(ZoneType.Graveyard).size(), 2);
        assertEquals(game.getStack().size(), 2);
    }

    @Test
    public void activatedAbilityRemainsOnCopiedStack() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card pinger = addCard("Prodigal Pyromancer", owner);
        pinger.setSickness(false);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = pinger.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.DealDamage)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        ability.getTargets().add(game.getPlayers().get(1));
        pay(game, ability, 0);
        assertTrue(pinger.isTapped());
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 1);
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayers().get(1).getLife(), 39);
        assertEquals(game.getPlayers().get(1).getLife(), 40);
    }

    @Test
    public void actualEnterTriggerRemainsOnCopiedStack() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Soul Warden", owner);
        addCards("Forest", 2, owner);
        game.getAction().checkStateEffects(true);
        SpellAbility bear = spell(game, "Grizzly Bears", owner);
        pay(game, bear, 2);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 1);
        assertTrue(game.getStack().peekAbility().isTrigger());
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 1);
        assertTrue(copy.getStack().peekAbility().isTrigger());
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayer(owner.getId()).getLife(), 41);
        assertEquals(owner.getLife(), 40);
    }

    @Test
    public void selectedModalChainRemainsOnCopiedStack() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        game.getPlayers().get(1).setLife(10, null);
        addCard("Plains", owner);
        addCard("Mountain", owner);
        SpellAbility charm = spell(game, "Boros Charm", owner);
        forge.game.ability.effects.CharmEffect.chainAbilities(charm,
                charm.getAdditionalAbilityList("Choices").stream()
                        .filter(choice -> choice.getApi() == ApiType.DealDamage)
                        .collect(Collectors.toList()));
        assertNotNull(charm.getSubAbility());
        charm.getSubAbility().getTargets().add(game.getPlayers().get(1));
        pay(game, charm, 2);
        SpellAbility selected = game.getStack().peekAbility().getSubAbility();
        assertNotNull(selected);
        assertEquals(selected.getApi(), ApiType.DealDamage);
        Player target = selected.getTargets().getFirstTargetedPlayer();
        assertNotNull(target);
        int before = target.getLife();
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 1);
        assertNotNull(copy.getStack().peekAbility().getSubAbility());
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayer(target.getId()).getLife(), before - 4);
        assertEquals(target.getLife(), before);
    }

    @org.testng.annotations.DataProvider(name = "buyback")
    public Object[][] buyback() { return new Object[][] {{false}, {true}}; }

    @Test(dataProvider = "buyback")
    public void paidBuybackKeepsResolutionDestination(boolean buyback) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card land = addCard("Forest", game.getPlayers().get(1));
        addCards("Island", buyback ? 6 : 3, owner);
        SpellAbility capsize = spell(game, "Capsize", owner);
        if (buyback) capsize = GameActionUtil.addOptionalCosts(capsize,
                GameActionUtil.getOptionalCostValues(capsize).stream()
                        .filter(value -> value.getType() == OptionalCost.Buyback).collect(Collectors.toList()));
        capsize.getTargets().add(land);
        pay(game, capsize, buyback ? 6 : 3);
        Game copy = snapshot(game);
        copy.getStack().resolveStack();
        assertTrue(copy.getPlayer(land.getOwner().getId()).getCardsIn(ZoneType.Hand).stream()
                .anyMatch(card -> card.getId() == land.getId()));
        assertTrue(copy.getPlayer(owner.getId()).getCardsIn(buyback ? ZoneType.Hand : ZoneType.Graveyard).stream()
                .anyMatch(card -> card.getName().equals("Capsize")));
        assertEquals(game.getStack().size(), 1);
        assertTrue(land.isInPlay());
    }

    @org.testng.annotations.DataProvider(name = "sunburst")
    public Object[][] sunburst() { return new Object[][] {{"Wastes", 0}, {"Forest", 1}, {"Island", 2}}; }

    @Test(dataProvider = "sunburst")
    public void paidColorsDriveSunburstAfterSnapshot(String firstLand, int colors) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard(firstLand, owner);
        addCard(colors == 2 ? "Forest" : firstLand, owner);
        SpellAbility prism = spell(game, "Pentad Prism", owner);
        pay(game, prism, 2);
        Game copy = snapshot(game);
        copy.getStack().resolveStack();
        copy.getAction().checkStateEffects(true);
        Card resolved = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Pentad Prism")).findFirst().orElseThrow();
        assertEquals(resolved.getCounters(forge.game.card.CounterEnumType.CHARGE), colors);
        assertEquals(game.getStack().size(), 1);
    }

    @Test
    public void sacrificedPowerUsesPaidHistoricalObject() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Glorious Anthem", owner);
        Card victim = addCard("Colossal Dreadmaw", owner);
        addCards("Mountain", 2, owner);
        game.getAction().checkStateEffects(true);
        assertEquals(victim.getNetPower(), 7);
        SpellAbility fling = spell(game, "Fling", owner);
        fling.getTargets().add(game.getPlayers().get(1));
        pay(game, fling, 2);
        assertEquals(game.getStack().peekAbility().getPaidList("Sacrificed", true).get(0).getNetPower(), 7);
        Game copy = snapshot(game);
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayers().get(1).getLife(), 33);
        assertEquals(game.getPlayers().get(1).getLife(), 40);
    }

    @org.testng.annotations.DataProvider(name = "targetChanges")
    public Object[][] targetChanges() { return new Object[][] {{false}, {true}}; }

    @Test(dataProvider = "targetChanges")
    public void invalidatedTargetIsRetainedAndFizzles(boolean returnToBattlefield) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", game.getPlayers().get(1));
        addCard("Mountain", owner);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(bear);
        pay(game, bolt, 1);
        Card departed = game.getAction().moveToGraveyard(bear, null);
        if (returnToBattlefield) {
            game.getAction().moveToPlay(departed, null, null);
        }
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 1);
        copy.getStack().resolveStack();
        copy.getAction().checkStateEffects(true);
        assertTrue(copy.getStack().isEmpty());
        assertEquals(game.getStack().peekAbility().getTargets().size(), 1);
        if (returnToBattlefield) assertTrue(copy.getCardsIn(ZoneType.Battlefield).stream()
                .anyMatch(card -> card.getId() == bear.getId()));
    }

    @Test
    public void activatedAbilitySurvivesItsSourceDying() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card pinger = addCard("Prodigal Pyromancer", owner);
        pinger.setSickness(false);
        addCard("Mountain", opponent);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = pinger.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.DealDamage).findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        ability.getTargets().add(opponent);
        pay(game, ability, 0);
        SpellAbility bolt = spell(game, "Lightning Bolt", opponent);
        bolt.getTargets().add(pinger);
        pay(game, bolt, 1);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        assertEquals(game.getStack().size(), 1);
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 1);
        assertSame(copy.getStack().peekAbility().getHostCard().getGame(), copy);
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayer(opponent.getId()).getLife(), 39);
        assertEquals(opponent.getLife(), 40);
    }

    @Test
    public void copyingDoesNotReplayYoungPyromancerCastTrigger() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Young Pyromancer", owner);
        addCard("Mountain", owner);
        game.getAction().checkStateEffects(true);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(game.getPlayers().get(1));
        pay(game, bolt, 1);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 2);
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 2);
        forge.ai.simulation.GameSimulator.resolveStack(copy, copy.getPlayer(owner.getId()));
        assertEquals(copy.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 1L);
        assertEquals(game.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 0L);
    }

    @org.testng.annotations.DataProvider(name = "priorCasters")
    public Object[][] priorCasters() { return new Object[][] {{-1}, {0}, {1}}; }

    @Test(dataProvider = "priorCasters")
    public void stormCountsPriorCastsFromEitherPlayerWithoutPendingStack(int priorCaster) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Forest", 3, owner);
        if (priorCaster >= 0) {
            Player caster = game.getPlayers().get(priorCaster);
            addCard("Island", caster);
            for (int i = 0; i < 4; i++) {
                addCardToZone("Forest", caster, ZoneType.Library);
            }
            SpellAbility opt = spell(game, "Opt", caster);
            pay(game, opt, 1);
            game.getStack().resolveStack();
            game.getAction().checkStateEffects(true);
            assertEquals(caster.getSpellsCastThisTurn(), 1);
        }
        SpellAbility weather = spell(game, "Weather the Storm", owner);
        Game copy = new GameCopier(game).withStack(false).makeCopy();
        Player mapped = copy.getPlayer(owner.getId());
        Card copiedWeather = copy.getCardsIn(ZoneType.Hand).stream()
                .filter(card -> card.getName().equals("Weather the Storm")).findFirst().orElseThrow();
        SpellAbility ability = copiedWeather.getFirstSpellAbility();
        ability.setActivatingPlayer(mapped);
        pay(copy, ability, 2);
        copy.getAction().checkStateEffects(true);
        copy.getTriggerHandler().runWaitingTriggers();
        copy.getStack().addAllTriggeredAbilitiesToStack();
        forge.ai.simulation.GameSimulator.resolveStack(copy, mapped);
        assertEquals(mapped.getLife(), priorCaster >= 0 ? 46 : 43);
        assertEquals(owner.getLife(), 40);
        assertEquals(weather.getHostCard().getZone().getZoneType(), ZoneType.Hand);
    }

    @Test
    public void floatingManaRetainsWorldAndPaysWithoutChangingOriginal() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card omnath = addCard("Omnath, Locus of Mana", owner);
        for (Card forest : addCards("Forest", 4, owner)) {
            SpellAbility mana = forest.getManaAbilities().get(0);
            mana.setActivatingPlayer(owner);
            assertTrue(ComputerUtil.playStack(mana, owner, game));
        }
        game.getAction().checkStateEffects(true);
        assertEquals(owner.getManaPool().totalMana(), 4);
        assertEquals(omnath.getNetPower(), 5);
        SpellAbility bear = spell(game, "Grizzly Bears", owner);
        Game copy = new GameCopier(game).withStack(false).makeCopy();
        Player mapped = copy.getPlayer(owner.getId());
        assertEquals(mapped.getManaPool().totalMana(), 4);
        for (forge.game.mana.Mana mana : mapped.getManaPool()) {
            assertSame(mana.getSourceCard().getGame(), copy);
            assertSame(mana.getPlayer().getGame(), copy);
            assertSame(mana.getManaAbility().getSourceCard().getGame(), copy);
        }
        Card copiedOmnath = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getId() == omnath.getId()).findFirst().orElseThrow();
        assertEquals(copiedOmnath.getNetPower(), 5);
        SpellAbility ability = copy.getCardsIn(ZoneType.Hand).stream()
                .filter(card -> card.getId() == bear.getHostCard().getId()).findFirst().orElseThrow().getFirstSpellAbility();
        ability.setActivatingPlayer(mapped);
        pay(copy, ability, 2);
        copy.getAction().checkStateEffects(true);
        assertEquals(mapped.getManaPool().totalMana(), 2);
        assertEquals(owner.getManaPool().totalMana(), 4);
        assertEquals(copiedOmnath.getNetPower(), 3);
        assertEquals(omnath.getNetPower(), 5);
    }

    @Test
    public void stormHistorySurvivesCopyWithNoVisibleEntries() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Mountain", owner);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(game.getPlayers().get(1));
        pay(game, bolt, 1);
        game.getStack().resolveStack();
        Game copy = new GameCopier(game).withStack(false).makeCopy();
        assertTrue(copy.getStack().isEmpty());
        assertEquals(copy.getPlayer(owner.getId()).getSpellsCastThisTurn(), 1);
    }

    @Test
    public void copiedMutablePaymentAndTargetContainersAreIndependent() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Mountain", 5, owner);
        SpellAbility burst = spell(game, "Burst Lightning", owner);
        burst = GameActionUtil.addOptionalCosts(burst, GameActionUtil.getOptionalCostValues(burst).stream()
                .filter(cost -> cost.getType() == OptionalCost.Kicker1).collect(Collectors.toList()));
        burst.getTargets().add(game.getPlayers().get(1));
        pay(game, burst, 5);
        SpellAbility original = game.getStack().peekAbility();
        SpellAbility copied = snapshot(game).getStack().peekAbility();
        java.util.Iterator<OptionalCost> costs = copied.getOptionalCosts().iterator();
        costs.next();
        costs.remove();
        copied.getPayingMana().clear();
        copied.getTargets().clear();
        assertTrue(original.isKicked());
        assertEquals(original.getPayingMana().size(), 5);
        assertEquals(original.getTargets().size(), 1);
    }

    @Test
    public void copierReuseDoesNotReuseOldAbilityGraph() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Mountain", owner);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(game.getPlayers().get(1));
        pay(game, bolt, 1);
        GameCopier copier = new GameCopier(game).withStack(true);
        Game first = copier.makeCopy();
        game.getStack().resolveStack();
        Game second = copier.makeCopy();
        assertTrue(second.getStack().isEmpty());
        assertEquals(second.getPlayers().get(1).getLife(), 37);
        assertEquals(first.getPlayers().get(1).getLife(), 40);
        assertEquals(first.getStack().size(), 1);
        assertEquals(first.getStack().peekAbility().getTargets().size(), 1);
    }

    @Test
    public void opaqueMutableEventPayloadFailsInsteadOfLeakingAcrossGames() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Mountain", owner);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(game.getPlayers().get(1));
        pay(game, bolt, 1);
        game.getStack().peekAbility().setTriggeringObject(forge.game.ability.AbilityKey.Amount, new StringBuilder("mutable"));
        expectThrows(IllegalStateException.class, () -> snapshot(game));
    }

    @Test
    public void rememberedStackAbilityUsesCanonicalSnapshotAbility() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", owner);
        addCard("Mountain", owner);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(game.getPlayers().get(1));
        pay(game, bolt, 1);
        bear.addRemembered(game.getStack().peekAbility());
        Game copy = snapshot(game);
        Card mapped = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getId() == bear.getId()).findFirst().orElseThrow();
        assertSame(mapped.getRemembered().iterator().next(), copy.getStack().peekAbility());
        assertSame(bear.getRemembered().iterator().next(), game.getStack().peekAbility());
    }

    @Test
    public void paidXCreatureUsesDeclaredXAfterCopy() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Forest", 3, owner);
        SpellAbility serpent = spell(game, "Stonecoil Serpent", owner);
        serpent.setXManaCostPaid(3);
        pay(game, serpent, 3);
        Game copy = snapshot(game);
        copy.getStack().resolveStack();
        copy.getAction().checkStateEffects(true);
        Card resolved = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Stonecoil Serpent")).findFirst().orElseThrow();
        assertEquals(resolved.getCounters(forge.game.card.CounterEnumType.P1P1), 3);
        assertEquals(resolved.getNetPower(), 3);
        assertEquals(game.getStack().size(), 1);
    }

    @Test
    public void dividedTargetAllocationsSurviveCopy() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player first = game.getPlayers().get(1);
        Player second = game.getPlayers().get(2);
        addCards("Mountain", 3, owner);
        SpellAbility arc = spell(game, "Arc Lightning", owner);
        arc.clearTargets();
        arc.getTargets().add(first);
        arc.getTargets().add(second);
        arc.getTargets().addDividedAllocation(first, 2);
        arc.getTargets().addDividedAllocation(second, 1);
        assertEquals(arc.getDividedValue().intValue(), 3);
        pay(game, arc, 3);
        Game copy = snapshot(game);
        assertEquals(copy.getStack().peekAbility().getTargets().getTotalDividedValue(), 3);
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayer(first.getId()).getLife(), 38);
        assertEquals(copy.getPlayer(second.getId()).getLife(), 39);
        assertEquals(first.getLife(), 40);
    }

    @Test
    public void lazyPipMetadataIsFrozenAtSnapshotTime() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Mountain", owner);
        SpellAbility bolt = spell(game, "Lightning Bolt", owner);
        bolt.getTargets().add(game.getPlayers().get(1));
        pay(game, bolt, 1);
        SpellAbility original = game.getStack().peekAbility();
        original.getPipsToReduce().add("G");
        SpellAbility copied = snapshot(game).getStack().peekAbility();
        original.getPipsToReduce().add("U");
        assertEquals(copied.getPipsToReduce(), java.util.List.of("G"));
        copied.getPipsToReduce().clear();
        assertEquals(original.getPipsToReduce(), java.util.List.of("G", "U"));
    }
}
