/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.assay.actions;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 26, 2007
 * Time: 7:01:48 PM
 */
public abstract class BaseAssayAction<T extends ProtocolIdForm> extends SimpleViewAction<T>
{
    public BaseAssayAction()
    {
        super();
    }

    public BaseAssayAction(Class<T> formClass)
    {
        super(formClass);
    }

    public ActionURL getSummaryLink(ExpProtocol protocol)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), protocol);
    }

    protected DataRegion createDataRegionForInsert(TableInfo baseTable, String lsidCol, List<? extends DomainProperty> domainProperties, Map<String, String> columnNameToPropertyName)
    {
        DataRegion rgn = new DataRegion();
        rgn.setTable(baseTable);
        for (DomainProperty dp : domainProperties)
        {
            if (dp.isShownInInsertView())
            {
                ColumnInfo info = dp.getPropertyDescriptor().createColumnInfo(baseTable, lsidCol, getUser(), getContainer());
                rgn.addColumn(info);
                if (columnNameToPropertyName != null)
                    columnNameToPropertyName.put(info.getName(), dp.getName());
            }
        }
        return rgn;
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        root.addChild("Assay List", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getContainer()));
        return root;
    }

    /**
     * Use the POST that was sent as the new selection in the session,
     * over-writing anything currently stored there.
     */
    public static List<Integer> getCheckboxIds(ViewContext context)
    {
        Set<String> idStrings = DataRegionSelection.getSelected(context, null, true, false);

        DataRegionSelection.clearAll(context, null);
        DataRegionSelection.setSelected(context, null, idStrings, true);

        List<Integer> ids = new ArrayList<>();
        for (String rowIdStr : idStrings)
        {
            try
            {
                ids.add(Integer.parseInt(rowIdStr));
            }
            catch (NumberFormatException e)
            {
                throw new NotFoundException("Unable to parse selected RowId value: " + rowIdStr);
            }
        }
        return ids;
    }
}
