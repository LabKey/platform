/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.iterator.MarkableIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 9/11/11
 */
public class TSVMapWriter extends TSVWriter
{
    private final Collection<String> _columns;
    private final Iterator<Map<String, Object>> _rows;

    /**
     * Columns will be written in order of the first row's keySet() iteration.
     * @param rows The data rows
     */
    public TSVMapWriter(Iterable<Map<String, Object>> rows)
    {
        // Using a MarkableIterator keeps us from generating the iterator() twice, which could be expensive
        MarkableIterator<Map<String, Object>> iter = new MarkableIterator<>(rows.iterator());

        // Infer columns from first map: mark the iterator, read the first map, reset the iterator
        iter.mark();
        Map<String, Object> firstRow;
        _columns = (iter.hasNext() && null != (firstRow = iter.next())) ? firstRow.keySet() : null;
        iter.reset();

        _rows = iter;
    }

    /**
     * Columns will be written in order of the columns collection.
     * @param rows The data rows
     */
    public TSVMapWriter(Collection<String> columns, Iterable<Map<String, Object>> rows)
    {
        _columns = columns;
        _rows = rows.iterator();
    }

    @Override
    protected void writeColumnHeaders()
    {
        writeLine(_columns);
    }

    // Make public
    @Override
    public void write()
    {
        super.write();
    }

    @Override
    protected void writeBody()
    {
        while (_rows.hasNext())
        {
            writeRow(_rows.next());
        }
    }

    protected void writeRow(final Map<String, Object> row)
    {
        Iterable<String> values = Iterables.transform(_columns, new Function<String, String>()
        {
            @Override
            public String apply(String col)
            {
                Object o = row.get(col);
                return o == null ? "" : String.valueOf(o);
            }
        });

        writeLine(values);
    }

    protected void setColumns(Collection<String> columns)
    {
        if (null != _columns && _columns.isEmpty())
            _columns.addAll(columns);

    }

    public static class Tests extends Assert
    {
        @Test
        public void test() throws Exception
        {
            Collection<String> columns = Arrays.asList("one", "two", "three");
            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("one", 1.1);
            row.put("two", "TWO");
            row.put("three", "test,quoting");
            rows.add(row);

            row = new HashMap<>();
            row.put("two", 2.2);
            row.put("three", "");
            rows.add(row);

            rows.add(Collections.singletonMap("three", 3.3));

            String lineSep = "|";

            String expectedWithColumnHeaders = "# file header" + lineSep +
                    "one,two,three" + lineSep +
                    "1.1,TWO,'test,quoting'" + lineSep +
                    ",2.2," + lineSep +
                    ",,3.3" + lineSep;

            String expectedWithoutColumnHeaders = "# file header" + lineSep +
                    "1.1,TWO,'test,quoting'" + lineSep +
                    ",2.2," + lineSep +
                    ",,3.3" + lineSep;

            // Provide columns and output header row
            try (TSVWriter writer = new TSVMapWriter(columns, rows))
            {
                testWriter(writer, expectedWithColumnHeaders);
            }

            // Provide columns and don't output header row
            try (TSVWriter writer = new TSVMapWriter(columns, rows))
            {
                writer.setHeaderRowVisible(false);
                testWriter(writer, expectedWithoutColumnHeaders);
            }

            // Infer columns and output header row
            try (TSVWriter writer = new TSVMapWriter(rows))
            {
                testWriter(writer, expectedWithColumnHeaders);
            }

            // Infer columns and don't output header row
            try (TSVWriter writer = new TSVMapWriter(rows))
            {
                writer.setHeaderRowVisible(false);
                testWriter(writer, expectedWithoutColumnHeaders);
            }
        }

        private void testWriter(TSVWriter writer, String expected) throws IOException
        {
            writer.setFileHeader(Arrays.asList("# file header"));
            writer.setDelimiterCharacter(',');
            writer.setQuoteCharacter('\'');
            writer.setRowSeparator("|");

            StringBuilder sb = new StringBuilder();
            writer.write(sb);

            assertEquals(expected, sb.toString());
        }
    }
}
