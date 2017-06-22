/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.api.rss;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.io.Writer;
import java.util.List;

/**
 * Created by Nick Arnold on 5/16/14.
 */
public interface RSSService
{
    static RSSService get()
    {
        return ServiceRegistry.get().getService(RSSService.class);
    }

    static void set(RSSService impl)
    {
        ServiceRegistry.get().registerService(RSSService.class, impl);
    }

    List<RSSFeed> getFeeds(Container container, User user);
    void aggregateFeeds(List<RSSFeed> feeds, User user, Writer writer);
}
