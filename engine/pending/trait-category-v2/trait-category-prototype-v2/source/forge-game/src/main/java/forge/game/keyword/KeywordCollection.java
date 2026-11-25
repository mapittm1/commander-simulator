package forge.game.keyword;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import forge.game.card.Card;
import forge.game.card.ICardTraitChanges;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;

public class KeywordCollection implements ICardTraitChanges, Iterable<KeywordInterface> {
    // don't use enumKeys it causes a slow down
    private final Multimap<Keyword, KeywordInterface> map = MultimapBuilder.hashKeys()
            .linkedHashSetValues().build();

    // Cache membership references only. Every trait-list query remains live.
    private transient volatile KeywordInterface[] traitMembers;
    private transient volatile boolean valuesExposed;

    private void invalidateTraitMembers() {
        traitMembers = null;
    }

    private KeywordInterface[] traitMembers() {
        KeywordInterface[] members = traitMembers;
        if (members == null) {
            members = map.values().toArray(new KeywordInterface[map.size()]);
            traitMembers = members;
        }
        return members;
    }

    private Collection<KeywordInterface> nativeIterationValues() {
        return getClass() == KeywordCollection.class ? map.values() : getValues();
    }

    public KeywordCollection() {
        super();
    }

    public boolean contains(Keyword keyword) {
        return map.containsKey(keyword);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.values().size();
    }

    public int getAmount(Keyword keyword) {
        int amount = 0;
        for (KeywordInterface inst : map.get(keyword)) {
            amount += inst.getAmount();
        }
        return amount;
    }

    public KeywordInterface add(String k) {
        KeywordInterface inst = Keyword.getInstance(k);
        if (insert(inst)) {
            return inst;
        }
        return null;
    }
    public boolean insert(KeywordInterface inst) {
        Keyword keyword = inst.getKeyword();
        Collection<KeywordInterface> list = map.get(keyword);
        boolean redundant = false;
        if (!list.isEmpty()) {
            // A keyword callback may retain this mutable native view.
            valuesExposed = true;
            invalidateTraitMembers();
            redundant = inst.redundant(list);
        }
        if (!redundant) {
            try {
                list.add(inst);
                return true;
            } finally {
                invalidateTraitMembers();
            }
        }
        return false;
    }

    public void addAll(Iterable<String> keywords) {
        for (String k : keywords) {
            add(k);
        }
    }

    public boolean insertAll(Iterable<KeywordInterface> inst) {
        boolean result = false;
        for (KeywordInterface k : inst) {
            if (insert(k)) {
                result = true;
            }
        }
        return result;
    }

    public boolean remove(String keyword) {
        Iterator<KeywordInterface> it = map.values().iterator();

        boolean result = false;
        while (it.hasNext()) {
            KeywordInterface k = it.next();
            if (k.getOriginal().startsWith(keyword)) {
                it.remove();
                invalidateTraitMembers();
                result = true;
            }
        }

        return result;
    }

    public boolean remove(KeywordInterface keyword) {
        try {
            return map.remove(keyword.getKeyword(), keyword);
        } finally {
            invalidateTraitMembers();
        }
    }

    public boolean removeAll(Keyword kenum) {
        try {
            return !map.removeAll(kenum).isEmpty();
        } finally {
            invalidateTraitMembers();
        }
    }

    public boolean removeAll(Iterable<String> keywords) {
        boolean result = false;
        for (String k : keywords) {
            if (remove(k)) {
                result = true;
            }
        }
        return result;
    }

    public boolean removeInstances(Iterable<KeywordInterface> keywords) {
        boolean result = false;
        for (KeywordInterface k : keywords) {
            try {
                if (map.remove(k.getKeyword(), k)) result = true;
            } finally {
                invalidateTraitMembers();
            }
        }
        return result;
    }

    public void clear() {
        try {
            map.clear();
        } finally {
            invalidateTraitMembers();
        }
    }

    public boolean contains(String keyword) {
        for (KeywordInterface inst : map.values()) {
            if (keyword.equals(inst.getOriginal())) {
                return true;
            }
        }
        return false;
    }

    public int getAmount(String k) {
        int amount = 0;
        for (KeywordInterface inst : map.values()) {
            if (k.equals(inst.getOriginal())) {
                amount++;
            }
        }
        return amount;
    }

    public Collection<KeywordInterface> getValues() {
        valuesExposed = true;
        invalidateTraitMembers();
        return map.values();
    }

