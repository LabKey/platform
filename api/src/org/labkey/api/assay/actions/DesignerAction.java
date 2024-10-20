/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.assay.actions;

import jakarta.servlet.http.HttpServletRequest;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;


/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:22:50 PM
*/
@RequiresPermission(DesignAssayPermission.class)
public class DesignerAction extends BaseAssayAction<DesignerAction.DesignerForm>
{
    public static class DesignerForm extends ProtocolIdForm
    {
        private boolean _copy;

        public boolean isCopy()
        {
            return _copy;
        }

        public void setCopy(boolean copy)
        {
            _copy = copy;
        }
    }

    private DesignerForm _form;
    private ExpProtocol _protocol;

    @Override
    public ModelAndView getView(DesignerForm form, BindException errors)
    {
        // Issue 45315: Move selected file to a URL parameter to be used in AssayDesigner.tsx
        HttpServletRequest request = getViewContext().getRequest();
        String path = request.getParameter("path");
        String[] files = request.getParameterValues("file");
        if (request.getParameter("isRedirect") == null && path != null && files != null && files.length == 1)
        {
            SimpleMetricsService.get().increment("Assay", "AssayDesigner", "InferDesignFromFileAndImport");
            ActionURL url = getViewContext().cloneActionURL();
            url.addParameter("file", files[0]);
            url.addParameter("isRedirect", true);
            return HttpView.redirect(url);
        }

        _form = form;
        try
        {
            _protocol = form.getProtocol(!form.isCopy());
        }
        catch (NotFoundException ignored)
        {
            /* User is probably creating a new assay design */
        }

        // hack for 4404 : Lookup picker performance is terrible when there are many containers
        ContainerManager.getAllChildren(ContainerManager.getRoot());

        VBox result = new VBox();
        if (_protocol != null && !form.isCopy())
        {
            result.addView(new AssayHeaderView(_protocol, form.getProvider(), false, false, ContainerFilter.current(getViewContext().getContainer())));
        }

        result.addView(ModuleHtmlView.get(ModuleLoader.getInstance().getModule("core"), ModuleHtmlView.getGeneratedViewPath("assayDesigner")));
        return result;
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        setHelpTopic("defineAssaySchema");

        super.addNavTrail(root);
        if (!_form.isCopy() && _protocol != null)
        {
            root.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        }
        root.addChild(_form.getProviderName() + " Assay Designer", new ActionURL(DesignerAction.class, getContainer()));
    }
}
