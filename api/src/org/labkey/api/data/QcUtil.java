/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.collections.Cache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.Pair;

import java.sql.SQLException;
import java.util.*;

/**
 * User: jgarms
 * Date: Jan 14, 2009
 */
public class QcUtil
{
    private static final String CACHE_PREFIX = QcUtil.class.getName() + "/";

    // Sentinel for the cache: if a container has no qc values set, we use this to indicate,
    // as null means a cache miss.
    private static final Map<String,String> NO_VALUES = Collections.unmodifiableMap(new HashMap<String,String>());

    private QcUtil() {}

    public static Set<String> getQcValues(Container c)
    {
        assert c != null : "Attempt to get QC values without a container";
        return getValuesAndLabels(c).keySet();
    }

    /**
     * Allows nulls and ""
     */
    public static boolean isValidQcValue(String value, Container c)
    {
        if (value == null || "".equals(value))
            return true;
        return isQcValue(value, c);
    }

    public static boolean isQcValue(String value, Container c)
    {
        return getQcValues(c).contains(value);
    }

    public static String getQcLabel(String qcValue, Container c)
    {
        Map<String,String> map = getValuesAndLabels(c);
        String label = map.get(qcValue);
        if (label != null)
            return label;
        return "";
    }

    /**
     * Given a container, this returns the container in which the QC values are defined.
     * It may be the container itself, or a parent container or project, or the root container.
     */
    public static Container getDefiningContainer(Container c)
    {
        return getValuesAndLabelsWithContainer(c).getKey();
    }

    public static Map<String,String> getValuesAndLabels(Container c)
    {
        return getValuesAndLabelsWithContainer(c).getValue();
    }

    /**
     * Return the Container in which these values are defined, along with the qc values.
     */
    public static Pair<Container,Map<String,String>> getValuesAndLabelsWithContainer(Container c)
    {
        String cacheKey = getCacheKey(c);

        //noinspection unchecked
        Map<String,String> result = (Map<String,String>)getCache().get(cacheKey);
        if (result == null)
        {
            result = getFromDb(c);
            if (result.isEmpty())
            {
                result = NO_VALUES;
                getCache().put(cacheKey, NO_VALUES);
            }
            else
            {
                getCache().put(cacheKey, result);
                return new Pair<Container,Map<String,String>>(c, Collections.unmodifiableMap(Collections.unmodifiableMap(result)));
            }
        }
        if (result == NO_VALUES)
        {
            // recurse
            assert !c.isRoot() : "We have no QC values for the root container. This should never happen";
            return getValuesAndLabelsWithContainer(c.getParent());
        }

        return new Pair<Container,Map<String,String>>(c, result);
    }

    /**
     * Sets the container given to inherit qc values from its
     * parent container, project, or site, whichever
     * in the hierarchy first has qc values.
     */
    public static void inheritQcValues(Container c) throws SQLException
    {
        deleteQcValues(c);
        clearCache(c);
    }

    private static void deleteQcValues(Container c) throws SQLException
    {
        TableInfo qcTable = CoreSchema.getInstance().getTableInfoQcValues();
        String sql = "DELETE FROM " + qcTable + " WHERE container = ?";
        Table.execute(CoreSchema.getInstance().getSchema(), sql, new Object[] {c.getId()});
    }

    /**
     * Sets the QC values and labels for this container.
     * Map should be value -> label.
     */
    public static void assignQcValues(Container c, String[] qcValues, String[] qcLabels) throws SQLException
    {
        assert qcValues.length > 0 : "No QC Values provided";
        assert qcValues.length == qcLabels.length : "Different number of values and labels provided";
        deleteQcValues(c);
        TableInfo qcTable = CoreSchema.getInstance().getTableInfoQcValues();
        // Need a map to use for each row
        Map<String,String> toInsert = new HashMap<String,String>();
        toInsert.put("container", c.getId());
        for (int i=0; i<qcValues.length; i++)
        {
            toInsert.put("qcValue", qcValues[i]);
            toInsert.put("label", qcLabels[i]);
            Table.insert(null, qcTable, toInsert);
        }
        clearCache(c);
    }

    private static Map<String,String> getFromDb(Container c)
    {
        Map<String,String> valuesAndLabels = new CaseInsensitiveHashMap<String>();
        try
        {
            TableInfo qcTable = CoreSchema.getInstance().getTableInfoQcValues();
            Set<String> selectColumns = new HashSet<String>();
            selectColumns.add("qcvalue");
            selectColumns.add("label");
            Filter filter = new SimpleFilter("container", c.getId());
            Map[] selectResults = Table.select(qcTable, selectColumns, filter, null, Map.class);

            //noinspection unchecked
            for (Map<String,String> m : selectResults)
            {
                valuesAndLabels.put(m.get("qcvalue"), m.get("label"));
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        return valuesAndLabels;
    }

    private static String getCacheKey(Container c)
    {
        return CACHE_PREFIX + c.getId();
    }

    private static Cache getCache()
    {
        return Cache.getShared();
    }

    public static void containerDeleted(Container c)
    {
        clearCache(c);
    }

    public static void clearCache(Container c)
    {
        getCache().removeUsingPrefix(getCacheKey(c));
    }

    /**
     * Returns the default QC values as originally implemented: "Q" and "N",
     * mapped to their labels.
     *
     * This should only be necessary at upgrade time.
     */
    public static Map<String,String> getDefaultQcValues()
    {
        Map<String,String> qcMap = new HashMap<String,String>();
        qcMap.put("Q", "Data currently under quality control review.");
        qcMap.put("N", "Required field marked by site as 'data not available'.");

        return qcMap;
    }
}
