/*
 * Copyright (c) 2009-2009 LabKey Corporation
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
package org.labkey.api.search;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.Resource;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:54:01 PM
 */
public interface SearchService
{
    enum PRIORITY
    {
        crawl,      // lowest
        background, // crawler item

        bulk,       // all wikis
        group,      // one container
        item        // one page/attachment
    }
    
    
    void addResource(ActionURL url, PRIORITY pri);
    void addResource(Runnable r, PRIORITY pri);
    void addResource(String identifier, PRIORITY pri);
    void addResource(Resource r, PRIORITY pri);

    void deleteResource(String identifier, PRIORITY pri);

    public interface ResourceResolver
    {
        Resource resolve(@NotNull String resourceIdentifier);
    }

    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver);
    public String search(String queryString);
    public void clearIndex();
}
