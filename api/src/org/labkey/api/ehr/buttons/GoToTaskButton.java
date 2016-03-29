/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.api.ehr.buttons;

import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.ehr.security.EHRInProgressInsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.DatasetTable;

import java.util.Set;

/**
 * User: bimber
 * Date: 8/2/13
 * Time: 12:26 PM
 */
public class GoToTaskButton extends SimpleButtonConfigFactory
{
    private String _formType;

    public GoToTaskButton(Module owner, String formType)
    {
        super(owner, "Bulk Enter Data", "window.location = LABKEY.ActionURL.buildURL('ehr', 'dataEntryForm', null, {formtype: '" + formType +"'})");
        _formType = formType;
    }

    public boolean isAvailable(TableInfo ti)
    {
        if (!super.isAvailable(ti))
            return false;

        if (ti instanceof DatasetTable)
        {
            Set<Class<? extends Permission>> perms = ((DatasetTable) ti).getDataset().getPermissions(ti.getUserSchema().getUser());
            return perms.contains(EHRInProgressInsertPermission.class);
        }

        return ti.hasPermission(ti.getUserSchema().getUser(), EHRInProgressInsertPermission.class);
    }
}

