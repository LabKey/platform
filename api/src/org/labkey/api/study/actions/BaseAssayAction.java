/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.ACL;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.util.PageFlowUtil;

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

    public static ExpProtocol getProtocol(ProtocolIdForm form)
    {
        return getProtocol(form, true);
    }

    public static ExpProtocol getProtocol(ProtocolIdForm form, boolean validateContainer)
    {
        if (form.getRowId() == null)
            HttpView.throwNotFound("Assay ID not specified.");
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getRowId().intValue());
        if (protocol == null || (validateContainer && !protocol.getContainer().equals(form.getContainer()) &&
                !protocol.getContainer().equals(form.getContainer().getProject())))
        {
            HttpView.throwNotFound("Assay " + form.getRowId() + " does not exist.");
        }
        if (protocol != null)
        {
            // even if we don't validate that the protocol is from the current or project container,
            // but we still make sure that the current user can read from the protocol container:
            if (!protocol.getContainer().hasPermission(form.getViewContext().getUser(), ACL.PERM_READ))
                HttpView.throwNotFound();
        }
        return protocol;
    }

    protected Container getContainer()
    {
        return getViewContext().getContainer();
    }

    protected DataRegion createDataRegion(TableInfo baseTable, String lsidCol, PropertyDescriptor[] propertyDescriptors, Map<String, String> columnNameToPropertyName)
    {
        DataRegion rgn = new DataRegion();
        rgn.setTable(baseTable);
        for (PropertyDescriptor pd : propertyDescriptors)
        {
            ColumnInfo info = pd.createColumnInfo(baseTable, lsidCol, getViewContext().getUser());
            rgn.addColumn(info);
            if (columnNameToPropertyName != null)
                columnNameToPropertyName.put(info.getName(), pd.getName());
        }
        return rgn;
    }

    protected AssayProvider getProvider(ProtocolIdForm form)
    {
        return AssayService.get().getProvider(getProtocol(form));
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return root.addChild("Assay List", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getContainer()));
    }

    protected List<Integer> getCheckboxIds(boolean clear)
    {
        Set<String> idStrings =  DataRegionSelection.getSelected(getViewContext(), null, true, clear);
        List<Integer> ids = new ArrayList<Integer>();
        if (idStrings == null)
            return ids;
        for (String rowIdStr : idStrings)
            ids.add(Integer.parseInt(rowIdStr));
        return ids;
    }
}
