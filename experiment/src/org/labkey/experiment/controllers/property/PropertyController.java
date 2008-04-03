package org.labkey.experiment.controllers.property;

import org.labkey.api.view.*;
import org.labkey.api.exp.property.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

import java.util.*;

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
    }
}
