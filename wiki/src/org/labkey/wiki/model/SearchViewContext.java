package org.labkey.wiki.model;

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;

/**
 * Context object for the wikiSearch.jsp view
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Oct 30, 2007
 * Time: 2:35:25 PM
 */
public class SearchViewContext
{
    public SearchViewContext(ViewContext ctx)
    {
        _searchUrl = ctx.getActionURL().clone();
        _searchUrl.setAction("search.view");
        _searchUrl.deleteParameters();
    }

    public String getSearchUrl()
    {
        return _searchUrl.getLocalURIString();
    }

    private ActionURL _searchUrl;

}
