/*
 * Copyright (c) 2010-2026 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.util.Pair;
import org.labkey.api.view.template.PageConfig;

import java.util.Collections;
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

    /**
     * GH Issue 1584: aliases the framework puts on a built-in column, such as "container" on the Sample Type "Folder"
     * column. They outrank every user-configurable token so a field alias or label can never steal one.
     */
    default Set<String> getSystemImportAliasSet()
    {
        return Set.of();
    }

    String getName();

    String getLabel();

    /**
     * The label as the designer set it, or null when this implementation derives one from the name instead. ColumnInfo
     * derives, so getLabel() alone cannot tell a chosen label from a generated one.
     */
    @Nullable
    default String getLabelValue()
    {
        return getLabel();
    }

    String getPropertyURI();

    class Helper
    {
        /**
         * Creates a set of the different possible aliases for import (name, label, import aliases) for a single column.
         */
        public static <T extends ImportAliasable> Set<String> createImportSet(T property)
        {
            if (property == null)
                return Collections.emptySet();

            Set<String> aliases = new CaseInsensitiveHashSet();
            aliases.add(property.getName());
            if (property.getLabel() != null)
                aliases.add(property.getLabel());
            if (!property.getImportAliasSet().isEmpty())
                aliases.addAll(property.getImportAliasSet());
            aliases.addAll(property.getSystemImportAliasSet());
            return aliases;
        }

        /**
         * Creates a mapping of many different possible names (actual name, label/caption, property URI, etc).
         * for a column to the column itself. Useful to provide flexibility in how the data is labeled during imports.
         */
        public static <T extends ImportAliasable> Map<String, T> createImportMap(List<T> properties, boolean includeMVIndicators)
        {
            return createImportMap(properties, includeMVIndicators, null);
        }

        /**
         * GH Issue 1585: {@code ambiguousTokens}, when supplied, collects every token two properties tie for. Which one
         * such a token resolves to is arbitrary, so a caller that cares can refuse to act on it.
         */
        public static <T extends ImportAliasable> Map<String, T> createImportMap(List<T> properties, boolean includeMVIndicators, @Nullable Set<String> ambiguousTokens)
        {
            Map<String, Pair<T, Rank>> ranked = new CaseInsensitiveHashMap<>(properties.size() * 3);

            for (T property : properties)
            {
                put(ranked, ambiguousTokens, property.getPropertyURI(), property, Rank.propertyUri);
                // The decapitalized legal-identifier form of the name, kept for backwards compatibility
                put(ranked, ambiguousTokens, PageConfig.makeIdFromName(property.getName()), property, Rank.idFromName);

                for (String alias : property.getImportAliasSet())
                    put(ranked, ambiguousTokens, alias, property, Rank.userAlias);

                // If no label, columns will create one for captions
                put(ranked, ambiguousTokens, null != property.getLabel() ? property.getLabel() : ColumnInfo.labelFromName(property.getName()), property, Rank.label);

                if (includeMVIndicators && property.isMvEnabled())
                    put(ranked, ambiguousTokens, property.getName() + MvColumn.MV_INDICATOR_SUFFIX, property, Rank.mvIndicator);

                for (String alias : property.getSystemImportAliasSet())
                    put(ranked, ambiguousTokens, alias, property, Rank.systemAlias);

                put(ranked, ambiguousTokens, property.getName(), property, Rank.name);
            }

            Map<String, T> m = new CaseInsensitiveHashMap<>(ranked.size());
            ranked.forEach((token, pair) -> m.put(token, pair.first));
            return m;
        }

        /** Precedence for resolving an incoming token to a property, lowest first. GH Issue 1584. */
        private enum Rank
        {
            propertyUri,
            idFromName,
            userAlias,
            label,
            mvIndicator,
            systemAlias,
            name
        }

        private static <T extends ImportAliasable> void put(Map<String, Pair<T, Rank>> ranked, @Nullable Set<String> ambiguousTokens,
                String token, T property, Rank rank)
        {
            if (null == token)
                return;

            Pair<T, Rank> existing = ranked.get(token);

            if (null == existing || rank.compareTo(existing.second) > 0)
            {
                ranked.put(token, new Pair<>(property, rank));
                // A higher-ranked claim settles a tie recorded earlier, so the token is no longer ambiguous.
                if (null != ambiguousTokens)
                    ambiguousTokens.remove(token);
            }
            // Only a tie on a token the designer controls is worth reporting; a tie on a propertyURI or name is a bug elsewhere.
            else if (null != ambiguousTokens && rank == existing.second && existing.first != property
                    && isDesignerToken(rank, existing.first, property))
            {
                ambiguousTokens.add(token);
            }
        }

        /**
         * GH Issue 1585: a label this property derived from its own name is not a token anyone chose, so two of those
         * colliding is a field-naming coincidence with no label to correct. One explicit label makes it a design problem.
         */
        private static <T extends ImportAliasable> boolean isDesignerToken(Rank rank, T existing, T incoming)
        {
            if (rank == Rank.userAlias)
                return true;
            return rank == Rank.label && (null != existing.getLabelValue() || null != incoming.getLabelValue());
        }
    }
}
