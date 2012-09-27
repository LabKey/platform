/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;

/**
 * This is a domain kind for the Study object itself
 *
 * User: jgarms
 * Date: Jul 30, 2008
 * Time: 2:35:46 PM
 */
public class StudyDomainKind extends BaseStudyDomainKind
{
    protected ExtensibleStudyEntity.DomainInfo getDomainInfo()
    {
        return StudyImpl.DOMAIN_INFO;
    }

    protected TableInfo getTableInfo()
    {
        return StudySchema.getInstance().getTableInfoStudy();
    }

    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return new ActionURL(StudyController.ManageStudyAction.class, containerUser.getContainer());
    }

    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return new ActionURL(StudyController.ManageStudyAction.class, containerUser.getContainer());
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return new SQLFragment("NULL");
    }
}
