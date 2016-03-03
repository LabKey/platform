/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexFormatTooNewException;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexUpgrader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
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
import org.apache.poi.hpsf.NoPropertySetStreamException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchUtils;
import org.labkey.api.search.SearchUtils.HtmlParseException;
import org.labkey.api.search.SearchUtils.LuceneMessageParser;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HTMLContentExtractor;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.MultiPhaseCPUTimer;
import org.labkey.api.util.MultiPhaseCPUTimer.InvocationTimer;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.search.view.SearchWebPart;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final MultiPhaseCPUTimer<SEARCH_PHASE> TIMER = new MultiPhaseCPUTimer<>(SEARCH_PHASE.class, SEARCH_PHASE.values());

    // Changes to _index are rare (only when admin changes the index path), but we want any changes to be visible to
    // other threads immediately. Initialize to Noop class to prevent rare NPE (e.g., system maintenance runs before index
    // is initialized).
    private volatile WritableIndexManager _indexManager = new NoopWritableIndex("the indexer has not been started yet", _log);

    private static ExternalIndexManager _externalIndexManager;

    enum FIELD_NAME
    {
        title,            // Used to be the "search" title (equivalent to keywordsMed), now the display title
        body,

        // Use keywords for english language terms that should be analyzed (stemmed)

        keywordsLo,       // Same weighting as body terms
        keywordsMed,      // Weighted twice the body terms... e.g., terms in the title, subject, or other summary
        keywordsHi,       // Weighted twice the medium keywords... these terms will dominate the search results, so probably not a good idea

        // Use identifiers for terms that should NOT be stemmed, like identifiers and folder names. These are case-insensitive.

        identifiersLo,    // Same weighting as body terms... perhaps use this for folder path parts?
        identifiersMed,   // Weighted twice the lo identifiers
        identifiersHi,    // Weighted twice the medium identifiers (e.g., unique ids like PTIDs, sample IDs, etc.)... be careful, these will dominate the search results (e.g., unique ids like PTIDs, sample IDs, etc.)

        summary,
        url,
        container,        // Used in two places: stored field in documents (used for low volume purposes, delete and results display) and field in doc values (for high volume security filtering)
        resourceId,
        uniqueId,
        navtrail
    }

    private void initializeIndex()
    {
        try
        {
            File indexDir = SearchPropertyManager.getPrimaryIndexDirectory();
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
            _log.error("Error: Unable to initialize search index. Search will be disabled and new documents will not be indexed for searching until this is corrected and the server is restarted. See below for details about the cause.");
            setConfigurationError(t);
            String statusMessage = "the search index is misconfigured. Search is disabled and new documents are not being indexed. Correct the problem and restart your server.";
            _indexManager = new NoopWritableIndex(statusMessage, _log);
            throw new RuntimeException("Error: Unable to initialize search index", t);
        }
    }


    @Override
    public String getIndexFormatDescription()
    {
        return _indexManager.getIndexFormatDescription();
    }

    @Override
    public List<Pair<String, String>> getDirectoryTypes()
    {
        LuceneDirectoryType configured = getDirectoryType();
        // Display the current directory class name, but only if we're currently set to Default (otherwise, we don't know what the default implementation is)
        String defaultDescription = "Default" + (LuceneDirectoryType.Default == configured ? " (" + _indexManager.getCurrentDirectory().getClass().getSimpleName() + ")" : "");

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
    public void updatePrimaryIndex()
    {
        super.updatePrimaryIndex();

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
            resetExternalIndex();
        }
        catch (Exception e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        super.start();
    }


    @Override
    public void resetPrimaryIndex()
    {
        closeIndex();
        initializeIndex();
    }

    // Clear lastIndexed columns if we have no documents in the index. See #25530
    private void clearLastIndexedIfEmpty()
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


    public void resetExternalIndex() throws IOException, InterruptedException
    {
        if (null != _externalIndexManager)
        {
            _externalIndexManager.close();
            _externalIndexManager = null;
        }

        ExternalIndexProperties props = SearchPropertyManager.getExternalIndexProperties();

        if (props.hasExternalIndex())
        {
            File externalIndexFile = new File(props.getExternalIndexPath());
            Analyzer analyzer = ExternalAnalyzer.valueOf(props.getExternalIndexAnalyzer()).getAnalyzer();

            if (externalIndexFile.exists())
                _externalIndexManager = ExternalIndexManager.get(externalIndexFile, analyzer);
        }
    }


    public void swapExternalIndex() throws IOException, InterruptedException
    {
        if (null != _externalIndexManager)
        {
            _externalIndexManager.swap();
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


    private static final Set<String> KNOWN_PROPERTIES = PageFlowUtil.set(
            PROPERTY.categories.toString(), PROPERTY.title.toString(), PROPERTY.keywordsLo.toString(),
            PROPERTY.keywordsMed.toString(), PROPERTY.keywordsHi.toString(), PROPERTY.identifiersHi.toString(),
            PROPERTY.navtrail.toString(), PROPERTY.securableResourceId.toString());

    @Override
    Map<?, ?> preprocess(String id, WebdavResource r, Throwable[] handledException)
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
                return null;

            try
            {
                fs = r.getFileStream(User.getSearchUser());
            }
            catch (FileNotFoundException x)
            {
                logAsWarning(r, r.getName() + " was not found");
                return null;
            }

            if (null == fs)
            {
                logAsWarning(r, r.getName() + " fileStream is null");
                return null;
            }
            
            Map<String, ?> props = r.getProperties();
            assert null != props;

            String categories = (String)props.get(PROPERTY.categories.toString());
            assert null != categories;

            String body = null;
            String title = (String)props.get(PROPERTY.title.toString());

            String keywordsMed = (String)props.get(PROPERTY.keywordsMed.toString());

            // Search title can be null
            if (null == keywordsMed)
                keywordsMed = "";

            try
            {
                Map<String, String> customProperties = r.getCustomProperties(User.getSearchUser());

                if (null != customProperties && !customProperties.isEmpty())
                {
                    for (String value : customProperties.values())
                        keywordsMed += " " + value;
                }
            }
            catch (UnauthorizedException ue)
            {
                // Some QueryUpdateService implementations don't special case the search user.  Continue indexing in this
                // case, but skip the custom properties.
            }

            // Fix #11393.  Can't append description to keywordMed in FileSystemResource() because constructor is too
            // early to retrieve description.  TODO: Move description into properties, instead of exposing it as a
            // top-level getter.  This is a bigger change, so we'll wait for 11.2.
            String description = r.getDescription();

            if (null != description)
                keywordsMed += " " + description;

            String type = r.getContentType();

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
                    return null;
                }

                if ("text/html".equals(type))
                {
                    String html;
                    if (isTooBig(fs, type))
                        html = "<html><body></body></html>";
                    else
                        html = PageFlowUtil.getStreamContentsAsString(is, StandardCharsets.UTF_8);

                    // TODO: Need better check for issue HTML vs. rendered page HTML
                    if (r instanceof ActionResource)
                    {
                        HTMLContentExtractor extractor = new HTMLContentExtractor.LabKeyPageHTMLExtractor(html);
                        body = extractor.extract();
                        String extractedTitle = extractor.getTitle();

                        if (StringUtils.isBlank(title))
                            title = extractedTitle;

                        keywordsMed = keywordsMed + " " + extractedTitle;
                    }

                    if (StringUtils.isEmpty(body))
                    {
                        body = new HTMLContentExtractor.GenericHTMLExtractor(html).extract();
                    }

                    if (null == title)
                        logBadDocument("Null title", r);
                }
                else if (type.startsWith("text/") && !type.contains("xml"))
                {
                    if (isTooBig(fs, type))
                        body = "";
                    else
                        body = PageFlowUtil.getStreamContentsAsString(is, StandardCharsets.UTF_8);
                }
                else
                {
                    Metadata metadata = new Metadata();
                    metadata.add(Metadata.RESOURCE_NAME_KEY, PageFlowUtil.encode(r.getName()));
                    metadata.add(Metadata.CONTENT_TYPE, r.getContentType());
                    ContentHandler handler = new BodyContentHandler(-1);     // no write limit on the handler -- rely on file size check to limit content

                    parse(r, fs, is, handler, metadata);

                    body = handler.toString();

                    String extractedTitle = metadata.get(Metadata.TITLE);
                    if (StringUtils.isBlank(title))
                        title = extractedTitle;
                    keywordsMed = keywordsMed + getInterestingMetadataProperties(metadata);
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

            if (StringUtils.isBlank(keywordsMed))
                keywordsMed = title;

            // Add all container path parts as low-priority keywords... see #9362
            String identifiersLo = StringUtils.join(c.getParsedPath(), " ");

            String summary = extractSummary(body, title);

            Document doc = new Document();

            // === Index without analyzing, store ===

            doc.add(new Field(FIELD_NAME.uniqueId.toString(), r.getDocumentId(), StringField.TYPE_STORED));
            doc.add(new Field(FIELD_NAME.container.toString(), r.getContainerId(), StringField.TYPE_STORED));

            doc.add(new SortedDocValuesField(FIELD_NAME.container.toString(), new BytesRef(r.getContainerId())));

            // === Index without analyzing, don't store ===

            // TODO: We're implementing a ghetto analyzer here... we really should create a PerFieldAnalyzerWrapper
            // that uses Snowball for text fields and a whitespace, lowercase analyzer for fields that contain multiple
            // terms that we don't want to stem. Custom properties could even specify an analyzer preference. This new
            // Analyzer should then be used at both index and search time.

            // Split the category string by whitespace, index each without stemming
            for (String category : categories.split("\\s+"))
                doc.add(new Field(PROPERTY.categories.toString(), category.toLowerCase(), StringField.TYPE_NOT_STORED));

            addIdentifiers(doc, props, PROPERTY.identifiersLo, FIELD_NAME.identifiersLo, identifiersLo);
            addIdentifiers(doc, props, PROPERTY.identifiersMed, FIELD_NAME.identifiersMed, null);
            addIdentifiers(doc, props, PROPERTY.identifiersHi, FIELD_NAME.identifiersHi, null);

            // === Index and analyze, don't store ===

            doc.add(new TextField(FIELD_NAME.body.toString(), body, Field.Store.NO));

            addKeywords(doc, props, PROPERTY.keywordsLo, FIELD_NAME.keywordsLo, null);
            addKeywords(doc, props, PROPERTY.keywordsMed, FIELD_NAME.keywordsMed, keywordsMed);
            addKeywords(doc, props, PROPERTY.keywordsHi, FIELD_NAME.keywordsHi, null);

            // === Don't index, store ===

            doc.add(new StoredField(FIELD_NAME.title.toString(), title));
            doc.add(new StoredField(FIELD_NAME.summary.toString(), summary));
            doc.add(new StoredField(FIELD_NAME.url.toString(), url));
            if (null != props.get(PROPERTY.navtrail.toString()))
                doc.add(new StoredField(FIELD_NAME.navtrail.toString(), (String)props.get(PROPERTY.navtrail.toString())));
            String resourceId = (String)props.get(PROPERTY.securableResourceId.toString());
            if (null != resourceId && !resourceId.equals(r.getContainerId()))
                doc.add(new SortedDocValuesField(FIELD_NAME.resourceId.toString(), new BytesRef(resourceId)));

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

            return Collections.singletonMap(Document.class, doc);
        }
        catch (NoClassDefFoundError err)
        {
            Throwable cause = err.getCause();
            // Suppress stack trace, etc., if Bouncy Castle isn't present.  Use cause since ClassNotFoundException's
            // message is consistent across JVMs; NoClassDefFoundError's is not.  Note: This shouldn't happen any more
            // since Bouncy Castle ships with Tika as of 0.7.
            if (cause != null && cause instanceof ClassNotFoundException && cause.getMessage().equals("org.bouncycastle.cms.CMSException"))
                _log.warn("Can't read encrypted document \"" + id + "\".  You must install the Bouncy Castle encryption libraries to index this document.  Refer to the LabKey Software documentation for instructions.");
            else
                logAsPreProcessingException(r, err);
            handledException[0] = err;
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
            else if (topMessage.equals("Not a HPSF document") && cause instanceof NoPropertySetStreamException)
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
            else
            {
                logAsPreProcessingException(r, e);
            }
            handledException[0] = cause;
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

        return null;
    }


    // We were using StringField.TYPE_NOT_STORED for identifiers, but that resulted in exceptions with phrase queries,
    // see #17174. This alternate approach indexes identifiers WITH postion information. There must be a way to index
    // identifiers without postition info but exclude them from phrase queries, but it all makes my head hurt too much.
    private final static FieldType INDEXED_IDENTIFIER = new FieldType();

    static
    {
        INDEXED_IDENTIFIER.setOmitNorms(true);
        INDEXED_IDENTIFIER.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        INDEXED_IDENTIFIER.setTokenized(false);
    }

    private void addIdentifiers(Document doc, Map<String, ?> props, PROPERTY property, FIELD_NAME fieldName, @Nullable String standardIdentifiers)
    {
        String documentIdentifiers = (String)props.get(property.toString());
        String identifiers = (null == standardIdentifiers ? "" : standardIdentifiers + " ") + (null == documentIdentifiers ? "" : documentIdentifiers);

        // Split the identifiers string by whitespace, index each without stemming
        if (!identifiers.isEmpty())
            for (String identifier : identifiers.split(("\\s+")))
                doc.add(new Field(fieldName.toString(), identifier.toLowerCase(), INDEXED_IDENTIFIER));
    }


    private void addKeywords(Document doc, Map<String, ?> props, PROPERTY property, FIELD_NAME fieldName, @Nullable String standardKeywords)
    {
        String documentKeywords = (String)props.get(property.toString());
        String keywords = (null == standardKeywords ? "" : standardKeywords + " ") + (null == documentKeywords ? "" : documentKeywords);

        if (!keywords.isEmpty())
            doc.add(new TextField(fieldName.toString(), keywords, Field.Store.NO));
    }


    private void logBadDocument(String problem, WebdavResource r)
    {
        String message = problem + ". Document creation stack trace:" + ExceptionUtil.renderStackTrace(r.getCreationStackTrace());
        _log.error(message);
        throw new IllegalStateException(problem);
    }


    // parse the document of the resource, not that parse() and accept() should agree on what is parsable
    void parse(WebdavResource r, FileStream fs, InputStream is, ContentHandler handler, Metadata metadata) throws IOException, SAXException, TikaException
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
        if (isTooBig(fs,r.getContentType()))
        {
            logAsWarning(r, "The document is too large");
            return;
        }

        Parser parser = getParser();
        parser.parse(is, handler, metadata, new ParseContext());
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

    // TODO: Switch these to TikaCoreProperties.*, I guess
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

    private int getDocCount(Query query) throws IOException
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

    protected boolean index(String id, WebdavResource r, Map preprocessMap)
    {
        try
        {
            Document doc = (Document)preprocessMap.get(Document.class);
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
            Directory directory = WritableIndexManagerImpl.openDirectory(SearchPropertyManager.getPrimaryIndexDirectory().toPath());

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


    public SearchHit find(String id) throws IOException
    {
        IndexSearcher searcher = _indexManager.getSearcher();

        try
        {
            TermQuery query = new TermQuery(new Term(FIELD_NAME.uniqueId.toString(), id));
            TopDocs topDocs = searcher.search(query, null, 1);
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

        enumMap.put(FIELD_NAME.title, 2.0f);          // TODO: Deprecated... old documents only
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

            Query query;
            Analyzer analyzer = null;

            try
            {
                analyzer = getAnalyzer();
                QueryParser queryParser = new MultiFieldQueryParser(standardFields, analyzer, boosts);
                query = queryParser.parse(queryString);
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
            finally
            {
                if (null != analyzer)
                    analyzer.close();
            }

            if (null != categories)
            {
                BooleanQuery bq = new BooleanQuery();
                bq.add(query, BooleanClause.Occur.MUST);
                Iterator itr = categories.iterator();

                if (requireCategories)
                {
                    BooleanQuery requiresBQ = new BooleanQuery();

                    while (itr.hasNext())
                    {
                        Query categoryQuery = new TermQuery(new Term(SearchService.PROPERTY.categories.toString(), itr.next().toString().toLowerCase()));
                        requiresBQ.add(categoryQuery, BooleanClause.Occur.SHOULD);
                    }

                    bq.add(requiresBQ, BooleanClause.Occur.MUST);
                }
                else
                {
                    while (itr.hasNext())
                    {
                        Query categoryQuery = new TermQuery(new Term(SearchService.PROPERTY.categories.toString(), itr.next().toString().toLowerCase()));
                        categoryQuery.setBoost(3.0f);
                        bq.add(categoryQuery, BooleanClause.Occur.SHOULD);
                    }
                }
                query = bq;
            }

            IndexSearcher searcher = _indexManager.getSearcher();

            try
            {
                iTimer.setPhase(SEARCH_PHASE.buildSecurityFilter);
                Filter securityFilter = user.isSearchUser() ? null : new SecurityFilter(user, scope.getRoot(current), current, scope.isRecursive(), iTimer);

                iTimer.setPhase(SEARCH_PHASE.search);

                TopDocs topDocs;

                if (null == sort)
                    topDocs = searcher.search(query, securityFilter, hitsToRetrieve);
                else
                    topDocs = searcher.search(query, securityFilter, hitsToRetrieve, new Sort(new SortField(sort, SortField.Type.STRING)));

                iTimer.setPhase(SEARCH_PHASE.processHits);
                return createSearchResult(offset, hitsToRetrieve, topDocs, searcher);
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


    private SearchResult createSearchResult(int offset, int hitsToRetrieve, TopDocs topDocs, IndexSearcher searcher)
            throws IOException
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

            // BUG patch see 10734 : Bad URLs for files in search results
            // this is only a partial fix, need to rebuild index
            if (hit.url.contains("/%40files?renderAs=DEFAULT/"))
            {
                int in = hit.url.indexOf("?renderAs=DEFAULT/");
                hit.url = hit.url.substring(0,in) + hit.url.substring(in+"?renderAs=DEFAULT".length()) + "?renderAs=DEFAULT";
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


    @Override
    public boolean hasExternalIndexPermission(User user)
    {
        if (null == _externalIndexManager)
            return false;

        SecurityPolicy policy = SecurityPolicyManager.getPolicy(_externalIndexManager);

        return policy.hasPermission(user, ReadPermission.class);
    }


    @Override
    public SearchResult searchExternal(String queryString, int offset, int limit) throws IOException
    {
        if (null == _externalIndexManager)
            throw new IllegalStateException("External index is not defined");

        int hitsToRetrieve = offset + limit;
        IndexSearcher searcher = _externalIndexManager.getSearcher();

        try
        {
            QueryParser queryParser = new MultiFieldQueryParser(new String[]{"content", "title"}, _externalIndexManager.getAnalyzer());
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
            _externalIndexManager.releaseSearcher(searcher);
        }
    }


    protected void shutDown()
    {
        closeIndex();

        try
        {
            if (null != _externalIndexManager)
                _externalIndexManager.close();
        }
        catch (Exception e)
        {
            _log.error("Closing external index", e);
        }
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
        if (null != _externalIndexManager)
        {
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(_externalIndexManager);
            if (policy.hasPermission(user, AdminPermission.class))
                return Collections.singletonList((SecurableResource) _externalIndexManager);
        }

        return Collections.emptyList();
    }


    // Due to https://issues.apache.org/jira/browse/LUCENE-3841, we construct a new Analyzer every time. This has been
    // fixed in Lucene 3.6, which we now use, so we could switch to a static instance of the Analyzer now.
    protected Analyzer getAnalyzer()
    {
        return ExternalAnalyzer.EnglishAnalyzer.getAnalyzer();
    }
}
