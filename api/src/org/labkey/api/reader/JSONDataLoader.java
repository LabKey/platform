package org.labkey.api.reader;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.data.Container;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.util.FileType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads data from an JSON table that matches the selectRows response format.
 *
 * Example:
 * <pre>
 * {
 *   rows: [{
 *      "Column Name 1": "Row 1 Value 1",
 *      "Column Name 2": "Row 1 Value 2",
 *      ...
 *   },{
 *      "Column Name 1": "Row 2 Value 1",
 *      "Column Name 2": "Row 2 Value 2",
 *   },{
 *     ...
 *   }]
 * }
 * </pre>
 *
 * User: kevink
 * Date: 10/1/12
 */
public class JSONDataLoader extends DataLoader
{
    public static final FileType FILE_TYPE = new FileType(Arrays.asList("json"), "json", ApiJsonWriter.CONTENT_TYPE_JSON)
    {
        @Override
        public boolean isHeaderMatch(@NotNull byte[] header)
        {
            String s;
            try
            {
                s = new String(header, "UTF-8");
            }
            catch (UnsupportedEncodingException e)
            {
                throw new RuntimeException(e);
            }

            // XXX: Need to use streaming parser for incomplete json string
            JSONObject json;
            try
            {
                json = new JSONObject(s);
            }
            catch (JSONException e)
            {
                return false;
            }

            // Look for top-level 'rows' array.
            return json.optJSONArray("rows") != null;
        }
    };

    public static class Factory extends AbstractDataLoaderFactory
    {
        @NotNull
        @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new JSONDataLoader(is, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull
        @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new JSONDataLoader(file, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull
        @Override
        public FileType getFileType()
        {
            return FILE_TYPE;
        }
    }

    JSONObject _json;

    public JSONDataLoader(File inputFile, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setSource(inputFile);
        setHasColumnHeaders(hasColumnHeaders);

        init(new FileInputStream(inputFile));
    }

    public JSONDataLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);

        init(is);
    }

    protected void init(InputStream is) throws IOException
    {
        BufferedReader r = null;
        try
        {
            r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while (null != (line = r.readLine()))
                sb.append(line).append("\n");

            // XXX: Use streaming json parser
            String data = sb.toString();
            _json = new JSONObject(data);
        }
        finally
        {
            IOUtils.closeQuietly(r);
        }
    }

    protected Collection<Object[]> parse(int limit)
    {
        if (_json == null)
            return null;

        JSONArray rows = _json.optJSONArray("rows");
        if (rows == null)
            return null;

        // XXX: We only look at the first row for the headers which may not be enough
        String[] header = null;
        List<Object[]> results = new ArrayList<Object[]>(rows.length()+1);
        for (int i = 0; i < rows.length(); i++)
        {
            JSONObject row = rows.getJSONObject(i);
            if (header == null)
            {
                Set<String> keys = row.keySet();
                header = keys.toArray(new String[keys.size()]);
                results.add(header);
            }

            Object[] values = new Object[header.length];
            for (int j = 0; j < header.length; j++)
            {
                Object value = row.get(header[j]);
                values[j] = value;
            }
            results.add(values);

            if (results.size() == limit)
                break;
        }

        return results;
    }


    @Override
    public String[][] getFirstNLines(int n) throws IOException
    {
        // Just get the header row
        List<Object[]> data = (List<Object[]>)parse(1);
        String[] header = (String[])data.get(0);

        String[][] lines = new String[1][];
        lines[0] = header;
        return lines;
    }

    @Override
    public CloseableIterator<Map<String, Object>> iterator()
    {
        try
        {
            return new Iter();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()
    {
        _json = null;
    }

    private class Iter extends DataLoaderIterator
    {
        Iterator<Object[]> iter;

        protected Iter() throws IOException
        {
            super(_skipLines, false);
            assert _skipLines != -1;

            Collection<Object[]> data = parse(0);

            iter = data.iterator();
            for (int i = 0; i < lineNum(); i++)
                iter.next();
        }

        @Override
        public void close() throws IOException
        {
            iter = null;
            super.close();
        }

        @Override
        protected Object[] readFields() throws IOException
        {
            if (!iter.hasNext())
                return null;

            Object[] row = iter.next();
            return row;
        }
    }
}

