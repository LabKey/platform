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
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.Resource;
import org.labkey.api.data.Container;
import org.xml.sax.ContentHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
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

    private static enum FIELD_NAMES { body, title, summary, url, container, uniqueId }

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
            throw new RuntimeException(e);
        }
    }


    public String escapeTerm(String term)
    {
        if (StringUtils.isEmpty(term))
            return "";
        String illegal = "+-&|!(){}[]^\"~*?:\\";
        if (StringUtils.containsNone(term,illegal))
            return term;
        StringBuilder sb = new StringBuilder(term.length()*2);
        for (char ch : term.toCharArray())
        {
            if (illegal.indexOf(ch) != -1)
                sb.append('\\');
            sb.append(ch);
        }
        return sb.toString();
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
            assert null != r.getDocumentId();
            assert null != r.getContainerId();

            Map<String, ?> props = r.getProperties();
            String body = null;
            String title = (String)props.get(SearchService.PROPERTY.title.toString());
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

            doc.add(new Field(FIELD_NAMES.uniqueId.name(), r.getDocumentId(), Field.Store.NO, Field.Index.NOT_ANALYZED));
            doc.add(new Field(FIELD_NAMES.body.name(), body, Field.Store.NO, Field.Index.ANALYZED));
            doc.add(new Field(FIELD_NAMES.title.name(), title, Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field(FIELD_NAMES.summary.name(), summary, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.url.name(), url, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.container.name(), r.getContainerId(), Field.Store.YES, Field.Index.NO));

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
        catch (NoClassDefFoundError err)
        {
            // Suppress stack trace, etc., if Bouncy Castle isn't present.
            if ("org/bouncycastle/cms/CMSException".equals(err.getMessage()))
                _log.warn("Can't read encrypted document \"" + id + "\".  You must install the Bouncy Castle encryption libraries to index this document.  Refer to the LabKey Software documentation for instructions.");
            else
                throw err;
        }
        catch (Throwable e)
        {
            String name = r.getPath().toString();
            File f = r.getFile();
            if (null != f)
                name = f.getPath();
            _log.error("Indexing error: " + name, e);
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
            IndexWriter iw = getIndexWriter();
            iw.deleteDocuments(new Term(FIELD_NAMES.uniqueId.name(), r.getDocumentId()));
            iw.addDocument(doc);
        }
        catch(Throwable e)
        {
            _log.error("Indexing error with " + id, e);
        }
    }


    @Override
    protected void deleteIndexedContainer(String id)
    {
        try
        {
            IndexWriter w = getIndexWriter();
            Query query;
            String s="";
            try
            {
                s = "+" + SearchService.PROPERTY.container.toString() + ":" + id;
                query = new QueryParser(Version.LUCENE_30, SearchService.PROPERTY.container.toString(), _analyzer).parse(s);
            }
            catch (ParseException x)
            {
                _log.error("Unexpected exception: s=" + s, x);
                IOException io = new IOException();
                io.initCause(x);
                throw io;
            }

            w.deleteDocuments(query);
        }
        catch (IOException x)
        {
            
        }
    }


    protected synchronized void commitIndex()
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
        // CONSIDER: Set a large, but finite max field length if we get OutOfMemory errors during indexing
        if (null == _iw)
            _iw = new IndexWriter(_directory, _analyzer, IndexWriter.MaxFieldLength.UNLIMITED);

        return _iw;
    }


    private synchronized IndexSearcher getIndexSearcher() throws IOException
    {
        if (null == _searcher)
            _searcher = new IndexSearcher(_directory, true);

        return _searcher;
    }


    public List<SearchHit> search(String queryString, User user, Container root, int page)
            throws IOException
    {
        String sort = null;  // TODO: add sort parameter
        int hitsPerPage = 20;

        // UNDONE: smarter query parsing
        boolean isParticipantId = isParticipantId(user, StringUtils.strip(queryString," +-"));
        if (isParticipantId)
        {
            queryString += " " + SearchService.PROPERTY.category.toString() + ":subject^1"; // UNDONE: StudyManager.subjectCategory
        }

        Query query;
        try
        {
            query = new QueryParser(Version.LUCENE_30, FIELD_NAMES.body.name(), _analyzer).parse(queryString.toLowerCase());
        }
        catch (ParseException x)
        {
            IOException io = new IOException();
            io.initCause(x);
            throw io;
        }
        TopDocs topDocs;
        IndexSearcher searcher = getIndexSearcher();
        Filter securityFilter = new SecurityFilter(user, root);

        if (null == sort)
            topDocs = searcher.search(query, securityFilter, (page + 1) * hitsPerPage);
        else
            topDocs = searcher.search(query, securityFilter, (page + 1) * hitsPerPage, new Sort(new SortField(sort, SortField.STRING)));

        ScoreDoc[] hits = topDocs.scoreDocs;
        ArrayList<SearchHit> ret = new ArrayList<SearchHit>(hitsPerPage);

        for (int i = page * hitsPerPage; i < Math.min((page + 1) * hitsPerPage, hits.length); i++)
        {
            ScoreDoc scoreDoc = hits[i];
            Document doc = searcher.doc(scoreDoc.doc);

            SearchHit hit = new SearchHit();
            hit.totalHits = topDocs.totalHits;
            hit.summary = doc.get(FIELD_NAMES.summary.name());
            hit.url = doc.get(FIELD_NAMES.url.name());
            hit.title = doc.get(FIELD_NAMES.title.name());
            ret.add(hit);
        }

        return ret;
    }
    

/*    public String searchFormatted(String queryString, User user, Container root, int page)
    {
        int hitsPerPage = 20;
        
        try
        {
            long start = System.nanoTime();
            List<SearchHit> hits = search(queryString, user, root, page);
            long time = (System.nanoTime() - start)/1000000;

            StringBuilder html = new StringBuilder("<table><tr><td colspan=2>Found ");

            int totalHits = hits.isEmpty() ? 0 : hits.get(0).totalHits;
            html.append(Formats.commaf0.format(totalHits)).append(" result");
            if (totalHits != 1)
                html.append("s");

            html.append(" in ").append(Formats.commaf0.format(time)).append(" millisecond").append(1 != time ? "s" : "").append(".  Displaying ");

            if (hitsPerPage < totalHits)
            {
                html.append("page ").append(Formats.commaf0.format(page + 1)).append(" of ");
                html.append(Formats.commaf0.format((int)Math.ceil((double)totalHits / hitsPerPage)));
            }
            else
            {
                html.append("all results");
            }

            html.append(".</td></tr>\n");

            for (int i = page * hitsPerPage; i < Math.min((page + 1) * hitsPerPage, hits.size()); i++)
            {
                SearchHit hit = hits.get(i);

                html.append("<tr><td colspan=2>&nbsp;</td></tr>\n");
                html.append("<tr><td colspan=2><a href=\"").append(PageFlowUtil.filter(hit.url)).append("\">").append(PageFlowUtil.filter(hit.title)).append("</a>").append("</td></tr>\n");
                html.append("<tr><td width=25>&nbsp;</td><td>").append(PageFlowUtil.filter(hit.summary)).append("</td></tr>\n");
            }

            html.append("</table>\n");

            return html.toString();
        }
        catch (IOException e)
        {
            Throwable t = e;
            if (e.getCause() instanceof ParseException)
                t = e.getCause();
            return "Error: " + t.getMessage();
        }
    }
*/

    protected void shutDown()
    {
        commit();
    }
}