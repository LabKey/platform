/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexFormatTooNewException;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.IndexUpgrader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
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
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.ReaderRole;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
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
    private volatile WritableIndexManager _indexManager = new NoopWritableIndex("the indexer has not been started yet", _log);

    private final MultiPhaseCPUTimer<SEARCH_PHASE> TIMER = new MultiPhaseCPUTimer<>(SEARCH_PHASE.class, SEARCH_PHASE.values());
    private final Analyzer _standardAnalyzer = LuceneAnalyzer.LabKeyAnalyzer.getAnalyzer();
    private final AutoDetectParser _autoDetectParser;

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


    private void initializeIndex()
    {
        try
        {
            File indexDir = SearchPropertyManager.getIndexDirectory();
            _indexManager = WritableIndexManagerImpl.get(indexDir.toPath(), getAnalyzer());
            setConfigurationError(null);  // Clear out any previous error
        }
        catch (IndexFormatTooOldException | IndexFormatTooNewException e)    // Lucene used to throw "TooOld" in this case; now throws "TooNew"... either way, suppress mothership logging
        {
            MinorConfigurationException mce = new MinorConfigurationException(
                "Index format is not supported; the configured index directory may have been created by a more recent version of LabKey Server", e);

            _log.error("Full-text search index format error", mce);

            throw mce;
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

        // Initialize new index and clear the last indexed
        initializeIndex();
        clearLastIndexed();
    }

    @Override
    public void start()
    {
        try
        {
            initializeIndex();
            clearLastIndexedIfEmpty();
        }
        catch (Exception e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        super.start();
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


    public void clearIndex()
    {
        boolean serviceStarted = _indexManager.isReal();

        try
        {
            // If the service hasn't been started yet then initialize the index and close it down in finally block below
            if (!serviceStarted)
                initializeIndex();

            try
            {
                _indexManager.clear();
            }
            catch (Throwable t)
            {
                // If any exceptions happen during commit() the IndexManager will attempt to close the IndexWriter, making
                // the IndexManager unusable.  Attempt to reset the index.
                ExceptionUtil.logExceptionToMothership(null, t);

                if (serviceStarted)
                    initializeIndex();
            }
        }
        finally
        {
            if (!serviceStarted)
            {
                closeIndex();
                _indexManager = new NoopWritableIndex("the indexer has not been started yet", _log);
            }
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
                return false;

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

                if ("text/html".equals(type))
                {
                    String html;
                    if (isTooBig(fs, type))
                        html = "<html><body></body></html>";
                    else
                        html = PageFlowUtil.getStreamContentsAsString(is);

                    body = new HTMLContentExtractor.GenericHTMLExtractor(html).extract();

                    if (null == title)
                        logBadDocument("Null title", r);
                }
                else if (type.startsWith("text/") && !type.contains("xml") && !StringUtils.equals(type, "text/comma-separated-values"))
                {
                    if (isTooBig(fs, type))
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

                    parse(r, fs, is, handler, metadata);

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

            String summary = extractSummary(body, title);

            Document doc = new Document();

            // === Index without analyzing, store ===

            doc.add(new Field(FIELD_NAME.uniqueId.toString(), r.getDocumentId(), StringField.TYPE_STORED));
            doc.add(new Field(FIELD_NAME.container.toString(), r.getContainerId(), StringField.TYPE_STORED));

            // === Index and analyze, don't store ===

            // We're using the LabKeyAnalyzer, which is a PerFieldAnalyzerWrapper that ensures categories and identifier fields
            // are not stemmed but all other fields are. This analyzer is used at search time as well to ensure consistency.
            // At the moment, custom fields can't specify an analyzer preference, but we could add this at some point.

            assert StringUtils.isNotEmpty((String)props.get(PROPERTY.categories.toString()));

            addTerms(doc, props, PROPERTY.categories, FIELD_NAME.searchCategories, null);
            addTerms(doc, props, PROPERTY.identifiersLo, FIELD_NAME.identifiersLo, identifiersLo);
            addTerms(doc, props, PROPERTY.identifiersMed, FIELD_NAME.identifiersMed, null);
            addTerms(doc, props, PROPERTY.identifiersHi, FIELD_NAME.identifiersHi, null);

            doc.add(new TextField(FIELD_NAME.body.toString(), body, Field.Store.NO));

            addTerms(doc, props, PROPERTY.keywordsLo, FIELD_NAME.keywordsLo, null);
            addTerms(doc, props, PROPERTY.keywordsMed, FIELD_NAME.keywordsMed, keywordsMed.toString());
            addTerms(doc, props, PROPERTY.keywordsHi, FIELD_NAME.keywordsHi, null);

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

            return index(r.getDocumentId(), r, doc);
        }
        catch (NoClassDefFoundError err)
        {
            Throwable cause = err.getCause();
            String message;

            // Prefer using cause since ClassNotFoundException's message is consistent across JVMs, but NoClassDefFoundError's is not.
            // However, fall back on the message if cause is null.
            if (cause != null && cause instanceof ClassNotFoundException)
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
        else if (topMessage.equals("Zip bomb detected!") && StringUtils.endsWithIgnoreCase(r.getName(), ".key"))
        {
            // Tika flags some .key files as "zip bombs"
            logAsWarning(r, "Can't parse this KEY file", rootMessage);
        }
        else if (topMessage.equals("Unable to unpack document stream"))
        {
            // Usually "org.apache.commons.compress.archivers.ArchiveException: No Archiver found for the stream signature"
            logAsWarning(r, "Can't decompress this file", rootMessage);
        }
        else
        {
            logAsPreProcessingException(r, e);
        }
    }


    private void addTerms(Document doc, Map<String, ?> props, PROPERTY property, FIELD_NAME fieldName, @Nullable String computedTerms)
    {
        String documentTerms = (String)props.get(property.toString());
        String terms = (null == computedTerms ? "" : computedTerms + " ") + (null == documentTerms ? "" : documentTerms);

        if (!terms.isEmpty())
            doc.add(new TextField(fieldName.toString(), terms, Field.Store.NO));
    }


    private void logBadDocument(String problem, WebdavResource r)
    {
        _log.error(problem);
        throw new IllegalStateException(problem);
    }


    // parse the document of the resource, not that parse() and accept() should agree on what is parsable
    private void parse(WebdavResource r, FileStream fs, InputStream is, ContentHandler handler, Metadata metadata) throws IOException, SAXException, TikaException
    {
        if (!is.markSupported())
            is = new BufferedInputStream(is);

        DocumentParser p = detectParser(r, is);
        if (null != p)
        {
            metadata.add(Metadata.CONTENT_TYPE, p.getMediaType());
            p.parse(is, handler);
            return;
        }

        // Treat files over the size limit as empty files
        if (isTooBig(fs, r.getContentType()))
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

        return size > FILE_SIZE_LIMIT;
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


    protected void deleteDocument(String id)
    {
        _indexManager.deleteDocument(id);
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
            return docs.totalHits;
        }
        finally
        {
            _indexManager.releaseSearcher(searcher);
        }
    }

    protected boolean index(String id, WebdavResource r, Document doc)
    {
        try
        {
            _indexManager.index(r.getDocumentId(), doc);
            return true;
        }
        catch (IndexManagerClosedException x)
        {
            // Happens when an admin switches the index configuration, e.g., setting a new path to the index files.
            // We've swapped in the new IndexManager, but the indexing thread still holds an old (closed) IndexManager.
            // The document is not marked as indexed so it'll get reindexed... plus we're switching index directories
            // anyway, so everything's getting reindexed anyway.
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


    protected void commitIndex()
    {
        try
        {
            _log.debug("Committing index");
            _indexManager.commit();
        }
        catch (Throwable t)
        {
            // If any exceptions happen during commit() the IndexManager will attempt to close the IndexWriter, making
            // the IndexManager unusable.  Attempt to reset the index.
            ExceptionUtil.logExceptionToMothership(null, t);
            initializeIndex();
        }
    }


    // Upgrade index to the latest version. This must be called BEFORE start() and initializeIndex() are called, otherwise upgrade will fail to obtain the lock.
    @Override
    public final void upgradeIndex()
    {
        try
        {
            Directory directory = WritableIndexManagerImpl.openDirectory(SearchPropertyManager.getIndexDirectory().toPath());

            if (DirectoryReader.indexExists(directory))
            {
                IndexUpgrader upgrader = new IndexUpgrader(directory);
                upgrader.upgrade();
            }
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }


    SearchHit find(String id) throws IOException
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

    public WebPartView getSearchView(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink, boolean isWebpart)
    {
        return new SearchWebPart(includeSubfolders, textBoxWidth, includeHelpLink, isWebpart);
    }

    @Override
    public SearchResult search(String queryString, @Nullable List<SearchCategory> categories, User user,
                               Container current, SearchScope scope, int offset, int limit) throws IOException
    {
        InvocationTimer<SEARCH_PHASE> iTimer = TIMER.getInvocationTimer();

        try
        {
            String sort = null;  // TODO: add sort parameter
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
                    topDocs = searcher.search(query, hitsToRetrieve, new Sort(new SortField(sort, SortField.Type.STRING)));

                iTimer.setPhase(SEARCH_PHASE.processHits);
                SearchResult result = createSearchResult(offset, hitsToRetrieve, topDocs, searcher);

// Uncomment to log an explanation of each hit
//                for (SearchHit hit : result.hits)
//                {
//                    Explanation e = searcher.explain(query, hit.doc);
//                    _log.info(e.toString());
//                }


                return result;
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
        ScoreDoc[] hits = topDocs.scoreDocs;

        List<SearchHit> ret = new LinkedList<>();

        for (int i = offset; i < Math.min(hitsToRetrieve, hits.length); i++)
        {
            ScoreDoc scoreDoc = hits[i];
            Document doc = searcher.doc(scoreDoc.doc);

            SearchHit hit = new SearchHit();
            hit.container = doc.get(FIELD_NAME.container.toString());
            hit.docid = doc.get(FIELD_NAME.uniqueId.toString());
            hit.summary = doc.get(FIELD_NAME.summary.toString());
            hit.url = doc.get(FIELD_NAME.url.toString());
            hit.doc = scoreDoc.doc;

            // BUG patch see 10734 : Bad URLs for files in search results
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

        SearchResult result = new SearchResult();
        result.totalHits = topDocs.totalHits;
        result.hits = ret;
        return result;
    }


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

    @Override
    public List<SecurableResource> getSecurableResources(User user)
    {
        return Collections.emptyList();
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
            LuceneSearchServiceImpl lssi = (LuceneSearchServiceImpl)ss;
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
                        lssi.parse(resource, new FileFileStream(file), is, handler, metadata);
                    }
                    else
                    {
                        try
                        {
                            lssi.parse(resource, new FileFileStream(file), is, handler, metadata);
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
                            fail(message);
                        else
                            _log.info(file.getName() + ": " + message);
                    }
                }
            }
        }

        private Map<String, Pair<Integer, String[]>> getExpectations()
        {
            Map<String, Pair<Integer, String[]>> map = new HashMap<>();

            add(map, "cmd_sample.cmd", 844, "Delete SetupPolicies directory");
            add(map, "cpp_sample.cpp", 1508, "Rcpp::NumericVector");
            add(map, "css_sample.css", 8642, "math display", "fixes display issues");
            add(map, "csv_sample.csv", 690, "NpodDonorSamplesTest.testWizardCustomizationAndDataEntry");
            add(map, "dll_sample.dll", 0);
            add(map, "doc_sample.doc", 9002, "In the Learn section you can find detailed information", "In reality that visit is at a different week across studies and treatments");
            add(map, "docx_sample.docx", 8989, "In the Learn section you can find detailed information", "In reality that visit is at a different week across studies and treatments");
            add(map, "dot_sample.dot", 9002, "In the Learn section you can find detailed information", "In reality that visit is at a different week across studies and treatments");
            add(map, "dotx_sample.dotx", 8989, "In the Learn section you can find detailed information", "In reality that visit is at a different week across studies and treatments");
            add(map, "exe_sample.exe", 0);
            add(map, "html_sample.html", 354182, "Align redeploy resource modification", "57855: Explicitly handle the case");
            add(map, "ico_sample.ico", 0);
//            Our version of Tika can't extract text from .class and .jar files since we stopped including asm.jar. If we ever add it back then uncomment the line below to test decompilation.
//            add(map, "jar_sample.jar", 712120, "private double _requiredVersion", "protected java.util.Map findObject(java.util.List, String, String);");
            add(map, "jar_sample.jar", 49162, "org/json/simple/JSONValue.class", "Main-Class: org.labkey.AssayValidator");
            add(map, "java_sample.java", 9251, "Jackson databinding bean for the controlInfo object", "private String errors");
            add(map, "jpg_sample.jpg", 0);
            add(map, "js_sample.js", 21405, "Magnific Popup Core JS file", "convert jQuery collection to array", "");
            add(map, "mov_sample.mov", 0);
            add(map, "pdf_sample.pdf", 1501, "acyclic is a filter that takes a directed graph", "The following options");
            add(map, "png_sample.png", 0);
            add(map, "ppt_sample.ppt", 116, "Slide With Image", "Slide With Text", "Hello world", "How are you?");
            add(map, "pptx_sample.pptx", 109, "Slide With Image", "Slide With Text", "Hello world", "How are you?");
            add(map, "rtf_sample.rtf", 11, "One on One");
            add(map, "sample.txt", 48, "Sample text file", "1", "2", "9");
            add(map, "sql_sample.sql", 15272, "for JDBC Login support", "Container of parent, if parent has no ACLs");
            add(map, "svg_sample.svg", 18, " "); // Not empty, but just a bunch of whitespace
            add(map, "tgz_sample.tgz", 7767, "assertthat is an extension", "Custom failure messages");
            add(map, "tif_sample.tif", 0);
            add(map, "tsv_sample.tsv", 488328, "1264.5", "10JAN07_plate_1.xls");
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
            File root = new File("c:\\Users\\adam");
            Predicate<WebdavResource> fileFilter = webdavResource -> webdavResource.getName().endsWith(".pdf");

            MutableSecurityPolicy policy = new MutableSecurityPolicy(ContainerManager.getRoot());
            policy.addRoleAssignment(User.getSearchUser(), ReaderRole.class);
            FileSystemResource rootResource = new FileSystemResource(Path.parse(root.getAbsolutePath()), root, policy)
            {
                @Override
                public String getContainerId()
                {
                    return ContainerManager.getRoot().getId();
                }
            };

            traverse(rootResource, fileFilter);
        }

        private void traverse(WebdavResource rootResource, Predicate<WebdavResource> fileFilter)
        {
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
            String originalText = "casale WISP-R 123ABC this.doc running coding dance dancing danced DIAMOND ACCEPTOR FACTOR";

            String simpleResult = "[casale, wisp, r, abc, this, doc, running, coding, dance, dancing, danced, diamond, acceptor, factor]";
            String keywordResult = "[" + originalText + "]";
            String englishResult = "[casal, wisp, r, 123abc, this.doc, run, code, danc, danc, danc, diamond, acceptor, factor]";
            String identifierResult = "[casale, wisp-r, 123abc, this.doc, running, coding, dance, dancing, danced, diamond, acceptor, factor]";

            analyze(LuceneAnalyzer.SimpleAnalyzer, originalText, simpleResult, FIELD_NAME.body.name(), FIELD_NAME.identifiersLo.name());
            analyze(LuceneAnalyzer.KeywordAnalyzer, originalText, keywordResult, FIELD_NAME.body.name(), FIELD_NAME.identifiersLo.name());
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
                List<String> result = new LinkedList<>();

                try (TokenStream stream = analyzer.tokenStream(fieldName, text))
                {
                    stream.reset();

                    while (stream.incrementToken())
                        result.add(stream.getAttribute(CharTermAttribute.class).toString());

                    stream.end();
                }

                assertEquals(expectedResult, result.toString());
            }

            analyzer.close();
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

        private void index(String docId, String title, String body, Map<String, Object> props) throws InterruptedException
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
            SearchResult result = _ss.search(query, Collections.singletonList(_category), _context.getUser(), _c, SearchScope.Folder, 0, 100);

            return result.hits;
        }
    }
}
