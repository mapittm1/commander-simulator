package forge.game.card;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import forge.card.CardStateName;
import forge.card.CardType;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.game.ability.ApiType;
import forge.game.ability.effects.DetachedCardEffect;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.trackable.TrackableProperty;
import io.sentry.Breadcrumb;
import io.sentry.Sentry;

import java.util.List;
import java.util.Map;

import static forge.game.card.CardFactory.getCard;

public class CardCopyService {
    // A service to copy all sorts of things from a card to a new card.
    // This pulled in functions from CardFactory and CardUtil

    private final Card copyFrom;
    private final Game toGame;
    private final IEntityMap historyMap;
    private final Map<Card, Card> historicalObjects;
    public CardCopyService(Card copyFrom) {
        this(copyFrom, copyFrom == null ? null : copyFrom.getGame());
    }

    public CardCopyService(Card copyFrom, Game toGame) {
        this(copyFrom, toGame, null);
    }

    private CardCopyService(Card copyFrom, Game toGame, IEntityMap historyMap) {
        this(copyFrom, toGame, historyMap, new java.util.IdentityHashMap<>());
    }

    private CardCopyService(Card copyFrom, Game toGame, IEntityMap historyMap, Map<Card, Card> historicalObjects) {
        this.historicalObjects = historicalObjects;
        this.copyFrom = copyFrom;
        this.toGame = toGame;
        this.historyMap = historyMap;
    }

    /** Copy historical characteristics, not the live incarnation with the same card ID. */
    public static Card getLKICopyForGame(Card source, IEntityMap map) {
        return new CardCopyService(source, map.getGame(), map).getLKICopy();
    }

    private IEntityMap historyGraph(Map<Integer, Card> cache) {
        return new IEntityMap() {
            @Override
            public Game getGame() { return toGame; }

            @Override
            public forge.game.combat.AttackingBand mapCombatBand(forge.game.combat.AttackingBand band) {
                return historyMap == null ? band : historyMap.mapCombatBand(band);
            }

            @Override
            public GameObject map(GameObject object) {
                return historyMap == null ? object : (GameObject) historyObject(object, cache);
            }
        };
    }

    private Player historyPlayer(Player player) {
        return historyMap == null || player == null ? player : historyMap.map(player);
    }

    private Card historyCard(Card card, Map<Integer, Card> cache) {
        return historyMap == null ? getLKICopy(card, cache)
                : new CardCopyService(card, toGame, historyMap, historicalObjects).getLKICopy(cache);
    }

    private GameEntity historyEntity(GameEntity entity, Map<Integer, Card> cache) {
        return entity instanceof Card ? historyCard((Card) entity, cache) : historyPlayer((Player) entity);
    }

    private List<Card> historyCards(Iterable<Card> cards, Map<Integer, Card> cache) {
        List<Card> result = Lists.newArrayList();
        if (cards != null) {
            for (Card card : cards) result.add(historyCard(card, cache));
        }
        return result;
    }

    private Zone historyZone(Zone zone) {
        if (historyMap == null || zone == null) return zone;
        if (zone.getZoneType() == ZoneType.Stack) return toGame.getStackZone();
        Player player = historyPlayer(zone.getPlayer());
        if (player == null) throw new IllegalStateException("Historical zone has no mapped player");
        return player.getZone(zone.getZoneType());
    }

    private SpellAbility historyAbility(SpellAbility ability, Map<Integer, Card> cache) {
        if (historyMap == null || ability == null) return ability;
        SpellAbility copy = ability.copy(historyCard(ability.getHostCard(), cache), true);
        copy.setActivatingPlayer(historyPlayer(ability.getActivatingPlayer()));
        return copy;
    }

    private Object historyObject(Object object, Map<Integer, Card> cache) {
        if (object instanceof Card) return historyCard((Card) object, cache);
        if (object instanceof Player) return historyPlayer((Player) object);
        if (object instanceof SpellAbility) return historyAbility((SpellAbility) object, cache);
        if (historyMap != null && object instanceof GameObject) {
            throw new IllegalStateException("Unsupported historical game-object reference");
        }
        return object;
    }

    private List<Object> historyObjects(Iterable<?> objects, Map<Integer, Card> cache) {
        List<Object> result = Lists.newArrayList();
        for (Object object : objects) result.add(historyObject(object, cache));
        return result;
    }

    public final Card copyCard(boolean assignNewId) {
        return copyCard(assignNewId, copyFrom.getOwner());
    }

