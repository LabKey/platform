package org.labkey.query;

import org.labkey.api.data.Container;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

/**
 * User: klum
 * Date: 9/10/13
 */
public class DataViewsWebPartFactory extends BaseWebPartFactory
{
    public DataViewsWebPartFactory()
    {
        super("Data Views", WebPartFactory.LOCATION_BODY, true, false); // is editable
        addLegacyNames("Dataset Browse", "Dataset Browse (Experimental)");
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        JspView<Portal.WebPart> view = new JspView<>("/org/labkey/query/reports/view/dataViews.jsp", webPart);
        view.setTitle("Data Views");
        view.setFrame(WebPartView.FrameType.PORTAL);
        Container c = portalCtx.getContainer();
        NavTree menu = new NavTree();

        if (portalCtx.hasPermission(InsertPermission.class))
        {
            NavTree reportMenu = new NavTree("Add Report");
            reportMenu.addChild("From File", PageFlowUtil.urlProvider(ReportUrls.class).urlAttachmentReport(portalCtx.getContainer(), portalCtx.getActionURL()));
            reportMenu.addChild("From Link", PageFlowUtil.urlProvider(ReportUrls.class).urlLinkReport(portalCtx.getContainer(), portalCtx.getActionURL()));
            menu.addChild(reportMenu);
        }

        if (portalCtx.hasPermission(AdminPermission.class))
        {
            NavTree customize = new NavTree("");

            String customizeScript = "customizeDataViews(" + webPart.getRowId() + ", \'" + webPart.getPageId() + "\', " + webPart.getIndex() + ");";

            customize.setScript(customizeScript);
            view.setCustomize(customize);

            String editScript = "editDataViews(" + webPart.getRowId() + ");";
            NavTree edit = new NavTree("Edit", "javascript:" + editScript, portalCtx.getContextPath() + "/_images/partedit.png");
            view.addCustomMenu(edit);

            if (StudyService.get().getStudy(c) != null)
                menu.addChild("Manage Datasets", PageFlowUtil.urlProvider(StudyUrls.class).getManageDatasetsURL(c));
            menu.addChild("Manage Queries", PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(c));
        }

        if(portalCtx.hasPermission(ReadPermission.class) && !portalCtx.getUser().isGuest())
        {
            ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(c);

            if (StudyService.get().getStudy(c) != null)
                url = PageFlowUtil.urlProvider(StudyUrls.class).getManageReports(c);
            menu.addChild("Manage Views", url);
        }

        view.setNavMenu(menu);

        return view;
    }
}
