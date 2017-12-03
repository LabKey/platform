/*
 * Copyright (c) 2010-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.data;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.MvColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Common methods for fields/properties that may be referred to by various names in incoming data. Useful so that we
 * can resolve them in a consistent way across different data types.
 *
 * User: jeckels
 * Date: Sep 17, 2010
 */
public interface ImportAliasable
{
    boolean isMvEnabled();

    Set<String> getImportAliasSet();

    String getName();

    String getLabel();

    String getPropertyURI();

    class Helper
    {
        /**
         * Creates a mapping of many different possible names (actual name, label/caption, property URI, etc).
         * for a column to the column itself. Useful to provide flexibility in how the data is labeled during imports.
         */
        public static <T extends ImportAliasable> Map<String, T> createImportMap(List<T> properties, boolean includeMVIndicators)
        {
            Map<String, T> m = new CaseInsensitiveHashMap<>(properties.size() * 3);
            List<T> reversedProperties = new ArrayList<>(properties.size());

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
            // Then propName variant of name
            for (T property : reversedProperties)
            {
                m.put(ColumnInfo.propNameFromName(property.getName()), property);
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
