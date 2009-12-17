/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Category;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.Resource;
import org.xml.sax.ContentHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Nov 18, 2009
 * Time: 1:14:44 PM
 */
public class LuceneSearchServiceImpl extends AbstractSearchService
{
    private static final Category _log = Category.getInstance(LuceneSearchServiceImpl.class);
    private static int _count = 0;
    private static IndexWriter _iw = null;            // Don't use this directly -- it could be null or change out underneath you.  Call getIndexWriter()
    private static Analyzer _analyzer = null;
    private static IndexSearcher _searcher = null;    // Don't use this directly -- it could be null or change out underneath you.  Call getIndexSearcher()
    private static Directory _directory = null;

    public LuceneSearchServiceImpl()
    {
        try
        {
            File tempDir = new File(FileUtil.getTempDirectory(), "labkey_full_text_index");
            _directory = FSDirectory.open(tempDir);
            _analyzer = new SnowballAnalyzer(Version.LUCENE_CURRENT, "English");
        }
        catch (IOException e)
        {
            throw new RuntimeException();
        }
    }


    public void clearIndex()
    {
        try
        {
            getIndexWriter().deleteAll();
            commit();
        }
        catch (IOException e)
        {
            throw new RuntimeException();
        }
    }


    @Override
    Map<?, ?> preprocess(String id, Resource r)
    {
        try
        {
            Map<String, ?> props = r.getProperties();
            String body = null;
            String title = (String)props.get("title");
            String type = r.getContentType();

            // Skip XML for now
            if (type.startsWith("image/") || type.contains("xml"))
            {
                return null;
            }
            else if ("text/html".equals(type))
            {
                String html="";
                InputStream in = r.getInputStream(User.getSearchUser());
                if (null != in)
                    html = PageFlowUtil.getStreamContentsAsString(in);

                // TODO: Need better check for issue HTML vs. rendered page HTML
                if (r instanceof ActionResource)
                {
                    HTMLContentExtractor extractor = new HTMLContentExtractor.LabKeyPageHTMLExtractor(html);
                    body = extractor.extract();
                    title = extractor.getTitle();
                }

                if (StringUtils.isEmpty(body))
                {
                    body = new HTMLContentExtractor.GenericHTMLExtractor(html).extract();
                }
            }
            else if (type.startsWith("text/") && !type.contains("xml"))
            {
                InputStream in = r.getInputStream(User.getSearchUser());
                if (null != in)
                    body = PageFlowUtil.getStreamContentsAsString(in);
            }
            else
            {
                InputStream is = null;

                try
                {
                    Metadata metadata = new Metadata();
                    ContentHandler handler = new BodyContentHandler();
                    is = r.getInputStream(User.getSearchUser());
                    new AutoDetectParser().parse(is, handler, metadata);
                    is.close();
                    body = handler.toString();
                    if (StringUtils.isBlank(title))
                        title = metadata.get(Metadata.TITLE);
                    _log.debug("Parsed " + id);
                }
                finally
                {
                    IOUtils.closeQuietly(is);
                }
            }

            String url = r.getExecuteHref(null);
            assert null != url;
            if (StringUtils.isBlank(title))
                title = r.getName();

            String summary = extractSummary(body, title);

            _count++;

            if (0 == _count % 100)
                _log.debug("Indexing: " + _count);

            Document doc = new Document();

            doc.add(new Field("body", body, Field.Store.NO, Field.Index.ANALYZED));
            doc.add(new Field("title", title, Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field("summary", summary, Field.Store.YES, Field.Index.NO));
            doc.add(new Field("url", url, Field.Store.YES, Field.Index.NO));

            for (Map.Entry<String, ?> entry : props.entrySet())
            {
                Object value = entry.getValue();

                if (null != value)
                {
                    String stringValue = value.toString().toLowerCase();

                    if (stringValue.length() > 0)
                    {
                        String key = entry.getKey().toLowerCase();

                        if (!"title".equals(key))
                            doc.add(new Field(key, stringValue, Field.Store.NO, Field.Index.ANALYZED));
                    }
                }
            }

            return Collections.singletonMap(Document.class, doc);
        }
        catch(Throwable e)
        {
            _log.error("Indexing error with " + id, e);
        }

        return null;
    }

    private static final int SUMMARY_LENGTH = 400;
    private static final Pattern TITLE_STRIPPING_PATTERN = Pattern.compile(": /" + GUID.guidRegEx);
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[\\s/]");  // Any whitespace character or slash

    private String extractSummary(String body, String title)
    {
        title = TITLE_STRIPPING_PATTERN.matcher(title).replaceAll("");

        if (body.startsWith(title))
            body = body.substring(title.length());

        if (body.length() <= SUMMARY_LENGTH)
            return body;

        Matcher wordSplitter = SEPARATOR_PATTERN.matcher(body);

        if (!wordSplitter.find(SUMMARY_LENGTH - 1))
            return body.substring(0, SUMMARY_LENGTH) + "...";
        else
            return body.substring(0, wordSplitter.start()) + "...";
    }


    protected void index(String id, Resource r, Map preprocessMap)
    {
        try
        {
            Document doc = (Document)preprocessMap.get(Document.class);
            getIndexWriter().addDocument(doc);
        }
        catch(Throwable e)
        {
            _log.error("Indexing error with " + id, e);
        }
    }


    protected synchronized void commit()
    {
        try
        {
            if (null != _iw)
                _iw.close();
        }
        catch (IOException e)
        {
            try
            {
                _log.error("Exception closing index", e);
                _log.error("Attempting to index close again");
                _iw.close();
            }
            catch (IOException e2)
            {
                _log.error("Exception closing index", e2);
            }
        }
        finally
        {
            _searcher = null;
            _iw = null;
        }
    }


    private synchronized IndexWriter getIndexWriter() throws IOException
    {
        if (null == _iw)
            _iw = new IndexWriter(_directory, _analyzer);

        return _iw;
    }


    private synchronized IndexSearcher getIndexSearcher() throws IOException
    {
        if (null == _searcher)
            _searcher = new IndexSearcher(_directory, true);

        return _searcher;
    }


    public String search(String queryString, User user)
    {
        try
        {
            String sort = null;  // TODO: add sort parameter
            int hitsPerPage = 20;

            long start = System.nanoTime();
            Query query = new QueryParser("body", _analyzer).parse(queryString.toLowerCase());

            TopDocs topDocs;
            IndexSearcher searcher = getIndexSearcher();
            Filter securityFilter = new SecurityFilter(user);

            if (null == sort)
                topDocs = searcher.search(query, securityFilter, hitsPerPage);
            else
                topDocs = searcher.search(query, securityFilter, hitsPerPage, new Sort(new SortField(sort, SortField.AUTO)));

            ScoreDoc[] hits = topDocs.scoreDocs;

            StringBuilder html = new StringBuilder("<table><tr><td colspan=2>Found ");
            html.append(topDocs.totalHits).append(" result");

            if (topDocs.totalHits != 1)
                html.append("s");

            long time = (System.nanoTime() - start)/1000000;
            html.append(" in ").append(time).append(" millisecond").append(1 != time ? "s" : "").append(".  Displaying ");

            if (hits.length < topDocs.totalHits)
            {
                html.append("page ").append(1).append(" of ");
                html.append((int)Math.ceil((double)topDocs.totalHits / hits.length));
            }
            else
            {
                html.append("all results");
            }

            html.append(".</td></tr>\n");

            for (ScoreDoc hit : hits)
            {
                Document doc = searcher.doc(hit.doc);

                String summary = doc.get("summary");
                String url = doc.get("url");
                String title = doc.get("title");

                html.append("<tr><td colspan=2>&nbsp;</td></tr>\n");
                html.append("<tr><td colspan=2><a href=\"").append(PageFlowUtil.filter(url)).append("\">").append(PageFlowUtil.filter(title)).append("</a>").append("</td></tr>\n");
                html.append("<tr><td width=25>&nbsp;</td><td>").append(PageFlowUtil.filter(summary)).append("</td></tr>\n");
            }

            html.append("</table>\n");

            return html.toString();
        }
        catch (ParseException e)
        {
            return "Error: " + e.getMessage();
        }
        catch (IOException e)
        {
            return "Error: " + e.getMessage();
        }
    }

    protected void shutDown()
    {
        commit();
    }
}