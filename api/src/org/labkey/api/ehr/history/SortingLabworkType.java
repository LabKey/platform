/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.api.ehr.history;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: bimber
 * Date: 3/13/13
 * Time: 1:24 PM
 */
public class SortingLabworkType extends DefaultLabworkType
{
    private String _testType;
    private String _testCol = "testid";
    private String _sortCol = "sort_order";

    private Map<String, Integer> _tests = null;

    public SortingLabworkType(String name, String schemaName, String queryName, String testType, Module declaringModule)
    {
        super(name, schemaName, queryName, declaringModule);

        _testType = testType;
    }

    private Map<String, Integer> loadTests(boolean forceRefresh)
    {
        if (forceRefresh || _tests == null)
        {
            TableInfo ti = DbSchema.get("ehr_lookups", DbSchemaType.Module).getTable("lab_tests");
            assert ti != null;

            _tests = new CaseInsensitiveHashMap<>();
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set(_sortCol, _testCol), new SimpleFilter(FieldKey.fromString("type"), _testType), null);
            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    if (rs.getObject(_sortCol) != null)
                        _tests.put(rs.getString(_testCol), rs.getInt(_sortCol));
                }
            });
        }

        return _tests;
    }

    @Override
    protected Map<String, List<String>> getRows(TableSelector ts, final Collection<ColumnInfo> cols, final boolean redacted)
    {
        final Map<String, Map<Integer, List<String>>> rows = new CaseInsensitiveHashMap<>();
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet object) throws SQLException
            {
                Results rs = new ResultsImpl(object, cols);
                String runId = rs.getString(FieldKey.fromString("runId"));

                Map<Integer, List<String>> map = rows.get(runId);
                if (map == null)
                    map = new TreeMap<>();

                Integer sort = getSortOrder(rs);
                List<String> list = map.get(sort);
                if (list == null)
                    list = new ArrayList<>();

                String line = getLine(rs, redacted);
                if (line != null)
                    list.add(line);

                map.put(sort, list);
                rows.put(runId, map);
            }
        });

        Map<String, List<String>> sortedResults = new CaseInsensitiveHashMap<>();
        for (String runId : rows.keySet())
        {
            List<String> sorted = new ArrayList<>();
            Map<Integer, List<String>> map = rows.get(runId);
            for (Integer sort : map.keySet())
            {
                sorted.addAll(map.get(sort));
            }

            sortedResults.put(runId, sorted);
        }

        return sortedResults;
    }

    protected Integer getSortOrder(ResultSet rs) throws SQLException
    {
        String testId = rs.getString(_testIdField);
        if (testId == null)
            return 9999;

        loadTests(false);
        return _tests.containsKey(testId) ? _tests.get(testId) : 9999;
    }
}
