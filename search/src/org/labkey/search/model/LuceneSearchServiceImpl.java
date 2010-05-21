/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Version;
import org.apache.poi.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.*;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.search.view.SearchWebPart;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
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
    private static final Logger _log = Logger.getLogger(LuceneSearchServiceImpl.class);
    static final Version LUCENE_VERSION = Version.LUCENE_30;

    private final Analyzer _analyzer = ExternalAnalyzer.SnowballAnalyzer.getAnalyzer();

    // Changes to _index are rare (only when admin changes the index path), but we want any changes to be visible to
    // other threads immediately.
    private volatile WritableIndex _index;

    private static ExternalIndex _externalIndex;

    static enum FIELD_NAMES { body, displayTitle, title /* use "title" keyword for search title */, summary,
        url, container, resourceId, uniqueId, navtrail }


    private void initializeIndex()
    {
        try
        {
            File indexDir = SearchPropertyManager.getPrimaryIndexDirectory();
            _index = new WritableIndex(indexDir, _analyzer);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void updatePrimaryIndex()
    {
        super.updatePrimaryIndex();
        initializeIndex();
        clearLastIndexed();
    }

    @Override
    public void start()
    {
        try
        {
            initializeIndex();
            resetExternalIndex();
        }
        catch (Exception e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        super.start();
    }


    public void resetExternalIndex() throws IOException, InterruptedException
    {
        if (null != _externalIndex)
        {
            _externalIndex.close();
            _externalIndex = null;
        }

        ExternalIndexProperties props = SearchPropertyManager.getExternalIndexProperties();

        if (props.hasExternalIndex())
        {
            File externalIndexFile = new File(props.getExternalIndexPath());
            Analyzer analyzer = ExternalAnalyzer.valueOf(props.getExternalIndexAnalyzer()).getAnalyzer();

            if (externalIndexFile.exists())
                _externalIndex = new ExternalIndex(externalIndexFile, analyzer);
        }
    }


    public void swapExternalIndex() throws IOException, InterruptedException
    {
        if (null != _externalIndex)
        {
            _externalIndex.swap();
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
        _index.clear();
    }


    private static final Set<String> KNOWN_PROPERTIES = PageFlowUtil.set(
            PROPERTY.categories.toString(), PROPERTY.displayTitle.toString(),
            PROPERTY.navtrail.toString(), PROPERTY.securableResourceId.toString());

    @Override
    Map<?, ?> preprocess(String id, WebdavResource r)
    {
        FileStream fs = null;

        try
        {
            assert null != r.getDocumentId();
            assert null != r.getContainerId();

            fs = r.getFileStream(User.getSearchUser());

            if (null == fs)
            {
                logAsWarning(r, "FileStream is null");
                return null;
            }
            
            Map<String, ?> props = r.getProperties();
            assert null != props;

            String categories = (String)props.get(PROPERTY.categories.toString());
            assert null != categories;

            String body = null;
            String displayTitle = (String)props.get(PROPERTY.displayTitle.toString());
            String searchTitle = (String)props.get(PROPERTY.searchTitle.toString());
            String type = r.getContentType();

            // Skip images for now
            if (isImage(type) || isZip(type))
            {
                return null;
            }

            InputStream is = fs.openInputStream();

            if (null == is)
            {
                logAsWarning(r, "InputStream is null");
                return null;
            }

            if ("text/html".equals(type))
            {
                String html;
                if (fs.getSize() > FILE_SIZE_LIMIT)
                    html = "<html><body></body></html>";
                else
                    html = PageFlowUtil.getStreamContentsAsString(is);

                // TODO: Need better check for issue HTML vs. rendered page HTML
                if (r instanceof ActionResource)
                {
                    HTMLContentExtractor extractor = new HTMLContentExtractor.LabKeyPageHTMLExtractor(html);
                    body = extractor.extract();
                    String extractedTitle = extractor.getTitle();

                    if (StringUtils.isBlank(displayTitle))
                        displayTitle = extractedTitle;

                    searchTitle = searchTitle + " " + extractedTitle;
                }

                if (StringUtils.isEmpty(body))
                {
                    body = new HTMLContentExtractor.GenericHTMLExtractor(html).extract();
                }
            }
            else if (type.startsWith("text/") && !type.contains("xml"))
            {
                if (fs.getSize() > FILE_SIZE_LIMIT)
                    body = "";
                else
                    body = PageFlowUtil.getStreamContentsAsString(is);
            }
            else
            {
                Metadata metadata = new Metadata();
                metadata.add(Metadata.RESOURCE_NAME_KEY, PageFlowUtil.encode(r.getName()));
                metadata.add(Metadata.CONTENT_TYPE, r.getContentType());
                ContentHandler handler = new BodyContentHandler();

                parse(r, fs, is, handler, metadata);

                body = handler.toString();

                String extractedTitle = metadata.get(Metadata.TITLE);
                if (StringUtils.isBlank(displayTitle))
                    displayTitle = extractedTitle;
                searchTitle = searchTitle + getInterestingMetadataProperties(metadata);
            }
            fs.closeInputStream();
            fs = null;

            String url = r.getExecuteHref(null);
            assert null != url;
            assert null != displayTitle;
            _log.debug("parsed " + url);

            if (StringUtils.isBlank(searchTitle))
                searchTitle = displayTitle;

            // Add all container path parts to search keywords
            Container c = ContainerManager.getForId(r.getContainerId());

            for (String part : c.getParsedPath())
                searchTitle = searchTitle + " " + part;

            String summary = extractSummary(body, displayTitle);
            // Split the category string by whitespace, index each without stemming
            String[] categoryArray = categories.split("\\s+");

            Document doc = new Document();

            // Index and store the unique document ID
            doc.add(new Field(FIELD_NAMES.uniqueId.toString(), r.getDocumentId(), Field.Store.YES, Field.Index.NOT_ANALYZED));

            // Index, but don't store
            doc.add(new Field(FIELD_NAMES.body.toString(), body, Field.Store.NO, Field.Index.ANALYZED));
            doc.add(new Field(FIELD_NAMES.title.toString(), searchTitle, Field.Store.NO, Field.Index.ANALYZED));
            for (String category : categoryArray)
                doc.add(new Field(PROPERTY.categories.toString(), category.toLowerCase(), Field.Store.NO, Field.Index.NOT_ANALYZED));

            // Store, but don't index
            doc.add(new Field(FIELD_NAMES.displayTitle.toString(), displayTitle, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.summary.toString(), summary, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.url.toString(), url, Field.Store.YES, Field.Index.NO));
            doc.add(new Field(FIELD_NAMES.container.toString(), r.getContainerId(), Field.Store.YES, Field.Index.NO));
            if (null != props.get(PROPERTY.navtrail.toString()))
                doc.add(new Field(FIELD_NAMES.navtrail.toString(), (String)props.get(PROPERTY.navtrail.toString()), Field.Store.YES, Field.Index.NO));
            String resourceId = (String)props.get(PROPERTY.securableResourceId.toString());
            if (null != resourceId && !resourceId.equals(r.getContainerId()))
                doc.add(new Field(FIELD_NAMES.resourceId.toString(), resourceId, Field.Store.YES, Field.Index.NO));
            // Index the remaining properties, but don't store
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
            // message is consistent across JVMs; NoClassDefFoundError's is not.  Note: This shouldn't happen any more
            // since Bouncy Castle now ships with Tika.
            if (cause != null && cause instanceof ClassNotFoundException && cause.getMessage().equals("org.bouncycastle.cms.CMSException"))
                _log.warn("Can't read encrypted document \"" + id + "\".  You must install the Bouncy Castle encryption libraries to index this document.  Refer to the LabKey Software documentation for instructions.");
            else
                logAsPreProcessingException(r, err);
        }
        catch (TikaException e)
        {
            String topMessage = (null != e.getMessage() ? e.getMessage() : "");
            Throwable cause = e.getCause();

            // Get the root cause
            Throwable rootCause = e;

            while (null != rootCause.getCause())
                rootCause = rootCause.getCause();

            // IndexOutOfBoundsException has a dumb message
            String rootMessage = (rootCause instanceof IndexOutOfBoundsException ? rootCause.getClass().getSimpleName() : rootCause.getMessage());

            if (topMessage.startsWith("TIKA-237: Illegal SAXException"))
            {
                // Malformed XML document -- CONSIDER: run XML tidy on the document and retry
                logAsWarning(r, "Malformed XML document");
            }
            else if (cause instanceof java.util.zip.ZipException)
            {
                // Malformed zip file
                logAsWarning(r, "Malformed zip file");
            }
            else if (cause instanceof EncryptedDocumentException)
            {
                // Encrypted office document
                logAsWarning(r, "Document is password protected");
            }
            else if (topMessage.startsWith("Error creating OOXML extractor"))
            {
                logAsWarning(r, "Can't parse this Office document [" + rootMessage + "]");
            }
            else if (topMessage.startsWith("TIKA-198: Illegal IOException from org.apache.tika.parser.microsoft.OfficeParser"))
            {
                // Document is currently open in Word
                logAsWarning(r, "Can't parse this Office document [" + rootMessage + "]");
            }
            else if (topMessage.startsWith("TIKA-198: Illegal IOException from org.apache.tika.parser.pdf.PDFParser"))
            {
                logAsWarning(r, "Can't parse this PDF document [" + rootMessage + "]");
            }
            else if (topMessage.startsWith("Unexpected RuntimeException from org.apache.tika.parser.microsoft"))
            {
                logAsWarning(r, "Can't parse this document [" + rootMessage + "]");
            }
            else
            {
                logAsPreProcessingException(r, e);
            }
        }
        catch (IOException e)
        {
            // Permissions problem, network drive disappeared, file disappeared, etc.
            logAsWarning(r, e);
        }
        catch (Throwable e)
        {
            logAsPreProcessingException(r, e);
        }
        finally
        {
            if (null != fs)
            {
                try
                {
                    fs.closeInputStream();
                }
                catch (IOException x)
                {
                }
            }
        }

        return null;
    }


    /**
     * parse the document of the resource, not that parse() and accept() should agree on what is parsable
     * 
     * @param r
     * @param fs
     * @param is
     * @param handler
     * @param metadata
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     */
    void parse(WebdavResource r, FileStream fs, InputStream is, ContentHandler handler, Metadata metadata) throws IOException, SAXException, TikaException
    {
        // TREAT files over the size limit as empty files
        long size = fs.getSize();

        Parser parser = getParser();

        if (!is.markSupported())
            is = new BufferedInputStream(is);

        boolean parsed = false;

        if (size <= FILE_SIZE_LIMIT)
        {
            parser.parse(is, handler, metadata);
            String type = metadata.get(Metadata.CONTENT_TYPE);
            if (!type.equals("application/octet-stream"))
                parsed = true;
        }
        
        if (!parsed)
        {
            DocumentParser p = detectParser(r, is);
            if (null != p)
            {
                metadata.add(Metadata.CONTENT_TYPE, p.getMediaType());
                p.parse(is, handler);
                parsed = true;
            }
        }

        if (!parsed && size > FILE_SIZE_LIMIT)
        {
            logAsWarning(r, "The document is too large");
        }
    }

    
    /**
     * This method is used to indicate to the crawler (or any external process) which files
     * this indexer will not index.
     *
     * The caller may choose to skip the document, or substitute an alternate document.
     * e.g. file name only
     *
     * @param r
     * @return
     */
    @Override
    public boolean accept(WebdavResource r)
    {
        try
        {
            String contentType = r.getContentType();
            if (isImage(contentType) || isZip(contentType))
                return false;
            FileStream fs = r.getFileStream(User.getSearchUser());
            if (null == fs)
                return false;
            long size = fs.getSize();
            fs.closeInputStream();
            if (size > FILE_SIZE_LIMIT)
            {
                DocumentParser p = detectParser(r, null);
                return p != null;
            }
            return true;
        }
        catch (IOException x)
        {
            return false;
        }
    }


    DocumentParser detectParser(WebdavResource r, InputStream in)
    {
        InputStream is = in;
        try
        {
            if (null == is)
            {
                is = r.getInputStream(User.getSearchUser());
                if (null == is)
                    return null;
            }
            DocumentParser[] parsers = _documentParsers.get();
            is.skip(Long.MIN_VALUE);
            byte[] header = FileUtil.readHeader(is, 8*1024);
            for (DocumentParser p : parsers)
            {
                if (p.detect(header))
                    return p;
            }
            return null;
        }
        catch (IOException x)
        {
            return null;
        }
        finally
        {
            if (is != in)
                IOUtils.closeQuietly(is);
        }
    }



    static final AutoDetectParser _parser = new AutoDetectParser();

    private Parser getParser()
    {
        return _parser;
    }

    
    // See https://issues.apache.org/jira/browse/TIKA-374 for status of a Tika concurrency problem that forces
    // us to use single-threaded pre-processing.
    @Override
    protected boolean isPreprocessThreadSafe()
    {
        return false;
    }
    

    private String getNameToLog(WebdavResource r)
    {
        String name = r.getPath().toString();
        File f = r.getFile();
        if (null != f)
            name = f.getPath();

        return name;
    }

    private void logAsPreProcessingException(WebdavResource r, Throwable e)
    {
        //noinspection ThrowableInstanceNeverThrown
        ExceptionUtil.logExceptionToMothership(null, new PreProcessingException(getNameToLog(r), e));
    }

    private void logAsWarning(WebdavResource r, Exception e)
    {
        logAsWarning(r, e.getMessage());
    }

    private void logAsWarning(WebdavResource r, String message)
    {
        _log.warn("Can't index file \"" + getNameToLog(r) + "\" due to: " + message);
    }

    private static class PreProcessingException extends Exception
    {
        private PreProcessingException(String name, Throwable cause)
        {
            super(name, cause);
        }
    }

    private static final String[] INTERESTING_PROP_NAMES = new String[] {
        Metadata.TITLE,
        Metadata.AUTHOR,
        Metadata.KEYWORDS,
        Metadata.COMMENTS,
        Metadata.NOTES,
        Metadata.COMPANY,
        Metadata.PUBLISHER
    };

    public String getInterestingMetadataProperties(Metadata metadata)
    {
        StringBuilder sb = new StringBuilder();

        for (String key : INTERESTING_PROP_NAMES)
        {
            String value = metadata.get(key);

            if (null != value)
            {
                sb.append(" ");
                sb.append(value);
            }
        }

        return sb.toString();
    }

    private static final int SUMMARY_LENGTH = 400;
    private static final Pattern TITLE_STRIPPING_PATTERN = Pattern.compile(": /" + GUID.guidRegEx);
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[\\s/]");  // Any whitespace character or slash

    private String extractSummary(String body, String title)
    {
        title = TITLE_STRIPPING_PATTERN.matcher(title).replaceAll("");

        if (body.startsWith(title))
        {
            body = body.substring(title.length());
            body = StringUtils.stripStart(body,"/. \n\r\t");
        }

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
        _index.deleteDocument(id);
    }

    
    protected void index(String id, WebdavResource r, Map preprocessMap)
    {
        try
        {
            Document doc = (Document)preprocessMap.get(Document.class);
            _index.index(r.getDocumentId(), doc);
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
            Query query;
            String s="";
            try
            {
                s = "+" + SearchService.PROPERTY.container.toString() + ":" + id;
                query = new QueryParser(LUCENE_VERSION, SearchService.PROPERTY.container.toString(), _analyzer).parse(s);
            }
            catch (ParseException x)
            {
                _log.error("Unexpected exception: s=" + s, x);
                IOException io = new IOException();
                io.initCause(x);
                throw io;
            }

            _index.deleteQuery(query);
        }
        catch (IOException x)
        {
            
        }
    }


    protected void commitIndex()
    {
        _index.commit();
    }


    public SearchHit find(String id) throws IOException
    {
        LabKeyIndexSearcher searcher = _index.getSearcher();

        try
        {
            TermQuery query = new TermQuery(new Term(FIELD_NAMES.uniqueId.toString(), id));
            TopDocs topDocs = searcher.search(query, null, 1);
            SearchResult result = createSearchResult(0, 1, topDocs, searcher);
            if (result.hits.size() != 1)
                return null;
            return result.hits.get(0);
        }
        finally
        {
            _index.releaseSearcher(searcher);
        }
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

    public WebPartView getSearchView(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink)
    {
        return new SearchWebPart(includeSubfolders, textBoxWidth, includeHelpLink);
    }

    public SearchResult search(String queryString, @Nullable SearchCategory searchCategory, User user, Container root, boolean recursive, int offset, int limit) throws IOException
    {
        String category = null == searchCategory ? null : searchCategory.toString();

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
            QueryParser queryParser = new MultiFieldQueryParser(LUCENE_VERSION, standardFields, _analyzer, boosts);
            query = queryParser.parse(queryString);
        }
        catch (ParseException x)
        {
            throw new IOException(x.getMessage());
        }
        catch (IllegalArgumentException x)
        {
            throw new IOException("Cannot parse '" + queryString + "': " + x.getMessage());
        }

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

        LabKeyIndexSearcher searcher = _index.getSearcher();

        try
        {
            Filter securityFilter = user==User.getSearchUser() ? null : new SecurityFilter(user, root, recursive);
            TopDocs topDocs;

            if (null == sort)
                topDocs = searcher.search(query, securityFilter, hitsToRetrieve);
            else
                topDocs = searcher.search(query, securityFilter, hitsToRetrieve, new Sort(new SortField(sort, SortField.STRING)));

            return createSearchResult(offset, hitsToRetrieve, topDocs, searcher);
        }
        finally
        {
            _index.releaseSearcher(searcher);
        }
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
            hit.url = doc.get(FIELD_NAMES.url.toString());

            if (null != hit.docid)
            {
                String docid = "_docid=" + PageFlowUtil.encode(hit.docid);
                hit.url = hit.url + (-1 == hit.url.indexOf("?") ? "?" : "&") + docid;
            }

            hit.displayTitle = doc.get(FIELD_NAMES.displayTitle.toString());

            // No display title, try title
            if (StringUtils.isBlank(hit.displayTitle))
                hit.displayTitle = doc.get(FIELD_NAMES.title.toString());

            // No title at all... just use URL
            if (StringUtils.isBlank(hit.displayTitle))
                hit.displayTitle = hit.url;

            // UNDONE FIELD_NAMES.navtree
            hit.navtrail = doc.get(FIELD_NAMES.navtrail.toString());
            ret.add(hit);
        }

        SearchResult result = new SearchResult();
        result.totalHits = topDocs.totalHits;
        result.hits = ret;
        return result;
    }


    @Override
    public boolean hasExternalIndexPermission(User user)
    {
        if (null == _externalIndex)
            return false;

        SecurityPolicy policy = SecurityManager.getPolicy(_externalIndex);

        return policy.hasPermission(user, ReadPermission.class);
    }


    @Override
    public SearchResult searchExternal(String queryString, int offset, int limit) throws IOException
    {
        if (null == _externalIndex)
            throw new IllegalStateException("External index is not defined");

        int hitsToRetrieve = offset + limit;
        LabKeyIndexSearcher searcher = _externalIndex.getSearcher();

        try
        {
            QueryParser queryParser = new MultiFieldQueryParser(Version.LUCENE_30, new String[]{"content", "title"}, _externalIndex.getAnalyzer());
            Query query = queryParser.parse(queryString);
            TopDocs docs = searcher.search(query, hitsToRetrieve);
            return createSearchResult(offset, hitsToRetrieve, docs, searcher);
        }
        catch (ParseException x)
        {
            throw new IOException(x.getMessage());
        }
        finally
        {
            _externalIndex.releaseSearcher(searcher);
        }
    }


    protected void shutDown()
    {
        commit();

        try
        {
            _index.close();
        }
        catch (Exception e)
        {
            _log.error("Closing index", e);
        }

        try
        {
            if (null != _externalIndex)
                _externalIndex.close();
        }
        catch (Exception e)
        {
            _log.error("Closing external index", e);
        }
    }


    @Override
    public Map<String, Object> getStats()
    {
        Map<String, Object> map = new LinkedHashMap<String,Object>();

        try
        {
            LabKeyIndexSearcher is = null;

            try
            {
                is = _index.getSearcher();
                map.put("Indexed Documents", is.getIndexReader().numDocs());
            }
            finally
            {
                if (null != is)
                    _index.releaseSearcher(is);
            }
        }
        catch (IOException x)
        {

        }

        map.putAll(super.getStats());
        return map;
    }



    private boolean isImage(String contentType)
    {
        return contentType.startsWith("image/");
    }
    

    private boolean isZip(String contentType)
    {
        if (contentType.startsWith("application/x-"))
        {
            String type = contentType.substring("application/x-".length());
            if (type.contains("zip"))
                return true;
            if (type.contains("tar"))
                return true;
            if (type.contains("compress"))
                return true;
            if (type.contains("archive"))
                return true;
        }
        return false;
    }

    @Override
    public void maintenance()
    {
        super.maintenance();
        _index.optimize();
    }

    @Override
    public List<SecurableResource> getSecurableResources(User user)
    {
        if (null != _externalIndex)
        {
            SecurityPolicy policy = org.labkey.api.security.SecurityManager.getPolicy(_externalIndex);
            if (policy.hasPermission(user, AdminPermission.class))
                return Collections.singletonList((SecurableResource)_externalIndex);
        }

        return Collections.emptyList();
    }
}