/*
 * Copyright (c) 2012 LabKey Corporation
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
    public AbstractDataLoaderFactory()
    {
        this(false);
    }
    
    public AbstractDataLoaderFactory(boolean indexable)
    {
        // NOTE: Disable all indexing for now
        //if (indexable)
        //    ServiceRegistry.get(SearchService.class).addDocumentParser(this);
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
        return fileType.isType(resource.getFile(), contentType, buf);
    }

    @Override
    public void parseContent(InputStream stream, ContentHandler h) throws IOException, SAXException
    {
        DataLoader loader = createLoader(stream, true);
        ColumnDescriptor[] cols = loader.getColumns();

        startTag(h, "table");
        newline(h);

        startTag(h, "tr");
        for (ColumnDescriptor cd : cols)
        {
            if (cd.load)
                continue;
            
            tab(h);
            startTag(h, "th");
            write(h, cd.name);
            endTag(h, "th");
        }
        endTag(h, "tr");
        newline(h);

        for (Map<String, Object> row : loader)
        {
            startTag(h, "tr");
            for (ColumnDescriptor cd : cols)
            {
                if (cd.load)
                    continue;

                tab(h);
                startTag(h, "td");
                // XXX: format value
                Object value = row.get(cd.name);
                write(h, String.valueOf(value));
                endTag(h, "td");
            }

            endTag(h, "tr");
            newline(h);
        }

        endTag(h, "table");
    }

}
