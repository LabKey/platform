/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.bigiron.sas;

import org.labkey.api.query.UserSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * User: adam
 * Date: Jan 20, 2009
 * Time: 10:40:37 AM
 */
public class SasSchema extends UserSchema
{
    private String _name;
    private String _path;

    // Cache schemas based on path (so different lib definitions resolve to same schema) -- should be datasource + path
    // or datasource + path + lib (different meta data xml per lib definition?) 
    private static final Map<String, DbSchema> _schemaMap = new HashMap<String, DbSchema>();

    public SasSchema(String name, String path, User user, Container c)
    {
        super(name, user, c, null);

        synchronized (_schemaMap)
        {
            if (_schemaMap.containsKey(path))
            {
                _dbSchema = _schemaMap.get(path);
            }
            else
            {
                try
                {
                    _dbSchema = DbSchema.createFromMetaData(name);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }

                _schemaMap.put(path, _dbSchema);
            }
        }

        _name = name;
        _path = path;
    }

    protected TableInfo createTable(String name, String alias)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Set<String> getTableNames()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
