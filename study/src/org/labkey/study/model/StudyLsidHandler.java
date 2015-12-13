/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
* User: adam
* Date: 2/1/13
* Time: 10:41 PM
*/
public class StudyLsidHandler implements LsidManager.LsidHandler
{
    public ExpObject getObject(Lsid lsid)
    {
        throw new UnsupportedOperationException();
    }

    public Container getContainer(Lsid lsid)
    {
        throw new UnsupportedOperationException();
    }

    @Nullable
    public ActionURL getDisplayURL(Lsid lsid)
    {
        // TODO fix getDisplayUrl
        if (true) throw new RuntimeException("not integrated with hard tables");

        String fullNamespace = lsid.getNamespace();
        if (!fullNamespace.startsWith("Study."))
            return null;
        String studyNamespace = fullNamespace.substring("Study.".length());
        int i = studyNamespace.indexOf("-");
        if (-1 == i)
            return null;
        String type = studyNamespace.substring(0, i);

        if (type.equalsIgnoreCase("Data"))
        {
            try
            {
                ResultSet rs = new SqlSelector(StudySchema.getInstance().getSchema(),
                        "SELECT Container, DatasetId, SequenceNum, ParticipantId FROM " + /*StudySchema.getInstance().getTableInfoStudyData(null) +*/ " WHERE LSID=?",
                        lsid.toString()).getResultSet();
                if (!rs.next())
                    return null;
                String containerId = rs.getString(1);
                int datasetId = rs.getInt(2);
                double sequenceNum = rs.getDouble(3);
                String ptid = rs.getString(4);
                Container c = ContainerManager.getForId(containerId);
                ActionURL url = new ActionURL(StudyController.DatasetAction.class, c);
                url.addParameter(DatasetDefinition.DATASETKEY, String.valueOf(datasetId));
                url.addParameter(VisitImpl.SEQUENCEKEY, String.valueOf(sequenceNum));
                url.addParameter("StudyData.participantId~eq", ptid);
                return url;
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
/*
        if (type.equalsIgnoreCase("Participant"))
        {
            try
            {
                ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(),
                        "SELECT Container, ParticipantId FROM " + StudySchema.getInstance().getTableInfoParticipant() + " WHERE IndividualLSID=?",
                        new Object[] {lsid.toString()});
                if (!rs.next())
                    return null;
                String containerId = rs.getString(1);
                String ptid = rs.getString(2);
                Container c = ContainerManager.getForId(containerId);
                ActionURL url = new ActionURL("Study", "participant", c);
                url.addParameter("Participant.participantId~eq", ptid);
                return url.getURIString();
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
*/
        return null;
    }

    public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
    {
        return false;
    }
}
