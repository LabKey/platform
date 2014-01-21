/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.query;

import org.apache.commons.io.IOUtils;
import org.labkey.api.resource.Resource;
import org.labkey.api.resource.ResourceRef;
import org.labkey.query.persist.QueryDef;
import org.labkey.api.data.Container;

import java.io.IOException;
import java.io.InputStream;

/*
* User: Dave
* Date: Jan 16, 2009
* Time: 11:44:38 AM
*/

/**
 * Bean that represents a query definition that is defined in file(s) in a module.
 * This is separate from ModuleCustomQueryDefinition so that it can be cached and
 * used for multiple containers.
 */
public class ModuleQueryDef extends ResourceRef
{
    public static final String FILE_EXTENSION = ".sql";
    public static final String META_FILE_EXTENSION = ".query.xml";

    private String _name;
    private String _schemaName;
    private String _moduleName;
    private String _sql;
    private ModuleQueryMetadataDef _metadataRef;

    public ModuleQueryDef(Resource r, String schemaName, String moduleName)
    {
        super(r);

        _schemaName = schemaName;
        _name = getNameFromFile();
        _moduleName = moduleName;

        //load the sql from the sqlFile
        try (InputStream is = r.getInputStream())
        {
            if (is != null)
                _sql = IOUtils.toString(is);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        //meta-data file is optional
        Resource parent = r.parent();
        if (parent != null)
        {
            Resource metadataResource = parent.find(_name + META_FILE_EXTENSION);
            if (metadataResource != null)
            {
                _metadataRef = new ModuleQueryMetadataDef(metadataResource);
                addDependency(_metadataRef);

                if (_metadataRef.getModuleName() != null)
                {
                    _moduleName = _metadataRef.getModuleName();
                }
            }
        }
    }

    protected String getNameFromFile()
    {
        String name = getResource().getName();
        return name.substring(0, name.length() - FILE_EXTENSION.length());
    }

    public String getName()
    {
        return _metadataRef == null ? _name : _metadataRef.getName();
    }

    public String getModuleName()
    {
        return _moduleName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public boolean isHidden()
    {
        return _metadataRef == null ? false : _metadataRef.isHidden();
    }

    public String getSql()
    {
        return _sql;
    }

    public String getQueryMetaData()
    {
        return _metadataRef == null ? null : _metadataRef.getQueryMetaData();
    }

    public String getDescription()
    {
        return _metadataRef == null ? null : _metadataRef.getDescription();
    }

    public double getSchemaVersion()
    {
        return _metadataRef == null ? 0 : _metadataRef.getSchemaVersion();
    }

    public QueryDef toQueryDef(Container container)
    {
        QueryDef ret;
        if (_metadataRef != null)
        {
            ret = _metadataRef.toQueryDef(container);
        }
        else
        {
            ret = new QueryDef();
            ret.setContainer(container.getId());
        }
        ret.setName(getName());
        ret.setSchema(getSchemaName());
        ret.setSql(getSql());

        return ret;
    }
}
