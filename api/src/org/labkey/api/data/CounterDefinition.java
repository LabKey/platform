package org.labkey.api.data;

import java.util.List;
import java.util.Set;

/**
 * Define DB Sequence that is unique in a container and on the values of the paired columns.
 * A Java customizer to a query describes this.
 */
public interface CounterDefinition
{
    /**
     * @return the name of the counter
     */
    String getCounterName();

    /**
     * @return list of names of paired columns
     */
    List<String> getPairedColumnNames();

    /**
     * @return set of names of columns to which the sequence is attached
     */
    Set<String> getAttachedColumnNames();

    /**
     * @param prefix A string to prepend to the sequence name
     * @param pairedValues values of paired columns
     * @return DB Sequence name constructed from pairedValues
     */
    String getDbSequenceName(String prefix, List<String> pairedValues);
}
