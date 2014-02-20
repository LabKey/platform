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
package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.ldk.AbstractNavItem;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 4/4/13
 * Time: 9:39 AM
 */
abstract public class AbstractUrlNavItem extends AbstractNavItem
{
    protected DataProvider _provider;
    protected String _labelText = null;
    protected String _itemText = null;
    protected String _category;
    protected DetailsURL _detailsURL = null;
    protected String _staticURL = null;

    public AbstractUrlNavItem(DataProvider provider, String labelText, String itemText, DetailsURL detailsURL, String category)
    {
        _provider = provider;
        _labelText = labelText;
        _itemText = itemText;
        _detailsURL = detailsURL;
        _category = category;
    }

    public AbstractUrlNavItem(DataProvider provider, String labelText, String itemText, String staticURL, String category)
    {
        _provider = provider;
        _labelText = labelText;
        _itemText = itemText;
        _staticURL = staticURL;
        _category = category;
    }

    @Override
    public String getName()
    {
        return _labelText == null ? _itemText : _labelText;
    }

    @Override
    public String getLabel()
    {
        return getName();
    }

    @Override
    public String getCategory()
    {
        return _category;
    }

    @Override
    public String getRendererName()
    {
        return _itemText == null ? "linkWithoutLabel" : "linkWithLabel";
    }

    @Override
    public boolean getDefaultVisibility(Container c, User u)
    {
        return true;
    }

    protected ActionURL getActionURL(Container c, User u)
    {
        if (_detailsURL != null)
            return _detailsURL.copy(getTargetContainer(c)).getActionURL();

        return null;
    }

    @Override
    public DataProvider getDataProvider()
    {
        return _provider;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);
        ret.put("itemText", _itemText);
        ret.put("label", getLabel());

        ActionURL url = getActionURL(c, u);
        if (url != null)
            ret.put("urlConfig", getUrlObject(url));
        else
            ret.put("url", _staticURL);

        return ret;
    }

    public void setTargetContainer(Container c)
    {
        _targetContainer = c;
    }
}
