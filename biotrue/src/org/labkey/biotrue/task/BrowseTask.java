/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.biotrue.task;

import org.labkey.biotrue.soapmodel.Browse_response;
import org.labkey.biotrue.soapmodel.Entityinfo;
import org.labkey.biotrue.datamodel.Task;
import org.labkey.biotrue.objectmodel.BtEntity;
import org.apache.log4j.Logger;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Arrays;

public class BrowseTask extends BtTask
{
    static final private Logger _log = Logger.getLogger(BrowseTask.class);
    static Set<String> directoryEnts = new LinkedHashSet(Arrays.asList(new String[] { "lab", "project", "run" }));
    public BrowseTask(Task task)
    {
        super(task);
    }

    public void doRun() throws Exception
    {
        BtEntity entity = getEntity();
        Browse_response entityResponse = loginBrowse(entity);
        if (entity != null)
        {
            entity.ensurePhysicalDirectory();
        }
        for (Entityinfo ei : entityResponse.getData().getAllContent())
        {
            BtEntity child = BtEntity.ensureChild(getServer(), entity, ei.getId(), ei.getType(), ei.getName());
            boolean browse = directoryEnts.contains(ei.getType());
            if (browse)
            {
                newTask(child, Operation.view);
            }
            else
            {
                if (!child.hasPhysicalName())
                {
                    newTask(child, Operation.download);
                }
            }
        }
    }
}
