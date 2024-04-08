package org.labkey.api.collections;

import org.apache.commons.collections4.MultiSet;

import java.util.Map;
import java.util.stream.Collectors;

public class MultiSetUtils
{
    // Provides a Map<E, Integer> occurrence map from a MultiSet, which is often more convenient than MultiSet's entry set
    public static <E> Map<E, Integer> getOccurrenceMap(MultiSet<E> multiSet)
    {
        return multiSet.entrySet().stream()
            .collect(Collectors.toMap(MultiSet.Entry::getElement, MultiSet.Entry::getCount));
    }
}
