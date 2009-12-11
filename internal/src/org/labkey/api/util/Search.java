/*
 * Copyright (c) 2005-2009 LabKey Corporation
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

package org.labkey.api.util;

import org.labkey.api.data.*;
import org.labkey.api.data.SimpleFilter.OperationClause;
import org.labkey.api.data.SimpleFilter.OrClause;
import org.labkey.api.security.User;
import org.labkey.api.view.*;
import org.springframework.beans.PropertyValues;

import java.io.PrintWriter;
import java.sql.Types;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: arauch
 * Date: Nov 25, 2005
 * Time: 11:47:29 PM
 */
public class Search
{
    private static List<Searchable> _searchables = new ArrayList<Searchable>();
    private static String _searchResultNames = null;
    public static List<Searchable> ALL_SEARCHABLES = _searchables;

    public static void register(Searchable searchable)
    {
        _searchables.add(searchable);
    }

    public static boolean includeSubfolders(PropertyValues props)
    {
        return includeSubfolders(props.getPropertyValue("includeSubfolders"));
    }

    public static boolean includeSubfolders(Object value)
    {
        return !("0".equals(value) || "off".equals(value));
    }

    public static boolean includeSubfolders(Portal.WebPart part)
    {
        return includeSubfolders(part.getPropertyMap().get("includeSubfolders"));
    }

    public static Searchable getDomain(String domain)
    {
        //since all calls to register are done only once during module startup,
        //we don't need to lock the _searchables list while iterating
        assert(null != domain);
        for(Searchable src : _searchables)
        {
            if(domain.equalsIgnoreCase(src.getDomainName()))
                return src;
        }
        return null;
    }


    public static String getSearchResultNames(List<Searchable> searchables)
    {
        String result;

        if (null == _searchResultNames || searchables != _searchables)
        {
            int size = searchables.size();

            StringBuilder joined = new StringBuilder();

            if (size > 1)
            {
                for (int i=0; i < size - 1; i++)
                {
                    joined.append(searchables.get(i).getSearchResultNamePlural().toLowerCase());
                    if (size > 2)
                        joined.append(", ");
                    else
                        joined.append(" ");
                }

                joined.append("and ");
            }
            joined.append(searchables.get(size - 1).getSearchResultNamePlural().toLowerCase());

            result = joined.toString();
            if (searchables == _searchables)
                _searchResultNames = result;
        }
        else
            result = _searchResultNames;

        return result;
    }

    /**
     * containers may be empty if there is no container column
     */
    public static SQLFragment getSQLFragment(String selectColumnNames, String innerSelectColumnNames, String fromClause, String containerColumnName, SimpleFilter whereFilter, Set<Container> containers, SearchTermProvider termProvider, SqlDialect dialect, String... searchColumnNames)
    {
        List<String> containerIds = new ArrayList<String>(containers.size());
        for(Container c : containers)
            containerIds.add(c.getId());

        return getSQLFragment(selectColumnNames, innerSelectColumnNames, fromClause, containerColumnName, whereFilter, containerIds, termProvider, dialect, searchColumnNames);
    }

