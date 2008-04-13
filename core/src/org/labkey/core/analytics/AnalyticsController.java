package org.labkey.core.analytics;

import org.apache.struts.action.ActionMapping;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewForm;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

public class AnalyticsController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(AnalyticsController.class);
    public AnalyticsController() {
        setActionResolver(_actionResolver);
    }

    static public class SettingsForm extends ViewForm
    {
        public AnalyticsServiceImpl.TrackingStatus ff_trackingStatus;
        public String ff_accountId;

        public void setFf_accountId(String ff_accountId)
        {
            this.ff_accountId = ff_accountId;
        }

        public void setFf_trackingStatus(String ff_trackingStatus)
        {
            this.ff_trackingStatus = AnalyticsServiceImpl.TrackingStatus.valueOf(ff_trackingStatus);
        }

        public void reset(ActionMapping actionMapping, HttpServletRequest request)
        {
            super.reset(actionMapping, request);
            ff_trackingStatus = AnalyticsServiceImpl.get().getTrackingStatus();
            ff_accountId = AnalyticsServiceImpl.get().getAccountId();
        }
    }

    @RequiresSiteAdmin
    public class BeginAction extends FormViewAction<SettingsForm>
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }

        public void validateCommand(SettingsForm target, Errors errors)
        {
        }

        public ModelAndView getView(SettingsForm settingsForm, boolean reshow, BindException errors) throws Exception
        {
            return FormPage.getView(AnalyticsController.class, settingsForm, "analyticsSettings.jsp");
        }

        public boolean handlePost(SettingsForm settingsForm, BindException errors) throws Exception
        {
            AnalyticsServiceImpl.get().setSettings(settingsForm.ff_trackingStatus, settingsForm.ff_accountId);
            return true;
        }

        public ActionURL getSuccessURL(SettingsForm settingsForm)
        {
            return new ActionURL("admin", "showAdmin", ContainerManager.getRoot());
        }
    }
}
