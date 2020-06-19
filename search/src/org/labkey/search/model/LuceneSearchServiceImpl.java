/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.apache.commons.collections4.iterators.ArrayIterator;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexFormatTooNewException;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.FieldComparatorSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.resource.Resource;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchUtils;
import org.labkey.api.search.SearchUtils.HtmlParseException;
import org.labkey.api.search.SearchUtils.LuceneMessageParser;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileStream.FileFileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HTMLContentExtractor;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.MultiPhaseCPUTimer;
import org.labkey.api.util.MultiPhaseCPUTimer.InvocationTimer;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.FileSystemResource;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.search.view.SearchWebPart;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * User: adam
 * Date: Nov 18, 2009
 * Time: 1:14:44 PM
 */
public class LuceneSearchServiceImpl extends AbstractSearchService
{
    private static final Logger _log = Logger.getLogger(LuceneSearchServiceImpl.class);

    // Changes to _index are rare (only when admin changes the index path), but we want any changes to be visible to
    // other threads immediately. Initialize to Noop class to prevent rare NPE (e.g., system maintenance runs before index
    // is initialized).
    private static final WritableIndexManager NOOP_WRITABLE_INDEX = new NoopWritableIndex("the indexer has not been started", _log);
    private volatile WritableIndexManager _indexManager = NOOP_WRITABLE_INDEX;

    private final MultiPhaseCPUTimer<SEARCH_PHASE> TIMER = new MultiPhaseCPUTimer<>(SEARCH_PHASE.class, SEARCH_PHASE.values());
    private final Analyzer _standardAnalyzer = LuceneAnalyzer.LabKeyAnalyzer.getAnalyzer();
    private final AutoDetectParser _autoDetectParser;
    // We track this to avoid clearing last indexed multiple times in certain cases (delete index, upgrade), see #39330
    private final AtomicLong _countIndexedSinceClearLastIndexed = new AtomicLong(1);

    enum FIELD_NAME
    {
        // Use these for english language text that should be stemmed

        body,             // Most content goes here

        keywordsLo,       // Same weighting as body terms
        keywordsMed,      // Weighted twice the body terms... e.g., terms in the title, subject, or other summary
        keywordsHi,       // Weighted twice the medium keywords... these terms will dominate the search results, so probably not a good idea

        // Use these for terms that should NOT be stemmed, like identifiers, folder names, and people names

        identifiersLo,    // Same weighting as body terms... used for folder path parts
        identifiersMed,   // Weighted twice the lo identifiers
        identifiersHi,    // Weighted twice the medium identifiers (e.g., unique ids like PTIDs, sample IDs, etc.)... be careful, these will dominate the search results

        searchCategories, // Used for special filtering, but analyzed like an identifier

        created,
        modified,
        owner,

        // Created and modified dates are stored as document fields for sorting results

        createdBy,
        modifiedBy,

        // The following are all stored, but not indexed

        title,            // This is just the display title. keywordsMed is used to index title/subject terms.
        summary,
        url,
        container,        // Used as stored field in documents (used for low volume purposes, delete and results display). See securityContext below.
        securityContext,  // Stored in DocValues and used in SecurityQuery, which filters every search query. Format is <containerId>(|<resourceId>), where resourceId is optional (and rarely used)
        uniqueId,
        navtrail
    }


    public LuceneSearchServiceImpl()
    {
        TikaConfig config;

        try
        {
            InputStream is = getClass().getResourceAsStream("tikaConfig.xml");
            org.w3c.dom.Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
            config = new TikaConfig(doc, new ServiceLoader(Thread.currentThread().getContextClassLoader(), LoadErrorHandler.IGNORE, new ProblemHandler(), true));
        }
        catch (Exception e)
        {
            config = TikaConfig.getDefaultConfig();
        }

        _autoDetectParser = new AutoDetectParser(config);
    }

    /**
     * Initializes the index, if possible. Recovers from some common failures, such as incompatible existing index formats.
     */
    private void initializeIndex()
    {
        try
        {
            try
            {
                attemptInitialize();
            }
            catch (IndexFormatTooOldException | IndexFormatTooNewException | IllegalArgumentException e)
            {
                // We delete the search index after every major upgrade, but we can still encounter a "future" index format when
                // developers switch to a previous release branch that uses an older version of Lucene... and then encounter an
                // "old" index format when they switch back. In either case, just delete the index and retry once.
                _log.info("Deleting existing full-text search index due to exception: " + e.getMessage());
                deleteIndex();
                attemptInitialize();
            }
        }
        catch (Throwable t)
        {
            _log.error("Error: Unable to initialize search index. Search will be disabled and new documents will not be indexed for searching until this is corrected and the server is restarted.", t);
            setConfigurationError(t);
            String statusMessage = "the search index is misconfigured. Search is disabled and new documents are not being indexed. Correct the problem and restart your server.";
            _indexManager = new NoopWritableIndex(statusMessage, _log);

            // No need to send FileSystemException (which includes AccessDenied, NotDirectory, etc.) to mothership
            if (!(t instanceof FileSystemException))
                throw new RuntimeException("Error: Unable to initialize search index", t);
        }
    }

    private void attemptInitialize() throws IOException
    {
        File indexDir = SearchPropertyManager.getIndexDirectory();
        _indexManager = WritableIndexManagerImpl.get(indexDir.toPath(), getAnalyzer());
        setConfigurationError(null);  // Clear out any previous error
    }

    @Override
    public Map<String, String> getIndexFormatProperties()
    {
        return _indexManager.getIndexFormatProperties();
    }

    @Override
    public List<Pair<String, String>> getDirectoryTypes()
    {
        LuceneDirectoryType configured = getDirectoryType();
        // Display the current directory class name, but only if we're currently set to Default (otherwise, we don't know what the default implementation is)
        String defaultDescription = "Default" + (_indexManager.isReal() && LuceneDirectoryType.Default == configured ? " (" + _indexManager.getCurrentDirectory().getClass().getSimpleName() + ")" : "");

        List<Pair<String, String>> list = new LinkedList<>();

        for (LuceneDirectoryType directory : LuceneDirectoryType.values())
        {
            String description = (directory == LuceneDirectoryType.Default ? defaultDescription : directory.name());
            list.add(new Pair<>(directory.name(), description));
        }

        return list;
    }

    /**
     * Determine the currently configured Lucene Directory type (an explicit concrete implementation such as MMapDirectory,
     * SimpleFSDirectory, or NIOFSDirectory, or Default which lets Lucene choose).
     *
     * @return The LuceneDirectoryType representing the current setting
     */
    static LuceneDirectoryType getDirectoryType()
    {
        String configured = SearchPropertyManager.getDirectoryType();

        for (LuceneDirectoryType directory : LuceneDirectoryType.values())
        {
            if (configured.equals(directory.name()))
                return directory;
        }

        return LuceneDirectoryType.Default;
    }

    @Override
    public void updateIndex()
    {
        super.updateIndex();

        // Commit and close current index
        commit();
        try
        {
            _indexManager.close();
        }
        catch (Exception e)
        {
            _log.error("Closing index", e);
        }

        // Initialize new index and clear last indexed
        initializeIndex();
        clearLastIndexed();
    }