    // Straight copying for things like moving a card to a different zone or using GameCopier
    public final Card copyCard(boolean assignNewId, Player owner) {
        Card out;
        if (copyFrom.isRealToken() || copyFrom.getCopiedPermanent() != null || copyFrom.getPaperCard() == null) {
            out = copyStats(copyFrom, owner, assignNewId);
            out.setEffectSource(copyFrom.getEffectSource());
            out.setBoon(copyFrom.isBoon());
            out.dangerouslySetGame(toGame);

            // need to copy this values for the tokens
            out.setTokenSpawningAbility(copyFrom.getTokenSpawningAbility());
            out.setCopiedPermanent(copyFrom.getCopiedPermanent());
        } else {
            out = assignNewId ? getCard(copyFrom.getPaperCard(), owner, toGame)
                    : getCard(copyFrom.getPaperCard(), owner, copyFrom.getId(), toGame);
        }

        out.setChosenCommanderColor(copyFrom.getChosenCommanderColor());
        out.setZone(copyFrom.getZone());
        out.setState(copyFrom.getFaceupCardStateName(), true);
        out.setBackSide(copyFrom.isBackSide());
        out.setGamePieceType(copyFrom.getGamePieceType());
        out.setTokenCard(copyFrom.isTokenCard());

        if (toGame == copyFrom.getGame()) {
            // Only copy these things if we're not copying them into a new game

            out.setCollectible(copyFrom.isCollectible());

            // this's necessary for forge.game.GameAction.unattachCardLeavingBattlefield(Card)
            if (copyFrom.hasCardAttachments()) {
                out.setAttachedCards(copyFrom.getAttachedCards());
            }
            if (copyFrom.isAttachedToEntity()) {
                out.setEntityAttachedTo(copyFrom.getEntityAttachedTo());
            }
            if (copyFrom.hasMergedCard()) {
                out.setMergedCards(copyFrom.getMergedCards());
            }

            out.setLeavesPlayCommands(copyFrom.getLeavesPlayCommands());

            out.setSpecialized(copyFrom.isSpecialized());
            out.addRemembered(copyFrom.getRemembered());
            out.addImprintedCards(copyFrom.getImprintedCards());
            out.setCommander(copyFrom.isRealCommander());
            //out.setFaceDown(copyFrom.isFaceDown());
        }

        int foil = copyFrom.getCurrentState().getFoil();
        if (foil > 0) {
            out.setFoil(foil);
        }

        return out;
    }

    private static void copyState(final Card from, final CardStateName fromState, final Card to,
                                 final CardStateName toState) {
        copyState(from, fromState, to, toState, true);
    }

    private static void copyState(final Card from, final CardStateName fromState, final Card to,
                                 final CardStateName toState, boolean updateView) {
        // copy characteristics not associated with a state
        to.setText(from.getSpellText());

        // get CardCharacteristics for desired state
        if (!to.getStates().contains(toState)) {
            to.addAlternateState(toState, updateView);
        }
        final CardState toCharacteristics = to.getState(toState), fromCharacteristics = from.getState(fromState);
        toCharacteristics.copyFrom(fromCharacteristics, false);
    }

    public static Card copyStats(final Card in, final Player newOwner, boolean assignNewId) {
        int id = in.getId();
        if (assignNewId) {
            id = newOwner == null ? 0 : newOwner.getGame().nextCardId();
        }
        final Card c;
        if(in instanceof DetachedCardEffect)
            c = new DetachedCardEffect((DetachedCardEffect) in, assignNewId);
        else
            c = new Card(id, in.getPaperCard(), in.getGame());

        c.setOwner(newOwner);
        c.setSetCode(in.getSetCode());

        for (final CardStateName state : in.getStates()) {
            copyState(in, state, c, state, false);
        }

        c.setState(in.getCurrentStateName(), false);
        c.setBackSide(in.isBackSide());

        return c;
    }

