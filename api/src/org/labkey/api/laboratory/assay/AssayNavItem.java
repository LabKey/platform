/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.laboratory.assay;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.laboratory.AbstractImportingNavItem;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.LaboratoryUrls;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 8:46 AM
 */
public class AssayNavItem extends AbstractImportingNavItem
{
    private AssayDataProvider _provider;
    private AssayProvider _ap;
    private ExpProtocol _protocol;

    public AssayNavItem(AssayDataProvider provider, ExpProtocol protocol)
    {
        super(provider, protocol.getName(), protocol.getName(), LaboratoryService.NavItemCategory.data, provider.getName());
        _ap = provider.getAssayProvider();
        _provider = provider;
        _protocol = protocol;
    }

    @Override
    public String getPropertyManagerKey()
    {
        return getDataProvider().getKey() + "||" + getReportCategory() + "||" + getName() + "||" + _protocol.getRowId();
    }

    @Override
    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        return true;
    }

    @Override
    public boolean getDefaultVisibility(Container c, User u)
    {
        //by default, we only enable assays if they were created in the current folder
        //if the DataProvider registered an associated module, also only turn on by default if that module is enabled
        Container toCompare = c.isWorkbook() ? c.getParent() : c;
        return _protocol.getContainer().equals(toCompare) && _provider.isModuleEnabled(c);
    }

    @Override
    public ActionURL getImportUrl(Container c, User u)
    {
        return _ap.getImportURL(c, _protocol);
    }

    @Override
    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(c, AssaySchema.NAME, _protocol.getName() + " Data");
    }

    @Override
    public ActionURL getBrowseUrl(Container c, User u)
    {
        ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(c, _protocol);
        return appendDefaultView(c, url, "Data");
    }

    private ActionURL getAssayRunTemplateUrl(Container c, User u)
    {
        if (_provider != null && _provider.supportsRunTemplates())
        {
            return PageFlowUtil.urlProvider(LaboratoryUrls.class).getAssayRunTemplateUrl(c, _protocol);
        }
        return null;
    }

    private ActionURL getViewAssayRunTemplateUrl(Container c, User u)
    {
        if (_provider != null && _provider.supportsRunTemplates())
        {
            return PageFlowUtil.urlProvider(LaboratoryUrls.class).getViewAssayRunTemplateUrl(c, u, _protocol);
        }
        return null;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);
        json.put("rowId", _protocol.getRowId());
        json.put("supportsRunTemplates", _provider.supportsRunTemplates());
        json.put("assayRunTemplateUrl", getUrlObject(getAssayRunTemplateUrl(c, u)));
        json.put("viewRunTemplateUrl", getUrlObject(getViewAssayRunTemplateUrl(c, u)));

        return json;
    }
}
