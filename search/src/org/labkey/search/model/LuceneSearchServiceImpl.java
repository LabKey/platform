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
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.pdfbox.exceptions.WrappedIOException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.labkey.api.data.Container;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.Resource;
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
    private static IndexWriter _iw = null;            // Don't use this directly -- it could be null or change out from underneath you.  Call getIndexWriter()
    private final Analyzer _analyzer = new SnowballAnalyzer(Version.LUCENE_CURRENT, "English");
    private static IndexSearcher _searcher = null;    // Don't use this directly -- it could be null or change out from underneath you.  Call getIndexSearcher()
    private static Directory _directory = null;

    static enum FIELD_NAMES { body, title, summary, url, container, uniqueId }

    public LuceneSearchServiceImpl()
    {
        try
        {
            File tempDir = new File(FileUtil.getTempDirectory(), "labkey_full_text_index");
            _directory = FSDirectory.open(tempDir);
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


    private static final Set<String> KNOWN_PROPERTIES = PageFlowUtil.set(PROPERTY.categories.toString(), PROPERTY.title.toString());

    @Override
    Map<?, ?> preprocess(String id, Resource r)
    {
        try
        {
            assert null != r.getDocumentId();
            assert null != r.getContainerId();

            Map<String, ?> props = r.getProperties();
            String categories = (String)props.get(PROPERTY.categories.toString());
            String body = null;
            String title = (String)props.get(PROPERTY.title.toString());
            String type = r.getContentType();

            // Skip XML for now
            if (type.startsWith("image/"))
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
                    AutoDetectParser parser = new AutoDetectParser();
                    _log.info(id + ": " + parser.getDetector());
                    parser.parse(is, handler, metadata);
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

            doc.add(new Field(FIELD_NAMES.uniqueId.toString(), r.getDocumentId(), Field.Store.YES, Field.Index.NOT_ANALYZED));
            doc.add(new Field(FIELD_NAMES.body.toString(), body, Field.Store.NO, Field.Index.ANALYZED));
            doc.add(new Field(FIELD_NAMES.title.toString(), title, Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field(FIELD_NAMES.summary.toString(), summary, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.url.toString(), url, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.container.toString(), r.getContainerId(), Field.Store.YES, Field.Index.NO));

            // TODO: all docs should have a category -- assert
            // Split the category string by whitespace, index each without stemming
            if (null != categories)
            {
                String[] array = categories.split("\\s+");

                for (String category : array)
                    doc.add(new Field(PROPERTY.categories.toString(), category.toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));
            }

            for (Map.Entry<String, ?> entry : props.entrySet())
            {
                Object value = entry.getValue();

                if (null != value)
                {
                    String stringValue = value.toString().toLowerCase();

                    if (stringValue.length() > 0)
                    {
                        String key = entry.getKey().toLowerCase();

                        // Skip known properties -- we added them above
                        if (!KNOWN_PROPERTIES.contains(key))
                            doc.add(new Field(key, stringValue, Field.Store.NO, Field.Index.ANALYZED));
                    }
                }
            }

            return Collections.singletonMap(Document.class, doc);
        }
        catch (NoClassDefFoundError err)
        {
            Throwable cause = err.getCause();
            // Suppress stack trace, etc., if Bouncy Castle isn't present.  Use cause since ClassNotFoundException's
            //  message is consistent across JVMs; NoClassDefFoundError's is not.
            if (cause != null && cause instanceof ClassNotFoundException && cause.getMessage().equals("org.bouncycastle.cms.CMSException"))
                _log.warn("Can't read encrypted document \"" + id + "\".  You must install the Bouncy Castle encryption libraries to index this document.  Refer to the LabKey Software documentation for instructions.");
            else
                logAsIndexingError(r, err);
        }
        catch (TikaException e)
        {
            Throwable cause = e.getCause();

            // Ignore NoSuchElementException in PDFBox -- see http://issues.apache.org/jira/browse/PDFBOX-546
            if (cause instanceof WrappedIOException && cause.getCause() instanceof NoSuchElementException)
                return null;

            logAsIndexingError(r, e);
        }
        catch (Throwable e)
        {
            logAsIndexingError(r, e);
        }

        return null;
    }

    private void logAsIndexingError(Resource r, Throwable e)
    {
        String name = r.getPath().toString();
        File f = r.getFile();
        if (null != f)
            name = f.getPath();

        //noinspection ThrowableInstanceNeverThrown
        ExceptionUtil.logExceptionToMothership(null, new IndexingException(name, e));
    }

    private static class IndexingException extends Exception
    {
        private IndexingException(String name, Throwable cause)
        {
            super(name, cause);
        }
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


    protected void deleteDocument(String id)
    {
        try
        {
            IndexWriter iw = getIndexWriter();
            iw.deleteDocuments(new Term(FIELD_NAMES.uniqueId.toString(), id));
        }
        catch(Throwable e)
        {
            _log.error("Indexing error deleting " + id, e);
        }
    }

    
    protected void index(String id, Resource r, Map preprocessMap)
    {
        try
        {
            Document doc = (Document)preprocessMap.get(Document.class);
            deleteDocument(r.getDocumentId());
            getIndexWriter().addDocument(doc);
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


    public SearchHit find(String id) throws IOException
    {
        IndexSearcher searcher = getIndexSearcher();
        TermQuery query = new TermQuery(new Term(FIELD_NAMES.uniqueId.toString(), id));
        TopDocs topDocs = searcher.search(query, null, 1);
        SearchResult result = createSearchResult(0, 1, topDocs, searcher);
        if (result.hits.size() != 1)
            return null;
        return result.hits.get(0);
    }
    

    // Always search title and body, but boost title
    private static final String[] standardFields = new String[]{FIELD_NAMES.title.toString(), FIELD_NAMES.body.toString()};
    private static final Float[] standardBoosts = new Float[]{2.0f, 1.0f};
    private static final Map<String, Float> boosts = new HashMap<String, Float>();

    static
    {
        for (int i = 0; i < standardFields.length; i++)
            boosts.put(standardFields[i], standardBoosts[i]);
    }

    public SearchResult search(String queryString, SearchCategory searchCategory, User user, Container root, int offset, int limit) throws IOException
    {
        String category = null==searchCategory ? null : searchCategory.toString();

        String sort = null;  // TODO: add sort parameter
        int hitsToRetrieve = offset + limit;
        boolean limitToCategory = (null != category);

        if (!limitToCategory)
        {
            // Boost "subject" results if this is a participant id
            if (isParticipantId(user, StringUtils.strip(queryString, " +-")))
                category = "subject";
        }

        Query query;

        try
        {
            QueryParser queryParser = new MultiFieldQueryParser(Version.LUCENE_30, standardFields, _analyzer, boosts);
            query = queryParser.parse(queryString);

            if (null != category)
            {
                BooleanQuery bq = new BooleanQuery();
                bq.add(query, BooleanClause.Occur.MUST);

                Query categoryQuery = new TermQuery(new Term(SearchService.PROPERTY.categories.toString(), category.toLowerCase()));

                if (limitToCategory)
                {
                    bq.add(categoryQuery, BooleanClause.Occur.MUST);
                }
                else
                {
                    categoryQuery.setBoost(3.0f);
                    bq.add(categoryQuery, BooleanClause.Occur.SHOULD);
                }
                query = bq;
            }
        }
        catch (ParseException x)
        {
            IOException io = new IOException();
            io.initCause(x);
            throw io;
        }

        IndexSearcher searcher = getIndexSearcher();
        Filter securityFilter = user==User.getSearchUser() ? null : new SecurityFilter(user, root);

        TopDocs topDocs;
        if (null == sort)
            topDocs = searcher.search(query, securityFilter, hitsToRetrieve);
        else
            topDocs = searcher.search(query, securityFilter, hitsToRetrieve, new Sort(new SortField(sort, SortField.STRING)));

        SearchResult result = createSearchResult(offset, hitsToRetrieve, topDocs, searcher);
        return result;
    }


    private SearchResult createSearchResult(int offset, int hitsToRetrieve, TopDocs topDocs, IndexSearcher searcher)
            throws IOException
    {
        ScoreDoc[] hits = topDocs.scoreDocs;

        List<SearchHit> ret = new LinkedList<SearchHit>();

        for (int i = offset; i < Math.min(hitsToRetrieve, hits.length); i++)
        {
            ScoreDoc scoreDoc = hits[i];
            Document doc = searcher.doc(scoreDoc.doc);

            SearchHit hit = new SearchHit();
            hit.container = doc.get(FIELD_NAMES.container.toString());
            hit.docid = doc.get(FIELD_NAMES.uniqueId.toString());
            hit.summary = doc.get(FIELD_NAMES.summary.toString());
            String url = doc.get(FIELD_NAMES.url.toString());
            String docid = "_docid=" + PageFlowUtil.encode(hit.docid);
            hit.url = url + (-1==url.indexOf("?") ? "?" : "&") + docid;
            hit.title = doc.get(FIELD_NAMES.title.toString());
            ret.add(hit);
        }

        SearchResult result = new SearchResult();
        result.totalHits = topDocs.totalHits;
        result.hits = ret;
        return result;
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


    @Override
    public Map<String, Object> getStats()
    {
        Map<String,Object> map = super.getStats();
        try
        {
            IndexSearcher is = getIndexSearcher();
            map.put("Indexed Documents", is.getIndexReader().numDocs());
        }
        catch (IOException x)
        {
            
        }
        return map;
    }
}