    @Deprecated
    public void copyCopiableCharacteristics(final Card to, SpellAbility sourceSA, SpellAbility targetSA) {
        final boolean toIsFaceDown = to.isFaceDown();
        if (toIsFaceDown) {
            // If to is face down, copy to its front side
            to.setState(CardStateName.Original, false);
            copyCopiableCharacteristics(to, sourceSA, targetSA);
            to.setState(CardStateName.FaceDown, false);
            return;
        }

        final boolean fromIsFlipCard = copyFrom.isFlipCard();
        final boolean fromIsTransformedCard = copyFrom.getCurrentStateName() == CardStateName.Backside || copyFrom.getCurrentStateName() == CardStateName.Meld;

        if (fromIsFlipCard) {
            if (to.getCurrentStateName().equals(CardStateName.Flipped)) {
                copyState(copyFrom, CardStateName.Original, to, CardStateName.Original);
            } else {
                copyState(copyFrom, CardStateName.Original, to, to.getCurrentStateName());
            }
            copyState(copyFrom, CardStateName.Flipped, to, CardStateName.Flipped);
        } else if (copyFrom.isTransformable()
                && sourceSA != null && ApiType.CopySpellAbility.equals(sourceSA.getApi())
                && targetSA != null && targetSA.isSpell() && targetSA.getHostCard().isPermanent()) {
            copyState(copyFrom, CardStateName.Original, to, CardStateName.Original);
            copyState(copyFrom, CardStateName.Backside, to, CardStateName.Backside);
            // 707.10g If an effect creates a copy of a transforming permanent spell, the copy is also a transforming permanent spell that has both a front face and a back face.
            // The characteristics of its front and back face are determined by the copiable values of the same face of the spell it is a copy of, as modified by any other copy effects.
            // If the spell it is a copy of has its back face up, the copy is created with its back face up. The token that’s put onto the battlefield as that spell resolves is a transforming token.
            to.setBackSide(copyFrom.isBackSide());
        } else if (fromIsTransformedCard) {
            copyState(copyFrom, copyFrom.getCurrentStateName(), to, CardStateName.Original);
        } else {
            copyState(copyFrom, copyFrom.getCurrentStateName(), to, to.getCurrentStateName());
        }
    }

    // ========================================================
    // LKI functions

    public static List<Card> getLKICopyList(final Iterable<Card> in, Map<Integer, Card> cachedMap) {
        if (in == null) {
            return null;
        }
        List<Card> result = Lists.newArrayList();
        for (final Card c : in) {
            result.add(new CardCopyService(c).getLKICopy(cachedMap));
        }
        return result;
    }

    public static Card getLKICopy(Card c) {
        // Ideally, we'd just convert all calls to getLKICopy to use the Map version
        return new CardCopyService(c).getLKICopy();
    }

    public static Card getLKICopy(final Card c, Map<Integer, Card> cachedMap) {
        return new CardCopyService(c).getLKICopy(cachedMap);
    }

    public static GameEntity getLKICopy(final GameEntity c, Map<Integer, Card> cachedMap) {
        // Ideally, we'd just convert all calls to getLKICopy to use the Map version
        if (c instanceof Card) {
            return new CardCopyService((Card) c).getLKICopy(cachedMap);
        }
        return c;
    }

    public Card getLKICopy() {
        return getLKICopy(Maps.newHashMap());
    }

    // The copy is built with a view that does not compute ability text, since walking every card in the
    // game to produce it is the single largest cost of making one. It takes its source's text instead.
    private static void copyAbilityText(final CardView from, final CardView to) {
        to.getCurrentState().set(TrackableProperty.AbilityText, from.getCurrentState().getAbilityText());
        if (from.hasAlternateState() && to.hasAlternateState()) {
            to.getAlternateState().set(TrackableProperty.AbilityText, from.getAlternateState().getAbilityText());
        }
    }

