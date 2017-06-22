/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.query;

import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: 9/10/13
 */
public class DataViewsWebPartFactory extends BaseWebPartFactory
{
    public static final String NAME = "Data Views";

    public DataViewsWebPartFactory()
    {
        super(NAME, true, false); // is editable
        addLegacyNames("Dataset Browse", "Dataset Browse (Experimental)");
    }

    @Override
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        JspView<Portal.WebPart> view = new JspView<>("/org/labkey/query/reports/view/dataViews.jsp", webPart);
        view.setTitle("Data Views");
        view.setFrame(WebPartView.FrameType.PORTAL);
        Container c = portalCtx.getContainer();
        NavTree menu = new NavTree();
        Map<String, String> properties = webPart.getPropertyMap();

        // the manageView flag refers to manage views
        boolean manageView = false;
        if (properties.containsKey("manageView"))
            manageView = BooleanUtils.toBoolean(properties.get("manageView"));

        if (portalCtx.hasPermission(ReadPermission.class) && !portalCtx.getUser().isGuest())
        {
            List<ReportService.DesignerInfo> reportDesigners = new ArrayList<>();
            List<ReportService.DesignerInfo> chartDesigners = new ArrayList<>();
            for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
            {
                for (ReportService.DesignerInfo designerInfo : provider.getDesignerInfo(portalCtx))
                {
                    if (designerInfo.getType() != ReportService.DesignerType.VISUALIZATION)
                        reportDesigners.add(designerInfo);
                    else
                        chartDesigners.add(designerInfo);
                }
            }

            Comparator<ReportService.DesignerInfo> comparator = Comparator.comparing(ReportService.DesignerInfo::getLabel);
            reportDesigners.sort(comparator);
            chartDesigners.sort(comparator);

            NavTree reportMenu = new NavTree("Add Report");
            for (ReportService.DesignerInfo info : reportDesigners)
                reportMenu.addChild(getItem(info, null));
            if (!reportDesigners.isEmpty())
                menu.addChild(reportMenu);

            // "Add Chart" options
            for (ReportService.DesignerInfo info : chartDesigners)
                menu.addChild(getItem(info, "Add "));
        }


        // We display the edit button for everyone with insert (Author, Editor, Admin). Other components are admin-only.
        if (portalCtx.hasPermission(InsertPermission.class))
        {
            boolean isAdmin = portalCtx.hasPermission(AdminPermission.class);

            if (!manageView)
            {
                if (isAdmin)
                {
                    NavTree customize = new NavTree("");
                    String customizeScript = "customizeDataViews(" + webPart.getRowId() + ", \'" + webPart.getPageId() + "\', " + webPart.getIndex() + ");";
                    customize.setScript(customizeScript);
                    view.setCustomize(customize);
                }

                String editScript = "editDataViews(" + webPart.getRowId() + ");";
                NavTree edit = new NavTree("Edit", "javascript:" + editScript, null, "fa fa-pencil");
                view.addCustomMenu(edit);
            }

            if (isAdmin)
            {
                StudyService svc = StudyService.get();
                StudyUrls urls = PageFlowUtil.urlProvider(StudyUrls.class);
                if (svc != null && svc.getStudy(c) != null && urls != null)
                    menu.addChild("Manage Datasets", urls.getManageDatasetsURL(c));
                menu.addChild("Manage Queries", PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(c));

                if (manageView)
                {
                    NavTree manageViews = new NavTree("Manage Categories");
                    manageViews.setScript("manageCategories(" + webPart.getRowId() + ");");
                    menu.addChild(manageViews);

                    NavTree reorderReportsAndCharts = new NavTree("Reorder Reports And Charts");
                    reorderReportsAndCharts.setScript("reorderReports(" + webPart.getRowId() + ");");
                    menu.addChild(reorderReportsAndCharts);

    /*
                    String deleteScript = "deleteDataViews(" + webPart.getRowId() + ");";
                    NavTree deleteViews = new NavTree("Delete Selected");
                    deleteViews.setScript(deleteScript);
                    deleteViews.setDescription("Hold cntl to select more than one record");
                    menu.addChild(deleteViews);
    */
                }
            }
        }

        if (manageView && portalCtx.hasPermission(ReadPermission.class) && !portalCtx.getUser().isGuest())
        {
            String deleteScript = "deleteDataViews(" + webPart.getRowId() + ");";
            NavTree deleteViews = new NavTree("Delete Selected");
            deleteViews.setScript(deleteScript);
            deleteViews.setDescription("Hold cntl to select more than one record");
            menu.addChild(deleteViews);
        }

        if (!manageView && portalCtx.hasPermission(ReadPermission.class) && !portalCtx.getUser().isGuest())
        {
            ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(c);
            menu.addChild("Manage Views", url);
        }

        if (portalCtx.hasPermission(ReadPermission.class) && !portalCtx.getUser().isGuest())
        {
            ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlManageNotifications(c);
            menu.addChild("Manage Notifications", url);
        }

        view.setNavMenu(menu);

        return view;
    }

    private NavTree getItem(ReportService.DesignerInfo info, String prefix)
    {
        String label = (prefix != null ? prefix : "") + info.getLabel();
        URLHelper iconURL = info.getIconURL();
        String iconCls = info.getIconCls();

        NavTree item = new NavTree(label, info.getDesignerURL().getLocalURIString(), null != iconURL ? iconURL.getLocalURIString() : null, iconCls);
        item.setId(info.getId());
        item.setDisabled(info.isDisabled());

        return item;
    }
}
