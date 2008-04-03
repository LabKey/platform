package org.labkey.api.util;

import org.labkey.api.data.*;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.*;
import org.apache.commons.collections15.MultiMap;

import java.io.*;
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
                    joined.append(searchables.get(i).getSearchResultName().toLowerCase());
                    if (size > 2)
                        joined.append("s, ");
                    else
                        joined.append("s ");
                }

                joined.append("and ");
            }
            joined.append(searchables.get(size - 1).getSearchResultName().toLowerCase());
            joined.append("s");

            result = joined.toString();
            if (searchables == _searchables)
                _searchResultNames = result;
        }
        else
            result = _searchResultNames;

        return result;
    }


    public static SQLFragment getSQLFragment(String selectColumnNames, String innerSelectColumnNames, String fromClause, String containerColumnName, SimpleFilter whereFilter, Collection<String> containerIds, SearchTermParser parser, SqlDialect dialect, String... searchColumnNames)
    {
        SQLFragment searchSql = new SQLFragment("SELECT " + selectColumnNames + ", TermCount FROM\n(\n\tSELECT " + selectColumnNames);
        SQLFragment caseSql = new SQLFragment();
        SimpleFilter termFilter = new SimpleFilter();
        StringBuilder termCount = new StringBuilder();

        List<SearchTerm> andTerms = parser.getAndTerms();
        List<SearchTerm> orTerms = parser.getOrTerms();

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
                termClause.addClause(new CompareType.ContainsClause(searchColumnName, term.getTerm()));

            caseSql.append(termClause.toSQLFragment(Collections.<String, ColumnInfo>emptyMap(), dialect));
            caseSql.append(") THEN 1 ELSE 0 END AS ");
            caseSql.append(termName);

            i++;
        }
    }


    public interface Searchable
    {
        // Takes a collection of container ids and a collection of search terms and returns a MultiMap of <container id> -> <List of links>.
        // Links point to each details page where the content contained all the specified search terms.  Container list has been filtered
        // for read permissions already; implementors should query & return content only in these containers (although we'd never display
        // content outside this list... just the link text; but the user would click on links that resulted in unauthorized exceptions).
        MultiMap<String, String> search(Collection<String> containerIds, SearchTermParser parser);
        String getSearchResultName();
    }


    public static class SearchTerm
    {
        private String _term;
        private boolean _not;

        private SearchTerm(String term, boolean not)
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
        public SearchResultsView(Container root, List<Searchable> searchables, String searchTerm, ActionURL searchUrl, User user, boolean includeSubfolders, boolean includeSettings)
        {
            addView(new SearchWebPart(searchables, searchTerm, searchUrl, includeSubfolders, includeSettings, 40, true));
            addView(new ResultsView(root, includeSubfolders, searchTerm, searchables, user));
        }

        private static class ResultsView extends WebPartView
        {
            private Container _root;
            private boolean _includeSubfolders;
            private String _searchTerm;
            private List<Searchable> _searchables;
            private User _user;

            private ResultsView(Container root, boolean includeSubfolders, String searchTerm, List<Searchable> searchables, User user)
            {
                _root = root;
                _includeSubfolders = includeSubfolders;
                _searchTerm = searchTerm;
                _searchables = searchables;
                _user = user;
                setTitle("Results");
            }

            @Override
            protected void renderView(Object model, PrintWriter out) throws Exception
            {
                Set<Container> containers;

                if (_includeSubfolders)
                {
                    // Sort containers by full container path
                    containers = new TreeSet<Container>(new Comparator<Container>()
                    {
                        public int compare(Container a, Container b)
                        {
                            return a.getPath().compareToIgnoreCase(b.getPath());
                        }
                    });

                    MultiMap<Container, Container> mm = ContainerManager.prune(ContainerManager.getContainerTree(), _root);
                    containers.addAll(ContainerManager.getContainerSet(mm, _user, ACL.PERM_READ));

                    if (0 == containers.size())
                        out.println("You don't have permission to search these folders.");
                }
                else
                {
                    if (_root.hasPermission(_user, ACL.PERM_READ))
                        containers = PageFlowUtil.set(_root);
                    else
                    {
                        containers = Collections.emptySet();
                        out.println("You don't have permission to search this folder.");
                    }
                }

                Collection<String> containerIds = new ArrayList<String>(containers.size());

                for (Container c : containers)
                    containerIds.add(c.getId());

                SearchTermParser parser = new SearchTermParser(_searchTerm);

                Map<String, MultiMap<String, String>> results = new TreeMap<String, MultiMap<String, String>>();

                if (parser.hasTerms())
                {
                    for(Searchable searchable : _searchables)
                        results.put(searchable.getSearchResultName(), searchable.search(containerIds, parser));
                }

                // Count the results
                int resultCount = 0;
                int folderCount = containers.size();

                for(MultiMap moduleResults : results.values())
                    resultCount += moduleResults.values().size();

                out.print("Searched ");
                //out.print(getSearchResultNames(_searchables)); //per Geroge and 4678
                out.print("in ");
                out.print(folderCount);
                out.print(" folder");
                if (folderCount > 1)
                    out.print('s');
                out.print(" for \"");
                out.print(PageFlowUtil.filter(_searchTerm));
                out.print("\" and found ");

                if (0 == resultCount)
                {
                    out.println("no results.");
                    return;
                }

                out.print(resultCount);
                out.print(" result");
                if (resultCount > 1)
                    out.print('s');
                out.println(".<br>");
                out.println("<table class=\"dataRegion\">");

                for(Container c : containers)
                {
                    boolean hasOutputContainerPath = false;

                    for (Searchable module : _searchables)
                    {
                        MultiMap<String, String> map = results.get(module.getSearchResultName());

                        if (null != map && map.size() > 0)
                        {
                            Collection<String> links = map.get(c.getId());

                            if (null != links)
                            {
                                if (!hasOutputContainerPath)
                                {
                                    out.print("<tr><td colspan=2><br><b>");
                                    out.print(c.getPath());
                                    out.println("</b><br></td></tr>");
                                    hasOutputContainerPath = true;
                                }

                                for (String link : links)
                                {
                                    out.print("<tr><td></td><td>");
                                    out.print(module.getSearchResultName());
                                    out.print(": ");
                                    out.print(link);
                                    out.println("</td></tr>");
                                }
                            }
                        }
                    }
                }

                out.println("</table>");
            }
        }
    }


    public static class SearchTermParser
    {
        List<SearchTerm> _andTerms = new ArrayList<SearchTerm>();
        List<SearchTerm> _orTerms = new ArrayList<SearchTerm>();

        private SearchTermParser(String query)
        {
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
            return !_andTerms.isEmpty() || !_orTerms.isEmpty();
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
