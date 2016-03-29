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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.security.EHRScheduledInsertPermission;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.template.ClientDependency;

/**
 * User: bimber
 * Date: 8/2/13
 * Time: 12:26 PM
 */
public class CreateTaskFromIdsButton extends SimpleButtonConfigFactory
{
    private String[] _datasets;

    public CreateTaskFromIdsButton(Module owner, String btnLabel, String taskLabel, String formType, String[] datasets)
    {
        super(owner, btnLabel, "EHR.window.CreateTaskFromIdsWindow.createTaskFromIdsHandler(dataRegionName, '" + formType + "', '" + taskLabel + "', ['" + StringUtils.join(datasets, "', '") + "'])");
        setClientDependencies(ClientDependency.fromPath("ehr/window/CreateTaskFromIdsWindow.js"));
        _datasets = datasets;
    }

    public boolean isAvailable(TableInfo ti)
    {
        if (!super.isAvailable(ti))
            return false;

        Container c = ti.getUserSchema().getContainer();
        User u = ti.getUserSchema().getUser();

        StudyService.Service svc = StudyService.get();
        if (svc == null)
            return false;

        Study s = svc.getStudy(c);
        if (s == null)
            return false;

        for (String dataset : _datasets)
        {
            Dataset ds = getDataset(s, dataset);
            if (ds == null)
                return false;

            if (!ds.getPermissions(u).contains(EHRScheduledInsertPermission.class))
                return false;
        }

        return true;
    }

    private Dataset getDataset(Study s, String name)
    {
        for (Dataset ds : s.getDatasets())
        {
            if (ds.getName().equalsIgnoreCase(name) || ds.getLabel().equalsIgnoreCase(name))
                return ds;
        }

        return null;
    }
}

