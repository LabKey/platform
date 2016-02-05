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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;

/**
 * User: kevink
 * Date: 9/21/15
 */
public class DataClassWebPart extends QueryView
{
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
    public boolean showImportDataButton()
    {
        return false;
    }

    @Override
    public ActionButton createDeleteButton()
    {
        // Use default delete button, but without showing the confirmation text -- DeleteDataClassAction will show a confirmation page.
        ActionButton button = super.createDeleteButton();
        if (button != null)
        {
            button.setRequiresSelection(true);
        }
        return button;
    }
}
