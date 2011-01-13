/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.query.metadata.client;

import org.labkey.api.gwt.client.model.GWTDomain;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class GWTTableInfo extends GWTDomain<GWTColumnInfo>
{
    private boolean _userDefinedQuery;
    /** If metadata is not stored in the current container, the folder path where it is stored */
    private String _definitionFolder;

    public boolean isEditable(GWTColumnInfo field)
    {
        return true;
    }

    public boolean isUserDefinedQuery()
    {
        return _userDefinedQuery;
    }

    public void setUserDefinedQuery(boolean userDefinedQuery)
    {
        _userDefinedQuery = userDefinedQuery;
    }

    public String getDefinitionFolder()
    {
        return _definitionFolder;
    }

    public void setDefinitionFolder(String definitionFolder)
    {
        _definitionFolder = definitionFolder;
    }
}