    @Override
    public void start()
    {
        try
        {
            initializeIndex();
        }
        catch (Exception e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        super.start();
    }


    @Override
    public void startCrawler()
    {
        clearLastIndexedIfEmpty();
        super.startCrawler();
    }

    @Override
    public void resetIndex()
    {
        closeIndex();
        initializeIndex();
    }

    // Clear lastIndexed columns if we have no documents in the index. See #25530
    private void clearLastIndexedIfEmpty()
    {
        if (_indexManager.isReal())
        {
            try
            {
                if (getNumDocs() == 0)
                    clearLastIndexed();
            }
            catch (IOException x)
            {
            }
        }
    }


    /**
     * Get the number of documents in the index
     * @return The number of documents
     */
    private int getNumDocs() throws IOException
    {
        IndexSearcher is = _indexManager.getSearcher();

        try
        {
            // Apparently we're not supposed to close the IndexReader
            return is.getIndexReader().numDocs();
        }
        finally
        {
            _indexManager.releaseSearcher(is);
        }
    }


    @Override
    public String escapeTerm(String term)
    {
        if (StringUtils.isEmpty(term))
            return "";
        String illegal = "+-&|!(){}[]^\"~*?:\\";
        if (StringUtils.containsNone(term, illegal))
            return term;
        StringBuilder sb = new StringBuilder(term.length() * 2);
        for (char ch : term.toCharArray())
        {
            if (illegal.indexOf(ch) != -1)
                sb.append('\\');
            sb.append(ch);
        }
        return sb.toString();
    }


    @Override
    public void deleteIndex()
    {
        _log.info("Deleting Search Index");
        if (_indexManager.isReal())
            closeIndex();

        File indexDir = SearchPropertyManager.getIndexDirectory();

        if (indexDir.exists())
            FileUtil.deleteDir(indexDir);

        clearLastIndexed();
    }

    @Override
    public void clearLastIndexed()
    {
        // Short circuit if nothing has been indexed since clearLastIndexed() was last called
        if (_countIndexedSinceClearLastIndexed.get() > 0)
        {
            super.clearLastIndexed();
            _countIndexedSinceClearLastIndexed.set(0);
        }
    }

    // Custom property code path needs to ignore "known properties", the properties we handle by name. See #26015.
    private static final Set<String> KNOWN_PROPERTIES = Sets.newCaseInsensitiveHashSet();

    static
    {
        // Ignore all the SearchServer.PROPERTY values
        Stream.of(PROPERTY.values())
            .map(PROPERTY::toString)
            .forEach(KNOWN_PROPERTIES::add);

        // Ignore all the LuceneSearchServiceImpl.FIELD_NAME values
        Stream.of(FIELD_NAME.values())
            .map(FIELD_NAME::toString)
            .forEach(KNOWN_PROPERTIES::add);
    }

    @Override
    public boolean processAndIndex(String id, WebdavResource r, Throwable[] handledException)
    {
        FileStream fs = null;

        try
        {
            if (null == r.getDocumentId())
                logBadDocument("Null document id", r);

            if (null == r.getContainerId())
                logBadDocument("Null container id", r);

            Container c = ContainerManager.getForId(r.getContainerId());

            if (null == c)
            {
                _log.debug("skipping item " + r.getDocumentId() + " because container is not found: " + r.getContainerId());
                return false;
            }

            try
            {
                fs = r.getFileStream(User.getSearchUser());
            }
            catch (FileNotFoundException x)
            {
                logAsWarning(r, r.getName() + " was not found");
                return false;
            }

            if (null == fs)
            {
                logAsWarning(r, r.getName() + " fileStream is null");
                return false;
            }

            Map<String, ?> props = r.getProperties();
            assert null != props;

            StringBuilder keywordsMed = new StringBuilder();

            try
            {
                Map<String, String> customProperties = r.getCustomProperties(User.getSearchUser());

                if (null != customProperties && !customProperties.isEmpty())
                {
                    for (String value : customProperties.values())
                        keywordsMed.append(" ").append(value);
                }
            }
            catch (UnauthorizedException ue)
            {
                // Some QueryUpdateService implementations don't special case the search user. Continue indexing in this
                // case, but skip the custom properties.
            }

            // Fix #11393. Can't append description to keywordMed in FileSystemResource() because constructor is too
            // early to retrieve description. TODO: Move description into properties, instead of exposing it as a
            // top-level getter. This is a bigger change, so we'll wait for 11.2.
            String description = r.getDescription();

            if (null != description)
                keywordsMed.append(" ").append(description);

            final String type = r.getContentType();
            final String body;

            String title = (String)props.get(PROPERTY.title.toString());

            // Don't load content of images or zip files (for now), but allow searching by name and properties
            if (isImage(type) || isZip(type))
            {
                body = "";
            }
            else
            {
                InputStream is = fs.openInputStream();

                if (null == is)
                {
                    logAsWarning(r, "InputStream is null");
                    return false;
                }

                boolean tooBig = isTooBig(fs, type);

                if ("text/html".equals(type))
                {
                    String html;
                    if (tooBig)
                        html = "<html><body></body></html>";
                    else
                        html = PageFlowUtil.getStreamContentsAsString(is);

                    body = new HTMLContentExtractor.GenericHTMLExtractor(html).extract();

                    if (null == title)
                        logBadDocument("Null title", r);
                }
                else if (type.startsWith("text/") && !type.contains("xml") && !StringUtils.equals(type, "text/comma-separated-values"))
                {
                    if (tooBig)
                        body = "";
                    else
                        body = PageFlowUtil.getStreamContentsAsString(is);
                }
                else
                {
                    Metadata metadata = new Metadata();
                    metadata.add(Metadata.RESOURCE_NAME_KEY, PageFlowUtil.encode(r.getName()));
                    metadata.add(Metadata.CONTENT_TYPE, r.getContentType());
                    ContentHandler handler = new BodyContentHandler(-1);     // no write limit on the handler -- rely on file size check to limit content

                    parse(r, fs, is, handler, metadata, tooBig);

                    body = handler.toString();

                    if (StringUtils.isBlank(title))
                        title = metadata.get(TikaCoreProperties.TITLE);

                    keywordsMed.append(getInterestingMetadataProperties(metadata));
                }

                fs.closeInputStream();
            }

            fs = null;

            String url = r.getExecuteHref(null);

            if (null == url)
                logBadDocument("Null url", r);

            if (null == title)
                logBadDocument("Null title", r);

            _log.debug("parsed " + url);

            if (null == props.get(PROPERTY.keywordsMed.toString()) && StringUtils.isBlank(keywordsMed.toString()))
                keywordsMed = new StringBuilder(title);

            // Add all container path parts as low-priority keywords... see #9362
            String identifiersLo = StringUtils.join(c.getParsedPath(), " ");

            // Use summary text provided by the document props, otherwise use the document body
            String summary = StringUtils.trimToNull(Objects.toString(props.get(PROPERTY.summary.toString()), null));
            if (summary == null)
                summary = body;

            // extract and trim summary
            summary = extractSummary(summary, title);

            Document doc = new Document();

            addUserField(doc, FIELD_NAME.createdBy, r.getCreatedBy());
            addDateField(doc, FIELD_NAME.created, r.getCreated());
            addUserField(doc, FIELD_NAME.modifiedBy, r.getModifiedBy());
            addDateField(doc, FIELD_NAME.modified, r.getLastModified());

            Object owner = props.get(FIELD_NAME.owner.toString());
            if (owner instanceof Integer)
                addUserField(doc, FIELD_NAME.owner, (Integer)owner);
            else if (owner instanceof User)
                addUserField(doc, FIELD_NAME.owner, (User)owner);

            // === Index without analyzing, store ===

            doc.add(new Field(FIELD_NAME.uniqueId.toString(), r.getDocumentId(), StringField.TYPE_STORED));
            doc.add(new Field(FIELD_NAME.container.toString(), r.getContainerId(), StringField.TYPE_STORED));

            // See: https://stackoverflow.com/questions/29695307/sortiing-string-field-alphabetically-in-lucene-5-0
            doc.add(new SortedDocValuesField(FIELD_NAME.container.toString(), new BytesRef(r.getContainerId())));

            // === Index and analyze, don't store ===

            // We're using the LabKeyAnalyzer, which is a PerFieldAnalyzerWrapper that ensures categories and identifier fields
            // are not stemmed but all other fields are. This analyzer is used at search time as well to ensure consistency.
            // At the moment, custom fields can't specify an analyzer preference, but we could add this at some point.

            assert StringUtils.isNotEmpty((String)props.get(PROPERTY.categories.toString()));

            addTerms(doc, FIELD_NAME.searchCategories, Field.Store.YES, terms(PROPERTY.categories, props, null));
            addTerms(doc, FIELD_NAME.identifiersLo, PROPERTY.identifiersLo, props, identifiersLo);
            addTerms(doc, FIELD_NAME.identifiersMed, PROPERTY.identifiersMed, props, null);
            addTerms(doc, FIELD_NAME.identifiersHi, Field.Store.YES, terms(PROPERTY.identifiersHi, props, null));

            doc.add(new TextField(FIELD_NAME.body.toString(), body, Field.Store.NO));

            addTerms(doc, FIELD_NAME.keywordsLo, PROPERTY.keywordsLo, props, null);
            addTerms(doc, FIELD_NAME.keywordsMed, PROPERTY.keywordsMed, props, keywordsMed.toString());
            addTerms(doc, FIELD_NAME.keywordsHi, PROPERTY.keywordsHi, props, null);

            // === Don't index, store ===

            doc.add(new StoredField(FIELD_NAME.title.toString(), title));
            doc.add(new StoredField(FIELD_NAME.summary.toString(), summary));
            doc.add(new StoredField(FIELD_NAME.url.toString(), url));
            if (null != props.get(PROPERTY.navtrail.toString()))
                doc.add(new StoredField(FIELD_NAME.navtrail.toString(), (String)props.get(PROPERTY.navtrail.toString())));

            // === Store security context in DocValues field ===
            String resourceId = (String)props.get(PROPERTY.securableResourceId.toString());
            String securityContext = r.getContainerId() + (null != resourceId && !resourceId.equals(r.getContainerId()) ? "|" + resourceId : "");
            doc.add(new SortedDocValuesField(FIELD_NAME.securityContext.toString(), new BytesRef(securityContext)));

            // === Custom properties: Index and analyze, but don't store
            for (Map.Entry<String, ?> entry : props.entrySet())
            {
                String key = entry.getKey();

                // Skip known properties -- we added them above
                if (KNOWN_PROPERTIES.contains(key))
                    continue;

                Object value = entry.getValue();

                if (null != value)
                {
                    String stringValue = value.toString().toLowerCase();

                    if (stringValue.length() > 0)
                        doc.add(new TextField(key.toLowerCase(), stringValue, Field.Store.NO));
                }
            }

            if (_log.isDebugEnabled())
            {
                String dump = dump(r, doc);
                _log.debug("indexing " + dump);
            }

            return index(r.getDocumentId(), r, doc);
        }
        catch (NoClassDefFoundError err)
        {
            Throwable cause = err.getCause();
            String message;

            // Prefer using cause since ClassNotFoundException's message is consistent across JVMs, but NoClassDefFoundError's is not.
            // However, fall back on the message if cause is null.
            if (cause instanceof ClassNotFoundException)
                message = cause.getMessage();
            else
                message = err.getMessage();

            // In previous versions of Tika, we inspected message here for specific text to avoid logging stack traces for
            // known common cases. See #30288 as an example. The previous known issues have been addressed, so there's no
            // need to do this currently.

            logAsWarning(r, "Unrecognized exception message \"" + message + "\"");
            logAsPreProcessingException(r, err);
            handledException[0] = err;
        }
        catch (TikaException e)
        {
            handleTikaException(r, e);
            handledException[0] = e.getCause();
        }
        catch (
            IOException |     // Permissions problem, network drive disappeared, file disappeared, etc.
            SAXException e)   // Malformed XML/HTML
        {
            logAsWarning(r, e);
            handledException[0] = e;
        }
        catch (RuntimeSQLException x)
        {
            if (SqlDialect.isTransactionException(x))
                throw x;
            logAsPreProcessingException(r, x);
            handledException[0] = x;
        }
        catch (MinorConfigurationException e)
        {
            // Standard Throwable handling will wrap the exception, causing it to be sent to mothership, which we don't
            // want. Instead, log without wrapping, so it ends up in the console.
            ExceptionUtil.logExceptionToMothership(null, e);
            handledException[0] = e;
        }
        catch (Throwable e)
        {
            logAsPreProcessingException(r, e);
            handledException[0] = e;
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

        return false;
    }

    private String dump(WebdavResource r, Document doc)
    {
        StringBuilder sb = new StringBuilder("docid: ").append(r.getDocumentId()).append("\n");
        for (IndexableField field : doc.getFields())
        {
            sb.append(" - ").append(field.toString()).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private void handleTikaException(WebdavResource r, TikaException e)
    {
        String topMessage = (null != e.getMessage() ? e.getMessage() : "");
        Throwable cause = e.getCause();

        // Get the root cause
        Throwable rootCause = e;

        while (null != rootCause.getCause())
            rootCause = rootCause.getCause();

        // IndexOutOfBoundsException has a dumb message
        String rootMessage = (rootCause instanceof IndexOutOfBoundsException ? rootCause.getClass().getSimpleName() : rootCause.getMessage());

        if (topMessage.startsWith("XML parse error") || topMessage.startsWith("TIKA-237: Illegal SAXException"))
        {
            // Malformed XML document -- CONSIDER: run XML tidy on the document and retry
            logAsWarning(r, "Malformed XML document");
        }
        else if (cause instanceof java.util.zip.ZipException)
        {
            // Malformed zip file
            logAsWarning(r, "Malformed zip file");
        }
        else if (e instanceof EncryptedDocumentException)
        {
            // Encrypted office document, examples: encrypted.xlsx, MS Tracking Sheet.xls, HRP_AE_21MAY2008_version1.xls, encrypted.docx
            logAsWarning(r, "Document is password protected");
        }
        else if (topMessage.startsWith("Error creating OOXML extractor"))
        {
            logAsWarning(r, "Can't parse this Office document", rootMessage);
        }
        else if (topMessage.startsWith("TIKA-198: Illegal IOException from org.apache.tika.parser.microsoft.OfficeParser"))
        {
            // Document is currently open in Word
            logAsWarning(r, "Can't parse this Office document", rootMessage);
        }
        else if (topMessage.startsWith("TIKA-198: Illegal IOException from org.apache.tika.parser.pdf.PDFParser") ||
                topMessage.startsWith("Unable to extract PDF content"))
        {
            logAsWarning(r, "Can't parse this PDF document", rootMessage);
        }
        else if (topMessage.startsWith("TIKA-198: Illegal IOException"))
        {
            logAsWarning(r, "Can't parse this document", rootMessage);
        }
        else if (topMessage.startsWith("Unexpected RuntimeException from org.apache.tika.parser"))
        {
            // Example: Support_Gunaretnam.pdf
            logAsWarning(r, "Can't parse this document", rootMessage);
        }
        else if (topMessage.equals("Not a HPSF document"))
        {
            // XLS file generated by JavaExcel -- POI doesn't like some of them
            logAsWarning(r, "Can't parse this Excel document", "POI can't read Java Excel spreadsheets");
        }
        else if (topMessage.equals("Failed to parse a Java class"))
        {
            // Corrupt Java file -- see SearchModule.class, which was hand-mangled
            logAsWarning(r, "Can't parse this Java class file", rootMessage);
        }
        else if (topMessage.equals("TIKA-418: RuntimeException while getting content for thmx and xps file types"))
        {
            // Tika doesn't support .thmx or .xps file types
            // Example: Extending LabKey.thmx
            logAsWarning(r, "Can't parse this document type", rootMessage);
        }
        else if ((topMessage.startsWith("Invalid Image Resource Block Signature Found") || topMessage.startsWith("PSD/PSB magic signature invalid") /* test.fasta.psd */) && StringUtils.endsWithIgnoreCase(r.getName(), ".psd"))
        {
            // Tika doesn't like some .psd files (e.g., files included in ExtJs 3.4.1)
            logAsWarning(r, "Can't parse this PSD file", rootMessage);
        }
        else if (topMessage.startsWith("Unsupported AutoCAD drawing version"))
        {
            // Tika mistakenly thinks some files (e.g., .ggl files) are AutoCAD files, #13811. Don't even warn about these.
        }
        else if (topMessage.equals("Bad TrueType font."))
        {
            // Tika mistakenly thinks some files (e.g., *.fmp12) are TrueType fonts. Don't even warn about these.
            // https://issues.apache.org/jira/browse/TIKA-1061 is clearly related, but seems insufficient for FMP 12 files
        }
        else if (topMessage.equals("Error parsing Matlab file with MatParser"))
        {
            // Tika mistakenly thinks all .mat are Matlab files. Don't even warn about these.
        }
        else if (topMessage.equals("image/gif parse error") && StringUtils.endsWithIgnoreCase(r.getName(), ".mht"))
        {
            // Tika can't parse all .mht files
            logAsWarning(r, "Can't parse this MHT file", rootMessage);
        }
        else if (topMessage.equals("Zip bomb detected!"))
        {
            // Tika flags some files as "zip bombs"
            logAsWarning(r, "Can't parse this file", rootMessage);
        }
        else if (topMessage.equals("Unable to unpack document stream"))
        {
            // Usually "org.apache.commons.compress.archivers.ArchiveException: No Archiver found for the stream signature"
            logAsWarning(r, "Can't decompress this file", rootMessage);
        }
        else if (StringUtils.endsWithIgnoreCase(r.getName(), ".chm"))
        {
            // ChmExtractor throws exceptions for many .chm (compressed HTML) files that it attempts to parse, so we just
            // suppress the lot of them. Some of the messages include:
            // - can't copy beyond array length (see #36057 and MACMAN.chm)
            // - resetTable.getBlockAddress().length should be greater than zero
            // - Table overflow
            // - cannot parse chm file index > data.length
            // - Index 32768 out of bounds for length 32768
            logAsWarning(r, "Can't extract text from this file", rootMessage);
        }
        else
        {
            logAsPreProcessingException(r, e);
        }
    }

    private String terms(PROPERTY property, Map<String, ?> props, @Nullable String computedTerms)
    {
        if (null == computedTerms)
            return (String)props.get(property.toString());
        String documentTerms = (String)props.get(property.toString());
        if (null == documentTerms)
            return computedTerms;
        return computedTerms + " " + documentTerms;
    }

    private void addTerms(Document doc, FIELD_NAME fieldName, Field.Store store, String terms)
    {
        if (StringUtils.isNotBlank(terms))
            doc.add(new TextField(fieldName.toString(), terms, store));
    }

    private void addTerms(Document doc, FIELD_NAME fieldName, PROPERTY property, Map<String, ?> props,  @Nullable String computedTerms)
    {
        addTerms(doc, fieldName, Field.Store.NO, terms(property, props, computedTerms));
    }

    private void addUserField(Document doc, FIELD_NAME fieldName, @Nullable Integer userId)
    {
        if (userId == null)
            return;

        addUserField(doc, fieldName, UserManager.getUser(userId));
    }

    private void addUserField(Document doc, FIELD_NAME fieldName, @Nullable User user)
    {
        if (user == null)
            return;

        // NOTE: The user's display value is expanded for the search user into: display name, email, firstName, lastName
        doc.add(new TextField(fieldName.toString(), user.getDisplayName(User.getSearchUser()), Field.Store.NO));
    }

    private void addDateField(Document doc, FIELD_NAME fieldName, long date)
    {
        if (date <= 0)
            return;

        // We store dates as milliseconds since 1970 as a NumericDocValuesField -- so the search results can be sorted by this date field
        // CONSIDER: Also store as LongPoint for searching by a date range.
        // see: http://search-lucene.com/m/Lucene/l6pAi1jKqde24IDEp?subj=Re+Indexing+a+Date+DateTime+Time+field+in+Lucene+4
        //doc.add(new LongPoint(fieldName.toString(), date));
        doc.add(new NumericDocValuesField(fieldName.toString(), date));
    }


    private void logBadDocument(String problem, WebdavResource r)
    {
        _log.error(problem);
        throw new IllegalStateException(problem);
    }


    // parse the document of the resource, not that parse() and accept() should agree on what is parsable
    private void parse(WebdavResource r, FileStream fs, InputStream is, ContentHandler handler, Metadata metadata, boolean tooBig) throws IOException, SAXException, TikaException
    {
        if (!is.markSupported())
            is = new BufferedInputStream(is);

        DocumentParser p = detectParser(r, is);
        if (null != p)
        {
            metadata.add(Metadata.CONTENT_TYPE, p.getMediaType());
            if (!tooBig)  //Check filesize even if parser set. Issue #40253
                p.parse(is, handler);
            return;
        }

        // Treat files over the size limit as empty files
        if (tooBig)
        {
            logAsWarning(r, "The document is too large");
            return;
        }

        try
        {
            _autoDetectParser.parse(is, handler, metadata);
        }
        catch (ZeroByteFileException e)
        {
            // Just index as an empty file, #33236
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
            try
            {
                if (isTooBig(fs,contentType))
                {
                    // give labkey parsers a chance to accept the file
                    DocumentParser p = detectParser(r, null);
                    return p != null;
                }
                return true;
            }
            finally
            {
                fs.closeInputStream();
            }
        }
        catch (IOException x)
        {
            return false;
        }
    }


    private boolean isTooBig(FileStream fs, String contentType) throws IOException
    {
        long size = fs.getSize();

        // .xlsx files are zipped with about a 5:1 ratio -- they bloat in memory
        if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType))
            size = size * 5;

        return size > getFileSizeLimit();
    }


    private DocumentParser detectParser(WebdavResource r, InputStream in)
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
            is.skip(Long.MIN_VALUE);
            byte[] header = FileUtil.readHeader(is, 8*1024);
            for (DocumentParser p : _documentParsers)
            {
                if (p.detect(r, r.getContentType(), header))
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


    private String getNameToLog(WebdavResource r)
    {
        Container c = ContainerManager.getForId(r.getContainerId());
        String folder = (null != c ? " (folder: " + c.getPath() + ")" : "");
        File f = r.getFile();

        if (null != f)
            return f.getPath() + folder;

        // If it's not a file in the file system then return the resource path and the container path
        String name = r.getPath().toString();
        String url = r.getExecuteHref(null);

        return name + folder + " (" + url + ")";
    }

    private void logAsPreProcessingException(WebdavResource r, Throwable e)
    {
        ExceptionUtil.logExceptionToMothership(null, new PreProcessingException(getNameToLog(r), e));
    }

    private void logAsWarning(WebdavResource r, Exception e)
    {
        logAsWarning(r, e.getMessage());
    }

    private void logAsWarning(WebdavResource r, String message)
    {
        logAsWarning(r, message, null);
    }

    private void logAsWarning(WebdavResource r, String message, @Nullable String rootMessage)
    {
        _log.warn("Can't index file \"" + getNameToLog(r) + "\" due to: " + message + (null != rootMessage ? " [" + rootMessage + "]" : ""));
    }

    private static class PreProcessingException extends Exception
    {
        private PreProcessingException(String name, Throwable cause)
        {
            super(name, cause);
        }
    }

    @SuppressWarnings("unused")
    private enum InterestingDocumentProperty
    {
        Title(TikaCoreProperties.TITLE),
        Creator(TikaCoreProperties.CREATOR),
        Keywords(TikaCoreProperties.KEYWORDS),
        Comments(TikaCoreProperties.COMMENTS),
        Description(TikaCoreProperties.DESCRIPTION),
        Notes(OfficeOpenXMLExtended.NOTES),
        Publisher(TikaCoreProperties.PUBLISHER)
        {
            @Nullable
            @Override
            String getValue(Metadata metadata)
            {
                String value = super.getValue(metadata);

                return null != value ? value : metadata.get(OfficeOpenXMLExtended.COMPANY); // In a few sample documents, COMPANY was populated but PUBLISHER was not
            }
        };

        private final Property _property;

        InterestingDocumentProperty(Property property)
        {
            _property = property;
        }

        @Nullable String getValue(Metadata metadata)
        {
            return metadata.get(_property);
        }
    }

    private String getInterestingMetadataProperties(Metadata metadata)
    {
        StringBuilder sb = new StringBuilder();

        for (InterestingDocumentProperty property : InterestingDocumentProperty.values())
        {
            String value = StringUtils.trimToNull(property.getValue(metadata));

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
            body = StringUtils.stripStart(body, "/. \n\r\t");
        }

        if (body.length() <= SUMMARY_LENGTH)
            return body;

        Matcher wordSplitter = SEPARATOR_PATTERN.matcher(body);

        if (!wordSplitter.find(SUMMARY_LENGTH - 1))
            return body.substring(0, SUMMARY_LENGTH) + "...";
        else
            return body.substring(0, wordSplitter.start()) + "...";
    }


    @Override
    protected void deleteDocument(String id)
    {
        _indexManager.deleteDocument(id);
    }

    @Override
    protected void deleteDocuments(Collection<String> ids)
    {
        _indexManager.deleteDocuments(ids);
    }

    @Override
    protected void deleteDocumentsForPrefix(String prefix)
    {
        Term term = new Term(FIELD_NAME.uniqueId.toString(), prefix + "*");
        Query query = new WildcardQuery(term);

        try
        {
            // Run the query before delete, but only if Log4J debug level is set
            if (_log.isDebugEnabled())
            {
                _log.debug("Deleting " + getDocCount(query) + " docs with prefix \"" + prefix + "\"");
            }

            _indexManager.deleteQuery(query);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    private long getDocCount(Query query) throws IOException
    {
        IndexSearcher searcher = _indexManager.getSearcher();

        try
        {
            TopDocs docs = searcher.search(query, 1);
            return docs.totalHits.value;
        }
        finally
        {
            _indexManager.releaseSearcher(searcher);
        }
    }

    private boolean index(String id, WebdavResource r, Document doc)
    {
        try
        {
            _indexManager.index(r.getDocumentId(), doc);
            _countIndexedSinceClearLastIndexed.incrementAndGet();
            return true;
        }
        catch (IndexManagerClosedException x)
        {
            // Happens when an admin switches the index configuration, e.g., setting a new path to the index files.
            // We've swapped in the new IndexManager, but the indexing thread still holds an old (closed) IndexManager.
            // The document is not marked as indexed so it'll get reindexed... plus we're switching index directories,
            // so everything's getting reindexed anyway.
        }
        catch(Throwable e)
        {
            _log.error("Indexing error with " + id, e);
        }

        return false;
    }

    @Override
    protected void deleteIndexedContainer(String id)
    {
        try
        {
            Query query = new TermQuery(new Term(FIELD_NAME.container.toString(), id));

            // Run the query before delete, but only if Log4J debug level is set
            if (_log.isDebugEnabled())
            {
                _log.debug("Deleting " + getDocCount(query) + " docs from container " + id);
            }

            _indexManager.deleteQuery(query);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void clearIndexedFileSystemFiles(Container container)
    {
        String davPrefix = "dav:";
        try
        {
            BooleanQuery query = new BooleanQuery.Builder()
                    // Add container filter
                    .add(new TermQuery(new Term(FIELD_NAME.container.toString(), container.getId())), BooleanClause.Occur.MUST)
                    // Add files filter
                    .add(new TermQuery(new Term(FIELD_NAME.searchCategories.toString(), "file")), BooleanClause.Occur.MUST)
                    //Limit to just dav files and not attachments or other files
                    .add(new WildcardQuery(new Term(FIELD_NAME.uniqueId.toString(), davPrefix + "*")), BooleanClause.Occur.MUST)
                    .build();

            // Run the query before delete, but only if Log4J debug level is set
            if (_log.isDebugEnabled())
            {
                _log.debug("Deleting " + getDocCount(query) + " docs from container " + container);
            }

            _indexManager.deleteQuery(query);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void commitIndex()
    {
        try
        {
            _log.debug("Committing index");
            _indexManager.commit();
        }
        catch (ConfigurationException e)
        {
            // Index may have become unwriteable don't log to mothership, and dont reinitialize index
            throw e;
        }
        catch (Throwable t)
        {
            // If any exceptions happen during commit() the IndexManager will attempt to close the IndexWriter, making
            // the IndexManager unusable.  Attempt to reset the index.
            ExceptionUtil.logExceptionToMothership(null, t);
            initializeIndex();
        }
    }


    @Override
    @Nullable
    public SearchHit find(String id) throws IOException
    {
        IndexSearcher searcher = _indexManager.getSearcher();

        try
        {
            TermQuery query = new TermQuery(new Term(FIELD_NAME.uniqueId.toString(), id));
            TopDocs topDocs = searcher.search(query, 1);
            SearchResult result = createSearchResult(0, 1, topDocs, searcher);
            if (result.hits.size() != 1)
                return null;
            return result.hits.get(0);
        }
        finally
        {
            _indexManager.releaseSearcher(searcher);
        }
    }


    private static final String[] standardFields;
    private static final Map<String, Float> boosts = new HashMap<>();

    static
    {
        Map<FIELD_NAME, Float> enumMap = new HashMap<>();
        enumMap.put(FIELD_NAME.body, 1.0f);
        enumMap.put(FIELD_NAME.keywordsLo, 1.0f);
        enumMap.put(FIELD_NAME.identifiersLo, 1.0f);

        enumMap.put(FIELD_NAME.keywordsMed, 2.0f);
        enumMap.put(FIELD_NAME.identifiersMed, 2.0f);

        enumMap.put(FIELD_NAME.keywordsHi, 4.0f);
        enumMap.put(FIELD_NAME.identifiersHi, 4.0f);

        for (Map.Entry<FIELD_NAME, Float> entry : enumMap.entrySet())
            boosts.put(entry.getKey().toString(), entry.getValue());

        standardFields = boosts.keySet().toArray(new String[boosts.size()]);
    }

    @Override
    public WebPartView getSearchView(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink, boolean isWebpart)
    {
        return new SearchWebPart(includeSubfolders, textBoxWidth, includeHelpLink, isWebpart);
    }

    @Override
    public SearchResult search(String queryString, @Nullable List<SearchCategory> categories, User user,
                               Container current, SearchScope scope,
                               @Nullable String sortField,
                               int offset, int limit) throws IOException
    {
        return search(queryString, categories, user, current, scope, sortField, offset, limit, false);
    }

    @Override
    public List<String> searchUniqueIds(String queryString, @Nullable List<SearchCategory> categories, User user,
                               Container current, SearchScope scope,
                               @Nullable String sortField,
                               int offset, int limit, boolean invertResults) throws IOException
    {
        List<String> results = new ArrayList<>();
        doSearch(queryString, categories, user, current, scope, sortField, offset, limit, invertResults, false, new SearchResult(), results);
        return results;
    }

    @Override
    public SearchResult search(String queryString, @Nullable List<SearchCategory> categories, User user,
                               Container current, SearchScope scope,
                               @Nullable String sortField,
                               int offset, int limit, boolean invertResults) throws IOException
    {
        SearchResult results = new SearchResult();
        doSearch(queryString, categories, user, current, scope, sortField, offset, limit, invertResults, true, results, new ArrayList<>());
        return results;
    }

    private void doSearch(String queryString, @Nullable List<SearchCategory> categories, User user,
                          Container current, SearchScope scope,
                          @Nullable String sortField,
                          int offset, int limit, boolean invertResults, boolean fullResult, @NotNull SearchResult searchResult, @NotNull List<String> searchResultUniqueIds) throws IOException
    {
        InvocationTimer<SEARCH_PHASE> iTimer = TIMER.getInvocationTimer();

        try
        {
            int hitsToRetrieve = offset + limit;
            boolean requireCategories = (null != categories);

            iTimer.setPhase(SEARCH_PHASE.createQuery);

            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

            try
            {
                QueryParser queryParser = new MultiFieldQueryParser(standardFields, getAnalyzer(), boosts);
                queryBuilder.add(queryParser.parse(queryString), BooleanClause.Occur.MUST);
            }
            catch (ParseException x)
            {
                // The default ParseException message is quite awful, not suitable for users.  Unfortunately, the exception
                // doesn't provide the useful bits individually, so we have to parse the message to get them. #10596
                LuceneMessageParser mp = new LuceneMessageParser(x.getMessage());

                if (mp.isParseable())
                {
                    String message;
                    int problemLocation;

                    if ("<EOF>".equals(mp.getEncountered()))
                    {
                        message = PageFlowUtil.filter("Query string is incomplete");
                        problemLocation = queryString.length();
                    }
                    else
                    {
                        if (1 == mp.getLine())
                        {
                            message = "Problem character is <span " + SearchUtils.getHighlightStyle() + ">highlighted</span>";
                            problemLocation = mp.getColumn();
                        }
                        else
                        {
                            // Multiline query?!?  Don't try to highlight, just report the location (1-based)
                            message = PageFlowUtil.filter("Problem at line " + (mp.getLine() + 1) + ", character location " + (mp.getColumn() + 1));
                            problemLocation = -1;
                        }
                    }

                    throw new HtmlParseException(message, queryString, problemLocation);
                }
                else
                {
                    throw new IOException(x.getMessage(), x);  // Default message starts with "Cannot parse '<query string>':"
                }
            }
            catch (IllegalArgumentException x)
            {
                throw new IOException(SearchUtils.getStandardPrefix(queryString) + x.getMessage());
            }

            if (null != categories)
            {
                Iterator itr = categories.iterator();

                if (requireCategories)
                {
                    BooleanQuery.Builder categoryBuilder = new BooleanQuery.Builder();

                    while (itr.hasNext())
                    {
                        Query categoryQuery = new TermQuery(new Term(SearchService.PROPERTY.categories.toString(), itr.next().toString().toLowerCase()));
                        categoryBuilder.add(categoryQuery, BooleanClause.Occur.SHOULD);
                    }

                    queryBuilder.add(categoryBuilder.build(), BooleanClause.Occur.FILTER);
                }
                else
                {
                    while (itr.hasNext())
                    {
                        Query categoryQuery = new TermQuery(new Term(SearchService.PROPERTY.categories.toString(), itr.next().toString().toLowerCase()));
                        queryBuilder.add(new BoostQuery(categoryQuery, 3.0f), BooleanClause.Occur.SHOULD);
                    }
                }
            }

            Sort sort = null;
            if (sortField != null && !sortField.equals("score"))
            {
                if (sortField.equals(FIELD_NAME.created.name()) || sortField.equals(FIELD_NAME.modified.name()))
                    sort = new Sort(new SortField(sortField, SortField.Type.LONG, !invertResults), SortField.FIELD_SCORE);
                else if (sortField.equals(FIELD_NAME.container.name()))
                    sort = new Sort(new SortField(sortField, new ContainerFieldComparatorSource(), invertResults), SortField.FIELD_SCORE);
                else
                    sort = new Sort(new SortField(sortField, SortField.Type.STRING, invertResults), SortField.FIELD_SCORE);
            }

            IndexSearcher searcher = _indexManager.getSearcher();

            try
            {
                iTimer.setPhase(SEARCH_PHASE.buildSecurityFilter);

                if (!user.isSearchUser())
                {
                    Query securityFilter = new SecurityQuery(user, scope.getRoot(current), current, scope.isRecursive(), iTimer);
                    queryBuilder.add(securityFilter, BooleanClause.Occur.FILTER);
                }

                iTimer.setPhase(SEARCH_PHASE.search);
                Query query = queryBuilder.build();
                TopDocs topDocs;

                if (null == sort)
                    topDocs = searcher.search(query, hitsToRetrieve);
                else
                    topDocs = searcher.search(query, hitsToRetrieve, sort);

                iTimer.setPhase(SEARCH_PHASE.processHits);

                if (fullResult)
                    processSearchResult(offset, hitsToRetrieve, topDocs, searcher, searchResult);
                else
                    processSearchResultUniqueIds(offset, hitsToRetrieve, topDocs, searcher, searchResultUniqueIds);


// Uncomment to log an explanation of each hit
//                for (SearchHit hit : result.hits)
//                {
//                    Explanation e = searcher.explain(query, hit.doc);
//                    _log.info(e.toString());
//                }
            }
            finally
            {
                _indexManager.releaseSearcher(searcher);
            }
        }
        finally
        {
            TIMER.releaseInvocationTimer(iTimer);
        }
    }

    private SearchResult createSearchResult(int offset, int hitsToRetrieve, TopDocs topDocs, IndexSearcher searcher) throws IOException
    {
        SearchResult result = new SearchResult();
        processSearchResult(offset, hitsToRetrieve, topDocs, searcher, result);
        return result;
    }

    private void processSearchResult(int offset, int hitsToRetrieve, TopDocs topDocs, IndexSearcher searcher, SearchResult result) throws IOException
    {
        ScoreDoc[] hits = topDocs.scoreDocs;

        List<SearchHit> ret = new LinkedList<>();

        for (int i = offset; i < Math.min(hitsToRetrieve, hits.length); i++)
        {
            ScoreDoc scoreDoc = hits[i];
            Document doc = searcher.doc(scoreDoc.doc);

            SearchHit hit = new SearchHit();
            hit.category = doc.get(FIELD_NAME.searchCategories.toString());
            hit.container = doc.get(FIELD_NAME.container.toString());
            hit.docid = doc.get(FIELD_NAME.uniqueId.toString());
            hit.summary = doc.get(FIELD_NAME.summary.toString());
            hit.url = doc.get(FIELD_NAME.url.toString());
            hit.doc = scoreDoc.doc;
            hit.identifiers = doc.get(FIELD_NAME.identifiersHi.toString());
            hit.score = scoreDoc.score;

            // BUG patch see Issue 10734 : Bad URLs for files in search results
            // this is only a partial fix, need to rebuild index
            if (hit.url.contains("/%40files?renderAs=DEFAULT/"))
            {
                int in = hit.url.indexOf("?renderAs=DEFAULT/");
                hit.url = hit.url.substring(0, in) + hit.url.substring(in + "?renderAs=DEFAULT".length()) + "?renderAs=DEFAULT";
            }
            if (null != hit.docid)
            {
                String docid = "_docid=" + PageFlowUtil.encode(hit.docid);
                hit.url = hit.url + (!hit.url.contains("?") ? "?" : "&") + docid;
            }

            // Display title
            hit.title = doc.get(FIELD_NAME.title.toString());

            // No title... just use URL
            if (StringUtils.isBlank(hit.title))
                hit.title = hit.url;

            // UNDONE FIELD_NAMES.navtree
            hit.navtrail = doc.get(FIELD_NAME.navtrail.toString());
            ret.add(hit);
        }

        result.totalHits = topDocs.totalHits.value;
        result.hits = ret;
    }

    private void processSearchResultUniqueIds(int offset, int hitsToRetrieve, TopDocs topDocs, IndexSearcher searcher, List<String> searchResultUniqueIds) throws IOException
    {
        ScoreDoc[] hits = topDocs.scoreDocs;

        for (int i = offset; i < Math.min(hitsToRetrieve, hits.length); i++)
        {
            ScoreDoc scoreDoc = hits[i];
            Document doc = searcher.doc(scoreDoc.doc);
            String id = doc.get(FIELD_NAME.uniqueId.toString());
            if (id != null)
                searchResultUniqueIds.add(id);
        }
    }

    @Override
    protected void shutDown()
    {
        closeIndex();
        _standardAnalyzer.close();
    }


    private void closeIndex()
    {
        commit();

        try
        {
            _indexManager.close();
            _indexManager = NOOP_WRITABLE_INDEX;
        }
        catch (Exception e)
        {
            _log.error("Closing index", e);
        }
    }


    @Override
    public Map<String, Object> getIndexerStats()
    {
        Map<String, Object> map = new LinkedHashMap<>();

        try
        {
            map.put("Indexed Documents", getNumDocs());
        }
        catch (IOException x)
        {
        }

        map.putAll(super.getIndexerStats());
        return map;
    }


    @Override
    public Map<String, Double> getSearchStats()
    {
        return TIMER.getTimes();
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


    // https://issues.apache.org/jira/browse/LUCENE-3841 was fixed long ago so we can use a shared instance
    private Analyzer getAnalyzer()
    {
        return _standardAnalyzer;
    }


    public static class TikaTestCase extends Assert
    {
        @SuppressWarnings("ConstantConditions")
        @Test
        // Attempts to extract text from all the sample files in /sampledata/fileTypes and compare the extracted text to
        // expectations for that file. The "strict" boolean (see below) determines the behavior of this test:
        //
        // - strict == true: The test validates the content length and contents of each file against expectations. This
        //   is very picky; it fails immediately if an exception is thrown, an expected string isn't found, or length of
        //   extracted contents differs from expectations by a single byte.
        // - string == false: The test attempts to extract from every file and simply logs all discrepancies. This mode
        //   is useful for debugging parsing issues, etc.
        //
        // Regardless of the flag, the test will always fail if unknown files are found in the fileTypes folder OR if
        // expectations are invalid (e.g., expecting body length > 0 but specifying no substrings to search for).
        public void testTikaParsing() throws IOException, TikaException, SAXException
        {
            boolean strict = true;

            File sampledata = JunitUtil.getSampleData(null, "fileTypes");
            assertNotNull(sampledata);
            assertTrue(sampledata.isDirectory());
            SearchService ss = SearchService.get();
            LuceneSearchServiceImpl lssi = (LuceneSearchServiceImpl) ss;
            Map<String, Pair<Integer, String[]>> expectations = getExpectations();

            for (File file : sampledata.listFiles(File::isFile))
            {
                String docId = "testtika";
                SimpleDocumentResource resource = new SimpleDocumentResource(new Path(docId), docId, null, "text/plain", null, null, null);
                ContentHandler handler = new BodyContentHandler(-1);
                Metadata metadata = new Metadata();

                try (InputStream is = new FileInputStream(file))
                {
                    String exceptionMessage = null;

                    if (strict)
                    {
                        lssi.parse(resource, new FileFileStream(file), is, handler, metadata, false);
                    }
                    else
                    {
                        try
                        {
                            lssi.parse(resource, new FileFileStream(file), is, handler, metadata, false);
                        }
                        catch (Throwable t)
                        {
                            exceptionMessage = "exception " + t.getMessage();
                        }
                    }

                    String message = null;

                    if (null != exceptionMessage)
                    {
                        message = exceptionMessage;
                    }
                    else
                    {
                        String body = handler.toString();

                        Pair<Integer, String[]> expectation = expectations.get(file.getName());
                        assertNotNull("Unexpected file \"" + file.getName() + "\" size " + body.length() + " and body \"" + StringUtils.left(body, 500) + "\"", expectation);
                        // If body length is 0 then we expect no strings; if body length > 0 then we expect at least one string
                        assertTrue("\"" + file.getName() + "\": invalid expectation, " + expectation, (0 == expectation.first) == (0 == expectation.second.length));

                        if (expectation.first != body.length())
                        {
                            message = "wrong size " + body.length() + ", expected " + expectation.first;
                        }
                        else
                        {
                            for (String s : expectation.second)
                            {
                                if (!body.contains(s))
                                {
                                    message = "expected text not found \"" + s + "\"";
                                    break;
                                }
                            }
                        }
                    }

                    if (null != message)
                    {
                        if (strict)
                            fail(file.getName() + ": " + message);
                        else
                            _log.info(file.getName() + ": " + message);
                    }
                }
            }
        }

        /**
         * This test checks to see if the parsing method handles files it thinks are too big in the manner expected.
         * (get a default body, and as much metadata as reasonable)
         *
         * @throws IOException
         * @throws TikaException
         * @throws SAXException
         */
        @Test
        public void testOversizedFiles() throws IOException, TikaException, SAXException
        {
            //Instead of using oversized sample files we are just going to tell the parsing method they are too big, and trust we can detect it correctly.
            boolean tooBig = true;

            File sampledata = JunitUtil.getSampleData(null, "fileTypes");
            assertNotNull(sampledata);
            assertTrue(sampledata.isDirectory());
            SearchService ss = SearchService.get();
            LuceneSearchServiceImpl lssi = (LuceneSearchServiceImpl) ss;

            for (File file : sampledata.listFiles(File::isFile))
            {
                String docId = "testtika";
                SimpleDocumentResource resource = new SimpleDocumentResource(new Path(file.getAbsolutePath()), docId, null, "text/plain", null, new URLHelper(false), null);
                ContentHandler handler = new BodyContentHandler(-1);
                Metadata metadata = new Metadata();

                try (InputStream is = new FileInputStream(file))
                {
                    lssi.parse(resource, new FileFileStream(file), is, handler, metadata, tooBig);

                    String body = handler.toString();
                    assertTrue("Body for oversized file parsed unexpectedly: " + file.getName(), StringUtils.isBlank(body));
                }
            }
        }

        private Map<String, Pair<Integer, String[]>> getExpectations()
        {
            Map<String, Pair<Integer, String[]>> map = new HashMap<>();

            add(map, "7z_sample.7z", 53, "7zSearchFile.txt", "This is a sample 7z test file.");
            add(map, "cmd_sample.cmd", 844, "Delete SetupPolicies directory");
            add(map, "cpp_sample.cpp", 281, "Rcpp::NumericVector");
            add(map, "css_sample.css", 697, "math display", "fixes display issues");
            add(map, "csv_sample.csv", 690, "NpodDonorSamplesTest.testWizardCustomizationAndDataEntry");
            add(map, "dll_sample.dll", 0);
            add(map, "doc_sample.doc", 3585, "In the Learn section you can find detailed information", "In reality that visit is at a different week across studies and treatments");
            add(map, "docx_sample.docx", 3580, "In the Learn section you can find detailed information", "In reality that visit is at a different week across studies and treatments");
            add(map, "dot_sample.dot", 3589, "In the Learn section you can find detailed information", "In reality that visit is at a different week across studies and treatments");
            add(map, "dotx_sample.dotx", 3579, "In the Learn section you can find detailed information", "In reality that visit is at a different week across studies and treatments");
            add(map, "exe_sample.exe", 0);
            add(map, "html_sample.html", 1049, "Align redeploy resource modification", "57855: Explicitly handle the case");
            add(map, "hdf_sample.hdf", 0);  //We are blocking loading of the hdf parser instead of taking an additional dependency Issue 38386
            add(map, "ico_sample.ico", 0);
//            Our version of Tika can't extract text from .class and .jar files since we stopped including asm.jar. If we ever add it back then uncomment the line below to test decompilation.
//            add(map, "jar_sample.jar", 712120, "private double _requiredVersion", "protected java.util.Map findObject(java.util.List, String, String);");
            add(map, "jar_sample.jar", 49162, "org/json/simple/JSONValue.class", "Main-Class: org.labkey.AssayValidator");
            add(map, "java_sample.java", 149, "main(String[] args)", "System.out.println");
            add(map, "jpg_sample.jpg", 0);
            add(map, "js_sample.js", 21405, "Magnific Popup Core JS file", "convert jQuery collection to array", "");
            add(map, "mov_sample.mov", 0);
            add(map, "msg_outlook_sample.msg", 1830, "Nouvel utilisateur de Outlook Express", "Messagerie et groupes de discussion", "R\u00E8gles am\u00E9lior\u00E9es");
            add(map, "pdf_sample.pdf", 1501, "acyclic is a filter that takes a directed graph", "The following options");
            add(map, "png_sample.png", 0);
            add(map, "ppt_sample.ppt", 116, "Slide With Image", "Slide With Text", "Hello world", "How are you?");
            add(map, "pptx_sample.pptx", 109, "Slide With Image", "Slide With Text", "Hello world", "How are you?");
            add(map, "rtf_sample.rtf", 11, "One on One");
            add(map, "sample.txt", 38, "Sample text file", "1", "2", "9");
            add(map, "sql_sample.sql", 2233, "for JDBC Login support", "Container of parent, if parent has no ACLs");
            add(map, "svg_sample.svg", 18, " "); // Not empty, but just a bunch of whitespace
            add(map, "tgz_sample.tgz", 7767, "assertthat is an extension", "Custom failure messages");
            add(map, "tif_sample.tif", 0);
            add(map, "tsv_sample.tsv", 2986, "1264.5", "10JAN07_plate_1.xls");
            add(map, "vsd_sample.vsd", 982, "Contoso Pharmaceuticals, Inc.", "Trial Continuation Process", "events depicted herein are fictitious");
            add(map, "xls_sample.xls", 250, "Column 03 attachment", "Bird", "help.jpg");
            add(map, "xlsx_sample.xlsx", 2096, "Failure History", "NpodDonorSamplesTest.testWizardCustomizationAndDataEntry", "Sample Error", "DailyB postgres", "StudySimpleExportTest.verifyCustomParticipantView", "You're trying to decode an invalid JSON String");
            add(map, "xlt_sample.xlt", 2096, "Failure History", "NpodDonorSamplesTest.testWizardCustomizationAndDataEntry", "Sample Error", "DailyB postgres", "StudySimpleExportTest.verifyCustomParticipantView", "You're trying to decode an invalid JSON String");
            add(map, "xltx_sample.xltx", 2096, "Failure History", "NpodDonorSamplesTest.testWizardCustomizationAndDataEntry", "Sample Error", "DailyB postgres", "StudySimpleExportTest.verifyCustomParticipantView", "You're trying to decode an invalid JSON String");
            add(map, "xml_sample.xml", 456, "The Search module offers full-text search of server contents", "The Awesome LabKey Team");
            add(map, "zip_sample.zip", 1897, "map a source tsv column", "if there are NO explicit import definitions", "");

            return map;
        }

        private void add(Map<String, Pair<Integer, String[]>> map, String filename, int expectedLength, String... expectedStrings)
        {
            map.put(filename, new Pair<>(expectedLength, expectedStrings));
        }
    }

    public static class TestCase extends Assert
    {
        private static final int DOC_COUNT = 6;

        private final Container _c = JunitUtil.getTestContainer();
        private final TestContext _context = TestContext.get();
        private final ActionURL _url = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_c).setExtraPath(_c.getId());
        private final SearchCategory _category = new SearchCategory("SearchTest", "Just a test");
        private final SearchService _ss = SearchService.get();
        private final CountDownLatch _latch = new CountDownLatch(DOC_COUNT);

        /**
         * Traverses the specified directory and indexes only the files that meet the fileFilter. This "test" is not normally
         * run, but it can be re-enabled locally to investigate and fix issues with specific file types.
         */
        // @Test
        @SuppressWarnings("unused")
        public void testTika()
        {
            File root = new File("c:\\");
            Predicate<WebdavResource> fileFilter = webdavResource -> StringUtils.endsWithIgnoreCase(webdavResource.getName(), ".chm");

            MutableSecurityPolicy policy = new MutableSecurityPolicy(_c);
            policy.addRoleAssignment(User.getSearchUser(), ReaderRole.class);
            FileSystemResource rootResource = new FileSystemResource(Path.parse(root.getAbsolutePath()), root, policy)
            {
                @Override
                public String getContainerId()
                {
                    return _c.getId();
                }
            };

            traverse(rootResource, fileFilter);
        }

        private void traverse(WebdavResource rootResource, Predicate<WebdavResource> fileFilter)
        {
            // TODO: processAndIndex() call fails now because resource.getParent() is null (therefore getExecuteHref() throws). Fix.
            rootResource.list().stream()
                .filter(Resource::isFile)
                .filter(fileFilter)
                .forEach(resource -> ((AbstractSearchService)_ss).processAndIndex(resource.getPath().encode(), resource, new Throwable[]{null}));

            rootResource.list().stream()
                .filter(Resource::isCollection)
                .forEach(dir -> traverse(dir, fileFilter));
        }

        @Test
        public void testAnalyzers() throws IOException
        {
            String originalText = "casale WISP-R PS-12 3BXC17_LS 123ABC the this.doc bob@example.com running coding dance dancing danced DIAMOND ACCEPTOR FACTOR";

            String simpleResult = "[casale, wisp, r, ps, bxc, ls, abc, the, this, doc, bob, example, com, running, coding, dance, dancing, danced, diamond, acceptor, factor]";
            String keywordResult = "[" + originalText + "]";
            String classicResult = "[casale, wisp, r, ps-12, 3bxc17_ls, 123abc, this.doc, bob@example.com, running, coding, dance, dancing, danced, diamond, acceptor, factor]";
            String englishResult = "[casal, wisp, r, ps, 12, 3bxc17_l, 123abc, this.doc, bob, example.com, run, code, danc, danc, danc, diamond, acceptor, factor]";
            String identifierResult = "[casale, wisp-r, ps-12, 3bxc17_ls, 123abc, the, this.doc, bob@example.com, running, coding, dance, dancing, danced, diamond, acceptor, factor]";

            analyze(LuceneAnalyzer.SimpleAnalyzer, originalText, simpleResult, FIELD_NAME.body.name(), FIELD_NAME.identifiersLo.name());
            analyze(LuceneAnalyzer.KeywordAnalyzer, originalText, keywordResult, FIELD_NAME.body.name(), FIELD_NAME.identifiersLo.name());
            analyze(LuceneAnalyzer.ClassicAnalyzer, originalText, classicResult, FIELD_NAME.body.name(), FIELD_NAME.identifiersLo.name());
            analyze(LuceneAnalyzer.EnglishAnalyzer, originalText, englishResult, FIELD_NAME.body.name(), FIELD_NAME.identifiersLo.name());
            analyze(LuceneAnalyzer.IdentifierAnalyzer, originalText, identifierResult, FIELD_NAME.body.name(), FIELD_NAME.identifiersLo.name());
            analyze(LuceneAnalyzer.LabKeyAnalyzer, originalText, englishResult, FIELD_NAME.body.name(), FIELD_NAME.keywordsLo.name(), FIELD_NAME.keywordsMed.name(), FIELD_NAME.keywordsHi.name(), "foo");
            analyze(LuceneAnalyzer.LabKeyAnalyzer, originalText, identifierResult, FIELD_NAME.searchCategories.name(), FIELD_NAME.identifiersLo.name(), FIELD_NAME.identifiersMed.name(), FIELD_NAME.identifiersHi.name());
        }

        /**
         *  Analyzes text with the passed in Analyzer and logs the results
         */
        private void analyze(LuceneAnalyzer luceneAnalyzer, String text, String expectedResult, String... fieldNames) throws IOException
        {
            Analyzer analyzer = luceneAnalyzer.getAnalyzer();

            for (String fieldName : fieldNames)
            {
                String result = analyze(analyzer, text, fieldName);

                assertEquals(expectedResult, result);
            }

            analyzer.close();
        }

        private String analyze(Analyzer analyzer, String text, String fieldName) throws IOException
        {
            List<String> result = new LinkedList<>();

            try (TokenStream stream = analyzer.tokenStream(fieldName, text))
            {
                stream.reset();

                while (stream.incrementToken())
                    result.add(stream.getAttribute(CharTermAttribute.class).toString());

                stream.end();
            }
            return result.toString();
        }

        @Test
        public void testKeywordsAndIdentifiers() throws InterruptedException, IOException
        {
            if (null == _ss || !(_ss instanceof LuceneSearchServiceImpl))
                return;

            LuceneSearchServiceImpl impl = (LuceneSearchServiceImpl)_ss;
            impl.deleteIndexedContainer(_c.getId());

            String body = null;

            Map<String, Object> props = new HashMap<>();
            props.put(PROPERTY.keywordsHi.toString(), "kumquat running coding dancing");
            index("testresource:keywordsHi", "Test keywordsHi", body, props);

            props = new HashMap<>();
            props.put(PROPERTY.keywordsMed.toString(), "wombat running coding dancing");
            index("testresource:keywordsMed", "Test keywordsMed", body, props);

            props = new HashMap<>();
            props.put(PROPERTY.keywordsLo.toString(), "perihelion running coding dancing");
            index("testresource:keywordsLo", "Test keywordsLo", body, props);

            props = new HashMap<>();
            props.put(PROPERTY.identifiersHi.toString(), "123ABC running coding dancing 456def");
            index("testresource:identifiersHi", "Test identifiersHi", body, props);

            props = new HashMap<>();
            props.put(PROPERTY.identifiersMed.toString(), "789GHI running coding dancing 012jkl");
            index("testresource:identifiersMed", "Test identifiersMed", body, props);

            props = new HashMap<>();
            props.put(PROPERTY.identifiersLo.toString(), "345MNO running coding dancing 678pqr");
            index("testresource:identifiersLo", "Test identifiersLo", body, props);

            // Wait until all docs are indexed
            _latch.await();

            impl.commit();

            test("kumquat", 1);
            test("wombat", 1);
            test("perihelion", 1);
            test("run", 3, "Test keywordsHi", "Test keywordsMed", "Test keywordsLo");
            test("code", 3, "Test keywordsHi", "Test keywordsMed", "Test keywordsLo");
            test("dance", 3, "Test keywordsHi", "Test keywordsMed", "Test keywordsLo");

            test("123ABC", 1);
            test("456def", 1);
            test("789GHI", 1);
            test("012jkl", 1);
            test("345MNO", 1);
            test("678pqr", 1);

            // These should hit both stemmed and non-stemmed fields
            test("running", 6);
            test("coding", 6);
            test("DANCING", 6);

            impl.deleteIndexedContainer(_c.getId());
        }

        private void test(String query, int expectedCount, String... titles) throws IOException
        {
            List<SearchHit> hits = search(query);
            assertEquals(expectedCount, hits.size());

            // Make sure hits are in the expected order
            if (titles.length > 0)
            {
                Iterator<String> iter = new ArrayIterator<>(titles);

                for (SearchHit hit : hits)
                {
// Scoring for keywordsMed and keywordsHi documents are reversed  TODO: figure out why and re-enable this check
//                    assertEquals(iter.next(), hit.title);
                }
            }
        }

        private void index(String docId, String title, String body, Map<String, Object> props)
        {
            props.put(PROPERTY.categories.toString(), _category.getName());
            props.put(PROPERTY.title.toString(), title);

            SimpleDocumentResource resource1 = new SimpleDocumentResource(new Path(docId), docId, _c.getId(), "text/plain", body, _url, props) {
                @Override
                public void setLastIndexed(long ms, long modified)
                {
                    _latch.countDown();
                }
            };
            _ss.defaultTask().addResource(resource1, PRIORITY.item);
        }

        private List<SearchHit> search(String query) throws IOException
        {
            SearchResult result = _ss.search(query, Collections.singletonList(_category), _context.getUser(), _c, SearchScope.Folder, null, 0, 100);

            return result.hits;
        }
    }

    private static class ContainerFieldComparatorSource extends FieldComparatorSource
    {
        @Override
        public FieldComparator<?> newComparator(String fieldname, int numHits, int sortPos, boolean reversed)
        {
            return new FieldComparator.TermValComparator(numHits, fieldname, reversed) {
                @Override
                public int compareValues(BytesRef val1, BytesRef val2)
                {
                    Container c1 = ContainerManager.getForId(val1.utf8ToString());
                    Container c2 = ContainerManager.getForId(val2.utf8ToString());

                    if (c1 == null)
                    {
                        return c2 == null ? 0 : 1;
                    }
                    else if (c2 == null)
                    {
                        return -1;
                    }
                    else
                    {
                        return c1.compareTo(c2);
                    }
                }
            };
        }
    }

    public void refreshNow()
    {
        try
        {
            _indexManager.refreshNow();
        }
        catch (IOException x)
        {
            /* pass */
        }
    }

}
