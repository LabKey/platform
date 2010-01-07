package org.labkey.core.workbook;

/**
 * Created by IntelliJ IDEA.
 * User: labkey
 * Date: Jan 6, 2010
 * Time: 10:45:01 AM
 */
public class WorkbookSearchBean
{
    private WorkbookQueryView _queryView;
    private String _searchString;

    public WorkbookSearchBean(WorkbookQueryView view, String searchString)
    {
        _queryView = view;
        _searchString = searchString;
    }

    public WorkbookQueryView getQueryView()
    {
        return _queryView;
    }

    public void setQueryView(WorkbookQueryView queryView)
    {
        _queryView = queryView;
    }

    public String getSearchString()
    {
        return _searchString;
    }

    public void setSearchString(String searchString)
    {
        _searchString = searchString;
    }
}
