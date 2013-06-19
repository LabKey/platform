/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.core.workbook;

/**
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
