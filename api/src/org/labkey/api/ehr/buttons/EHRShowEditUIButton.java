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
package org.labkey.api.ehr.buttons;

import org.labkey.api.ldk.buttons.ShowEditUIButton;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.template.ClientDependency;

/**
 * Similar to ShowEditUIButton, except it will send the user to /ehr/updateQuery, instead of /ldk/updateQuery.
 * This allows tables to use EHR's metadata customization scheme.
 */
public class EHRShowEditUIButton extends ShowEditUIButton
{
    public EHRShowEditUIButton(Module owner, String schemaName, String queryName, Class<? extends Permission>... perms)
    {
        super(owner, schemaName, queryName, perms);
        setClientDependencies(ClientDependency.fromModuleName("ehr"));
    }

    public EHRShowEditUIButton(Module owner, String schemaName, String queryName, String label, Class<? extends Permission>... perms)
    {
        super(owner, schemaName, queryName, label, perms);
        setClientDependencies(ClientDependency.fromModuleName("ehr"));
    }

    @Override
    protected String getHandlerName()
    {
        return "EHR.Utils.editUIButtonHandler";
    }
}