    /**
     * containers may be empty if there is no container column
     */
    public static SQLFragment getSQLFragment(String selectColumnNames, String innerSelectColumnNames, String fromClause, String containerColumnName, SimpleFilter whereFilter, Collection<String> containerIds, SearchTermProvider termProvider, SqlDialect dialect, String... searchColumnNames)
    {
        SQLFragment searchSql = new SQLFragment("SELECT " + selectColumnNames + ", TermCount FROM\n(\n\tSELECT " + selectColumnNames);
        SQLFragment caseSql = new SQLFragment();
        SimpleFilter termFilter = new SimpleFilter();
        StringBuilder termCount = new StringBuilder();

        List<SearchTerm> andTerms = termProvider.getAndTerms();
        List<SearchTerm> orTerms = termProvider.getOrTerms();

        String termPrefix = "and";
        addTerms(andTerms, searchColumnNames, dialect, searchSql, caseSql, termCount, termPrefix);

        int i = 0;

        for (SearchTerm term : andTerms)
        {
            String termName = termPrefix + i++;

            if (term.isNot())
                termFilter.addCondition(termName, 0, CompareType.EQUAL);
            else
                termFilter.addCondition(termName, 0, CompareType.GT);
        }

        if (!orTerms.isEmpty())
        {
            termPrefix = "or";
            addTerms(orTerms, searchColumnNames, dialect, searchSql, caseSql, termCount, termPrefix);

            OrClause or = new OrClause();
            i = 0;

            for (SearchTerm term : orTerms)
            {
                String termName = termPrefix + i++;

                if (term.isNot())
                    or.addClause(new CompareType.CompareClause(termName, CompareType.EQUAL, 0));
                else
                    or.addClause(new CompareType.CompareClause(termName, CompareType.GT, 0));
            }

            termFilter.addClause(or);
        }

        searchSql.append(", ");
        searchSql.append(termCount);
        searchSql.append(" AS TermCount FROM\n\t(\n\t\tSELECT ");
        searchSql.append(innerSelectColumnNames);
        searchSql.append(caseSql);
        searchSql.append("\n\t\tFROM ");
        searchSql.append(fromClause);
        searchSql.append("\n\t\t");

        SimpleFilter filter = (null == whereFilter ? new SimpleFilter() : whereFilter);

        if (containerIds.size() > 0)
            filter.addClause(new SimpleFilter.InClause(containerColumnName, containerIds));
        
        searchSql.append(filter.getSQLFragment(dialect));

        searchSql.append("\n\t) x\n\tGROUP BY " + selectColumnNames + "\n) y\n");
        searchSql.append(termFilter.getSQLFragment(dialect));

        return searchSql;
    }


    private static void addTerms(Collection<SearchTerm> terms, String[] searchColumnNames, SqlDialect dialect, SQLFragment searchSql, SQLFragment caseSql, StringBuilder termCount, String termPrefix)
    {
        int i = 0;

        for (SearchTerm term : terms)
        {
            String termName = termPrefix + i;

            searchSql.append(", SUM(");
            searchSql.append(termName);
            searchSql.append(") AS ");
            searchSql.append(termName);

            termCount.append(term.isNot() ? "-" : "+").append("SUM(").append(termName).append(")");

            caseSql.append(",\n\t\t\tCASE WHEN (");

            OperationClause termClause = new OrClause();

            for (String searchColumnName : searchColumnNames)
            {
                termClause.addClause(new CompareType.ContainsClause(getSafeColumnName(searchColumnName, dialect), term.getTerm()));
            }

            caseSql.append(termClause.toSQLFragment(Collections.<String, ColumnInfo>emptyMap(), dialect));
            caseSql.append(") THEN 1 ELSE 0 END AS ");
            caseSql.append(termName);

            i++;
        }
    }

    private static String getSafeColumnName(String originalColumnName, SqlDialect dialect)
    {
        if (dialect.isPostgreSQL())
        {
            // Postgres 8.3 doesn't like to treat number columns as varchars,
            // so add a cast.
            // Also need to escape the column name in case it's a reserved sql term, like "primary"
            StringBuilder sb = new StringBuilder();
            sb.append("CAST(");
            sb.append(dialect.getColumnSelectName(originalColumnName));
            sb.append(" AS ");
            sb.append(dialect.sqlTypeNameFromSqlType(Types.VARCHAR));
            sb.append(")");

            return sb.toString();
        }
        else
        {
            return dialect.getColumnSelectName(originalColumnName);
        }
    }


    public interface Searchable
    {
        /**
         * Perform the search given a set of search terms. Append search hits to
         * the list provided as the hits parameter. This list may already contain
         * hits from previous search providers, so do not clear it. Simply append
         * your hits to the end.
         * @param parser
         * @param containers The set of containers to search in
         * @param hits List of hits to append to
         * @param user
         */
        void search(SearchTermParser parser, Set<Container> containers, List<SearchHit> hits, User user);

        /**
         * Returns a static string used to classify the search results to the user
         * For example "Wiki Pages" or "Issues".
         * @return The type of search results returned from this provier
         */
        String getSearchResultNamePlural();

        /**
         * Returns a name used to programmatically identify the search domain.
         * This may be used by clients to restrict searches to specific domains.
         * @return The name of your search domain (e.g., "wiki")
         */
        String getDomainName();
    }


    public static class SearchTerm
    {
        private String _term;
        private boolean _not;

        protected SearchTerm(String term, boolean not)
        {
            _term = term;
            _not = not;
        }

        public boolean isNot()
        {
            return _not;
        }

