package org.labkey.api.data;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.MvColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Sep 17, 2010
 */
public interface ImportAliasable
{
    public boolean isMvEnabled();

    public Set<String> getImportAliasSet();

    public String getName();

    public String getLabel();

    public String getPropertyURI();

    public static class Helper
    {
        public static <T extends ImportAliasable> Map<String, T> createImportMap(List<T> properties, boolean includeMVIndicators)
        {
            Map<String, T> m = new CaseInsensitiveHashMap<T>(properties.size() * 3);
            List<T> reversedProperties = new ArrayList<T>(properties.size());

            // Reverse the order of the descriptors so that we can preserve the right priority for resolving by names, aliases, etc
            for (T prop : properties)
            {
                reversedProperties.add(0, prop);
            }

            // PropertyURI is lowest priority, so put it in the map first so it will be overwritten by higher priority usages
            for (T property : reversedProperties)
            {
                m.put(property.getPropertyURI(), property);
            }
            // Then aliases
            for (T property : reversedProperties)
            {
                for (String alias : property.getImportAliasSet())
                {
                    m.put(alias, property);
                }
            }
            // Then labels
            for (T property : reversedProperties)
            {
                if (null != property.getLabel())
                    m.put(property.getLabel(), property);
                else
                    m.put(ColumnInfo.labelFromName(property.getName()), property); // If no label, columns will create one for captions
            }
            if (includeMVIndicators)
            {
                // Then missing value-specific columns
                for (T property : reversedProperties)
                {
                    if (property.isMvEnabled())
                        m.put(property.getName() + MvColumn.MV_INDICATOR_SUFFIX, property);
                }
            }
            // Finally, names have the highest priority
            for (T property : reversedProperties)
            {
                m.put(property.getName(), property);
            }
            return m;
        }
    }
}
