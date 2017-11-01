/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.*;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Set;

/**
 * User: jeckels
* Date: Dec 30, 2008
*/
@RequiresPermission(ReadPermission.class)
public class ShowSelectedRunsAction extends RedirectAction<ShowSelectedRunsAction.ShowSelectedForm>
{
    public static class ShowSelectedForm extends ProtocolIdForm
    {
        private String containerFilterName;

        public String getContainerFilterName()
        {
            return containerFilterName;
        }

        public void setContainerFilterName(String containerFilterName)
        {
            this.containerFilterName = containerFilterName;
        }
    }


    public ActionURL getSuccessURL(ShowSelectedForm form)
    {
        Set<String> selection = DataRegionSelection.getSelected(getViewContext(), true);
        int[] selectedIds = PageFlowUtil.toInts(selection);

        ContainerFilter containerFilter = null;
        if (form.getContainerFilterName() != null)
            containerFilter = ContainerFilter.getContainerFilterByName(form.getContainerFilterName(), getUser());

        ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), form.getProtocol(), containerFilter, selectedIds);
        if (form.getContainerFilterName() != null)
            url.addParameter("containerFilterName", form.getContainerFilterName());
        return url;
    }

    public boolean doAction(ShowSelectedForm form, BindException errors) throws Exception
    {
        return true;
    }
}