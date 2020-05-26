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
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
* User: adam
* Date: 2/1/13
*/
public class StudyLsidHandler implements LsidManager.LsidHandler<Identifiable>
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
            int containerId = Integer.parseInt(studyNamespace.substring(i+1));
            Container container = ContainerManager.getForRowId(containerId);
            int datasetId = Integer.parseInt(lsid.getObjectId().split("\\.")[0]);

            StudyService studyService = StudyService.get();
            if (null != studyService)
            {
                Dataset dataset = studyService.getDataset(container, datasetId);

                if (null != dataset)
                {
                    String datasetName = dataset.getName();

                    ActionURL queryURL = PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(container, "study", datasetName);
                    queryURL.addFilter("query", FieldKey.fromParts("lsid"), CompareType.EQUAL, lsid.toString());
                    return queryURL;
                }
            }

        }
        return null;
    }

    public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
    {
        return false;
    }
}
