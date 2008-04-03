package org.labkey.core.admin;

import org.labkey.api.action.BeehivePortingActionResolver;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.view.*;
import org.labkey.api.exp.api.AdminUrls;
import org.labkey.api.data.Container;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 27, 2008
 */
public class AdminControllerSpring extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new BeehivePortingActionResolver(AdminController.class, AdminControllerSpring.class);

    public AdminControllerSpring() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class ShowAuditLogAction extends QueryViewAction<ShowAuditLogForm, QueryView>
    {
        public ShowAuditLogAction()
        {
            super(ShowAuditLogForm.class);
        }

        protected ModelAndView getHtmlView(ShowAuditLogForm form, BindException errors) throws Exception
        {
            if (!getViewContext().getUser().isAdministrator())
                HttpView.throwUnauthorized();
            VBox view = new VBox();

            String selected = form.getView();
            if (selected == null)
                selected = AuditLogService.get().getAuditViewFactories()[0].getEventType();

            JspView jspView = new JspView("/org/labkey/core/admin/auditLog.jsp");
            ((ModelAndView)jspView).addObject("currentView", selected);

            view.addView(jspView);
            view.addView(createInitializedQueryView(form, errors, false, null));

            return view;
        }

        protected QueryView createQueryView(ShowAuditLogForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            String selected = form.getView();
            if (selected == null)
                selected = AuditLogService.get().getAuditViewFactories()[0].getEventType();

            AuditLogService.AuditViewFactory factory = AuditLogService.get().getAuditViewFactory(selected);
            if (factory != null)
                return factory.createDefaultQueryView(getViewContext());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Audit Log");
        }
    }

    public static class ShowAuditLogForm extends QueryViewAction.QueryExportForm
    {
        private String _view;

        public String getView()
        {
            return _view;
        }

        public void setView(String view)
        {
            _view = view;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public static class ShowModuleErrors extends SimpleViewAction
    {

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Module Errors");
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            JspView jspView = new JspView("/org/labkey/core/admin/moduleErrors.jsp");
            return jspView;
        }
    }

    public static class AdminUrlsImpl implements AdminUrls
    {

        public ActionURL getModuleErrorsUrl(Container container)
        {
            return new ActionURL(ShowModuleErrors.class, container);
        }
    }

}
