/*
 * Copyright (c) 2014-2018 LabKey Corporation
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
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Bits;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.TSVWriter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

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
        try (TSVIndexWriter writer = !"Excel".equals(format) ? new TSVIndexWriter() : new TSVIndexWriter() {
            {
                setDelimiterCharacter(DELIM.COMMA);
            }

            // We want the "Export to Excel" option to open Excel and load the file. We're providing Excel content type
            // but CSV file name and format because that seems to work and exporting in an Excel-native format would
            // require a bit of a rewrite to the below, since ExcelWriter and TSVWriter don't share any useful
            // interfaces. I haven't found a definitive reference that says this content type + extension discrepancy
            // always works, but here's an old post that suggests it's reasonable:
            // https://stackoverflow.com/questions/393647/response-content-type-as-csv#1719233
            @Override
            protected String getContentType()
            {
                return ExcelWriter.ExcelDocumentType.xls.getMimeType();
            }

            @Override
            protected String getFilenameExtension()
            {
                return "csv";
            }
        })
        {
            writer.setFilenamePrefix("search-index");
            writer.write(response);
        }
    }

    private static Path getIndexDirectory()
    {
        return LuceneSearchServiceImpl.getIndexDirectory().toPath();
    }

    private static boolean isLive(@Nullable Bits liveDocs, int docId)
    {
        return null == liveDocs || liveDocs.get(docId);
    }

    private static class TSVIndexWriter extends TSVWriter
    {
        @Override
        protected void writeColumnHeaders()
        {
            writeLine(new CsvSet("Title,Category,Folder,URL,NavTrail,UniqueId,Body Terms"));
        }

        @Override
        protected int writeBody()
        {
            try (Directory directory = WritableIndexManagerImpl.openDirectory(getIndexDirectory());
                 IndexReader reader = DirectoryReader.open(directory))
            {
                int docCount = reader.maxDoc();
                Bits liveDocs = MultiBits.getLiveDocs(reader);

                // Lucene provides no way to query a document for its size in the index, so we enumerate the terms and increment
                // term counts on each live document to calculate a proxy for doc size.
                int[] termCountPerDoc = new int[docCount];

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
                                if (isLive(liveDocs, doc))
                                    termCountPerDoc[doc]++;
                            }
                        }
                    }
                }

                StoredFields stored = reader.storedFields();

                // The stored terms are much easier to get. For each live document, output stored fields plus the statistics computed above
                for (int i = 0; i < docCount; i++)
                {
                    if (isLive(liveDocs, i)) // Skip deleted docs
                    {
                        Document doc = stored.document(i);
                        String[] titles = doc.getValues("title");
                        String[] categories = doc.getValues("searchCategories");
                        String[] urls = doc.getValues("url");
                        String[] navTrails = doc.getValues("navtrail");
                        String[] uniqueIds = doc.getValues("uniqueId");
                        String[] containerIds = doc.getValues("container");

                        if (titles.length != 1 || urls.length != 1 || uniqueIds.length != 1 || containerIds.length != 1)
                        {
                            throw new IOException("Incorrect number of term values found for document " + i);
                        }

                        Container c = ContainerManager.getForId(containerIds[0]);
                        String path = null != c ? c.getPath() : "UNKNOWN: " + containerIds[0];

                        String navTrail = navTrails != null && navTrails.length > 0 ? navTrails[0] : null;
                        String category = categories.length == 1 ? categories[0] : Arrays.toString(categories);

                        writeLine(Arrays.asList(titles[0], category, path, urls[0], navTrail, uniqueIds[0], String.valueOf(termCountPerDoc[i])));
                    }
                }

                return docCount;
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
