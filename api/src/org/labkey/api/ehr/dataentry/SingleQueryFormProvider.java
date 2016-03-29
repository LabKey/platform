/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.ehr.dataentry;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;

/**
 * User: bimber
 * Date: 2/1/14
 * Time: 11:19 AM
 */
public class SingleQueryFormProvider
{
    private Module _owner;
    private String _schemaName;
    private String _queryName;
    private SingleQueryFormSection _section;

    public SingleQueryFormProvider(Module owner, String schemaName, String queryName, SingleQueryFormSection section)
    {
        _owner = owner;
        _schemaName = schemaName;
        _queryName = queryName;
        _section = section;
    }

    public boolean isAvailable(Container c, TableInfo ti)
    {
        if (!c.getActiveModules().contains(_owner))
            return false;

        return (_schemaName.equals(ti.getPublicSchemaName()) && _queryName.equals(ti.getName()));
    }

    public SingleQueryFormSection getSection()
    {
        return _section;
    }
}
