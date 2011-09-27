/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
package org.labkey.wiki;

import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import java.util.Map;

/**
 * User: adam
 * Date: Oct 13, 2010
 * Time: 4:38:09 PM
 */
public class RenderedWikiResource extends WikiWebdavProvider.WikiPageResource
{
    public RenderedWikiResource(Container c, String name, String entityId, String body, WikiRendererType rendererType, Map<String, Object> m)
    {
        super(c, name, entityId, body, rendererType, m);
    }

    @Override
    protected void setBody(String body)
    {
        super.setBody(getHtml(body, _type));
    }

    @Override
    public String getContentType()
    {
        return "text/html";
    }

    // TODO: Send plain text wikis straight through instead of rendering to HTML then parsing?
    private String getHtml(String body, WikiRendererType type)
    {
        WikiService service = ServiceRegistry.get().getService(WikiService.class);

        if (null == service)
            throw new IllegalStateException("WikiService not found");

        return service.getFormattedHtml(type, body);
    }
}
