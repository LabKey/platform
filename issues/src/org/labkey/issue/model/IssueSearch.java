/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.issue.model;

import org.labkey.api.data.*;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.util.*;
import org.labkey.api.util.Search;
import org.labkey.api.util.Search.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.security.User;
import org.labkey.issue.IssuesController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: adam
 * Date: Aug 25, 2008
 * Time: 11:09:08 AM
 */
public class IssueSearch implements Searchable
{
    private static final String SEARCH_DOMAIN = "issues";
    private static final String SEARCH_RESULT_TYPE = "labkey/issue";
    private static final String SEARCH_RESULT_TYPE_DESCR = "Issues";

    private static final Map<Container,Searchable> _instances = new HashMap<Container,Searchable>();
    static
    {
        _instances.put(null, new IssueSearch());
    }

    private IssueManager.EntryTypeNames _names;

    private IssueSearch()
    {
    }

    private IssueSearch(Container c)
    {
        _names = IssueManager.getEntryTypeNames(c);
    }

    public static Searchable getInstance()
    {
        return _instances.get(null);
    }

    public static synchronized Searchable getInstance(Container c)
    {
        Searchable ret = _instances.get(c);
        if(null == ret)
        {
            ret = new IssueSearch(c);
            _instances.put(c, ret);
        }
        return ret;
    }

    public void search(SearchTermParser parser, Set<Container> containers, List<SearchHit> hits, User user)
    {
        SearchTermProvider issueTermProvider = new IssueSearchTermProvider(parser);
        IssuesSchema schema = IssuesSchema.getInstance();
        SqlDialect dialect = schema.getSchema().getSqlDialect();
        String from = schema.getTableInfoIssues() + " i LEFT OUTER JOIN " + schema.getTableInfoComments() + " c ON i.IssueId = c.IssueId";
        SQLFragment searchSql = Search.getSQLFragment("Container, Title, IssueId", "Container, Title, i.IssueId", from, "Container", null, containers, issueTermProvider, dialect, "Comment"); // No need to search title since it ends up in the comment
        ResultSet rs = null;
        Map<Container, IssueManager.EntryTypeNames> entryNamesMap = new HashMap<Container, IssueManager.EntryTypeNames>();

        try
        {
            rs = Table.executeQuery(schema.getSchema(), searchSql);

            while(rs.next())
            {
                String containerId = rs.getString(1);
                Container c = ContainerManager.getForId(containerId);

                IssueManager.EntryTypeNames names = entryNamesMap.get(c);
                if (null == names)
                {
                    names = IssueManager.getEntryTypeNames(c);
                    entryNamesMap.put(c, names);
                }

                ActionURL url = IssuesController.issueURL(c, "details");
                url.addParameter("issueId", rs.getString(3));

                SimpleSearchHit hit = new SimpleSearchHit(SEARCH_DOMAIN, c.getPath(), rs.getString(2),
                        url.getLocalURIString(), SEARCH_RESULT_TYPE,
                        names.pluralName.toString());

                hits.add(hit);
            }
        }
        catch(SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(HttpView.currentRequest(), e);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    public String getSearchResultNamePlural()
    {
        return null == _names ? SEARCH_RESULT_TYPE_DESCR : _names.pluralName.toString();
    }

    public String getDomainName()
    {
        return SEARCH_DOMAIN;
    }

    // Issue comments are stored HTML encoded, so create a wrapper that encodes the search terms.  This allows us to search
    //  for <init>, <html>, and other terms that include special characters.
    private static class IssueSearchTermProvider implements SearchTermProvider
    {
        List<SearchTerm> _andTerms;
        List<SearchTerm> _orTerms;

        IssueSearchTermProvider(SearchTermProvider provider)
        {
            _andTerms = convertTerms(provider.getAndTerms());
            _orTerms = convertTerms(provider.getOrTerms());
        }

        private static List<SearchTerm> convertTerms(List<SearchTerm> oldTerms)
        {
            List<SearchTerm> newTerms = new ArrayList<SearchTerm>(oldTerms.size());

            for (SearchTerm term : oldTerms)
                newTerms.add(new IssueSearchTerm(term));

            return newTerms;
        }

        public List<SearchTerm> getAndTerms()
        {
            return _andTerms;
        }

        public List<SearchTerm> getOrTerms()
        {
            return _orTerms;
        }
    }

    private static class IssueSearchTerm extends SearchTerm
    {
        private IssueSearchTerm(SearchTerm term)
        {
            super(PageFlowUtil.filter(term.getTerm()), term.isNot());
        }
    }
}
