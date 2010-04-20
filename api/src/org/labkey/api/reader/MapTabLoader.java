package org.labkey.api.reader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.CloseableIterator;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * kevink
 */
public class MapTabLoader extends DataLoader<Map<String, Object>>
{
    List<Map<String, String>> _rows;

    public MapTabLoader(List<Map<String, String>> rows) throws IOException
    {
        _rows = rows;
        _skipLines = -1;
    }

    @Override
    public String[][] getFirstNLines(int n) throws IOException
    {
        List<String[]> lineFields = new ArrayList<String[]>(n);

        int i = 0;

        assert _skipLines == -1;
        if (_rows.size() > 1)
        {
            Collection<String> headers = _rows.get(0).keySet();
            lineFields.add(headers.toArray(new String[headers.size()]));
            _skipLines = 1;

            for (i = 0; i < n-1 && i < _rows.size(); i++)
            {
                Map<String, String> row = _rows.get(i);
                ArrayList<String> values = new ArrayList<String>(headers.size());
                for (String header : headers)
                {
                    String value = row.get(header);
                    values.add(value);
                }
                lineFields.add(values.toArray(new String[values.size()]));
            }
        }
        else
        {
            _skipLines = 0;
        }

        return lineFields.toArray(new String[i][]);
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
            super(_skipLines, false);
        }

        @Override
        protected String[] readFields()
        {
            int index = lineNum() - _skipLines;
            if (index < _rows.size())
            {
                Map<String, String> row = _rows.get(index);
                ArrayList<String> values = new ArrayList<String>(_columns.length);
                for (ColumnDescriptor cd : _columns)
                {
                    String value = row.get(cd.name);
                    values.add(value);
                }
                return values.toArray(new String[values.size()]);
            }

            return null;
        }

        public void close() throws IOException
        {
        }
    }

    public static class MapLoaderTestCase extends TestCase
    {
        public static Test suite()
        {
            return new TestSuite(MapLoaderTestCase.class);
        }

        public MapLoaderTestCase()
        {
            this("MapLoader Test");
        }

        public MapLoaderTestCase(String name)
        {
            super(name);
        }

        public void testLoad() throws Exception
        {
            Map<String, String> row1 = new LinkedHashMap<String, String>();
            row1.put("name", "bob");
            row1.put("date", "1/2/2006");
            row1.put("number", "1.1");

            Map<String, String> row2 = new HashMap<String, String>();
            row2.put("name", "jim");
            row2.put("date", "");
            row2.put("number", "");

            Map<String, String> row3 = new CaseInsensitiveHashMap<String>();
            row3.put("Name", "sally");
            row3.put("Date", "2-Jan-06");
            row3.put("Number", "1.2");

            List<Map<String, String>> rows = Arrays.asList(row1, row2, row3);
            MapTabLoader loader = new MapTabLoader(rows);

            ColumnDescriptor[] cd = loader.getColumns();
            assertEquals("name",   cd[0].name); assertEquals(String.class, cd[0].clazz);
            assertEquals("date",   cd[1].name); assertEquals(Date.class,   cd[1].clazz);
            assertEquals("number", cd[2].name); assertEquals(Double.class, cd[2].clazz);

            List<Map<String, Object>> data = loader.load();
            assertEquals("bob", data.get(0).get("name"));
            assertEquals(new GregorianCalendar(2006, 0, 2).getTime(), data.get(0).get("date"));
            assertEquals(1.1, data.get(0).get("number"));
        }
    }
}
