package org.labkey.core.admin;


import org.labkey.api.view.Portal;

/**
 * Created with IntelliJ IDEA.
 * User: davebradlee
 * Date: 9/10/12
 * Time: 1:02 PM
 */

public class CustomizeMenuForm
{
    private String _schemaName;
    private String _queryName;
    private String _folderName;
    private String _viewName;
    private String _columnName;
    private String _title;
    private String _urlTop;
    private String _urlBottom;
    private boolean _choiceListQuery;   // choice between List/Query (true) or Folders (false)
    private String _rootFolder;
    private String _folderTypes;
    private boolean _includeAllDescendants;

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schema)
    {
        _schemaName = schema;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName
            (String query)
    {
        _queryName = query;
    }

    public String getFolderName()
    {
        return _folderName;
    }

    public void setFolderName(String folder)
    {
        _folderName = folder;
    }

    public String getViewName()
    {
        return _viewName;
    }

    public void setViewName(String view)
    {
        _viewName = view;
    }

    public String getColumnName()
    {
        return _columnName;
    }

    public void setColumnName(String column)
    {
        _columnName = column;
    }

    public String getTitle()
    {
        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public String getUrlTop()
    {
        return _urlTop;
    }

    public void setUrlTop(String urlTop)
    {
        _urlTop = urlTop;
    }

    public String getUrlBottom()
    {
        return _urlBottom;
    }

    public void setUrlBottom(String urlBottom)
    {
        _urlBottom = urlBottom;
    }

    public boolean isChoiceListQuery()
    {
        return _choiceListQuery;
    }

    public void setChoiceListQuery(boolean choiceListQuery)
    {
        _choiceListQuery = choiceListQuery;
    }

    public String getRootFolder()
    {
        return _rootFolder;
    }

    public void setRootFolder(String rootFolder)
    {
        _rootFolder = rootFolder;
    }

    public String getFolderTypes()
    {
        return _folderTypes;
    }

    public void setFolderTypes(String folderTypes)
    {
        _folderTypes = folderTypes;
    }

    public boolean isIncludeAllDescendants()
    {
        return _includeAllDescendants;
    }

    public void setIncludeAllDescendants(boolean includeAllDescendants)
    {
        _includeAllDescendants = includeAllDescendants;
    }
}
