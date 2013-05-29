/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.reports.getdata;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Pair;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: 5/16/13
 */
public class AggregateQueryDataTransform extends AbstractQueryReportDataTransform
{
    private final SimpleFilter _filters;
    private final List<Aggregate> _aggregates;
    private final List<FieldKey> _groupBys;
    private final Pair<List<FieldKey>, FieldKey> _pivotBy;

    private static final String SUBQUERY_ALIAS = "A";

    public AggregateQueryDataTransform(QueryReportDataSource source, SimpleFilter filters, List<Aggregate> aggregates, List<FieldKey> groupBys, Pair<List<FieldKey>, FieldKey> pivotBy)
    {
        super(source);
        _filters = filters;
        _aggregates = aggregates;
        _groupBys = groupBys;
        _pivotBy = pivotBy;
    }

    @Override
    public String getLabKeySQL()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        String separator = "";
        if (_groupBys.isEmpty())
        {
            sb.append(SUBQUERY_ALIAS);
            sb.append(".*");
            separator = ", ";
        }
        else
        {
            appendFieldKeyList(sb, _groupBys, SUBQUERY_ALIAS);
            if (!_groupBys.isEmpty())
            {
                separator = ", ";
            }
        }

        for (Aggregate aggregate : _aggregates)
        {
            sb.append(separator);
            separator = ", ";
            sb.append(aggregate.toLabKeySQL());
        }

        sb.append(" FROM (\n");
        sb.append(getSource().getLabKeySQL());
        sb.append("\n) ");
        sb.append(SUBQUERY_ALIAS);
        String where = _filters.toLabKeySQL(getSource().getColumnMap(getRequiredInputs()));
        if (where.length() > 0)
        {
            sb.append("\nWHERE ");
            sb.append(where);
        }
        if (!_groupBys.isEmpty())
        {
            sb.append("\nGROUP BY ");
            appendFieldKeyList(sb, _groupBys, SUBQUERY_ALIAS);
        }
        if (_pivotBy != null)
        {
            sb.append("\nPIVOT ");
            appendFieldKeyList(sb, _pivotBy.getKey(), SUBQUERY_ALIAS);
            sb.append(" BY ");
            sb.append(SUBQUERY_ALIAS);
            sb.append(".");
            sb.append(_pivotBy.getValue().toSQLString());
        }

        return sb.toString();
    }

    private void appendFieldKeyList(StringBuilder sb, Collection<FieldKey> fieldKeys, String tableAlias)
    {
        String separator = "";
        for (FieldKey fk : fieldKeys)
        {
            sb.append(separator);
            separator = ", ";
            sb.append(tableAlias);
            sb.append(".");
            sb.append(fk.toSQLString());
        }
    }

    public Collection<FieldKey> getRequiredInputs()
    {
        Set<FieldKey> result = new HashSet<>();
        result.addAll(_filters.getAllFieldKeys());
        for (Aggregate aggregate : _aggregates)
        {
            result.add(aggregate.getFieldKey());
        }
        result.addAll(_groupBys);
        if (_pivotBy != null)
        {
            result.addAll(_pivotBy.getKey());
            result.add(_pivotBy.getValue());
        }
        return result;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testPassthrough()
        {
            AggregateQueryDataTransform transform = new AggregateQueryDataTransform(new DummyQueryDataSource(),
                    new SimpleFilter(), Collections.<Aggregate>emptyList(), Collections.<FieldKey>emptyList(), null);

            assertEqualsIgnoreWhitespace("SELECT A.* FROM ( mySchema.myTable ) A", transform.getLabKeySQL());
        }

        @Test
        public void testPivot()
        {
            AggregateQueryDataTransform transform = new AggregateQueryDataTransform(new DummyQueryDataSource(),
                    new SimpleFilter(), Collections.<Aggregate>emptyList(), Collections.<FieldKey>emptyList(),
                    new Pair<>(Arrays.asList(FieldKey.fromParts("Pivot1"), FieldKey.fromParts("Pivot2")), FieldKey.fromParts("Pivot3")));

            assertEqualsIgnoreWhitespace("SELECT A.* FROM ( mySchema.myTable ) A PIVOT A.\"Pivot1\", A.\"Pivot2\" BY A.\"Pivot3\"", transform.getLabKeySQL());
        }

        @Test
        public void testGroupBy()
        {
            AggregateQueryDataTransform transform = new AggregateQueryDataTransform(new DummyQueryDataSource(),
                    new SimpleFilter(), Collections.<Aggregate>emptyList(), Arrays.asList(FieldKey.fromParts("Group1", "Child1"), FieldKey.fromParts("Group2")),
                    null);

            assertEqualsIgnoreWhitespace("SELECT A.\"Group1\".\"Child1\", A.\"Group2\" FROM ( mySchema.myTable ) A GROUP BY A.\"Group1\".\"Child1\", A.\"Group2\"", transform.getLabKeySQL());
        }

        private void assertEqualsIgnoreWhitespace(String expected, String actual)
        {
            expected = expected.replaceAll("\\s+", " ");
            actual = actual.replaceAll("\\s+", " ");
            assertEquals(expected, actual);
        }
    }
}
