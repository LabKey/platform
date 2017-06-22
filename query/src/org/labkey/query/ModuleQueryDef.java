/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.query.persist.QueryDef;

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
public class ModuleQueryDef
{
    public static final String FILE_EXTENSION = ".sql";
    public static final String META_FILE_EXTENSION = ".query.xml";

    private final Module _module;
    private final Resource _resource;
    private final String _name;

    private String _sql;
    private ModuleQueryMetadataDef _metadataDef;

    public ModuleQueryDef(Module module, Resource r)
    {
        _module = module;
        _resource = r;
        _name = getNameFromFile();

        //load the sql from the sqlFile
        try (InputStream is = r.getInputStream())
        {
            if (is != null)
                _sql = IOUtils.toString(is, StringUtilsLabKey.DEFAULT_CHARSET);
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
                _metadataDef = new ModuleQueryMetadataDef(metadataResource);
      }
    }

    protected String getNameFromFile()
    {
        String name = _resource.getName();
        return name.substring(0, name.length() - FILE_EXTENSION.length());
    }

    public String getName()
    {
        return _metadataDef == null ? _name : _metadataDef.getName();
    }

    public boolean isHidden()
    {
        return _metadataDef == null ? false : _metadataDef.isHidden();
    }

    public String getSql()
    {
        return _sql;
    }

    public String getQueryMetaData()
    {
        return _metadataDef == null ? null : _metadataDef.getQueryMetaData();
    }

    public String getDescription()
    {
        return _metadataDef == null ? null : _metadataDef.getDescription();
    }

    public double getSchemaVersion()
    {
        return _metadataDef == null ? 0 : _metadataDef.getSchemaVersion();
    }

    public QueryDef toQueryDef(Container container, SchemaKey schemaKey)
    {
        QueryDef ret;
        if (_metadataDef != null)
        {
            ret = _metadataDef.toQueryDef(container);
        }
        else
        {
            ret = new QueryDef();
            ret.setContainer(container.getId());
        }
        ret.setName(getName());
        ret.setSchemaPath(schemaKey);
        ret.setSql(getSql());

        return ret;
    }

    public Path getPath()
    {
        return _resource.getPath();
    }

    public Module getModule()
    {
        return _module;
    }
}
