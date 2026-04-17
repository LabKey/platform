package org.labkey.api.dataiterator;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @deprecated Use {@link MapDataIterator#of(List)}
 */
@Deprecated
public class ListofMapsDataIterator extends AbstractMapDataIterator.ListOfMapsDataIterator
{
    public ListofMapsDataIterator(Set<String> colNames, List<Map<String, Object>> rows)
    {
        super(new DataIteratorContext(), colNames, rows);
    }

    @Deprecated
    public static class Builder extends DataIteratorBuilder.Wrapper
    {
        public Builder(Set<String> colNames, List<Map<String, Object>> rows)
        {
            super(new ListofMapsDataIterator(colNames, rows));
        }
    }
}
