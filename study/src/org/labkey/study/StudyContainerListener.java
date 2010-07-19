/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.study;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.study.model.StudyManager;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Nov 3, 2008
 * Time: 3:48:15 PM
 */
public class StudyContainerListener implements ContainerManager.ContainerListener
{
    private static final Logger _log = Logger.getLogger(StudyContainerListener.class);

    public void containerCreated(Container c, User user)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        try
        {
            StudyManager.getInstance().deleteAllStudyData(c);
        }
        catch (SQLException e)
        {
            // ignore any failures.
            _log.error("Failure cleaning up study data when deleting container " + c.getPath(), e);
        }
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
