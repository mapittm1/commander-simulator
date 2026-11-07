package forge.ai;

import com.google.common.collect.HashMultiset;
import com.google.common.eventbus.Subscribe;
import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.event.GameEventPlayerDamaged;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/** Fixed mana-source preconditions; native payment, not source casting or AI strategy proof. */
public class ReflectedManaCoverageTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("mana" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Reflected mana"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int count = 0; count < 8; count++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private void paid(Game game, Card source, boolean expected) {
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone("Opt", actor, ZoneType.Hand);
        SpellAbility action = spell.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        assertEquals(ComputerUtil.playStack(action, actor, game), expected);
        if (expected) {
            SpellAbility pending = game.getStack().peekAbility();
            assertEquals(pending.getPayingMana().size(), 1);
            // Native paid mana retains a last-known-information source, not object identity.
            assertEquals(pending.getPayingMana().get(0).getSourceCard().getId(), source.getId());
            assertSame(pending.getPayingMana().get(0).getSourceCard().getGame(), game);
            assertTrue(source.isTapped());
        } else {
            assertTrue(game.getStack().isEmpty());
            assertEquals(spell.getZone().getZoneType(), ZoneType.Hand);
            assertFalse(source.isTapped());
        }
    }

    private void enemyLand(String sourceName, String land, boolean expected) {
        Game game = pod();
        Card source = addCardToZone(sourceName, game.getPlayers().get(0), ZoneType.Battlefield);
        if (land != null) addCardToZone(land, game.getPlayers().get(1), ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        paid(game, source, expected);
    }

    @Test
    public void exoticOrchardPaysFromOpponentIsland() {
        enemyLand("Exotic Orchard", "Island", true);
    }
    @Test
    public void exoticOrchardCannotInventBlueFromWastes() {
        enemyLand("Exotic Orchard", "Wastes", false);
    }
    @Test
    public void exoticOrchardCannotInventBlueWithoutOpponentLand() {
        enemyLand("Exotic Orchard", null, false);
    }
    @Test
    public void fellwarStonePaysFromOpponentIsland() {
        enemyLand("Fellwar Stone", "Island", true);
    }
    @Test
    public void fellwarStoneCannotInventBlueFromWastes() {
        enemyLand("Fellwar Stone", "Wastes", false);
    }

    private void ownLand(String sourceName, String land, boolean expected) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = addCardToZone(sourceName, actor, ZoneType.Battlefield);
        source.setSickness(false);
        if (land != null) addCardToZone(land, actor, ZoneType.Battlefield).setTapped(true);
        game.getAction().checkStateEffects(true);
        paid(game, source, expected);
    }

    @Test
    public void reflectingPoolUsesTappedIslandCapability() {
        ownLand("Reflecting Pool", "Island", true);
    }
    @Test
    public void reflectingPoolAloneCannotInventBlue() {
        ownLand("Reflecting Pool", null, false);
    }
    @Test
    public void nagaVitalistUsesTappedIslandCapability() {
        ownLand("Naga Vitalist", "Island", true);
    }
    @Test
    public void nagaVitalistCannotInventBlueFromWastes() {
        ownLand("Naga Vitalist", "Wastes", false);
    }
    @Test
    public void harvesterDruidUsesTappedIslandCapability() {
        ownLand("Harvester Druid", "Island", true);
    }
    @Test
    public void starCompassUsesTappedBasicIsland() {
        ownLand("Star Compass", "Island", true);
    }
    @Test
    public void starCompassDoesNotTreatNonbasicAsBasic() {
        ownLand("Star Compass", "Tropical Island", false);
    }

    private void amber(String permanent, boolean expected) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = addCardToZone("Mox Amber", actor, ZoneType.Battlefield);
        if (permanent != null) {
            Card enabling = addCardToZone(permanent, actor, ZoneType.Battlefield);
            if (enabling.isPlaneswalker()) {
                // Fixed prior loyalty; direct battlefield fixtures do not run ETB placement.
                var counters = HashMultiset.create(enabling.getCounters());
                counters.setCount(CounterEnumType.LOYALTY, 3);
                enabling.setCounters(counters);
            }
        }
        game.getAction().checkStateEffects(true);
        paid(game, source, expected);
    }

    @Test
    public void moxAmberUsesLegendaryPlaneswalkerColor() {
        amber("Jace Beleren", true);
    }
    @Test
    public void moxAmberUsesLegendaryCreatureColor() {
        amber("Baral, Chief of Compliance", true);
    }
    @Test
    public void moxAmberUsesColorNotCommanderIdentity() {
        amber("Kenrith, the Returned King", false);
    }
    @Test
    public void moxAmberDoesNotUseNonlegendaryCreatureColor() {
        amber("Merfolk Looter", false);
    }
    @Test
    public void moxAmberCannotInventManaOnEmptyBoard() {
        amber(null, false);
    }

    private void multiplier(String permanent) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card land = addCardToZone("Mountain", actor, ZoneType.Battlefield);
        addCardToZone(permanent, actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        Card spell = addCardToZone("Myr Retriever", actor, ZoneType.Hand);
        SpellAbility action = spell.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.playStack(action, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        assertTrue(land.isTapped());
    }

    @Test
    public void manaFlareTriggerPaysAdditionalMana() {
        multiplier("Mana Flare");
    }
    @Test
    public void heartbeatTriggerPaysAdditionalMana() {
        multiplier("Heartbeat of Spring");
    }
    @Test
    public void dictateTriggerPaysAdditionalMana() {
        multiplier("Dictate of Karametra");
    }
    @Test
    public void zhurTaaTriggerPaysAdditionalMana() {
        multiplier("Zhur-Taa Ancient");
    }
    @Test
    public void vorinclexTriggerPaysAdditionalMana() {
        multiplier("Vorinclex, Voice of Hunger");
    }

    private boolean cast(Game game, String name, Card target) {
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone(name, actor, ZoneType.Hand);
        SpellAbility action = spell.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        if (target != null) action.getTargets().add(target);
        boolean paid = ComputerUtil.playStack(action, actor, game);
        if (!paid) {
            assertTrue(game.getStack().isEmpty());
            assertEquals(spell.getZone().getZoneType(), ZoneType.Hand);
        }
        return paid;
    }

    @Test
    public void reflectingPoolPaysColorlessFromTappedWastesCapability() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card pool = addCardToZone("Reflecting Pool", actor, ZoneType.Battlefield);
        addCardToZone("Wastes", actor, ZoneType.Battlefield).setTapped(true);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        assertTrue(cast(game, "Spatial Contortion", bear));
        var mana = game.getStack().peekAbility().getPayingMana();
        assertEquals(mana.size(), 2);
        assertTrue(mana.stream().anyMatch(value -> value.getColor() == (byte) ManaAtom.COLORLESS
                && value.getSourceCard().getId() == pool.getId()));
    }

    @Test
    public void exoticOrchardCannotPayColorlessFromOpponentWastes() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card orchard = addCardToZone("Exotic Orchard", actor, ZoneType.Battlefield);
        addCardToZone("Wastes", game.getPlayers().get(1), ZoneType.Battlefield);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        assertFalse(cast(game, "Spatial Contortion", bear));
        assertFalse(orchard.isTapped());
    }

    @Test
    public void twoReflectingPoolsCannotInventBlueInAClosedCycle() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card pool = addCardToZone("Reflecting Pool", actor, ZoneType.Battlefield);
        Card other = addCardToZone("Reflecting Pool", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        paid(game, pool, false);
        assertFalse(other.isTapped());
    }

    private Card incubationDruid(Game game, boolean boosted) {
        Player actor = game.getPlayers().get(0);
        Card druid = addCardToZone("Incubation Druid", actor, ZoneType.Battlefield);
        druid.setSickness(false);
        addCardToZone("Island", actor, ZoneType.Battlefield).setTapped(true);
        if (boosted) {
            // Fixed prior counter, not an adapt activation witness.
            var counters = HashMultiset.create(druid.getCounters());
            counters.setCount(CounterEnumType.P1P1, 1);
            druid.setCounters(counters);
        }
        game.getAction().checkStateEffects(true);
        return druid;
    }

    @Test
    public void incubationDruidWithCounterPaysThreeNativeMana() {
        Game game = pod();
        Card druid = incubationDruid(game, true);
        assertTrue(cast(game, "Divination", null));
        var mana = game.getStack().peekAbility().getPayingMana();
        assertEquals(mana.size(), 3);
        assertTrue(mana.stream().allMatch(value -> value.getSourceCard().getId() == druid.getId()));
        assertTrue(druid.isTapped());
    }

    @Test
    public void incubationDruidWithoutCounterCannotPayThreeMana() {
        Game game = pod();
        Card druid = incubationDruid(game, false);
        assertFalse(cast(game, "Divination", null));
        assertFalse(druid.isTapped());
    }

    private void imprintColor(String imprint, boolean expected) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = addCardToZone("Chrome Mox", actor, ZoneType.Battlefield);
        // Explicit prior imprint and exile state, not an imprint-choice witness.
        source.addImprintedCard(addCardToZone(imprint, actor, ZoneType.Exile));
        game.getAction().checkStateEffects(true);
        paid(game, source, expected);
    }

    @Test
    public void chromeMoxCanPayBlueFromMulticoloredImprint() {
        imprintColor("Cloudblazer", true);
    }
    @Test
    public void chromeMoxUsesImprintedColorNotColorIdentity() {
        imprintColor("Kenrith, the Returned King", false);
    }

    private void graveyardColor(boolean own) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = addCardToZone("Corrupted Grafstone", actor, ZoneType.Battlefield);
        addCardToZone("Cloudblazer", game.getPlayers().get(own ? 0 : 1), ZoneType.Graveyard);
        game.getAction().checkStateEffects(true);
        paid(game, source, own);
    }

    @Test
    public void corruptedGrafstoneUsesOwnGraveyardColor() {
        graveyardColor(true);
    }
    @Test
    public void corruptedGrafstoneDoesNotUseOpponentGraveyardColor() {
        graveyardColor(false);
    }

    @Test
    public void meteorCraterUsesBluePermanentColor() {
        ownLand("Meteor Crater", "Merfolk Looter", true);
    }
    @Test
    public void meteorCraterDoesNotUseLandProductionAsPermanentColor() {
        ownLand("Meteor Crater", "Island", false);
    }

    @Test
    public void plazaOfHeroesCanPayUnrestrictedReflectedBlue() {
        ownLand("Plaza of Heroes", "Baral, Chief of Compliance", true);
    }
    @Test
    public void plazaOfHeroesLegendaryOnlyManaCannotPayForOpt() {
        ownLand("Plaza of Heroes", null, false);
    }

    private void katilda(String creature, boolean expected) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        addCardToZone("Katilda, Dawnhart Prime", actor, ZoneType.Battlefield);
        Card source = addCardToZone(creature, actor, ZoneType.Battlefield);
        source.setSickness(false);
        game.getAction().checkStateEffects(true);
        paid(game, source, expected);
    }

    @Test
    public void katildaGrantsActualBlueManaToBlueHuman() {
        katilda("Baral, Chief of Compliance", true);
    }
    @Test
    public void katildaDoesNotGrantManaToBlueNonhuman() {
        katilda("Merfolk Looter", false);
    }

    private void kinnan(String sourceName, boolean expected) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = addCardToZone(sourceName, actor, ZoneType.Battlefield);
        source.setSickness(false);
        addCardToZone("Kinnan, Bonder Prodigy", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        assertEquals(cast(game, "Myr Retriever", null), expected);
        if (expected) assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        assertEquals(source.isTapped(), expected);
    }

    @Test
    public void kinnanAddsSecondManaFromNonlandSource() {
        kinnan("Llanowar Elves", true);
    }
    @Test
    public void kinnanDoesNotDoubleLandMana() {
        kinnan("Forest", false);
    }
    @Test
    public void nikyaDoublesLandManaForCreatureSpell() {
        multiplier("Nikya of the Old Ways");
    }

    @Test
    public void overabundanceAddsManaAndDealsDamageDuringPayment() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        addCardToZone("Mountain", actor, ZoneType.Battlefield);
        addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        int life = actor.getLife();
        assertTrue(cast(game, "Myr Retriever", null));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        assertEquals(actor.getLife(), life - 1);
    }

    private void activateMana(Card source) {
        Player actor = source.getController();
        SpellAbility action = source.getManaAbilities().get(0);
        action.setActivatingPlayer(actor);
        assertTrue(action.isManaAbility());
        assertTrue(action.canPlay());
        actor.getController().playChosenSpellAbility(action);
        assertTrue(source.isTapped());
        assertTrue(actor.getGame().getStack().isEmpty());
    }

    @Test
    public void reflectingPoolProducesColorlessAndFloatingManaPaysSpatialContortion() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card pool = addCardToZone("Reflecting Pool", actor, ZoneType.Battlefield);
        addCardToZone("Wastes", actor, ZoneType.Battlefield).setTapped(true);
        game.getAction().checkStateEffects(true);
        activateMana(pool);
        assertEquals(actor.getManaPool().getAmountOfColor((byte) ManaAtom.COLORLESS), 1);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        assertTrue(cast(game, "Spatial Contortion", bear));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
    }

    @Test
    public void overabundanceManualLandActivationProducesBonusAndDamage() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card mountain = addCardToZone("Mountain", actor, ZoneType.Battlefield);
        Card enchantment = addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        ManaAtDamage observed = new ManaAtDamage(actor, enchantment);
        game.subscribeToEvents(observed);
        game.getAction().checkStateEffects(true);
        int life = actor.getLife();
        activateMana(mountain);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.RED), 2);
        assertEquals(actor.getLife(), life - 1);
        assertEquals(observed.redMana, List.of(2));
        assertTrue(cast(game, "Myr Retriever", null));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
    }

    @Test
    public void opponentOverabundancePaysTypedColorlessAndDamagesLandController() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card wastes = addCardToZone("Wastes", actor, ZoneType.Battlefield);
        addCardToZone("Overabundance", opponent, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", opponent, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        int actorLife = actor.getLife();
        int opponentLife = opponent.getLife();

        assertTrue(cast(game, "Spatial Contortion", bear));
        assertTrue(wastes.isTapped());
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        assertEquals(actor.getLife(), actorLife - 1);
        assertEquals(opponent.getLife(), opponentLife);
    }

    public static final class ManaAtDamage {
        private final Player player;
        private final Card source;
        private final List<Integer> redMana = new ArrayList<>();

        private ManaAtDamage(Player player, Card source) {
            this.player = player;
            this.source = source;
        }

        @Subscribe
        public void onDamage(GameEventPlayerDamaged event) {
            if (event.source().getId() == source.getId() && event.target().getId() == player.getId()) {
                redMana.add(player.getManaPool().getAmountOfColor(MagicColor.RED));
            }
        }
    }
}
