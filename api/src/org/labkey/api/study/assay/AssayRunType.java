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

package org.labkey.api.study.assay;

import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.ACL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: brittp
* Date: Oct 10, 2007
* Time: 2:24:42 PM
*/
public class AssayRunType extends ExperimentRunType
{
    public static final String SCHEMA_NAME = "assay";
    private final ExpProtocol _protocol;
    private final Container _container;

    public AssayRunType(ExpProtocol protocol, Container c)
    {
        super(protocol.getName(), SCHEMA_NAME, getRunListTableName(protocol));
        _protocol = protocol;
        _container = c;
    }

    public static String getRunListTableName(ExpProtocol protocol)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        return provider.getRunListTableName(protocol);
    }

    @Override
    public void populateButtonBar(ViewContext context, ButtonBar bar, DataView view, ContainerFilter filter)
    {
        Set<Container> containers;
        if (filter != null)
        {
            Collection<String> containerIds = filter.getIds(context.getContainer(), context.getUser());
            Set<Container> filterContainers = new HashSet<Container>();
            for (String containerId : containerIds)
            {
                filterContainers.add(ContainerManager.getForId(containerId));
            }

            ExpRun[] expRuns = _protocol.getExpRuns();
            containers = new HashSet<Container>();
            for (ExpRun run : expRuns)
                containers.add(run.getContainer());

            containers.retainAll(filterContainers); // Use only the intersection of runs and containers from the filter
        }
        else
        {
            containers = Collections.singleton(context.getContainer());
        }
        if (containers.isEmpty())
            return;

        AssayProvider provider = AssayService.get().getProvider(_protocol);
        if (containers.size() == 1)
        {
            Container container = containers.iterator().next();

            if (provider.allowUpload(context.getUser(), container, _protocol) &&
                    context.hasPermission(ACL.PERM_INSERT) &&
                    container.hasPermission(context.getUser(), ACL.PERM_INSERT))
            {
                ActionButton uploadButton = new ActionButton("Import Runs", AssayService.get().getUploadWizardURL(container, _protocol));
                bar.add(uploadButton);
            }

            return;
        }

        MenuButton uploadButton = new MenuButton("Import Runs");
        for (Container container : containers)
        {
            if (provider.allowUpload(context.getUser(), container, _protocol) &&
                    context.hasPermission(ACL.PERM_INSERT) &&
                    container.hasPermission(context.getUser(), ACL.PERM_INSERT))
            {
                ActionURL url = AssayService.get().getUploadWizardURL(container, _protocol);
                uploadButton.addMenuItem(container.getName(), url);
            }
        }
        bar.add(uploadButton);
    }

    public Priority getPriority(ExpProtocol protocol)
    {
        if (_protocol.getLSID().equals(protocol.getLSID()))
        {
            return Priority.HIGH;
        }
        return null;
    }
}
