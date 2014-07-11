package org.labkey.search.model;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.data.TextWriter;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
        TextWriter writer = !"Excel".equals(format) ? new TSVIndexWriter() : new TSVIndexWriter() {
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
        };
        writer.write(response);
    }

    private static String getType(String uid)
    {
        return uid.substring(0, uid.indexOf(':'));
    }

    private static File getIndexDirectory()
    {
        return SearchPropertyManager.getPrimaryIndexDirectory();
    }

    // TODO: Refactor TSVMapWriter to make this cleaner... e.g., don't require an iterator
    private static class TSVIndexWriter extends TSVMapWriter
    {
        public TSVIndexWriter()
        {
            super(new CsvSet("Title,Type,Folder,URL,UniqueId,Body Terms"), null);
        }

        @Override
        protected void writeBody()
        {
            Map<String, Object> map = new HashMap<>();

            try (Directory directory = FSDirectory.open(getIndexDirectory()); IndexReader reader = DirectoryReader.open(directory))
            {
                // Lucene provides no way to query a document for its size in the index, so we enumerate the terms and increment
                // term counts on each document to calculate a proxy for doc size.
                int[] termCountPerDoc = new int[reader.maxDoc()];

                for (AtomicReaderContext arc : reader.leaves())
                {
                    AtomicReader ar = arc.reader();
                    TermsEnum termsEnum = ar.terms("body").iterator(null);

                    while (null != termsEnum.next())
                    {
                        DocsEnum de = termsEnum.docs(ar.getLiveDocs(), null);
                        int doc;

                        while((doc = de.nextDoc()) != DocsEnum.NO_MORE_DOCS)
                        {
                            termCountPerDoc[doc]++;
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

                    map.put("Title", titles[0]);
                    map.put("Type", type);
                    map.put("Folder", path);
                    map.put("URL",  urls[0]);
                    map.put("UniqueId", uniqueIds[0]);
                    map.put("Body Terms", termCountPerDoc[i]);

                    writeRow(map);
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
