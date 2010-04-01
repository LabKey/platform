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

import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.AppBar;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:22:50 PM
*/
@RequiresPermissionClass(DesignAssayPermission.class)
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

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
        {
            _returnURL = returnURL;
        }
    }

    private DesignerForm _form;
    private ExpProtocol _protocol;

    public ModelAndView getView(DesignerForm form, BindException errors) throws Exception
    {
        _form = form;
        Integer rowId = form.getRowId();
        Map<String, String> properties = new HashMap<String, String>();
        if (rowId != null)
        {
            _protocol = form.getProtocol(!form.isCopy());
            properties.put("protocolId", "" + rowId);
            properties.put("copy", Boolean.toString(form.isCopy()));
        }
        properties.put("providerName", form.getProviderName());
        if (form.getReturnURL() != null)
        {
            properties.put("returnURL", form.getReturnURL());
        }

        // hack for 4404 : Lookup picker performance is terrible when there are many containers
        ContainerManager.getAllChildren(ContainerManager.getRoot());

        return createGWTView(properties);
    }

    protected ModelAndView createGWTView(Map<String, String> properties)
    {
        return new GWTView("gwt.AssayDesigner", properties);
    }

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

    public AppBar getAppBar()
    {
        return getAppBar(_form.isCopy() ? null : _protocol);
    }
}
