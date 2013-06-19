/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.study.controllers.reports;

/**
 * User: Karl Lum
 * Date: Mar 1, 2007
 */
public class StudyManageReportsBean
{
    private boolean _isAdminView;
    private boolean _isWideView;
    private String _queryName;
    private String _schemaName;
    private String _baseFilterItems;

    public boolean getAdminView(){return _isAdminView;}
    public void setAdminView(boolean admin){_isAdminView = admin;}

    public boolean isWideView()
    {
        return _isWideView;
    }

    public void setWideView(boolean wideView)
    {
        _isWideView = wideView;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getBaseFilterItems()
    {
        return _baseFilterItems;
    }

    public void setBaseFilterItems(String baseFilterItems)
    {
        _baseFilterItems = baseFilterItems;
    }
}
