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
import org.labkey.api.search.AbstractDocumentParser;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.webdav.WebdavResource;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 9/30/12
 */
public abstract class AbstractDataLoaderFactory extends AbstractDocumentParser implements DataLoaderFactory
{
    private boolean _indexable;

    public AbstractDataLoaderFactory()
    {
        this(false);
    }
    
    public AbstractDataLoaderFactory(boolean indexable)
    {
        _indexable = indexable;
    }

    @Override
    public boolean indexable()
    {
        return _indexable;
    }

    @NotNull
    public DataLoader createLoader(InputStream is, boolean hasColumnHeaders) throws IOException
    {
        return createLoader(is, hasColumnHeaders, null);
    }

    @NotNull
    public DataLoader createLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        return createLoader(file, hasColumnHeaders, null);
    }

    @Override
    public String getMediaType()
    {
        List<String> contentType = getFileType().getContentTypes();
        if (!contentType.isEmpty())
            return contentType.get(0);

        return null;
    }

    @Override
    public boolean detect(WebdavResource resource, String contentType, byte[] buf) throws IOException
    {
        FileType fileType = getFileType();
        return fileType.isType(resource.getName(), contentType, buf);
    }

    @Override
    public void parseContent(InputStream stream, ContentHandler h) throws IOException, SAXException
    {
        DataLoader loader = createLoader(stream, true);
        ColumnDescriptor[] cols = loader.getColumns();


        startTag(h, "pre");
        newline(h);

        for (ColumnDescriptor cd : cols)
        {
            if (!cd.load)
                continue;
            
            write(h, cd.name);
            tab(h);
        }
        newline(h);

        for (Map<String, Object> row : loader)
        {
            for (ColumnDescriptor cd : cols)
            {
                if (!cd.load)
                    continue;

                Object value = row.get(cd.name);
                if (value != null)
                {
                    if (value instanceof String)
                    {
                        String str = (String)value;
                        if (str.contains("<"))
                            str = PageFlowUtil.filterXML(str);
                        write(h, str);
                    }
                    else
                        write(h, String.valueOf(value));
                }
                tab(h);
            }

            newline(h);
       }
       endTag(h, "pre");
    }

}
