/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.api.reader;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.settings.DateParsingMode;
import org.labkey.api.settings.LookAndFeelProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * kevink
 */
public class MapLoader extends DataLoader
{
    String[] headers;
    Object[][] data;

    public MapLoader(List<Map<String, Object>> rows) throws IOException
    {
        convertToArrays(rows);
        _skipLines = rows.size() > 0 ? 1 : 0;
        setScrollable(true);
    }

    // Convert the list of maps.
    // The UploadSamplesHelper changes the ColumnDescriptor.name to
    // propertyURIs after inferring the columns but before calling load()
    // causing the map.get(column.name) on each row to fail.
    private void convertToArrays(List<Map<String, Object>> rows)
    {
        List<Object[]> lineFields = new ArrayList<>(rows.size());

        if (rows.size() > 0)
        {
            Collection<String> keys = rows.get(0).keySet();
            headers = keys.toArray(new String[keys.size()]);
            lineFields.add(headers);

            for (Map<String, Object> row : rows)
            {
                if (!(row instanceof CaseInsensitiveMapWrapper))
                    row = new CaseInsensitiveMapWrapper<>(row);

                ArrayList<Object> values = new ArrayList<>(headers.length);
                for (String header : headers)
                {
                    Object value = row.get(header);
                    values.add(value);
                }
                lineFields.add(values.toArray(new Object[values.size()]));
            }
        }

        data = lineFields.toArray(new Object[rows.size()][]);
    }

    @Override
    public String[][] getFirstNLines(int n) throws IOException
    {
        n = Math.min(n, data.length);
        String[][] firstLines = new String[n][];
        for (int i = 0; i < n; i++)
        {
            Object[] row = data[i];
            String[] line = new String[row.length];
            for (int j = 0; j < row.length; j++)
                line[j] = String.valueOf(row[j]);
            firstLines[i] = line;
        }
        return firstLines;
    }

    @Override
    public CloseableIterator<Map<String, Object>> iterator()
    {
        try
        {
            return new MapLoaderIterator();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()
    {
    }

    public class MapLoaderIterator extends DataLoaderIterator
    {
        protected MapLoaderIterator()
                throws IOException
        {
            super(_skipLines);
        }

        @Override
        protected Object[] readFields() throws IOException
        {
            if (lineNum() < data.length)
            {
                // 11374: Return values only for active columns
                ColumnDescriptor[] columns = getColumns();
                List<Object> values = new ArrayList<>(_activeColumns.length);
                Object[] parsedValues = data[lineNum()];

                for (int i = 0; i < columns.length; i++)
                {
                    if (columns[i].load)
                    {
                        Object parsedValue = i<parsedValues.length ? parsedValues[i] : null;
                        values.add(parsedValue);
                    }
                }

                assert values.size() == _activeColumns.length;
                
                return values.toArray();
            }
            return null;           
        }

        public void close() throws IOException
        {
            super.close();
        }
    }

    public static class MapLoaderTestCase extends Assert
    {
        @Test
        public void testLoad() throws Exception
        {
            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("name", "bob");
            row1.put("date", "1/2/2006");
            row1.put("number", "1.1");
            row1.put("noload", "dont load");

            Map<String, Object> row2 = new HashMap<>();
            row2.put("name", "jim");
            row2.put("date", "");
            row2.put("number", "");
            row2.put("noload", "");

            Map<String, Object> row3 = new CaseInsensitiveHashMap<>();
            row3.put("Name", "sally");
            row3.put("Date", "2-Jan-06");
            row3.put("Number", 1.2); // NOTE: not a String!
            row3.put("Noload", "");

            List<Map<String, Object>> rows = Arrays.asList(row1, row2, row3);
            MapLoader loader = new MapLoader(rows);

            ColumnDescriptor[] cd = loader.getColumns();
            assertEquals("name",   cd[0].name); assertEquals(String.class, cd[0].clazz);
            assertEquals("date",   cd[1].name); assertEquals(Date.class,   cd[1].clazz);
            assertEquals("number", cd[2].name); assertEquals(Double.class, cd[2].clazz);
            assertEquals("noload", cd[3].name); assertEquals(String.class, cd[3].clazz);
            cd[3].load = false;

            List<Map<String, Object>> data = loader.load();
            assertEquals("bob", data.get(0).get("name"));

            // "1/2/2006" will be parsed based on the current date parsing mode (US vs. non-US), so change the expected value based on the setting
            DateParsingMode mode = LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDateParsingMode();
            Calendar cal = DateParsingMode.US == mode ? new GregorianCalendar(2006, 0, 2) : new GregorianCalendar(2006, 1, 1);

            assertEquals(cal.getTime(), data.get(0).get("date"));
            assertEquals(1.1, data.get(0).get("number"));
            assertEquals(1.2, data.get(2).get("number"));

            // 11374: make sure we don't load inactive columns
            assertEquals(data.get(0).size(), 3);
            assertEquals(data.get(1).size(), 3);
            assertNull(data.get(0).get("noload"));
            assertNull(data.get(0).get("noload"));
        }
    }
}
