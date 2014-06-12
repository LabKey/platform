/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.util.ArrayList;
import java.util.Collections;
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
        super(NAME, WebPartFactory.LOCATION_BODY, true, false); // is editable
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
        Map<String, String> properties = webPart.getPropertyMap();

        // the manageView flag refers to manage views
        boolean manageView = false;
        if (properties.containsKey("manageView"))
            manageView = BooleanUtils.toBoolean(properties.get("manageView"));

        if (portalCtx.hasPermission(ReadPermission.class) && !portalCtx.getUser().isGuest())
        {
            NavTree reportMenu = new NavTree("Add Report");

            List<ReportService.DesignerInfo> designers = new ArrayList<>();
            for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
                designers.addAll(provider.getDesignerInfo(portalCtx));

            Collections.sort(designers, new Comparator<ReportService.DesignerInfo>()
            {
                @Override
                public int compare(ReportService.DesignerInfo o1, ReportService.DesignerInfo o2)
                {
                    return o1.getLabel().compareTo(o2.getLabel());
                }
            });

            for (ReportService.DesignerInfo info : designers)
            {
                NavTree item = new NavTree(info.getLabel(), info.getDesignerURL().getLocalURIString(), info.getIconPath());

                item.setId(info.getId());
                item.setDisabled(info.isDisabled());

                reportMenu.addChild(item);
            }
            menu.addChild(reportMenu);
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
                NavTree edit = new NavTree("Edit", "javascript:" + editScript, portalCtx.getContextPath() + "/_images/partedit.png");
                view.addCustomMenu(edit);
            }

            if (isAdmin)
            {
                if (StudyService.get().getStudy(c) != null)
                    menu.addChild("Manage Datasets", PageFlowUtil.urlProvider(StudyUrls.class).getManageDatasetsURL(c));
                menu.addChild("Manage Queries", PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(c));

                if (manageView)
                {
                    NavTree manageViews = new NavTree("Manage Categories");
                    manageViews.setScript("manageCategories(" + webPart.getRowId() + ");");
                    menu.addChild(manageViews);

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
}