    public Collection<KeywordInterface> getValues(final Keyword keyword) {
        valuesExposed = true;
        invalidateTraitMembers();
        return map.get(keyword);
    }

    public List<String> asStringList() {
        List<String> result = Lists.newArrayList();
        for (KeywordInterface kw : nativeIterationValues()) {
            result.add(kw.getOriginal());
        }
        return result;
    }

    public KeywordCollectionView getView() {
        return new KeywordCollectionView(nativeIterationValues().stream().map(KeywordInterface::getView).collect(Collectors.toList()));
    }

    public void setHostCard(final Card host) {
        for (KeywordInterface k : map.values()) {
            k.setHostCard(host);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb  = new StringBuilder();

        sb.append(map.values());
        return sb.toString();
    }

    @Override
    public List<SpellAbility> applySpellAbility(List<SpellAbility> list) {
        if (map.isEmpty()) {
            return list;
        }
        for (KeywordInterface k : nativeIterationValues()) {
            k.applySpellAbility(list);
        }
        return list;
    }
    @Override
    public List<Trigger> applyTrigger(List<Trigger> list) {
        if (map.isEmpty()) {
            return list;
        }
        for (KeywordInterface k : nativeIterationValues()) {
            k.applyTrigger(list);
        }
        return list;
    }
    @Override
    public List<ReplacementEffect> applyReplacementEffect(List<ReplacementEffect> list) {
        if (map.isEmpty()) {
            return list;
        }
        for (KeywordInterface k : nativeIterationValues()) {
            k.applyReplacementEffect(list);
        }
        return list;
    }
    @Override
    public List<StaticAbility> applyStaticAbility(List<StaticAbility> list) {
        // most cards carry no keywords, and building the multimap's iterator to discover that
        // is the bulk of this call's cost
        if (map.isEmpty()) {
            return list;
        }
        for (KeywordInterface k : nativeIterationValues()) {
            k.applyStaticAbility(list);
        }
        return list;
    }
    /** Inspect live lists only when the standard keyword applicators are inherited. */
    public final boolean hasNoStaticTraitsForCache() {
        if (getClass() != KeywordCollection.class) return false;
        if (map.isEmpty()) return true;
        if (!valuesExposed) {
            for (KeywordInterface keyword : traitMembers()) {
                if (!(keyword instanceof KeywordInstance<?> instance)
                        || !instance.hasNoStaticTraitsForCache()) return false;
            }
            return true;
        }
        for (KeywordInterface keyword : map.values()) {
            if (!(keyword instanceof KeywordInstance<?> instance)
                    || !instance.hasNoStaticTraitsForCache()) return false;
        }
        return true;
    }

    public final boolean hasNoReplacementTraitsForCache() {
        if (getClass() != KeywordCollection.class) return false;
        if (map.isEmpty()) return true;
        if (!valuesExposed) {
            for (KeywordInterface keyword : traitMembers()) {
                if (!(keyword instanceof KeywordInstance<?> instance)
                        || !instance.hasNoReplacementTraitsForCache()) return false;
            }
            return true;
        }
        for (KeywordInterface keyword : map.values()) {
            if (!(keyword instanceof KeywordInstance<?> instance)
                    || !instance.hasNoReplacementTraitsForCache()) return false;
        }
        return true;
    }

    @Override
    public KeywordCollection copy(Card host, boolean lki) {
        KeywordCollection result = new KeywordCollection();
        for (KeywordInterface ki : nativeIterationValues()) {
            result.insert(ki.copy(host, lki));
        }
        return result;
    }

    public void applyChanges(Iterable<? extends IKeywordsChange> changes) {
        for (final IKeywordsChange ck : changes) {
            ck.applyKeywords(this);
        }
    }

    @Override
    public Iterator<KeywordInterface> iterator() {
        final Iterator<KeywordInterface> delegate = map.values().iterator();
        if (valuesExposed || getClass() != KeywordCollection.class) return delegate;
        return new Iterator<>() {
            @Override public boolean hasNext() { return delegate.hasNext(); }
            @Override public KeywordInterface next() { return delegate.next(); }
            @Override public void remove() {
                try {
                    delegate.remove();
                } finally {
                    invalidateTraitMembers();
                }
            }
            @Override public void forEachRemaining(java.util.function.Consumer<? super KeywordInterface> action) {
                delegate.forEachRemaining(action);
            }
        };
    }
}