    public Card getLKICopy(Map<Integer, Card> cachedMap) {
        if (copyFrom == null) {
            return null;
        }
        Card cachedCard = historyMap == null ? cachedMap.get(copyFrom.getId()) : historicalObjects.get(copyFrom);
        if (cachedCard != null) {
            return cachedCard;
        }

        String msg = "CardUtil:getLKICopy copy object";
        Breadcrumb bread = new Breadcrumb(msg);
        bread.setData("Card", copyFrom.getName());
        bread.setData("CardState", copyFrom.getCurrentStateName().toString());
        bread.setData("Player", copyFrom.getController().getName());
        Sentry.addBreadcrumb(bread);

        final Card newCopy;
        if(copyFrom instanceof DetachedCardEffect)
            newCopy = new DetachedCardEffect((DetachedCardEffect) copyFrom, toGame, false);
        else
            newCopy = new Card(copyFrom.getId(), copyFrom.getPaperCard(), toGame, null, true);
        if (historyMap == null) cachedMap.put(copyFrom.getId(), newCopy);
        else historicalObjects.put(copyFrom, newCopy);
        newCopy.setSetCode(copyFrom.getSetCode());
        newCopy.setOwner(historyPlayer(copyFrom.getOwner()));
        newCopy.setController(historyPlayer(copyFrom.getController()), 0);
        newCopy.setCommander(copyFrom.isCommander());
        newCopy.setChosenCommanderColor(copyFrom.getChosenCommanderColor());
        newCopy.setCollectible(copyFrom.isCollectible());

        // needed to ensure that the LKI object has correct CMC info no matter what state the original card was in
        // (e.g. Scrap Trawler + transformed Harvest Hand)
        newCopy.setLKICMC(copyFrom.getCMC());
        // used for the purpose of cards that care about the zone the card was known to be in last
        newCopy.setLastKnownZone(historyZone(copyFrom.getLastKnownZone()));
        // copy EffectSource for description
        newCopy.setEffectSource(historyCard(copyFrom.getEffectSource(), cachedMap));

        if (copyFrom.isFlipCard()) {
            newCopy.getState(CardStateName.Original).copyFrom(copyFrom.getState(CardStateName.Original), true);
            newCopy.addAlternateState(CardStateName.Flipped, false);
            newCopy.getState(CardStateName.Flipped).copyFrom(copyFrom.getState(CardStateName.Flipped), true);
        } else if (copyFrom.isTransformable()) {
            newCopy.getState(CardStateName.Original).copyFrom(copyFrom.getState(CardStateName.Original), true);
            newCopy.addAlternateState(CardStateName.Backside, false);
            newCopy.getState(CardStateName.Backside).copyFrom(copyFrom.getState(CardStateName.Backside), true);
        } else if (copyFrom.hasState(CardStateName.Secondary)) {
            newCopy.getState(CardStateName.Original).copyFrom(copyFrom.getState(CardStateName.Original), true);
            newCopy.addAlternateState(CardStateName.Secondary, false);
            newCopy.getState(CardStateName.Secondary).copyFrom(copyFrom.getState(CardStateName.Secondary), true);
        } else if (copyFrom.hasState(CardStateName.PreparedSpell)) {
            newCopy.getState(CardStateName.Original).copyFrom(copyFrom.getState(CardStateName.Original), true);
            newCopy.addAlternateState(CardStateName.PreparedSpell, false);
            newCopy.getState(CardStateName.PreparedSpell).copyFrom(copyFrom.getState(CardStateName.PreparedSpell), true);
        } else if (copyFrom.isSplitCard()) {
            newCopy.getState(CardStateName.Original).copyFrom(copyFrom.getState(CardStateName.Original), true);
            newCopy.addAlternateState(CardStateName.LeftSplit, false);
            newCopy.getState(CardStateName.LeftSplit).copyFrom(copyFrom.getState(CardStateName.LeftSplit), true);
            newCopy.addAlternateState(CardStateName.RightSplit, false);
            newCopy.getState(CardStateName.RightSplit).copyFrom(copyFrom.getState(CardStateName.RightSplit), true);
        } else {
            newCopy.getCurrentState().copyFrom(copyFrom.getState(copyFrom.getFaceupCardStateName()), true);
        }
        newCopy.setFlipped(copyFrom.isFlipped());
        newCopy.setBackSide(copyFrom.isBackSide());
        if (newCopy.hasAlternateState()) {
            newCopy.setState(copyFrom.getCurrentStateName(), false, true);
        }
        if (copyFrom.isFaceDown()) {
            newCopy.turnFaceDownNoUpdate();
            newCopy.setType(new CardType(copyFrom.getFaceDownState().getType()));
        }
        // prevent StackDescription from revealing face
        newCopy.updateStateForView();
        copyAbilityText(copyFrom.getView(), newCopy.getView());

        /*
        if (in.isCloned()) {
            newCopy.addAlternateState(CardStateName.Cloner, false);
            newCopy.getState(CardStateName.Cloner).copyFrom(in.getState(CardStateName.Cloner), true);
        }
        */

        newCopy.setGamePieceType(copyFrom.getGamePieceType());
        newCopy.setTokenCard(copyFrom.isTokenCard());
        newCopy.setEmblem(copyFrom.isEmblem());

        // lock in the current P/T
        newCopy.setBasePower(copyFrom.getCurrentPower());
        newCopy.setBaseToughness(copyFrom.getCurrentToughness());

        // extra copy PT boost
        newCopy.setPTBoost(copyFrom.getPTBoostTable());

        newCopy.copyFrom(copyFrom);
        newCopy.copyStickerRecordsForLKI(copyFrom, historyMap);
        newCopy.setCounters(HashMultiset.create(copyFrom.getCounters()));

        newCopy.setColor(copyFrom.getColor());
        newCopy.copySnapshotPhasingFrom(copyFrom, historyGraph(cachedMap));
        newCopy.copySnapshotExertionFrom(copyFrom, historyGraph(cachedMap));
        newCopy.copySnapshotProtectorFrom(copyFrom, historyGraph(cachedMap));
        newCopy.setTapped(copyFrom.isTapped());
        newCopy.setSickness(copyFrom.isFirstTurnControlled());
        newCopy.setTributed(copyFrom.isTributed());
        newCopy.setUnearthed(copyFrom.isUnearthed());
        newCopy.setMonstrous(copyFrom.isMonstrous());
        newCopy.setHarnessed(copyFrom.isHarnessed());
        newCopy.copySnapshotWorldFrom(copyFrom);
        newCopy.copySnapshotDungeonFrom(copyFrom);
        if (newCopy.getClassLevel() != copyFrom.getClassLevel()) {
            newCopy.setClassLevel(copyFrom.getClassLevel());
        }
        newCopy.setRenowned(copyFrom.isRenowned());
        // Historical state retains the designation; ordinary copiable characteristics do not.
        newCopy.setRingBearer(copyFrom.isRingBearer());
        newCopy.copySnapshotRegenerationFrom(copyFrom);
        newCopy.copySnapshotDetainFrom(copyFrom, historyMap);
        newCopy.setSolved(copyFrom.isSolved());
        newCopy.setSaddled(copyFrom.isSaddled());
        if (newCopy.isSaddled()) newCopy.setSaddledByThisTurn(historyMap == null ? copyFrom.getSaddledByThisTurn() : new CardCollection(historyCards(copyFrom.getSaddledByThisTurn(), cachedMap)));
        if (copyFrom.isSuspected()) {
            newCopy.setSuspectedStatic(copyFrom.getSuspectedStatic().copy(newCopy, true));
        }

        newCopy.setDamageHistory(copyFrom.getDamageHistory().copy(historyGraph(cachedMap)));
        newCopy.copyDamageReceivedFrom(copyFrom);
        newCopy.setExcessDamageReceivedThisTurn(copyFrom.getExcessDamageThisTurn());

        // these are LKI already
        newCopy.getBlockedThisTurn().addAll(historyMap == null ? copyFrom.getBlockedThisTurn() : historyCards(copyFrom.getBlockedThisTurn(), cachedMap));
        newCopy.getBlockedByThisTurn().addAll(historyMap == null ? copyFrom.getBlockedByThisTurn() : historyCards(copyFrom.getBlockedByThisTurn(), cachedMap));

        newCopy.setAttachedCards(historyCards(copyFrom.getAttachedCards(), cachedMap));
        newCopy.setEntityAttachedTo(historyEntity(copyFrom.getEntityAttachedTo(), cachedMap));

        newCopy.setCopiedPermanent(historyMap == null ? copyFrom.getCopiedPermanent() : historyCard(copyFrom.getCopiedPermanent(), cachedMap));

        newCopy.setHaunting(historyMap == null ? copyFrom.getHaunting() : historyCard(copyFrom.getHaunting(), cachedMap));
        for (final Card haunter : copyFrom.getHauntedBy()) {
            newCopy.addHauntedBy(historyMap == null ? haunter : historyCard(haunter, cachedMap), false);
        }

        newCopy.setIntensity(copyFrom.getIntensity(false));
        // Don't re-apply perpetual effects - they're already copied via copyFrom().
        // Re-applying would create duplicate ReplacementEffect objects that cause
        // infinite recursion in getReplacementList for "enters tapped" effects.
        newCopy.setPerpetual(copyFrom, false);

        newCopy.addRemembered(historyMap == null ? copyFrom.getRemembered() : historyObjects(copyFrom.getRemembered(), cachedMap));
        newCopy.copySnapshotImprintedFrom(copyFrom, historyGraph(cachedMap));
        newCopy.copySnapshotChoicesFrom(copyFrom, historyGraph(cachedMap));

        // for getReplacementList (run after setChangedCardKeywords for caching)
        newCopy.setStoredKeywords(copyFrom.getStoredKeywords(), true);
        newCopy.setStoredReplacements(copyFrom.getStoredReplacements());

        newCopy.copyChangedTextFrom(copyFrom);
        newCopy.changedCardKeywordsByWord = copyFrom.changedCardKeywordsByWord.copy(newCopy, true);

        newCopy.setGameTimestamp(copyFrom.getGameTimestamp());
        newCopy.setLayerTimestamp(copyFrom.getLayerTimestamp());

        newCopy.setBestowTimestamp(copyFrom.getBestowTimestamp());

        newCopy.setTurnInZone(copyFrom.getTurnInZone());

        newCopy.setForetold(copyFrom.isForetold());
        newCopy.setForetoldCostByEffect(copyFrom.isForetoldCostByEffect());

        newCopy.setPlotted(copyFrom.isPlotted());

        newCopy.setPrepared(historyCard(copyFrom.getPrepared(), cachedMap));

        newCopy.setMeldedWith(historyCard(copyFrom.getMeldedWith(), cachedMap));

        // update keyword cache on all states
        for (CardStateName s : newCopy.getStates()) {
            newCopy.updateKeywordsCache(newCopy.getState(s));
        }

        if (copyFrom.getCastSA() != null) {
            SpellAbility castSA = copyFrom.getCastSA().copy(newCopy, true);
            castSA.setActivatingPlayer(historyPlayer(castSA.getActivatingPlayer()));
            castSA.setLastStateBattlefield(CardCollection.EMPTY);
            castSA.setLastStateGraveyard(CardCollection.EMPTY);
            newCopy.setCastSA(castSA);
        }
        if (historyMap != null) {
            for (SpellAbility ability : newCopy.getSpellAbilities()) {
                ability.setActivatingPlayer(historyPlayer(ability.getActivatingPlayer()));
            }
        }
        newCopy.setCastFrom(historyZone(copyFrom.getCastFrom()));

        if (historyMap != null) {
            for (Card linked : copyFrom.getUntilLeavesBattlefield()) {
                newCopy.addUntilLeavesBattlefield(historyMap.map(linked));
            }
        }
        newCopy.setExiledBy(historyPlayer(copyFrom.getExiledBy()));
        newCopy.setExiledWith(historyCard(copyFrom.getExiledWith(), cachedMap));
        newCopy.setExiledSA(historyAbility(copyFrom.getExiledSA(), cachedMap));
        newCopy.addExiledCards(historyMap == null ? copyFrom.getExiledCards() : historyCards(copyFrom.getExiledCards(), cachedMap));

        newCopy.setDiscarded(copyFrom.wasDiscarded());
        newCopy.setMilled(copyFrom.wasMilled());
        newCopy.setSurveilled(copyFrom.wasSurveilled());

        newCopy.getAbilityActivatedThisTurn().putAll(copyFrom.getAbilityActivatedThisTurn());
        newCopy.getAbilityActivatedThisGame().putAll(copyFrom.getAbilityActivatedThisGame());
        newCopy.getAbilityResolvedThisTurn().putAll(copyFrom.getAbilityResolvedThisTurn());

        if (historyMap != null && copyFrom.getCombatLKI() != null) {
            newCopy.setCombatLKI(copyFrom.getCombatLKI().copy(historyGraph(cachedMap)));
        } else if (historyMap == null && copyFrom.getGame().getCombat() != null && copyFrom.isPermanent()) {
            newCopy.setCombatLKI(copyFrom.getGame().getCombat().saveLKI(newCopy));
        }

        for (Map.Entry<Long, Player> entry : copyFrom.getGoadMap().entrySet()) {
            newCopy.getGoadMap().put(entry.getKey(), historyPlayer(entry.getValue()));
        }

        if (historyMap != null) {
            for (CardPlayOption option : copyFrom.getMayPlay().values()) {
                forge.game.staticability.StaticAbility ability = option.getAbility().copy(historyCard(option.getHost(), cachedMap), true);
                forge.game.player.PlayerCollection players = new forge.game.player.PlayerCollection();
                for (Player player : option.getPlayers()) players.add(historyPlayer(player));
                newCopy.setMayPlay(players, option.getPayManaCost() == CardPlayOption.PayManaCost.NO,
                        option.getAltManaCost() == null ? null : option.getAltManaCost().copy(),
                        option.isWithFlash(), option.grantsZonePermissions(), ability);
            }
        }

        return newCopy;
    }

}
