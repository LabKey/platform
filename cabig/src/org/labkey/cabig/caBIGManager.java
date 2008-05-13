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

package org.labkey.cabig;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Table;

import java.sql.SQLException;

public class caBIGManager
{
    private static caBIGManager _instance;
    private static Logger _log = Logger.getLogger(caBIGManager.class);

    private caBIGManager()
    {
        // prevent external construction with a private default constructor
    }

    public static synchronized caBIGManager get()
    {
        if (_instance == null)
            _instance = new caBIGManager();
        return _instance;
    }

    public void publish(Container c) throws SQLException
    {
        ContainerManager.setPublishBit(c, Boolean.TRUE);
    }

    public void unpublish(Container c) throws SQLException
    {
        ContainerManager.setPublishBit(c, Boolean.FALSE);
    }

    public boolean isPublished(Container c) throws SQLException
    {
        Long rows = Table.executeSingleton(caBIGSchema.getInstance().getSchema(), "SELECT COUNT(*) FROM " + caBIGSchema.getInstance().getTableInfoContainers() + " WHERE EntityId = ?", new Object[]{c.getId()}, Long.class);

        return (1 == rows.longValue());
    }
}