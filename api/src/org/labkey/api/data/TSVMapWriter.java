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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import org.junit.Assert;
import org.junit.Test;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 9/11/11
 */
public class TSVMapWriter extends TSVWriter
{
    protected Iterable<String> _columns;
    private Iterable<Map<String, Object>> _rows;

    /**
     * Columns will be written in order of the first row's keySet() iteration.
     * @param rows
     */
    public TSVMapWriter(Iterable<Map<String, Object>> rows)
    {
        _rows = rows;

        Iterator<Map<String, Object>> it = _rows.iterator();
        if (it.hasNext())
        {
            Map<String, Object> firstRow = it.next();
            if (firstRow != null)
                _columns = firstRow.keySet();
        }
    }

    /**
     * Columns will be written in order of the columns collection.
     * @param rows
     */
    public TSVMapWriter(Iterable<String> columns, Iterable<Map<String, Object>> rows)
    {
        _columns = columns;
        _rows = rows;
    }

    @Override
    protected void writeColumnHeaders()
    {
        writeLine(_columns);
    }

    @Override
    public void write()
    {
        super.write();
    }

    @Override
    protected void writeBody()
    {
        for (Map<String, Object> row : _rows)
        {
            writeRow(_columns, row);
        }
    }

    protected void writeRow(Iterable<String> columns, final Map<String, Object> row)
    {
        Iterable<String> values = Iterables.transform(columns, new Function<String, String>()
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

    public static class Tests extends Assert
    {
        @Test
        public void test() throws Exception
        {
            Collection<String> columns = Arrays.asList("one", "two", "three");
            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row = new HashMap<>();
            row.put("one", 1.1);
            row.put("two", "TWO");
            row.put("three", "test,quoting");
            rows.add(row);

            row = new HashMap<>();
            row.put("two", 2.2);
            row.put("three", "");
            rows.add(row);

            rows.add(Collections.<String, Object>singletonMap("three", 3.3));

            TSVWriter writer = new TSVMapWriter(columns, rows);
            writer.setFileHeader(Arrays.asList("# file header"));
            writer.setDelimiterCharacter(',');
            writer.setQuoteCharacter('\'');

            // Test
            {
                StringBuilder sb = new StringBuilder();
                writer.write(sb);

                String expected = "# file header\n" +
                        "one,two,three\n" +
                        "1.1,TWO,'test,quoting'\n" +
                        ",2.2,\n" +
                        ",,3.3\n";

                assertEquals(expected, sb.toString());
            }

            // Test header row not visible
            {
                writer.setHeaderRowVisible(false);

                StringBuilder sb = new StringBuilder();
                writer.write(sb);

                String expected = "# file header\n" +
                        "1.1,TWO,'test,quoting'\n" +
                        ",2.2,\n" +
                        ",,3.3\n";

                assertEquals(expected, sb.toString());
            }
        }
    }
}
