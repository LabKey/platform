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

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.*;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.*;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.search.SearchController;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/*
* User: adam
* Date: Nov 5, 2009
* Time: 8:05:02 AM
*/

// NOT USED: old implementation of lucene issues prototype
// TODO: Delete
public class LuceneSearch
{
    private static final Logger LOG = Logger.getLogger(LuceneSearch.class);
    private static final String SEARCH_COLUMNS = "IssueId, Container, Type, Area, Title, AssignedTo, Priority, Status, Milestone";

    private LuceneSearch()
    {
    }

    public static void buildIndex() throws SQLException, IOException
    {
        TableInfo tinfo = IssuesSchema.getInstance().getTableInfoIssues();
        List<ColumnInfo> columns = tinfo.getColumns(SEARCH_COLUMNS);
        ResultSet rs = null;
        IndexWriter iw = null;

        try
        {
            rs = Table.select(tinfo, columns, null, null);

            File tempDir = new File(FileUtil.getTempDirectory(), "issues");
            Directory directory = FSDirectory.open(tempDir);

            Analyzer analyzer = new SnowballAnalyzer(Version.LUCENE_CURRENT, "English");

            iw = new IndexWriter(directory, analyzer, IndexWriter.MaxFieldLength.UNLIMITED);

            iw.deleteAll();

            long start = System.currentTimeMillis();
            int count = 0;

            while (rs.next())
            {
                Document doc = new Document();

                for (ColumnInfo column : columns)
                {
                    Fieldable field;

                    Object value = rs.getObject(column.getSelectName());

                    if (null != value)
                    {
                        if (value instanceof Number)
                        {
                            // TODO: Check for other numeric types
                            // TODO: Reuse NumericField
                            NumericField numField;

                            if (column.isKeyField())
                                numField = new NumericField(column.getName(), Field.Store.YES, true);
                            else
                                numField = new NumericField(column.getName());

                            numField.setIntValue(((Integer) value).intValue());
                            field = numField;
                        }
                        else
                        {
                            field = new Field(column.getName(), rs.getString(column.getSelectName()), Field.Store.YES, Field.Index.ANALYZED);
                        }

                        doc.add(field);
                    }
                }

                count++;
                iw.addDocument(doc);
            }

            LOG.info("Indexed " + count + " issues in " + DateUtil.formatDuration(System.currentTimeMillis() - start));
        }
        finally
        {
            if (null != iw)
                iw.close();

            if (null != rs)
                rs.close();
        }
    }

    public static String search(String queryString, @Nullable String sort) throws ParseException, IOException
    {
        // Should stash all this and reuse
        Analyzer analyzer = new SnowballAnalyzer(Version.LUCENE_CURRENT, "English");
        File tempDir = new File(FileUtil.getTempDirectory(), "issues");
        Directory directory = FSDirectory.open(tempDir);
        IndexSearcher searcher = new IndexSearcher(directory, true);
        int hitsPerPage = 20;

        long start = System.nanoTime();
        Query query = new QueryParser(Version.LUCENE_30, "Title", analyzer).parse(queryString);

        TopDocs topDocs;

        if (null == sort)
            topDocs = searcher.search(query, hitsPerPage);
        else
            topDocs = searcher.search(query, null, hitsPerPage, new Sort(new SortField(sort, SortField.STRING)));

        ScoreDoc[] hits = topDocs.scoreDocs;

        StringBuilder html = new StringBuilder("<table><tr><td>Found ");
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

        html.append(".</td></tr>\n<tr><td>&nbsp;</td></tr>\n");

        for (ScoreDoc hit : hits)
        {
            Document doc = searcher.doc(hit.doc);
            String title = doc.get("Title");
            Container container = ContainerManager.getForId(doc.get("Container"));
            int issueId = Integer.valueOf(doc.get("IssueId")).intValue();

            ActionURL url = new ActionURL(SearchController.SearchAction.class, container);
            url.addParameter("issueId", issueId);

            html.append("<tr><td><a href=\"").append(PageFlowUtil.filter(url)).append("\">").append(issueId).append(": ").append(title).append("</a>").append("</td></tr>\n");
        }

        return html.toString();
    }


    @RequiresSiteAdmin
    public class BuildIndexAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            LuceneSearch.buildIndex();
            return new ActionURL(SearchController.SearchAction.class, getViewContext().getContainer());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class SearchLuceneAction extends SimpleViewAction<LuceneSearchForm>
    {
        public ModelAndView getView(LuceneSearchForm form, BindException errors) throws Exception
        {
            String query = form.getQuery();

            HtmlView searchBox = new HtmlView("<form><input type=\"text\" size=50 id=\"query\" name=\"query\" value=\"" + PageFlowUtil.filter(query) + "\">" + PageFlowUtil.generateSubmitButton("Search") + "</form>");
            getPageConfig().setFocusId("query");

            if (null == query)
                return searchBox;

            String results = LuceneSearch.search(query, form.getSort());

            return new VBox(searchBox, new HtmlView(results));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Lucene Search");
        }
    }


    public static class LuceneSearchForm
    {
        private String _query;
        private String _sort;

        public String getQuery()
        {
            return _query;
        }

        public void setQuery(String query)
        {
            _query = query;
        }

        public String getSort()
        {
            return _sort;
        }

        public void setSort(String sort)
        {
            _sort = sort;
        }
    }
}
