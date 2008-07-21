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
package org.labkey.study.controllers;

import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.StudyManager;

/**
 * User: jgarms
 * Date: Jul 17, 2008
 * Time: 11:24:51 AM
 */
public class StudyDefinitionController extends BaseStudyController
{
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(StudyDefinitionController.class);

    public StudyDefinitionController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class EditCohortDefinitionAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // Get domain Id
            String domainURI = StudyManager.getInstance().getDomainURI(getContainer(), Cohort.class);
            Domain domain = PropertyService.get().getDomain(getContainer(), domainURI);
            if (domain == null)
            {
                domain = PropertyService.get().createDomain(getContainer(), domainURI, "Cohort");
                domain.save(getUser());
            }

            return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(getContainer(), domain.getTypeId());
        }
    }
}