        public String getTerm()
        {
            return _term;
        }

        public String toString()
        {
            return (_not ? "NOT " : "") + _term;
        }
    }


    public static class SearchResultsView extends VBox
    {
        public SearchResultsView(Container root, List<Searchable> searchables, String searchTerm, ActionURL searchUrl, boolean includeSubfolders, boolean includeSettings)
        {
            addView(new SearchWebPart(searchables, searchTerm, searchUrl, includeSubfolders, includeSettings, 40, true));
            addView(new ResultsView(root, includeSubfolders, searchTerm, searchables));
        }

        private static class ResultsView extends WebPartView
        {
            private Container _root;
            private boolean _includeSubfolders;
            private String _searchTerm;
            private List<Searchable> _searchables;

            private ResultsView(Container root, boolean includeSubfolders, String searchTerm, List<Searchable> searchables)
            {
                _root = root;
                _includeSubfolders = includeSubfolders;
                _searchTerm = searchTerm;
                _searchables = searchables;
                setTitle("Results");
            }

            @Override
            protected void renderView(Object model, PrintWriter out) throws Exception
            {
                //determine the set of containers to search
                //if includeSubfolders is true, get all children in which the user has read permission
                Set<Container> containers = _includeSubfolders ?
                        ContainerManager.getAllChildren(_root, getViewContext().getUser()) :
                        Collections.singleton(_root);

                //parse the search terms
                Search.SearchTermParser parser = new Search.SearchTermParser(_searchTerm);
                List<SearchHit> hits = new ArrayList<SearchHit>();
                if (parser.hasTerms())
                {
                    //perform the searches
                    for(Search.Searchable src : _searchables)
                    {
                        src.search(parser, containers, hits, getViewContext().getUser());
                    }
                }

                //sort the results
                Collections.sort(hits, new SearchHitComparator());

                //start the output
                out.print("Searched in ");
                out.print(containers.size());
                out.print(" folder");
                if (containers.size() > 1)
                    out.print('s');
                out.print(" for \"");
                out.print(PageFlowUtil.filter(_searchTerm));
                out.print("\" and found ");

                if (0 == hits.size())
                {
                    out.println("no results.");
                    return;
                }

                out.print(hits.size());
                out.print(" result");
                if (hits.size() > 1)
                    out.print('s');
                out.println(".<br>");

                //output the hits themselves
                writeHits(hits, out);
            }

            protected void writeHits(List<SearchHit> hits, PrintWriter out)
            {
                //the hits are sorted by path, type, title
                String curPath = "";

                out.println("<table class=\"labkey-data-region\">");

                for (SearchHit hit : hits)
                {
                    if (!curPath.equals(hit.getContainerPath()))
                    {
                        out.println();         // Empty line separating each section
                        out.print("<tr><td colspan=\"2\"><br/><b>");
                        out.print(PageFlowUtil.filter(hit.getContainerPath()));
                        out.println("</b></td></tr>");
                        curPath = hit.getContainerPath();
                    }
                    
                    writeHit(hit, out);
                }

                out.println("</table>");
            }

            protected void writeHit(SearchHit hit, PrintWriter out)
            {
                out.print("<tr><td>&nbsp;</td><td>");
                if (null != hit.getTypeDescription() && hit.getTypeDescription().length() > 0)
                {
                    out.print(PageFlowUtil.filter(hit.getTypeDescription()));
                    out.print(": ");
                }
                out.print("<a href=\"");
                out.print(PageFlowUtil.filter(hit.getHref()));
                out.print("\">");
                out.print(PageFlowUtil.filter(hit.getTitle()));
                out.print("</a>");

                String context = hit.getDetails();
                if (null != context && context.length() > 0)
                {
                    out.print("<div style=\"color: #565051;padding-left: 2em\">");
                    out.print(PageFlowUtil.filter(context));
                    out.print("</div>");
                }

                //close the table cell and row
                out.println("</td></tr>");
            }
        }
    }


    public static interface SearchTermProvider
    {
        public List<SearchTerm> getAndTerms();
        public List<SearchTerm> getOrTerms();
    }

    public static class SearchTermParser implements SearchTermProvider
    {
        private final String _query;
        private final List<SearchTerm> _andTerms = new ArrayList<SearchTerm>();
        private final List<SearchTerm> _orTerms = new ArrayList<SearchTerm>();

