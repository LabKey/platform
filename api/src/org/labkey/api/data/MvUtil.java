/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.labkey.api.Constants;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for dealing with Missing Value Indicators
 */
public class MvUtil
{
    private static final Cache<Container, Map<String, String>> CACHE = CacheManager.getBlockingCache(Constants.getMaxContainers(), CacheManager.YEAR, "Missing value indicators", (c, argument) -> getFromDb(c));

    private MvUtil() {}

    public static Set<String> getMvIndicators(@NotNull Container c)
    {
        assert c != null : "Attempt to get missing value indicators without a container";
        return getIndicatorsAndLabels(c).keySet();
    }

    /**
     * Allows nulls and ""
     */
    public static boolean isValidMvIndicator(String indicator, @NotNull Container c)
    {
        if (indicator == null || indicator.isEmpty())
            return true;
        return isMvIndicator(indicator, c);
    }

    public static boolean isMvIndicator(String indicator, @NotNull Container c)
    {
        return getIndicatorsAndLabels(c).containsKey(indicator);
    }

    public static String getMvLabel(String mvIndicator, @NotNull Container c)
    {
        Map<String, String> map = getIndicatorsAndLabels(c);
        String label = map.get(mvIndicator);
        if (label != null)
            return label;
        return "";
    }

    /**
     * Given a container, this returns the container in which the MV indicators are defined.
     * It may be the container itself, or a parent container or project, or the root container.
     */
    public static Container getDefiningContainer(@NotNull Container c)
    {
        return getIndicatorsAndLabelsWithContainer(c).getKey();
    }

    /**
     * Returns an unmodifiable, case-insensitive map of MV indicator -> label for this container (may be defined in
     * this container or inherited)
     */
    public static @NotNull Map<String, String> getIndicatorsAndLabels(@NotNull Container c)
    {
        return getIndicatorsAndLabelsWithContainer(c).getValue();
    }

    /**
     * Returns the Container in which these indicators are defined, along with the indicators
     */
    public static @NotNull Pair<Container, Map<String, String>> getIndicatorsAndLabelsWithContainer(@NotNull Container c)
    {
        Map<String, String> result = CACHE.get(c);

        if (null == result)
        {
            // recurse
            assert !c.isRoot() : "We have no MV indicators for the root container. This should never happen!";
            return getIndicatorsAndLabelsWithContainer(c.getParent());
        }

        return new Pair<>(c, result);
    }

    /**
     * Sets the container given to inherit indicators from its
     * parent container, project, or site, whichever
     * in the hierarchy first has mv indicators.
     */
    public static void inheritMvIndicators(@NotNull Container c)
    {
        deleteMvIndicators(c);
    }

    private static void deleteMvIndicators(@NotNull Container c)
    {
        TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();
        String sql = "DELETE FROM " + mvTable + " WHERE container = ?";
        new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(sql, c);
        clearCache(c);
    }

    /**
     * Sets the indicators and labels for this container.
     * Map should be value -> label.
     */
    public static void assignMvIndicators(@NotNull Container c, @NotNull String[] indicators, @NotNull String[] labels)
    {
        assert indicators.length > 0 : "No indicators provided";
        assert indicators.length == labels.length : "Different number of indicators and labels provided";
        deleteMvIndicators(c);
        TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();
        // Need a map to use for each row
        Map<String, String> toInsert = new HashMap<>();
        toInsert.put("container", c.getId());
        for (int i = 0; i < indicators.length; i++)
        {
            toInsert.put("mvIndicator", indicators[i]);
            toInsert.put("label", labels[i]);
            Table.insert(null, mvTable, toInsert);
        }
        clearCache(c);
    }

    // Returns an unmodifiable, case-insensitive map of MV indicators -> labels in this folder OR null if they're inherited
    private static @Nullable Map<String, String> getFromDb(@NotNull Container c)
    {
        TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();
        Set<String> selectColumns = new CsvSet("mvindicator, label");
        Filter filter = new SimpleFilter(FieldKey.fromParts("container"), c.getId());
        Map<String, String> indicatorsAndLabels = new TableSelector(mvTable, selectColumns, filter, null).getValueMap();

        return indicatorsAndLabels.isEmpty() ? null : Collections.unmodifiableMap(new CaseInsensitiveTreeMap<>(indicatorsAndLabels));
    }

    public static void containerDeleted(@NotNull Container c)
    {
        deleteMvIndicators(c);
    }

    public static void clearCache(@NotNull Container c)
    {
        CACHE.remove(c);
    }

    /**
     * Returns the default indicators as originally implemented: "Q" and "N",
     * mapped to their labels. This should only be necessary at bootstrap time.
     */
    public static Map<String, String> getDefaultMvIndicators()
    {
        Map<String, String> mvMap = new HashMap<>();
        mvMap.put("Q", "Data currently under quality control review.");
        mvMap.put("N", "Data in this field has been marked as not usable.");

        return mvMap;
    }
}
