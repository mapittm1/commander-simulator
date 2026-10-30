package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/** Paid action histories must constrain future actions in every copied world. */
public class UsageGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("usage" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Usage fixture"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 4; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private void pay(Game game, SpellAbility ability, Player owner, int mana) {
        ability.setActivatingPlayer(owner);
        assertTrue(ability.canPlay(), "Native action admission");
        assertTrue(ComputerUtil.playStack(ability, owner, game), "Native action payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getAction().checkStateEffects(true);
    }

    private Card cast(Game game, String name, Player owner, int mana) {
        Card card = addCardToZone(name, owner, ZoneType.Hand);
        pay(game, card.getFirstSpellAbility(), owner, mana);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        return find(game, card.getId());
    }

    private Card find(Game game, int identity) {
        return game.getCardsInGame().stream().filter(card -> card.getId() == identity)
                .findFirst().orElseThrow();
    }

    private SpellAbility activated(Card card, ApiType api, Player owner) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        return ability;
    }

    private Game snapshot(Game game) {
        return new GameCopier(game).withStack(true).makeCopy();
    }

    private void triggers(Game game) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    @Test
    public void paidOncePerTurnActivationRemainsSpentAfterCopy() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Plains", owner);
        addCard("Mountain", owner);
        addCard("Forest", owner);
        Card bear = addCard("Grizzly Bears", owner);
        Card spring = cast(game, "Invigorating Hot Spring", owner, 3);
        assertEquals(spring.getCounters(CounterEnumType.P1P1), 4);
        SpellAbility ability = activated(spring, ApiType.PutCounter, owner);
        ability.getTargets().add(bear);
        pay(game, ability, owner, 0);
        game.getStack().resolveStack();
        assertEquals(ability.getActivationsThisTurn(), 1);
        assertEquals(ability.getResolvedThisTurn(), 1);
        assertFalse(ability.canPlay());
        Game copy = snapshot(game);
        SpellAbility mapped = activated(find(copy, spring.getId()), ApiType.PutCounter,
                copy.getPlayer(owner.getId()));
        assertEquals(mapped.getActivationsThisTurn(), 1, "Copied per-turn usage");
        assertEquals(mapped.getResolvedThisTurn(), 1, "Copied resolved usage");
        assertFalse(mapped.canPlay(), "No second activation this turn");
        assertEquals(find(copy, bear.getId()).getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void counteringAnActivationDoesNotRefundItsLimit() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCard("Plains", owner);
        addCard("Mountain", owner);
        addCard("Forest", owner);
        addCard("Island", opponent);
        Card bear = addCard("Grizzly Bears", owner);
        Card spring = cast(game, "Invigorating Hot Spring", owner, 3);
        SpellAbility ability = activated(spring, ApiType.PutCounter, owner);
        ability.getTargets().add(bear);
        pay(game, ability, owner, 0);
        SpellAbility stifle = addCardToZone("Stifle", opponent, ZoneType.Hand).getFirstSpellAbility();
        stifle.getTargets().add(game.getStack().peekAbility());
        pay(game, stifle, opponent, 1);
        game.getStack().resolveStack();
        assertTrue(game.getStack().isEmpty());
        assertEquals(ability.getActivationsThisTurn(), 1);
        assertEquals(ability.getResolvedThisTurn(), 0);
        assertFalse(ability.canPlay());
        Game copy = snapshot(game);
        SpellAbility mapped = activated(find(copy, spring.getId()), ApiType.PutCounter,
                copy.getPlayer(owner.getId()));
        assertEquals(mapped.getActivationsThisTurn(), 1);
        assertEquals(mapped.getResolvedThisTurn(), 0);
        assertFalse(mapped.canPlay());
        assertEquals(find(copy, spring.getId()).getCounters(CounterEnumType.P1P1), 3);
        assertEquals(find(copy, bear.getId()).getCounters(CounterEnumType.P1P1), 0);
    }

    @Test
    public void onlyOnceAbilityRemainsSpentWithManaStillAvailable() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Plains", 15, owner);
        Card bear = addCard("Grizzly Bears", game.getPlayers().get(1));
        Card trenches = cast(game, "In the Trenches", owner, 3);
        SpellAbility ability = activated(trenches, ApiType.ChangeZone, owner);
        ability.getTargets().add(bear);
        pay(game, ability, owner, 6);
        game.getStack().resolveStack();
        assertEquals(owner.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isLand() && !card.isTapped()).count(), 6L);
        assertEquals(ability.getActivationsThisGame(), 1);
        assertFalse(ability.canPlay());
        Game copy = snapshot(game);
        SpellAbility mapped = activated(find(copy, trenches.getId()), ApiType.ChangeZone,
                copy.getPlayer(owner.getId()));
        assertEquals(mapped.getActivationsThisGame(), 1);
        assertFalse(mapped.canPlay(), "An only-once ability cannot be reused");
        assertEquals(find(copy, bear.getId()).getZone().getZoneType(), ZoneType.Exile);
    }

    @Test
    public void oncePerTurnTriggerDoesNotDrawAgainAfterSnapshot() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Forest", 4, owner);
        Card vampire = addCard("Welcoming Vampire", owner);
        game.getAction().checkStateEffects(true);
        cast(game, "Grizzly Bears", owner, 2);
        triggers(game);
        assertEquals(game.getStack().size(), 1);
        game.getStack().resolveStack();
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 1);
        Game copy = snapshot(game);
        Player mappedOwner = copy.getPlayer(owner.getId());
        cast(copy, "Grizzly Bears", mappedOwner, 2);
        triggers(copy);
        while (!copy.getStack().isEmpty()) {
            copy.getStack().resolveStack();
        }
        assertEquals(mappedOwner.getCardsIn(ZoneType.Hand).size(), 1,
                "Welcoming Vampire already triggered this turn");
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(vampire.getGame(), game);
    }

    @org.testng.annotations.DataProvider(name = "pendingStack")
    public Object[][] pendingStack() {
        return new Object[][] {{true}, {false}};
    }

    @Test(dataProvider = "pendingStack")
    public void pendingActivationsStaySpentEvenWhenTheVisibleStackIsOmitted(boolean includeStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Plains", owner);
        addCard("Mountain", owner);
        addCard("Forest", owner);
        Card bear = addCard("Grizzly Bears", owner);
        Card spring = cast(game, "Invigorating Hot Spring", owner, 3);
        SpellAbility ability = activated(spring, ApiType.PutCounter, owner);
        ability.getTargets().add(bear);
        pay(game, ability, owner, 0);
        Game copy = new GameCopier(game).withStack(includeStack).makeCopy();
        SpellAbility mapped = activated(find(copy, spring.getId()), ApiType.PutCounter,
                copy.getPlayer(owner.getId()));
        assertEquals(mapped.getActivationsThisTurn(), 1);
        assertEquals(mapped.getResolvedThisTurn(), 0);
        assertEquals(copy.getStack().size(), includeStack ? 1 : 0);
        if (includeStack) {
            copy.getStack().resolveStack();
            assertEquals(mapped.getResolvedThisTurn(), 1);
        }
        assertFalse(mapped.canPlay());
        assertEquals(game.getStack().size(), 1);
        assertEquals(ability.getResolvedThisTurn(), 0);
    }

    @Test
    public void nativeTurnCounterResetIsIndependentAndKeepsLifetimeUsage() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Plains", owner);
        addCard("Mountain", owner);
        addCard("Forest", owner);
        Card bear = addCard("Grizzly Bears", owner);
        Card spring = cast(game, "Invigorating Hot Spring", owner, 3);
        SpellAbility ability = activated(spring, ApiType.PutCounter, owner);
        ability.getTargets().add(bear);
        pay(game, ability, owner, 0);
        game.getStack().resolveStack();
        Game copy = snapshot(game);
        Card mappedCard = find(copy, spring.getId());
        SpellAbility mapped = activated(mappedCard, ApiType.PutCounter, copy.getPlayer(owner.getId()));
        assertEquals(mapped.getActivationsThisGame(), 1);
        for (com.google.common.collect.Table.Cell<SpellAbility,
                java.util.Optional<forge.game.staticability.StaticAbility>,
                com.google.common.collect.Multiset<Player>> cell
                : mappedCard.getAbilityActivatedThisTurn().cellSet()) {
            assertSame(cell.getRowKey(), mapped);
            for (Player actor : cell.getValue()) {
                assertSame(actor.getGame(), copy);
            }
        }
        // Exercise the native cleanup primitive, not a full turn-transition fixture.
        mappedCard.resetActivationsPerTurn();
        assertEquals(mapped.getActivationsThisTurn(), 0);
        assertEquals(mapped.getActivationsThisGame(), 1);
        assertEquals(ability.getActivationsThisTurn(), 1);
        mapped.getTargets().clear();
        mapped.getTargets().add(find(copy, bear.getId()));
        pay(copy, mapped, copy.getPlayer(owner.getId()), 0);
        copy.getStack().resolveStack();
        assertEquals(mapped.getActivationsThisTurn(), 1);
        assertEquals(mapped.getActivationsThisGame(), 2);
        assertEquals(ability.getActivationsThisGame(), 1);
        assertEquals(find(copy, bear.getId()).getCounters(CounterEnumType.P1P1), 2);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void onlyOnceUsageSurvivesRepeatedSnapshotsAndTurnCounterReset() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Plains", 15, owner);
        Card bear = addCard("Grizzly Bears", game.getPlayers().get(1));
        Card card = cast(game, "In the Trenches", owner, 3);
        SpellAbility ability = activated(card, ApiType.ChangeZone, owner);
        ability.getTargets().add(bear);
        pay(game, ability, owner, 6);
        game.getStack().resolveStack();
        Game copy = snapshot(snapshot(game));
        Card mappedCard = find(copy, card.getId());
        SpellAbility mapped = activated(mappedCard, ApiType.ChangeZone, copy.getPlayer(owner.getId()));
        mappedCard.resetActivationsPerTurn();
        assertEquals(mapped.getActivationsThisTurn(), 0);
        assertEquals(mapped.getActivationsThisGame(), 1);
        assertFalse(mapped.canPlay());
        assertEquals(ability.getActivationsThisTurn(), 1);
        assertEquals(ability.getActivationsThisGame(), 1);
    }

    @org.testng.annotations.DataProvider(name = "crewSource")
    public Object[][] crewSource() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "crewSource")
    public void keywordCrewKeepsItsUsageAndCrewMemberProvenance(boolean removeCrewMember) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Forest", 5, owner);
        addCard("Grizzly Bears", owner).setSickness(false);
        addCard("Grizzly Bears", owner).setSickness(false);
        Card vehicle = cast(game, "Luxurious Locomotive", owner, 5);
        SpellAbility crew = vehicle.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility()
                        && sa.isKeyword(forge.game.keyword.Keyword.CREW)).findFirst().orElseThrow();
        pay(game, crew, owner, 0);
        game.getStack().resolveStack();
        assertEquals(crew.getActivationsThisTurn(), 1);
        assertFalse(vehicle.getCrewedByThisTurn().isEmpty());
        int crewSize = vehicle.getCrewedByThisTurn().size();
        Card member = vehicle.getCrewedByThisTurn().iterator().next();
        if (removeCrewMember) {
            game.getAction().moveToGraveyard(member, null);
        }
        addCard("Grizzly Bears", owner).setSickness(false);
        Game copy = snapshot(game);
        Card mappedVehicle = find(copy, vehicle.getId());
        SpellAbility mappedCrew = mappedVehicle.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility()
                        && sa.isKeyword(forge.game.keyword.Keyword.CREW)).findFirst().orElseThrow();
        mappedCrew.setActivatingPlayer(copy.getPlayer(owner.getId()));
        assertEquals(mappedCrew.getActivationsThisTurn(), 1);
        assertFalse(mappedCrew.canPlay(), "Once-per-turn crew was already used");
        assertEquals(mappedVehicle.getCrewedByThisTurn().size(), crewSize);
        for (Card recorded : mappedVehicle.getCrewedByThisTurn()) {
            assertSame(recorded.getGame(), copy, "Crew provenance is in the copied world");
            if (removeCrewMember && recorded.getId() == member.getId()) {
                assertTrue(recorded.isLKI());
            }
        }
    }

    @Test
    public void equipmentGrantedAbilityKeepsItsUsageAndGrantor() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCards("Forest", 4, owner);
        Card bear = addCard("Grizzly Bears", owner);
        bear.setSickness(false);
        Card bow = cast(game, "Viridian Longbow", owner, 1);
        SpellAbility equip = activated(bow, ApiType.Attach, owner);
        equip.getTargets().add(bear);
        pay(game, equip, owner, 3);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        SpellAbility ping = activated(bear, ApiType.DealDamage, owner);
        assertNotNull(ping.getGrantorStatic());
        ping.getTargets().add(opponent);
        pay(game, ping, owner, 0);
        game.getStack().resolveStack();
        assertEquals(opponent.getLife(), 39);
        assertEquals(ping.getActivationsThisTurn(), 1);
        Game copy = snapshot(game);
        SpellAbility mapped = activated(find(copy, bear.getId()), ApiType.DealDamage,
                copy.getPlayer(owner.getId()));
        assertEquals(mapped.getActivationsThisTurn(), 1);
        assertEquals(mapped.getResolvedThisTurn(), 1);
        assertSame(mapped.getGrantorStatic().getHostCard(), find(copy, bow.getId()));
        assertEquals(ping.getActivationsThisTurn(), 1);
    }

    private void playLand(Game game, String name, Player owner) {
        Card land = addCardToZone(name, owner, ZoneType.Hand);
        SpellAbility play = land.getAllPossibleAbilities(owner, false).stream()
                .filter(SpellAbility::isLandAbility).findFirst().orElseThrow();
        play.setActivatingPlayer(owner);
        assertTrue(play.canPlay(), "Native land-play admission");
        assertTrue(ComputerUtil.playNoStack(owner, play, game, false));
        triggers(game);
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }
    }

    private int floatingMana(Player owner) {
        int count = 0;
        for (forge.game.mana.Mana mana : owner.getManaPool()) {
            assertNotNull(mana);
            count++;
        }
        return count;
    }

    @Test
    public void successiveOmnathLandfallsKeepTheirResolvedOrdinal() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        for (String land : List.of("Mountain", "Forest", "Plains", "Island")) {
            addCard(land, owner);
        }
        addCard("Azusa, Lost but Seeking", owner);
        game.getAction().checkStateEffects(true);
        cast(game, "Omnath, Locus of Creation", owner, 4);
        triggers(game);
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }
        playLand(game, "Forest", owner);
        assertEquals(owner.getLife(), 44);
        assertEquals(floatingMana(owner), 0);
        assertEquals(owner.getLandsPlayedThisTurn(), 1);
        Game second = snapshot(game);
        Player secondOwner = second.getPlayer(owner.getId());
        playLand(second, "Mountain", secondOwner);
        assertEquals(secondOwner.getLife(), 44);
        assertEquals(floatingMana(secondOwner), 4);
        assertEquals(secondOwner.getLandsPlayedThisTurn(), 2);
        Game third = snapshot(second);
        Player thirdOwner = third.getPlayer(owner.getId());
        playLand(third, "Plains", thirdOwner);
        assertEquals(thirdOwner.getLife(), 44);
        assertEquals(floatingMana(thirdOwner), 4);
        assertEquals(thirdOwner.getLandsPlayedThisTurn(), 3);
        for (Player opponent : third.getPlayers()) {
            if (opponent != thirdOwner) {
                assertEquals(opponent.getLife(), 36);
            }
        }
        Card fourthLand = addCardToZone("Forest", thirdOwner, ZoneType.Hand);
        boolean fourthAdmitted = false;
        for (SpellAbility candidate : fourthLand.getAllPossibleAbilities(thirdOwner, false)) {
            candidate.setActivatingPlayer(thirdOwner);
            if (candidate.isLandAbility() && candidate.canPlay()) {
                fourthAdmitted = true;
            }
        }
        assertFalse(fourthAdmitted, "Azusa permits three land plays, not four");
        assertEquals(owner.getLife(), 44);
        assertEquals(floatingMana(owner), 0);
        assertEquals(secondOwner.getLife(), 44);
    }

    private SpellAbility allianceMode(Card card) {
        return card.getTriggers().stream()
                .filter(trigger -> "TrigCharm".equals(trigger.getParam("Execute")))
                .findFirst().orElseThrow().getOverridingAbility();
    }

    @Test
    public void allianceCannotRepeatAChosenModeAfterSnapshots() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Plains", owner);
        addCard("Island", owner);
        addCards("Forest", 11, owner);
        Card galadriel = cast(game, "Galadriel, Light of Valinor", owner, 5);
        cast(game, "Grizzly Bears", owner, 2);
        triggers(game);
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }
        SpellAbility original = allianceMode(galadriel);
        assertNotNull(original);
        List<String> chosen = galadriel.getChosenModes(original, "ThisTurn");
        assertNotNull(chosen);
        assertEquals(chosen.size(), 1);
        String first = chosen.get(0);
        Game second = snapshot(game);
        Card secondGaladriel = find(second, galadriel.getId());
        SpellAbility secondMode = allianceMode(secondGaladriel);
        List<String> retained = secondGaladriel.getChosenModes(secondMode, "ThisTurn");
        assertNotNull(retained, "Previously chosen mode remains recorded");
        assertEquals(retained, chosen);
        cast(second, "Grizzly Bears", second.getPlayer(owner.getId()), 2);
        triggers(second);
        while (!second.getStack().isEmpty()) {
            second.getStack().resolveStack();
        }
        List<String> afterSecond = secondGaladriel.getChosenModes(secondMode, "ThisTurn");
        assertEquals(afterSecond.size(), 2);
        assertEquals(new java.util.HashSet<>(afterSecond).size(), 2);
        assertTrue(afterSecond.contains(first));
        Game third = snapshot(second);
        Card thirdGaladriel = find(third, galadriel.getId());
        SpellAbility thirdMode = allianceMode(thirdGaladriel);
        cast(third, "Grizzly Bears", third.getPlayer(owner.getId()), 2);
        triggers(third);
        while (!third.getStack().isEmpty()) {
            third.getStack().resolveStack();
        }
        List<String> afterThird = thirdGaladriel.getChosenModes(thirdMode, "ThisTurn");
        assertEquals(afterThird.size(), 3);
        assertEquals(new java.util.HashSet<>(afterThird).size(), 3);
        assertEquals(chosen.size(), 1);
        assertEquals(afterSecond.size(), 2);
        thirdGaladriel.resetChosenModeTurn();
        assertNull(thirdGaladriel.getChosenModes(thirdMode, "ThisTurn"));
        assertEquals(thirdGaladriel.getChosenModes(thirdMode, "ThisGame").size(), 3);
        assertEquals(afterSecond.size(), 2);
    }

    @Test
    public void repeatedSaddleCountsAndMembersSurviveCopy() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Forest", 3, owner);
        addCard("Grizzly Bears", owner).setSickness(false);
        Card mount = cast(game, "Ornery Tumblewagg", owner, 3);
        SpellAbility saddle = mount.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility()
                        && sa.isKeyword(forge.game.keyword.Keyword.SADDLE)).findFirst().orElseThrow();
        pay(game, saddle, owner, 0);
        game.getStack().resolveStack();
        addCard("Grizzly Bears", owner).setSickness(false);
        pay(game, saddle, owner, 0);
        game.getStack().resolveStack();
        assertEquals(mount.getTimesSaddledThisTurn(), 2);
        assertEquals(saddle.getActivationsThisTurn(), 2);
        Game copy = snapshot(game);
        Card mappedMount = find(copy, mount.getId());
        assertEquals(mappedMount.getTimesSaddledThisTurn(), 2, "All saddle uses are retained");
        assertTrue(mappedMount.isSaddled());
        assertEquals(mappedMount.getSaddledByThisTurn().size(), mount.getSaddledByThisTurn().size());
        for (Card member : mappedMount.getSaddledByThisTurn()) {
            assertSame(member.getGame(), copy);
        }
    }

    @Test
    public void riotStillResolvesAfterSnapshotAndPaidCast() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCard("Mountain", owner);
        addCard("Forest", owner);
        Card goblin = addCardToZone("Zhur-Taa Goblin", owner, ZoneType.Hand);
        Game copy = snapshot(game);
        Player mappedOwner = copy.getPlayer(owner.getId());
        Card mapped = find(copy, goblin.getId());
        pay(copy, mapped.getFirstSpellAbility(), mappedOwner, 2);
        copy.getStack().resolveStack();
        Card resolved = find(copy, goblin.getId());
        assertEquals(resolved.getZone().getZoneType(), ZoneType.Battlefield);
        assertTrue(resolved.hasKeyword(forge.game.keyword.Keyword.HASTE)
                || resolved.getCounters(CounterEnumType.P1P1) > 0);
        assertEquals(goblin.getZone().getZoneType(), ZoneType.Hand);
    }

    @Test
    public void optionalReplacementOverrideRemainsNullWithMappedHost() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card clone = addCardToZone("Clone", owner, ZoneType.Hand);
        // Synthetic native contract fixture: a prevention replacement needs no child ability.
        forge.game.replacement.ReplacementEffect original =
                forge.game.replacement.ReplacementHandler.parseReplacement(
                        "Event$ Draw | ValidPlayer$ You | ReplacementResult$ Prevented",
                        clone, true);
        clone.addReplacementEffect(original);
        assertNull(original.getOverridingAbility());
        Game copy = snapshot(game);
        Card mapped = find(copy, clone.getId());
        forge.game.replacement.ReplacementEffect retained = mapped.getReplacementEffects().stream()
                .filter(effect -> effect.getId() == original.getId()).findFirst().orElseThrow();
        assertNull(retained.getOverridingAbility());
        assertNotSame(retained, original);
        assertSame(retained.getHostCard(), mapped);
        assertSame(original.getHostCard(), clone);
    }
}
