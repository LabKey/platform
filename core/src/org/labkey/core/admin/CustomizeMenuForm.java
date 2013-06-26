/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.core.admin;


/**
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
    private String _url;
    private boolean _choiceListQuery;   // choice between List/Query (true) or Folders (false)
    private String _rootFolder;
    private String _folderTypes;
    private boolean _includeAllDescendants;
    private boolean _currentProjectOnly;
    private int _webPartIndex;
    private String _pageId;

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

    public String getUrl()
    {
        return _url;
    }

    public void setUrl(String url)
    {
        _url = url;
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

    public int getWebPartIndex()
    {
        return _webPartIndex;
    }

    public void setWebPartIndex(int webPartIndex)
    {
        _webPartIndex = webPartIndex;
    }

    public String getPageId()
    {
        return _pageId;
    }

    public void setPageId(String pageId)
    {
        _pageId = pageId;
    }

    public boolean isCurrentProjectOnly()
    {
        return _currentProjectOnly;
    }

    public void setCurrentProjectOnly(boolean currentProjectOnly)
    {
        _currentProjectOnly = currentProjectOnly;
    }
}
