/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.ldk.buttons;

import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.Permission;

/**
 * User: bimber
 * Date: 7/14/13
 * Time: 4:05 PM
 */
public class ShowEditUIButton extends SimpleButtonConfigFactory
{
    protected String _schemaName;
    protected String _queryName;
    protected Class<? extends Permission>[] _perms;

    public ShowEditUIButton(Module owner, String schemaName, String queryName, Class<? extends Permission>... perms)
    {
        this(owner, schemaName, queryName, "Edit Records", perms);
    }

    public ShowEditUIButton(Module owner, String schemaName, String queryName, String label, Class<? extends Permission>... perms)
    {
        super(owner, label, "");

        _schemaName = schemaName;
        _queryName = queryName;
        _perms = perms;
    }

    public boolean isAvailable(TableInfo ti)
    {
        if (!super.isAvailable(ti))
            return false;

        for (Class<? extends Permission> perm : _perms)
        {
            if (!ti.getUserSchema().getContainer().hasPermission(ti.getUserSchema().getUser(), perm))
                return false;
        }

        return true;
    }

    @Override
    protected String getJsHandler(TableInfo ti)
    {
        return "window.location = LABKEY.ActionURL.buildURL('ldk', 'updateQuery', null, {schemaName: '" + _schemaName + "', 'query.queryName': '" + _queryName + "'});";
    }
}
