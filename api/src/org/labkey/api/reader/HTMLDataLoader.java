/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.util.FileType;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TidyUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Reads data from an HTML table.
 * The first html table element found will be used.
 *
 * Example:
 * <pre>
 *     &lt;html&gt;
 *         &lt;body&gt;
 *             &lt;table&gt;
 *                 &lt;tr&gt;
 *                     &lt;th&gt;Column Name 1&lt;/th&gt;
 *                     &lt;th&gt;Column Name 2&lt;/th&gt;
 *                     &lt;th&gt;...&lt;/th&gt;
 *                 &lt;/tr&gt;
 *                 &lt;tr&gt;
 *                     &lt;td&gt;Row 1 Value 1&lt;/td&gt;
 *                     &lt;td&gt;Row 1 Value 2&lt;/td&gt;
 *                     &lt;td&gt;...&lt;/td&gt;
 *                 &lt;/tr&gt;
 *                 &lt;tr&gt;
 *                     &lt;td&gt;Row 2 Value 1&lt;/td&gt;
 *                     &lt;td&gt;Row 2 Value 2&lt;/td&gt;
 *                     &lt;td&gt;...&lt;/td&gt;
 *                 &lt;/tr&gt;
 *             &lt;/table&gt;
 *         &lt;/body&gt;
 *     &lt;/html&gt;
 * </pre>
 *
 * User: kevink
 * Date: 10/1/12
 */
public class HTMLDataLoader extends DataLoader
{
    public static final FileType FILE_TYPE = new FileType(Arrays.asList(".html", ".xhtml"), ".html")
    {
        @Override
        public boolean isHeaderMatch(@NotNull byte[] header)
        {
            String s = new String(header, StringUtilsLabKey.DEFAULT_CHARSET);

            List<String> errors = new ArrayList<>();
            Document doc = TidyUtil.convertHtmlToDocument(s, true, errors);
            if (!errors.isEmpty() || doc == null)
                return false;

            // Look for a html>body>table element
            return findTable(doc) != null;
        }
    };

    public static class Factory extends AbstractDataLoaderFactory
    {
        @NotNull
        @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new HTMLDataLoader(is, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull
        @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new HTMLDataLoader(file, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull
        @Override
        public FileType getFileType()
        {
            return FILE_TYPE;
        }
    }

    String _html;

    public HTMLDataLoader(File inputFile, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setSource(inputFile);
        setScrollable(true);
        setHasColumnHeaders(hasColumnHeaders);

        init(new FileInputStream(inputFile));
    }

    public HTMLDataLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        setScrollable(false);

        init(is);
    }

    protected void init(InputStream is) throws IOException
    {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StringUtilsLabKey.DEFAULT_CHARSET)))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while (null != (line = r.readLine()))
                sb.append(line).append("\n");

            _html = sb.toString();
        }
    }

    protected Collection<String[]> parse(int limit)
    {
        List<String> errors = new LinkedList<>();
        Document doc = TidyUtil.convertHtmlToDocument(_html, true, errors);
        if (errors.size() > 0 || doc == null)
            return null;

        Element table = findTable(doc);
        if (table == null)
            return null;

        return parseTable(table, limit);
    }

    protected static Element findTable(@NotNull Document doc)
    {
        Element html = doc.getDocumentElement();
        if (html == null || !"html".equalsIgnoreCase(html.getTagName()))
            return null;

        NodeList bodyNL = html.getElementsByTagName("body");
        for (int bodyIdx = 0, bodyLen = bodyNL.getLength(); bodyIdx < bodyLen; bodyIdx++)
        {
            Element body = (Element)bodyNL.item(bodyIdx);
            NodeList tableNL = body.getElementsByTagName("table");
            for (int tableIdx = 0, tableLen = tableNL.getLength(); tableIdx < tableLen; tableIdx++)
            {
                Element table = (Element)tableNL.item(tableIdx);
                return table;
            }
        }

        return null;
    }

    protected Collection<String[]> parseTable(Element table, int limit)
    {
        List<String[]> rows = new ArrayList<>();

        boolean header = true;
        NodeList trNL = table.getElementsByTagName("tr");
        for (int trIdx = 0, trLen = trNL.getLength(); trIdx < trLen; trIdx++)
        {
            Element tr = (Element)trNL.item(trIdx);
            String[] row;
            if (header)
                row = parseHeaderRow(tr);
            else
                row = parseRow(tr, "td");

            if (row != null)
            {
                header = false;
                rows.add(row);
                if (limit > 0 && rows.size() == limit)
                    break;
            }

        }

        return rows;
    }

    protected String[] parseHeaderRow(Element tr)
    {
        String[] row = parseRow(tr, "th");
        if (row.length == 0)
            row = parseRow(tr, "td");
        return row;
    }

    protected String[] parseRow(Element tr, String tag)
    {
        ArrayList<String> values = new ArrayList<>();

        NodeList tdNL = tr.getElementsByTagName(tag);
        for (int tdIdx = 0, tdLen = tdNL.getLength(); tdIdx < tdLen; tdIdx++)
        {
            Element td = (Element)tdNL.item(tdIdx);
            String value = getInnerText(td);
            values.add(value);
        }

        return values.toArray(new String[values.size()]);
    }

    // XXX: Duplicateed from FlowJoWorkspace.  Refactor into DOMUtil or XMLUtil.
    private String getInnerText(Element el)
    {
        NodeList nl = el.getChildNodes();
        int len = nl.getLength();
        if (len == 0)
            return "";
        if (len == 1)
            return nl.item(0).getNodeValue();
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < nl.getLength(); i ++)
            ret.append(nl.item(i).getNodeValue());
        return ret.toString();
    }


    @Override
    public String[][] getFirstNLines(int n) throws IOException
    {
        Collection<String[]> table = parse(n);
        return table.toArray(new String[table.size()][]);
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
        _html = null;
    }

    private class Iter extends DataLoaderIterator
    {
        Iterator<String[]> iter;

        protected Iter() throws IOException
        {
            super(_skipLines);
            assert _skipLines != -1;

            Collection<String[]> table = parse(0);

            iter = table.iterator();
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

            String[] row = iter.next();
            return row;
        }
    }
}
