/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager.ContainerListener;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.assay.AssayService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;

import java.beans.PropertyChangeEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AssayContainerListener implements ContainerListener
{
    @Override
    public void containerCreated(Container c, User user)
    {
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        //
        // plate service
        //
        PlateManager.get().deleteAllPlateData(c);

        // Changing the container tree can change what assays are in scope
        AssayManager.get().clearProtocolCache();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
    }

    @NotNull
    @Override
    public Collection<String> canMove(Container c, Container newParent, User user)
    {
        List<ExpProtocol> assayProtocolsInUse = new ArrayList<>();
        // Build a list of all assay designs that are in use
        for (ExpProtocol protocol : ExperimentService.get().getExpProtocolsUsedByRuns(c, new ContainerFilter.CurrentAndSubfolders(user)))
        {
            // Not all protocols are assay designs, so filter them based on looking up their provider
            if (AssayService.get().getProvider(protocol) != null)
            {
                assayProtocolsInUse.add(protocol);
            }
        }

        // Figure out the assay designs that will be in scope in the new parent
        Set<ExpProtocol> assayProtocolsToBeInScope = new HashSet<>(AssayService.get().getAssayProtocols(newParent));
        addAssayProtocols(c, assayProtocolsToBeInScope);

        // Remove all of the designs that will still be in scope
        assayProtocolsInUse.removeAll(assayProtocolsToBeInScope);

        // If there's anything that will no longer be in scope, block the move
        if (!assayProtocolsInUse.isEmpty())
        {
            StringBuilder sb = new StringBuilder(c.getPath() + " or its children contain assay runs that reference assay designs that would no longer be in scope if moved to " + newParent.getPath() + ". The assay designs are: ");
            String separator = "";
            for (ExpProtocol expProtocol : assayProtocolsInUse)
            {
                sb.append(separator);
                sb.append(expProtocol.getName());
                separator = ", ";
            }
            return Collections.singletonList(sb.toString());
        }
        return Collections.emptyList();
    }

    /** Recursively builds up the set of assay designs that are in defined in the given container or its children */
    private void addAssayProtocols(Container c, Set<ExpProtocol> protocols)
    {
        for (ExpProtocol protocol : ExperimentService.get().getExpProtocols(c))
        {
            if (AssayService.get().getProvider(protocol) != null)
            {
                protocols.add(protocol);
            }
        }
        for (Container child : c.getChildren())
        {
            addAssayProtocols(child, protocols);
        }
    }
}