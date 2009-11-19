package org.labkey.core.search;

import org.labkey.core.search.HTMLContentExtractor.*;
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
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.webdav.Resource;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * User: adam
 * Date: Nov 18, 2009
 * Time: 1:14:44 PM
 */
public class LuceneSearchServiceImpl extends AbstractSearchService
{
    private static final Category _log = Category.getInstance(LuceneSearchServiceImpl.class);
    private static int _count = 0;
    private static IndexWriter _iw = null;
    private static Analyzer _analyzer = null;
    private static IndexSearcher _searcher = null;

    public LuceneSearchServiceImpl()
    {
        try
        {
            File tempDir = new File(PageFlowUtil.getTempDirectory(), "labkey_full_text_index");
            Directory directory = FSDirectory.open(tempDir);
            _analyzer = new SnowballAnalyzer(Version.LUCENE_CURRENT, "English");
            _iw = new IndexWriter(directory, _analyzer);
            _searcher = new IndexSearcher(directory, true);
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
            _iw.deleteAll();
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
            String type = r.getContentType();

            if ("text/html".equals(type))
            {
                _count++;

                if (0 == _count % 100)
                    _log.info("Indexing: " + _count);

                {
                    Document doc = new Document();
                    String html = PageFlowUtil.getStreamContentsAsString(r.getInputStream(User.getSearchUser()));
                    Map<String, ?> props = r.getProperties();
                    String title = (String)props.get("title");
                    String body;

                    // TODO: Need better check for issue HTML vs. rendered page HTML
                    if (null == title)
                    {
                        HTMLContentExtractor extractor = new LabKeyPageHTMLExtractor(html);
                        body = extractor.extract();
                        title = extractor.getTitle();
                    }
                    else
                    {
                        body = new GenericHTMLExtractor(html).extract();
                    }

                    String url = r.getExecuteHref(null);
                    assert null != url;
                    if (null == title)
                    {
                        _log.error("Bogus content for: " + id + "\n" + html);
                        title = url;
                    }

                    String summary = extractSummary(body, title);

                    doc.add(new Field("body", body, Field.Store.NO, Field.Index.ANALYZED));
                    doc.add(new Field("title", title, Field.Store.YES, Field.Index.ANALYZED));
                    doc.add(new Field("summary", summary, Field.Store.YES, Field.Index.NO));
                    doc.add(new Field("url", url, Field.Store.YES, Field.Index.NO));

                    return Collections.singletonMap(Document.class, doc);
                }
            }
            else
            {
                _log.info("Unknown content type: " + type);
            }
        }
        catch(Exception e)
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
            _iw.addDocument(doc);
        }
        catch(Exception e)
        {
            _log.error("Indexing error with " + id, e);
        }
    }


    protected void commit()
    {
        try
        {
            _iw.commit();
        }
        catch (IOException e)
        {
            _log.error("Exception commiting index", e);
            _log.error("Attempting to close index");
            shutDown();
        }
    }


    public String search(String queryString)
    {
        try
        {
            String sort = null;  // TODO: add sort parameter

            // Should stash all this and reuse
            int hitsPerPage = 20;

            long start = System.nanoTime();
            Query query = new QueryParser("body", _analyzer).parse(queryString.toLowerCase());

            TopDocs topDocs;

            if (null == sort)
                topDocs = _searcher.search(query, hitsPerPage);
            else
                topDocs = _searcher.search(query, null, hitsPerPage, new Sort(new SortField(sort, SortField.AUTO)));

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
                Document doc = _searcher.doc(hit.doc);

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
        try
        {
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
    }
}