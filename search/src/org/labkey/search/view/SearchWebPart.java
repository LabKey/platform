package org.labkey.search.view;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.search.SearchController;

/**
 * User: adam
 * Date: Jan 19, 2010
 * Time: 1:59:42 PM
 */
public class SearchWebPart  extends JspView<SearchController.SearchForm>
{
    public SearchWebPart(String searchTerm, ActionURL searchUrl, boolean includeSubfolders, boolean showSettings)
    {
        this(searchTerm, searchUrl, includeSubfolders, showSettings, 40, false);
    }

    public SearchWebPart(String searchTerm, ActionURL searchUrl, boolean includeSubfolders, boolean showSettings, int textBoxWidth, boolean showExplanationText)
    {
        super("/org/labkey/search/view/search.jsp", new SearchController.SearchForm());

        setTitle("Search");
    }
}
