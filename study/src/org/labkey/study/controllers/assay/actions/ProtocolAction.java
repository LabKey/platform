/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.study.controllers.assay.actions;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.assay.AssayServiceImpl;
import org.springframework.validation.BindException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * API action to get, create, update, or delete an assay protocol.
 *
 * User: kevink
 * Date: 3/11/16
 */
@Marshal(Marshaller.Jackson)
@RequiresPermission(DesignAssayPermission.class)
public class ProtocolAction extends ApiAction<GWTProtocol>
{
    public ProtocolAction()
    {
        super();
        setSupportedMethods(new String[] { "GET", "POST", "DELETE" });
    }

    @Override
    public Object execute(GWTProtocol protocol, BindException errors) throws Exception
    {
        if (isPost())
        {
            // UPDATE
            boolean isNew = protocol.getProtocolId() == null || protocol.getProtocolId() == 0;
            AssayServiceImpl svc = new AssayServiceImpl(getViewContext());
            GWTProtocol updated = svc.saveChanges(protocol, true);
            return success((isNew  ? "Created" : "Updated") + " assay protocol '" + updated.getName() + "'");
        }
        else if (isDelete())
        {
            // DELETE
            ExpProtocol expProtocol = null;
            if (protocol.getProtocolId() != null)
                expProtocol = ExperimentService.get().getExpProtocol(protocol.getProtocolId());
            if (expProtocol == null && protocol.getName() != null)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol.getProviderName());
                if (provider == null)
                    throw new NotFoundException("Assay provider '" + protocol.getProviderName() + "' not found");

                List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(getContainer(), provider);
                if (protocols.isEmpty())
                    throw new NotFoundException("Assay protocol '" + protocol.getName() + "' not found");

                protocols = protocols.stream().filter(p -> protocol.getName().equals(p.getName())).collect(Collectors.toList());
                if (protocols.isEmpty())
                    throw new NotFoundException("Assay protocol '" + protocol.getName() + "' not found");

                if (protocols.size() > 1)
                    throw new NotFoundException("More than one assay protocol named '" + protocol.getName() + "' was found.");

                expProtocol = protocols.get(0);
            }
            if (expProtocol == null)
                throw new NotFoundException();

            // user must have both design assay AND delete permission, as this will delete both the design and uploaded data
            //noinspection unchecked
            if (!expProtocol.getContainer().getPolicy().hasPermissions(getUser(), DesignAssayPermission.class, DeletePermission.class))
                throw new UnauthorizedException("You do not have sufficient permissions to delete this assay design.");

            expProtocol.delete(getUser());
            return success("Deleted assay protocol '" + protocol.getName() + "'");
        }
        else
        {
            // GET
            if (protocol.getProtocolId() != null)
            {
                // get existing protocol
                AssayServiceImpl svc = new AssayServiceImpl(getViewContext());
                GWTProtocol ret = svc.getAssayDefinition(protocol.getProtocolId(), false);
                return success("Assay protocol " + protocol.getName() + "'", ret);
            }
            else if (protocol.getProviderName() != null)
            {
                // get the assay template
                AssayServiceImpl svc = new AssayServiceImpl(getViewContext());
                GWTProtocol ret = svc.getAssayTemplate(protocol.getProviderName());
                return success("Generated assay template for provider '" + protocol.getProviderName() + "'", ret);
            }
            else
            {
                throw new ApiUsageException("Assay protocolId or providerName required");
            }
        }
    }

}
