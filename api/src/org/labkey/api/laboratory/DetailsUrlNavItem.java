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
package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.ldk.AbstractNavItem;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/8/12
 * Time: 10:09 PM
 */
public class DetailsUrlNavItem extends AbstractNavItem
{
    protected DataProvider _provider;
    protected DetailsURL _labelUrl;
    protected String _label;
    protected String _category;

    public DetailsUrlNavItem(DataProvider provider, DetailsURL labelUrl, String label, String category)
    {
        _provider = provider;
        _labelUrl = labelUrl;
        _label = label;
        _category = category;
    }

    public String getName()
    {
        return _label;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getCategory()
    {
        return _category;
    }

    public String getRendererName()
    {
        return "defaultRenderer";
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return true;
    }

    public ActionURL getLabelUrl(Container c, User u)
    {
        return _labelUrl == null ? null : _labelUrl.copy(c).getActionURL();
    }

    public DataProvider getDataProvider()
    {
        return _provider;
    }

    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);
        ret.put("url", getUrlObject(getLabelUrl(c, u)));
        ret.put("browseUrl", getUrlObject(getLabelUrl(c, u)));

        return ret;
    }
}
