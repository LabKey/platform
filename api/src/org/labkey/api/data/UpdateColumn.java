/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

package org.labkey.api.data;

import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;

import java.util.Set;

public class UpdateColumn extends UrlColumn
{
    private static final FieldKey CONTAINER_FIELD_KEY = FieldKey.fromParts("Container");
    private static final FieldKey FOLDER_FIELD_KEY = FieldKey.fromParts("Folder");

    public UpdateColumn(StringExpression urlExpression)
    {
        super(urlExpression, "edit");
        setName("Update");
        setGridHeaderClass("");
        setWidth("0");
        addDisplayClass("labkey-update");
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        String result = super.renderURL(ctx);
        Object containerValue = ctx.get(FOLDER_FIELD_KEY);
        if (containerValue == null)
        {
            containerValue = ctx.get(CONTAINER_FIELD_KEY);
        }
        if (containerValue != null)
        {
            if (containerValue instanceof String)
            {
                containerValue = ContainerManager.getForId(containerValue.toString());
            }
            if (containerValue instanceof Container)
            {
                try
                {
                    ActionURL url = new ActionURL(result);
                    url.setContainer((Container)containerValue);
                    result = url.toString();
                }
                catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.add(CONTAINER_FIELD_KEY);
        keys.add(FOLDER_FIELD_KEY);
    }
}

