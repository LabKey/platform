/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.search.model;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.TextWriter;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;

/**
 * User: adam
 * Date: 7/1/2014
 * Time: 10:30 AM
 */

// Analyzes LabKey-generated Lucene indexes and displays/exports their contents. Output generally includes a row for each
// document in the index listing title, type, folder path, url, and an approximation of its size in the index. Much of this
// code was created originally in the standalone /tools/LuceneStats project. It was integrated into the server to help
// diagnose the cause of large indexes, see #19919.
public class IndexInspector
{
    public IndexInspector()
    {
    }

    public void export(HttpServletResponse response, final String format) throws IOException
    {
        try (TextWriter writer = !"Excel".equals(format) ? new TSVIndexWriter() : new TSVIndexWriter() {
            @Override
            protected String getContentType()
            {
                return ExcelWriter.ExcelDocumentType.xls.getMimeType();
            }

            @Override
            protected String getFilenameExtension()
            {
                return "xls";
            }
        })
        {
            writer.write(response);
        }
    }

    private static String getType(String uid)
    {
        return uid.substring(0, uid.indexOf(':'));
    }

    private static Path getIndexDirectory()
    {
        return SearchPropertyManager.getIndexDirectory().toPath();
    }

    private static class TSVIndexWriter extends TSVWriter
    {
        @Override
        protected void writeColumnHeaders()
        {
            writeLine(new CsvSet("Title,Type,Folder,URL,UniqueId,Body Terms"));
        }

        @Override
        protected void writeBody()
        {
            try (Directory directory = WritableIndexManagerImpl.openDirectory(getIndexDirectory()); IndexReader reader = DirectoryReader.open(directory))
            {
                // Lucene provides no way to query a document for its size in the index, so we enumerate the terms and increment
                // term counts on each document to calculate a proxy for doc size.
                int[] termCountPerDoc = new int[reader.maxDoc()];

                for (LeafReaderContext arc : reader.leaves())
                {
                    LeafReader ar = arc.reader();
                    Terms terms = ar.terms("body");

                    if (null != terms)
                    {
                        TermsEnum termsEnum = terms.iterator();
                        PostingsEnum pe = null;

                        while (null != termsEnum.next())
                        {
                            pe = termsEnum.postings(pe);
                            int doc;

                            while ((doc = pe.nextDoc()) != PostingsEnum.NO_MORE_DOCS)
                            {
                                termCountPerDoc[doc]++;
                            }
                        }
                    }
                }

                // The stored terms are much easier to get. For each document, output stored fields plus the statistics computed above
                for (int i = 0; i < reader.maxDoc(); i++)
                {
                    Document doc = reader.document(i);
                    String[] titles = doc.getValues("title");
                    String[] urls = doc.getValues("url");
                    String[] uniqueIds = doc.getValues("uniqueId");
                    String[] containerIds = doc.getValues("container");

                    if (titles.length != 1 || urls.length != 1 || uniqueIds.length != 1 || containerIds.length != 1)
                    {
                        throw new IOException("Incorrect number of term values found for document " + i);
                    }

                    String type = getType(uniqueIds[0]);

                    Container c = ContainerManager.getForId(containerIds[0]);
                    String path = null != c ? c.getPath() : "UNKNOWN: " + containerIds[0];

                    writeLine(PageFlowUtil.set(titles[0], type, path, urls[0], uniqueIds[0], String.valueOf(termCountPerDoc[i])));
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
