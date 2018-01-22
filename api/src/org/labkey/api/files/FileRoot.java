/*
 * Copyright (c) 2010-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.files;

import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;

import java.io.Serializable;

/**
 * User: klum
 * Date: Jan 29, 2010
 * Time: 11:26:31 AM
 */
public class FileRoot implements Serializable
{
    private Integer _rowId;
    private String _path;
    private String _type = FileContentService.FILES_LINK;
    private String _container;
    private String _properties;
    private boolean _enabled = true;
    private boolean _useDefault;

    public FileRoot(Container c)
    {
        _container = c.getId();
    }

    public FileRoot(){}

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public String getPath()
    {
        return _path;
    }

    public void setPath(String path)
    {
        _path = path;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getProperties()
    {
        return _properties;
    }

    public void setProperties(String properties)
    {
        _properties = properties;
    }

    public boolean isEnabled()
    {
        return _enabled;
    }

    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }

    public boolean isUseDefault()
    {
        return _useDefault;
    }

    public void setUseDefault(boolean useDefault)
    {
        _useDefault = useDefault;
    }

    public boolean isNew()
    {
        return _rowId == null;
    }
}
