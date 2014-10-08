/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* User: adam
* Date: May 21, 2011
* Time: 11:16:36 AM
*/
public class UpgradeUtils
{
    private static final Logger LOG = Logger.getLogger(UpgradeUtils.class);

    /**
     * Uniquifies values in a column, making it possible to add a UNIQUE CONSTRAINT/INDEX OR change a case-sensitive
     * UNIQUE CONSTRAINT/INDEX to case-insensitive. This is designed to be called from UpdateCode that's invoked by an
     * upgrade script. Column is uniquified by adding _2, _3, etc. to any values that duplicate previous values in that
     * container.
     *
     * Note: PostgreSQL UNIQUE CONSTRAINTs are ALWAYS case-sensitive; you must create a UNIQUE INDEX to constrain in
     * a case-insensitive manner.
     *
     * @param column Column to uniquify.  Must have a character type.  Parent table must have a Container column.
     *      (It wouldn't be hard to add support for global tables, but there's no need right now.)
     * @param sort Determines the order that rows are uniquifyied; those earlier in the sort take precedence.
     * @param caseSensitive Determines whether uniquifying is done on a case-sensitive or case-insensitive basis
     * @param ignoreNulls If true, multiple null values are ignored (not updated).  If false, subsequent null values are
     *      updated with a value of _ followed by a unique integer.  (Some databases allow multiple null values in a
     *      UNIQUE constraint, some don't.)
     * @throws SQLException
     */
    public static void uniquifyValues(ColumnInfo column, Sort sort, boolean caseSensitive, boolean ignoreNulls) throws SQLException
    {
        LOG.info("Removing duplicate values from " + column.getParentTable().toString() + "." + column.getName());

        // Do an aggregate query to determine all containers with uniqueness problems in this column
        List<String> containersToCorrect = getContainersWithDuplicateValues(column, caseSensitive, ignoreNulls);

        if (!containersToCorrect.isEmpty())
        {
            DbScope scope = column.getParentTable().getSchema().getScope();

            try (DbScope.Transaction transction = scope.ensureTransaction())
            {
                // Fix up the values in each container
                for (String cid : containersToCorrect)
                    uniquifyValuesInContainer(column, cid, sort, caseSensitive, ignoreNulls);

                transction.commit();
            }
        }
    }


    private static void uniquifyValuesInContainer(ColumnInfo col, String cid, Sort sort, boolean caseSensitive, boolean ignoreNulls) throws SQLException
    {
        TableInfo table = col.getParentTable();
        String columnName = col.getName();
        Set<ColumnInfo> selectColumns = Collections.singleton(col);

        LOG.info("  Updating duplicate values in container " + cid);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), cid);

        if (ignoreNulls)
            filter.addCondition(col, null, CompareType.NONBLANK);

        // First, grab all the existing values so when we have to change a value we don't collide with another existing value.
        Set<String> existingValues = getSet(caseSensitive);
        existingValues.addAll(new TableSelector(table, selectColumns, filter, null).getCollection(String.class));

        // Now enumerate the rows in the specified order and fix up the duplicates.  Use selectForDisplay to ensure PKs are selected.
        TableSelector selector = new TableSelector(table, selectColumns, filter, sort).setForDisplay(true);

        Map<String, Object>[] maps = selector.getMapArray();
        Set<String> newValues = getSet(caseSensitive);

        for (Map<String, Object> map : maps)
        {
            String value = (String)map.get(columnName);

            if (newValues.contains(value))
            {
                // Change null to blank -- "_2" is a bit better than "null_2"
                String baseValue = null == value ? "" : value;
                int i = 1;
                String candidateValue;

                do
                {
                    i++;
                    candidateValue = baseValue + "_" + i;
                }
                while(newValues.contains(candidateValue) || existingValues.contains(candidateValue));

                LOG.info("    Changing " + value + " to " + candidateValue);

                value = candidateValue;

                ArrayList<Object> pkVals = new ArrayList<>(table.getPkColumnNames().size());

                for (String pkName : table.getPkColumnNames())
                    pkVals.add(map.get(pkName));

                Table.update(null, table, PageFlowUtil.map(columnName, value), pkVals.toArray(), filter, Level.WARN);
            }

            newValues.add(value);
        }
    }


    private static Set<String> getSet(boolean caseSensitive)
    {
        if (caseSensitive)
            return new HashSet<>();
        else
            return new CaseInsensitiveHashSet();
    }


    private static List<String> getContainersWithDuplicateValues(ColumnInfo column, boolean caseSensitive, boolean ignoreNulls) throws SQLException
    {
        TableInfo table = column.getParentTable();
        String groupBy;

        if (caseSensitive)
            groupBy = "t." + column.getSelectName();
        else
            groupBy = "LOWER(t." + column.getSelectName() + ")";

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT DISTINCT Container FROM ");
        sql.append(table, "t");

        if (ignoreNulls)
        {
            sql.append(" WHERE ");
            sql.append(column.getSelectName());
            sql.append(" IS NOT NULL");
        }

        sql.append(" GROUP BY Container, ");
        sql.append(groupBy);
        sql.append(" HAVING COUNT(*) > 1");

        return new SqlSelector(table.getSchema(), sql).getArrayList(String.class);
    }
}
