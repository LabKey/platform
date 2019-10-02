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

import org.labkey.api.assay.AssayQCService;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

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
        private String _returnURL;

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
        _form = form;
        try
        {
            _protocol = form.getProtocol(!form.isCopy());
        }
        catch (NotFoundException ignored)
        {
            /* User is probably creating a new assay design */
        }

        AssayProvider provider = AssayService.get().getProvider(form.getProviderName());
        if (provider == null)
            provider = form.getProvider();

        // hack for 4404 : Lookup picker performance is terrible when there are many containers
        ContainerManager.getAllChildren(ContainerManager.getRoot());

        VBox result = new VBox();
        if (_protocol != null && !form.isCopy())
        {
            result.addView(new AssayHeaderView(_protocol, form.getProvider(), false, false, ContainerFilter.CURRENT));
        }

        // use new UX Assay Designer view if experimental flag is turned on
        if (ExperimentService.get().useUXDomainDesigner())
        {
            result.addView(ModuleHtmlView.get(ModuleLoader.getInstance().getModule("assay"), "assayDesigner"));
        }
        else
        {
            Map<String, String> properties = new HashMap<>();
            if (_protocol != null)
            {
                properties.put("protocolId", "" + _protocol.getRowId());
                properties.put("copy", Boolean.toString(form.isCopy()));
            }

            properties.put("providerName", provider.getName());
            properties.put("osName", System.getProperty("os.name").toLowerCase());
            properties.put("supportsEditableResults", Boolean.toString(provider.supportsEditableResults()));
            properties.put("supportsBackgroundUpload", Boolean.toString(provider.supportsBackgroundUpload()));

            // if the provider supports QC and if there is a valid QC service registered
            if (AssayQCService.getProvider().supportsQC())
                properties.put("supportsQC", Boolean.toString(provider.supportsQC()));

            if (form.getReturnURLHelper() != null)
                properties.put(ActionURL.Param.returnUrl.name(), form.getReturnURLHelper().getLocalURIString());

            result.addView(createGWTView(properties));
        }

        setHelpTopic(new HelpTopic("defineAssaySchema"));
        return result;
    }

    protected ModelAndView createGWTView(Map<String, String> properties)
    {
        return AssayService.get().createAssayDesignerView(properties);
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        if (!_form.isCopy() && _protocol != null)
        {
            result.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        }
        result.addChild(_form.getProviderName() + " Assay Designer", new ActionURL(DesignerAction.class, getContainer()));
        return result;
    }
}
