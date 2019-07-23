/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.beans.PropertyChangeEvent;

/**
 * User: adam
 * Date: Nov 3, 2008
 * Time: 3:48:15 PM
 */
public class StudyContainerListener extends ContainerManager.AbstractContainerListener
{
    @Override
    public void containerDeleted(Container c, User user)
    {
        StudyManager.getInstance().deleteAllStudyData(c, user);
        for (StudyImpl ancillaryStudy : StudyManager.getInstance().getAncillaryStudies(c))
        {
            // Explicitly break the link between any ancillary studies dependent on this container:
            ancillaryStudy.setSourceStudyContainerId(null);
            StudyManager.getInstance().updateStudy(user, ancillaryStudy);
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
        StudyManager.getInstance().clearCaches(c, true);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        if ("Name".equals(evt.getPropertyName()))
        {
            Container c = (Container)evt.getSource();
            StudyManager.getInstance().clearCaches(c, true);
        }
    }
}
