/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.laboratory.button;

import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.template.ClientDependency;

/**
 * User: bimber
 * Date: 9/8/13
 * Time: 10:18 AM
 */
public class ChangeAssayResultStatusBtn extends SimpleButtonConfigFactory
{
    public ChangeAssayResultStatusBtn(Module owner)
    {
        this(owner, "Change Result Status");
    }

    public ChangeAssayResultStatusBtn(Module owner, String label)
    {
        super(owner, label, "Laboratory.window.ChangeAssayResultStatusWindow.buttonHandler(dataRegionName);");
        setClientDependencies(ClientDependency.fromModuleName("laboratory"), ClientDependency.fromPath("laboratory/window/ChangeAssayResultStatusWindow.js"));
    }

    public boolean isAvailable(TableInfo ti)
    {
        return super.isAvailable(ti) && ti.hasPermission(ti.getUserSchema().getUser(), UpdatePermission.class);
    }
}
