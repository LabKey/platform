/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.labkey.api.laboratory.AbstractQueryNavItem;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.LaboratoryUrls;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 8:46 AM
 */
public class AssayNavItem extends AbstractQueryNavItem
{
    AssayDataProvider _ad;
    AssayProvider _ap;
    ExpProtocol _protocol;

    public AssayNavItem(AssayDataProvider ad, ExpProtocol protocol)
    {
        _ad = ad;
        _ap = ad.getAssayProvider();
        _protocol = protocol;
    }

    public String getName()
    {
        return _protocol.getName();
    }

    public String getLabel()
    {
        return _protocol.getName();
    }

    @Override
    public String getPropertyManagerKey()
    {
        return getDataProvider().getKey() + "||" + getCategory() + "||" + getName() + "||" + _protocol.getRowId();
    }

    public AssayDataProvider getDataProvider()
    {
        return _ad;
    }

    public String getCategory()
    {
        return LaboratoryService.NavItemCategory.data.name();
    }

    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        return true;
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        //by default, we only enable assays if they were created in the current folder
        //if the DataProvider registered an associated module, also only turn on by default if that module is enabled
        Container toCompare = c.isWorkbookOrTab() ? c.getParent() : c;
        return _protocol.getContainer().equals(toCompare) && getDataProvider().isModuleEnabled(c);
    }

    public ActionURL getImportUrl(Container c, User u)
    {
        return _ap.getImportURL(c, _protocol);
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(c, AssaySchema.NAME, _protocol.getName() + " Data");
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(c, _protocol);
    }

    public ActionURL getAssayRunTemplateUrl(Container c, User u)
    {
        if (_ad != null && _ad.supportsRunTemplates())
        {
            return PageFlowUtil.urlProvider(LaboratoryUrls.class).getAssayRunTemplateUrl(c, _protocol);
        }
        return null;
    }

    public ActionURL getViewAssayRunTemplateUrl(Container c, User u)
    {
        if (_ad != null && _ad.supportsRunTemplates())
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
        json.put("supportsRunTemplates", _ad.supportsRunTemplates());
        json.put("assayRunTemplateUrl", getUrlObject(getAssayRunTemplateUrl(c, u)));
        json.put("viewRunTemplateUrl", getUrlObject(getViewAssayRunTemplateUrl(c, u)));

        return json;
    }
}
