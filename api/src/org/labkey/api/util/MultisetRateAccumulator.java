package org.labkey.api.util;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * User: adam
 * Date: 6/11/13
 * Time: 12:34 PM
 */
public class MultisetRateAccumulator<E> extends RateAccumulator<Multiset<E>, E>
{
    private Comparator<Entry<E>> OCCURENCE_COMPARATOR = new Comparator<Entry<E>>() {
   		public int compare(Entry<E> e1, Entry<E> e2) {
   			return e2.getCount() - e1.getCount() ;
   		}
   	};

    public MultisetRateAccumulator(long start)
    {
        super(start, HashMultiset.<E>create());
    }

    public MultisetRateAccumulator(long start, Multiset<E> multiset)
    {
        super(start, multiset);
    }

    @Override
    public void accumulate(E element)
    {
        _counter.add(element);
    }

    @Override
    public long getCount()
    {
        return _counter.size();
    }

    // Based on http://www.philippeadjiman.com/blog/2010/02/20/a-generic-method-for-sorting-google-collections-multiset-per-entry-count
    public List<Entry<E>> getSortedEntries()
    {
    	List<Entry<E>> sortedByCount = new ArrayList<>(_counter.entrySet());
    	Collections.sort(sortedByCount, OCCURENCE_COMPARATOR);

    	return sortedByCount;
    }
}
