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

package org.labkey.api.study.actions;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.query.RunDataQueryView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:30:05 PM
*/
@RequiresPermission(ACL.PERM_READ)
public class AssayDataAction extends BaseAssayAction<ProtocolIdForm>
{
    private ExpProtocol _protocol;

    public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
    {
        _protocol = getProtocol(form);
        AssayHeaderView headerView = new AssayHeaderView(_protocol, AssayService.get().getProvider(_protocol), false);

        ViewContext context = getViewContext();

        VBox fullView = new VBox();
        fullView.addView(headerView);
        AssayProvider provider = AssayService.get().getProvider(_protocol);

        if (!provider.allowUpload(context.getUser(), context.getContainer(), _protocol))
            fullView.addView(provider.getDisallowedUploadMessageView(context.getUser(), context.getContainer(), _protocol));

        RunDataQueryView dataView = provider.createRunDataView(context, _protocol);
        
        fullView.addView(dataView);
        return fullView;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), AssayService.get().getAssayRunsURL(getContainer(), _protocol));
        result.addChild(_protocol.getName() + " Data");
        return result;
    }

}
