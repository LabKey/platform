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

import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.assay.AssayManager;
import org.springframework.validation.BindException;

@RequiresPermission(DeletePermission.class)
public class DeleteProtocolAction extends MutatingApiAction<GWTProtocol>
{
    @Override
    public Object execute(GWTProtocol protocol, BindException errors)
    {
        ExpProtocol expProtocol = AssayManager.get().findExpProtocol(protocol, getContainer());
        if (expProtocol == null)
            throw new NotFoundException("Protocol " + protocol.getName() + " not found");

        // user must have both design assay AND delete permission, as this will delete both the design and uploaded data
        if (!expProtocol.getContainer().hasPermission(getUser(), DesignAssayPermission.class))
            throw new UnauthorizedException("You do not have sufficient permissions to delete this assay design.");

        expProtocol.delete(getUser());
        return success("Deleted assay protocol '" + protocol.getName() + "'");
    }
}
