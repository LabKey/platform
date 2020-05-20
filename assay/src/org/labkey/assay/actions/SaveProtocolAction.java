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
package org.labkey.assay.actions;

import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.assay.AssayManager;
import org.labkey.assay.AssayDomainManager;
import org.springframework.validation.BindException;

@Marshal(Marshaller.Jackson)
@RequiresPermission(ReadPermission.class)
public class SaveProtocolAction extends MutatingApiAction<GWTProtocol>
{
    @Override
    public Object execute(GWTProtocol protocol, BindException errors) throws Exception
    {
        boolean isNew = protocol.getProtocolId() == null || protocol.getProtocolId() == 0;
        if (isNew)
        {
            if (protocol.getName() == null)
                throw new IllegalArgumentException("Name is required to create an assay design.");
            if (!getContainer().hasPermission(getUser(), DesignAssayPermission.class))
                throw new UnauthorizedException("You do not have sufficient permissions to create this assay design.");
        }
        else
        {
            ExpProtocol expProtocol = AssayManager.get().findExpProtocol(protocol, getContainer());
            if (expProtocol == null)
                throw new NotFoundException();

            // user must have design assay permission
            if (!expProtocol.getContainer().hasPermission(getUser(), DesignAssayPermission.class))
                throw new UnauthorizedException("You do not have sufficient permissions to update this assay design.");
        }

        AssayDomainManager manager = new AssayDomainManager(getViewContext());
        GWTProtocol updated = manager.saveChanges(protocol, true);
        return success((isNew  ? "Created" : "Updated") + " assay protocol '" + updated.getName() + "'", updated);
    }
}
