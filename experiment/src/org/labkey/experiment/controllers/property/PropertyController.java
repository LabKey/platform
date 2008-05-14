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

package org.labkey.experiment.controllers.property;

import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.*;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropertyController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(PropertyController.class);

    public PropertyController()
    {
        setActionResolver(_actionResolver);
    }
    
    @RequiresPermission(ACL.PERM_NONE)
    public class EditDomainAction extends SimpleViewAction<DomainForm>
    {
        private Domain _domain;

        public ModelAndView getView(DomainForm form, BindException errors) throws Exception
        {
            _domain = form.getDomain();
            if (!_domain.getDomainKind().canEditDefinition(getUser(), _domain))
            {
                HttpView.throwUnauthorized();
            }
            Map<String, String> props = new HashMap<String, String>();
            ActionURL returnURL = _domain.getDomainKind().urlEditDefinition(_domain);
            props.put("typeURI", _domain.getTypeURI());
            props.put("returnURL", returnURL.toString());
            props.put("allowFileLinkProperties", String.valueOf(form.getAllowFileLinkProperties()));
            props.put("allowAttachmentProperties", String.valueOf(form.getAllowAttachmentProperties()));

            // HttpView info = new JspView<ListDefinition>("editType.jsp", def);
            return new GWTView("org.labkey.experiment.property.Designer", props);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Experiment", ExperimentController.ExperimentUrlsImpl.get().getBeginURL(getContainer()));
            return root.addChild("Edit Fields in " + _domain.getLabel());
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class PropertyServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new PropertyServiceImpl(getViewContext());
        }
    }


    class PropertyServiceImpl extends DomainEditorServiceBase implements org.labkey.experiment.property.client.PropertyService
    {
        public PropertyServiceImpl(ViewContext context)
        {
            super(context);
        }

        public List updateDomainDescriptor(GWTDomain orig, GWTDomain update)
        {
            try
            {
                return super.updateDomainDescriptor(orig, update);
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }
    }
}
