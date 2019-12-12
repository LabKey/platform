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
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* User: adam
* Date: 2/1/13
* Time: 10:41 PM
*/
public class StudyLsidHandler implements LsidManager.LsidHandler
{
    public Identifiable getObject(Lsid lsid)
    {
        OntologyObject oo = OntologyManager.getOntologyObject(null, lsid.toString());
        if (oo == null)
            return null;

        return new IdentifiableBase(oo);
    }

    public Container getContainer(Lsid lsid)
    {
        OntologyObject oo = OntologyManager.getOntologyObject(null, lsid.toString());
        if (oo == null)
            return null;

        return oo.getContainer();
    }

    @Nullable
    public ActionURL getDisplayURL(Lsid lsid)
    {
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
                Container c = getContainer(lsid);
                Set<? extends StudyImpl> allStudies = StudyManager.getInstance().getAllStudies(c);
                DatasetDefinition targetDataset = null;
                for (StudyImpl study : allStudies)
                {
                    List<DatasetDefinition> datasetDefinitions = study.getDatasets();
                    for (DatasetDefinition datasetDefinition : datasetDefinitions)
                    {
                        List<String> datasetLsids = StudyManager.getInstance().getDatasetLSIDs(UserManager.getUser(c.getCreatedBy()), datasetDefinition);
                        if (datasetLsids.contains(lsid.toString()))
                        {
                            for (String datasetLsid : datasetLsids)
                            {
                                if (lsid.toString().equals(datasetLsid))
                                {
                                    targetDataset = datasetDefinition;
                                }
                            }
                        }
                    }
                }

                if (null != targetDataset)
                {
                    Map<String, Object> datasetRow = targetDataset.getDatasetRow(UserManager.getUser(c.getCreatedBy()), lsid.toString());

                    BigDecimal sequenceNum = (BigDecimal) datasetRow.get("SequenceNum");
                    String ptid = (String) datasetRow.get("ParticipantId");
                    ActionURL url = new ActionURL(StudyController.DatasetAction.class, c);
                    url.addParameter(DatasetDefinition.DATASETKEY, String.valueOf(targetDataset.getDatasetId()));
                    url.addParameter(VisitImpl.SEQUENCEKEY, String.valueOf(sequenceNum));
                    url.addParameter("StudyData.participantId~eq", ptid);
                    return url;
                }
        }
        return null;
    }

    public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
    {
        return false;
    }
}
