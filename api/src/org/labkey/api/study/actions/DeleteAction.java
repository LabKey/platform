/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:23:24 PM
*/
@RequiresPermissionClass(ReadPermission.class) //will check explicity in code below
public class DeleteAction extends BaseAssayAction<ProtocolIdForm>
{
    public ModelAndView getView(ProtocolIdForm protocolIdForm, BindException errors) throws Exception
    {
        ExpProtocol protocol = protocolIdForm.getProtocol();
        
        if(!allowDelete(protocol))
            HttpView.throwUnauthorized("You do not have sufficient permissions to delete this assay design.");

        protocolIdForm.getProtocol().delete(getViewContext().getUser());
        HttpView.throwRedirect(PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getViewContext().getContainer()));
        return null;
    }

    private boolean allowDelete(ExpProtocol protocol)
    {
        ViewContext ctx = getViewContext();
        User user = ctx.getUser();

        //user must have both design assay AND delete permission, as this will delete both the design and updloaded data
        return protocol.getContainer().getPolicy().hasPermissions(user, DesignAssayPermission.class, DeletePermission.class);
    }
}
