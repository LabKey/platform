/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.products;

import org.labkey.api.view.ActionURL;

public class MenuItem
{
    private String _label; // the display text for the UI
    private Integer _id; // generally, the row id of the item being linked to
    private String _key; // to be used in routing to the item within the application
    private String _url; // the URL on the server side
    private Integer _orderNum; // ordinal for producing the primary sort order of the items
    private Boolean _requiresLogin = false; // indicates if link should be shown if not logged in.
    private String _productId = null; // indicates the product/application this link should direct to.  Can (should?) be null if the current application is to be used.

    public MenuItem(String label, String url, Integer id, String key, Integer orderNum, String productId)
    {
        _label = label;
        _id = id;
        _key = key;
        _url = url;
        _orderNum = orderNum == null ? -1 : orderNum;
        _productId = productId;
    }

    public MenuItem(String label, ActionURL url, Integer id, Integer orderNum, String productId)
    {
        this(label, url == null ? null : url.toString(), id, String.valueOf(id), orderNum, productId);
    }

    public MenuItem(String label, ActionURL url, Integer id, String productId)
    {
        this(label, url, id, null, productId);
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public Integer getId()
    {
        return _id;
    }

    public void setId(Integer id)
    {
        _id = id;
    }

    public String getKey()
    {
        return _key;
    }

    public void setKey(String key)
    {
        _key = key;
    }

    public String getUrl()
    {
        return _url;
    }

    public void setUrl(ActionURL url)
    {
        _url = url.toString();
    }

    public void setUrl(String url)
    {
        _url = url;
    }

    public String getProductId()
    {
        return _productId;
    }

    public void setProductId(String productId)
    {
        _productId = productId;
    }

    public Integer getOrderNum()
    {
        return _orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        _orderNum = orderNum;
    }

    public Boolean getRequiresLogin()
    {
        return _requiresLogin;
    }

    public void setRequiresLogin(Boolean requiresLogin)
    {
        _requiresLogin = requiresLogin;
    }
}
