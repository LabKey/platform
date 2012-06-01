package org.labkey.visualization.report;

import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.GenericChartReport;
import org.labkey.visualization.VisualizationController;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 31, 2012
 */
public class GenericChartReportImpl extends GenericChartReport
{
    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        VisualizationController.GenericReportForm form = new VisualizationController.GenericReportForm();

        form.setReportId(getReportId());
        form.setComponentId("generic-report-panel-" + UniqueID.getRequestScopedUID(context.getRequest()));

        JspView view = new JspView<VisualizationController.GenericReportForm>("/org/labkey/visualization/views/genericChartWizard.jsp", form);

        view.setTitle(getDescriptor().getReportName());
        view.setFrame(WebPartView.FrameType.PORTAL);

        if (canEdit(context.getUser(), context.getContainer()))
        {
            String script = String.format("javascript:customizeGenericReport('%s');", form.getComponentId());
            NavTree edit = new NavTree("Edit", script, AppProps.getInstance().getContextPath() + "/_images/partedit.png");
            view.addCustomMenu(edit);

            NavTree menu = new NavTree();
            menu.addChild("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(context.getContainer()));
            view.setNavMenu(menu);
        }

        return view;
    }
}
