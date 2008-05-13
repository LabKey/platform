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

import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ACL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.NavTree;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayService;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

import java.util.Map;
import java.util.HashMap;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:22:50 PM
*/
@RequiresPermission(ACL.PERM_INSERT)
public class DesignerAction extends BaseAssayAction<DesignerAction.DesignerForm>
{
    public static class DesignerForm extends ProtocolIdForm
    {
        public boolean isCopy()
        {
            return _copy;
        }

        public void setCopy(boolean copy)
        {
            _copy = copy;
        }

        private boolean _copy;

    }
    private DesignerForm _form;

    public ModelAndView getView(DesignerForm form, BindException errors) throws Exception
    {
        _form = form;
        Integer rowId = form.getRowId();
        Map<String, String> properties = new HashMap<String, String>();
        if (rowId != null)
        {
            properties.put("protocolId", "" + rowId);
            properties.put("copy", Boolean.toString(form.isCopy()));
        }
        properties.put("providerName", form.getProviderName());

        // hack for 4404 : Lookup picker performance is terrible when there are many containers
        ContainerManager.getAllChildren(ContainerManager.getRoot());
       
        return new GWTView("org.labkey.assay.designer.AssayDesigner", properties);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        if (!_form.isCopy() && _form.getRowId() != null && _form.getProtocol() != null)
        {
            ExpProtocol protocol = _form.getProtocol(!_form.isCopy());
            result.addChild(protocol.getName(), AssayService.get().getAssayRunsURL(getContainer(), protocol));
        }
        result.addChild("Assay Designer", getUrl("designer"));
        return result;
    }
}
