/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.experiment;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.DesignDataClassPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.api.DataClassDomainKind;
import org.labkey.experiment.controllers.exp.ExperimentController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Set;

/**
 * User: kevink
 * Date: 9/21/15
 */
public class DataClassWebPart extends QueryView
{
    private String _errorMessage;
    private final boolean _narrow;

    public DataClassWebPart(boolean narrow, ViewContext context, @Nullable Portal.WebPart webPart)
    {
        super(new ExpSchema(context.getUser(), context.getContainer()));
        _narrow = narrow;

        String dataRegionName = null;
        if (webPart != null)
            dataRegionName = webPart.getPropertyMap().get(QueryParam.dataRegionName.name());
        if (dataRegionName == null)
            dataRegionName = webPart != null ? "qwp" + webPart.getIndex() : "DataClass";

        setSettings(createQuerySettings(context, dataRegionName));
        setTitle("Data Classes");
        setTitleHref(PageFlowUtil.urlProvider(ExperimentUrls.class).getDataClassListURL(context.getContainer()));
        setShowDetailsColumn(false);

        // hide all of the default insert/update/delete buttons for this grid,
        // those will be added separately in the populateButtonBar method below
        setShowUpdateColumn(false);
        setShowDeleteButton(false);
        setShowInsertNewButton(false);
        setShowImportDataButton(false);

        if (_narrow)
        {
            setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            setShowSurroundingBorder(false);
        }
        else
        {
            setShowExportButtons(false);
        }
        setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentPlusProjectAndShared);
    }

    private QuerySettings createQuerySettings(ViewContext portalCtx, String dataRegionName)
    {
        UserSchema schema = getSchema();
        QuerySettings settings = schema.getSettings(portalCtx, dataRegionName, ExpSchema.TableType.DataClasses.toString());
        if (_narrow)
        {
            settings.setViewName("NameOnly");
        }
        if (settings.getContainerFilterName() == null)
        {
            settings.setContainerFilterName(ContainerFilter.CurrentPlusProjectAndShared.class.getSimpleName());
        }
        settings.getBaseSort().insertSortColumn(FieldKey.fromParts("Name"));
        return settings;
    }

    @Override
    protected boolean canInsert()
    {
        TableInfo table = getTable();
        return table != null && table.hasPermission(getUser(), InsertPermission.class);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        ActionURL deleteURL = new ActionURL(ExperimentController.DeleteDataClassAction.class, getContainer());
        deleteURL.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().toString());

        ActionButton deleteButton = new ActionButton(ExperimentController.DeleteDataClassAction.class, "Delete", ActionButton.Action.GET);
        deleteButton.setDisplayPermission(DesignDataClassPermission.class);
        deleteButton.setIconCls("trash");
        deleteButton.setURL(deleteURL);
        deleteButton.setActionType(ActionButton.Action.POST);
        deleteButton.setRequiresSelection(true);
        bar.add(deleteButton);

        ActionURL urlInsert = new ActionURL(ExperimentController.EditDataClassAction.class, getContainer());
        urlInsert.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().toString());
        Set<String> templates = DomainTemplateGroup.getTemplatesForDomainKind(getContainer(), DataClassDomainKind.NAME);
        if (templates.size() > 0)
        {
            MenuButton createMenuButton = new MenuButton("New Data Class");
            createMenuButton.setDisplayPermission(DesignDataClassPermission.class);

            NavTree insertItem = createMenuButton.addMenuItem("Design Manually", urlInsert);
            insertItem.setId("NewDataClass:fromDesigner");

            ActionURL urlTemplate = new ActionURL(ExperimentController.CreateDataClassFromTemplateAction.class, getContainer());
            urlTemplate.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().toString());
            NavTree templateItem = createMenuButton.addMenuItem("Create from Template", urlTemplate);
            templateItem.setId("NewDataClass:fromTemplate");

            bar.add(createMenuButton);
        }
        else
        {
            ActionButton createNewButton = new ActionButton(urlInsert, "New Data Class", ActionButton.Action.LINK);
            createNewButton.setDisplayPermission(DesignDataClassPermission.class);
            createNewButton.setURL(urlInsert);
            bar.add(createNewButton);
        }
    }

    @Override
    protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        PrintWriter out = response.getWriter();
        if (_errorMessage != null)
        {
            out.write("<font class=\"labkey-error\">" + PageFlowUtil.filter(_errorMessage) + "</font><br>");
        }
        super.renderView(model, request, response);
    }

    public void setErrorMessage(String errorMessage)
    {
        _errorMessage = errorMessage;
    }

}
