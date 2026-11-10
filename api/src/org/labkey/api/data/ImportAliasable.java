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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.util.Pair;
import org.labkey.api.view.template.PageConfig;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

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
     * GH Issue 1585: aliases the framework puts on a built-in column, such as "container" on the Sample Type "Folder"
     * column. They outrank every user-configurable token so a field alias or label can never steal one.
     */
    default Set<String> getSystemImportAliasSet()
    {
        return Set.of();
    }

    String getName();

    String getLabel();

    /**
     * The stored label, without the substitution getLabel() may make for a blank one. Still not proof of a chosen
     * label: ColumnInfo stores the generated one for an unlabelled field, so compare against labelFromName().
     */
    @Nullable
    default String getLabelValue()
    {
        return getLabel();
    }

    String getPropertyURI();

    /**
     * GH Issue 1585: whether the designer chose this label, as opposed to the one generated from the field name.
     * PropertyColumn.copyAttributes stores labelFromName() on every unlabelled field, so a non-null label is not proof
     * of a chosen one; a label that merely restates its own field name counts as generated either way.
     */
    static boolean isChosenLabel(ImportAliasable property)
    {
        String label = property.getLabelValue();
        return null != label && !label.equalsIgnoreCase(ColumnInfo.labelFromName(property.getName()));
    }

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
         * GH Issue 1585: the designer's import aliases followed by the framework's, for callers that report which
         * headers resolve to a column. Nothing reads the result back into a save, so the two need not stay apart.
         */
        public static Set<String> allImportAliases(ImportAliasable property)
        {
            Set<String> aliases = new LinkedHashSet<>(property.getImportAliasSet());
            aliases.addAll(property.getSystemImportAliasSet());
            return aliases;
        }

        /**
         * Creates a mapping of many different possible names (actual name, label/caption, property URI, etc).
         * for a column to the column itself. Useful to provide flexibility in how the data is labeled during imports.
         */
        public static <T extends ImportAliasable> Map<String, T> createImportMap(List<T> properties, boolean includeMVIndicators)
        {
            return createImportMap(properties, includeMVIndicators, null, Set.of());
        }

        /**
         * GH Issue 1585: {@code ambiguousTokens}, when supplied, collects every token two properties tie for at a rank
         * the designer controls; which one such a token resolves to is arbitrary, so a caller that cares can refuse to
         * act on it. {@code kindSystemAliases} are the tokens the domain kind claims for its built-in columns, so a
         * field that shadowed such a column cannot inherit its token -- pass what
         * DataIteratorUtil.kindSystemImportAliases() returns for the same table, or an empty set to skip the check.
         *
         * <p>A tie resolves to the first property here and to the last in DataIteratorUtil.buildTableMap; each keeps
         * what its own callers have always had, so read it out of {@code ambiguousTokens} rather than relying on either.
         */
        public static <T extends ImportAliasable> Map<String, T> createImportMap(List<T> properties, boolean includeMVIndicators,
                @Nullable Set<String> ambiguousTokens, @NotNull Set<String> kindSystemAliases)
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

            removeShadowedTokens(ranked, kindSystemAliases, Rank.systemAlias);

            Map<String, T> m = new CaseInsensitiveHashMap<>(ranked.size());
            ranked.forEach((token, pair) -> m.put(token, pair.first));
            return m;
        }

        /**
         * GH Issue 1585: drops every token the kind claims for a built-in column that something lower-ranked got hold of.
         * A field named for such a column shadows it, taking the kind's token off the table, and leaving the token to
         * that field's own alias or label would write the server's value into it. A claim at {@code systemAliasRank} or
         * above -- the built-in column itself, by alias or by name -- still wins.
         *
         * <p>Shared with DataIteratorUtil.buildTableMap, which ranks the same tokens over a TableInfo. The two keep
         * their own rank orders and tie-breaks, which their respective callers have always had.
         */
        public static <T, R extends Enum<R>> void removeShadowedTokens(Map<String, Pair<T, R>> ranked,
                @NotNull Set<String> kindSystemAliases, R systemAliasRank)
        {
            removeShadowedTokens(ranked, kindSystemAliases, systemAliasRank, (token, target) -> {});
        }

        /** As above, handing {@code onRemoved} each dropped token and the field that had claimed it. */
        public static <T, R extends Enum<R>> void removeShadowedTokens(Map<String, Pair<T, R>> ranked,
                @NotNull Set<String> kindSystemAliases, R systemAliasRank, @NotNull BiConsumer<String, T> onRemoved)
        {
            for (String token : kindSystemAliases)
            {
                Pair<T, R> claim = ranked.get(token);
                if (null != claim && claim.second.compareTo(systemAliasRank) < 0)
                {
                    ranked.remove(token);
                    onRemoved.accept(token, claim.first);
                }
            }
        }

        /** Precedence for resolving an incoming token to a property, lowest first. GH Issue 1585. */
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
         * GH Issue 1585: a label derived from the property's own name is not a token anyone chose, so two of those
         * colliding is a field-naming coincidence with no label to correct. One chosen label makes it a design problem.
         */
        private static boolean isDesignerToken(Rank rank, ImportAliasable existing, ImportAliasable incoming)
        {
            if (rank == Rank.userAlias)
                return true;
            return rank == Rank.label && (isChosenLabel(existing) || isChosenLabel(incoming));
        }
    }
}
