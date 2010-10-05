/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.ms2;

import org.apache.log4j.Logger;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.query.FieldKey;

/**
 * User: jeckels
 * Date: Jan 9, 2007
 */
public class MS2Service
{
    private static Service _serviceImpl = null;

    public interface Service
    {
        public String getRunsTableName();

        SearchClient createSearchClient(String server, String url, Logger instanceLogger, String userAccount, String userPassword);

        TableInfo createPeptidesTableInfo(User user, Container container);
        TableInfo createPeptidesTableInfo(User user, Container container, boolean includeFeatureFk, 
                                          ContainerFilter containerFilter, SimpleFilter filter, Iterable<FieldKey> defaultColumns);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}