        public SearchTermParser(String query)
        {
            _query = query;

            Pattern searchTermPattern = Pattern.compile("\\s*((-?)((\"(.+?)\")|([^\"\\s]+)))");
            Matcher searchTermMatcher = searchTermPattern.matcher(query);
            List<SearchTerm> terms = new ArrayList<SearchTerm>();

            while(searchTermMatcher.find())
            {
                boolean not = "-".equals(searchTermMatcher.group(2));
                String term = (null != searchTermMatcher.group(5) ? searchTermMatcher.group(5) : searchTermMatcher.group(3));
                terms.add(new SearchTerm(term, not));
            }

            // CONSIDER: Check for malformed OR constructs: "OR" "this OR OR that" etc.
//                    if ("OR".equals(parsedTerms.get(0)) || "OR".equals(parsedTerms.get(parsedTerms.size() - 1)))
//                        ;

            if (terms.isEmpty())
                return;

            boolean prevWasOr = false;

            for (int i = 1; i < terms.size(); i++)
            {
                SearchTerm term = terms.get(i);
                SearchTerm previousTerm = terms.get(i - 1);

                if ("OR".equals(term.getTerm()))
                {
                    _orTerms.add(previousTerm);
                    i++;
                    prevWasOr = true;
                }
                else if (prevWasOr)
                {
                    _orTerms.add(previousTerm);
                    prevWasOr = false;
                }
                else
                {
                    _andTerms.add(previousTerm);
                    prevWasOr = false;
                }
            }

            if (prevWasOr)
                _orTerms.add(terms.get(terms.size() - 1));
            else
                _andTerms.add(terms.get(terms.size() - 1));
        }

        @Override
        public String toString()
        {
            return _query;
        }

        public List<SearchTerm> getAndTerms()
        {
            return _andTerms;
        }

        public List<SearchTerm> getOrTerms()
        {
            return _orTerms;
        }

        public boolean hasTerms()
        {
            return !getAndTerms().isEmpty() || !getOrTerms().isEmpty();
        }

        public String getQuery()
        {
            return _query;
        }

        /**
         * Returns true if the terms match the provided text,
         * handling NOT, AND and OR correctly. This matching is
         * case-insensitive.
         */
        public boolean matches(String text)
        {
            if (!hasTerms())
                return false;
            if (text == null)
                return false;

            text = text.toLowerCase();

            for (SearchTerm term : _andTerms)
            {
                String termString = term.getTerm().toLowerCase();

                if (term.isNot() && text.contains(termString))
                    return false;

                else if (!text.contains(termString))
                    return false;

            }

            // If we had some AND terms, then they must have matched.
            // No need to check the OR terms
            if (!_andTerms.isEmpty())
                return true;

            for (SearchTerm term : _orTerms)
            {
                String termString = term.getTerm().toLowerCase();
                if (term.isNot() && !text.contains(termString))
                    return true;

                else if (text.contains(termString))
                    return true;
            }

            // We must have gotten through all of our OR terms,
            // without matching.
            return false;

        }
    }


    public static class SearchWebPart extends JspView<SearchBean>
    {
        public SearchWebPart(List<Searchable> modules, String searchTerm, ActionURL searchUrl, boolean includeSubfolders, boolean showSettings)
        {
            this(modules, searchTerm, searchUrl, includeSubfolders, showSettings, 40, false);
        }

        public SearchWebPart(List<Searchable> modules, String searchTerm, ActionURL searchUrl, boolean includeSubfolders, boolean showSettings, int textBoxWidth, boolean showExplanationText)
        {
            super("/org/labkey/portal/search.jsp", new SearchBean());

            SearchBean bean = getModelBean();
            bean.postURL = searchUrl;
            bean.searchTerm = searchTerm;
            bean.textBoxWidth = textBoxWidth;
            bean.what = getSearchResultNames(modules);
            bean.includeSubfolders = includeSubfolders;
            bean.showSettings = showSettings;
            bean.showExplanatoryText = showExplanationText;

            setTitle("Search");
        }
    }


    public static class SearchBean
    {
        public ActionURL postURL;
        public String searchTerm;
        public int textBoxWidth;
        public String what;
        public boolean includeSubfolders;
        public boolean showSettings;
        public boolean showExplanatoryText;
    }
}
