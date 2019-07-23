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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.List;

public class UserInfoMenuSection extends MenuSection
{
    public static final String NAME = "Your Items";
    public static final String DOCS_KEY = "docs";

    private ProductMenuProvider _provider;

    public UserInfoMenuSection(ViewContext context, ProductMenuProvider provider)
    {
        super(context, NAME, "user", null);
        _provider = provider;
    }

    @Override
    public ActionURL getUrl()
    {
        return null;
    }

    @Override
    @NotNull
    public List<MenuItem> getAllItems()
    {
        List<MenuItem> items = new ArrayList<>();
        UserUrls urlProvider =  PageFlowUtil.urlProvider(UserUrls.class);
        if (urlProvider != null)
        {
            MenuItem profileItem = new MenuItem("Profile", urlProvider.getUserDetailsURL(getContainer(), getUser().getUserId(), null), getUser().getUserId(), 0);
            profileItem.setRequiresLogin(true);
            profileItem.setKey("profile");
            items.add(profileItem);
        }
        String docUrl = _provider.getDocumentationUrl();
        if (docUrl != null)
        {
            MenuItem docsItem = new MenuItem("Documentation", docUrl, null, 1);
            docsItem.setKey(DOCS_KEY);
            items.add(docsItem);
        }
        items.addAll(_provider.getUserMenuItems());

        return items;
    }

}
