/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.ExtensibleStudyEntity;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.ExtensibleObjectQueryView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

/**
 * Provides actions for extensible study objects,
 * like cohort, visit and study.
 *
 * User: jgarms
 * Date: Jul 17, 2008
 * Time: 11:24:51 AM
 */
public class StudyDefinitionController extends BaseStudyController
{
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(StudyDefinitionController.class);

    public StudyDefinitionController()
    {
        setActionResolver(ACTION_RESOLVER);
    }

    private abstract class EditDefinitionAction extends FormHandlerAction<ReturnUrlForm>
    {
        private Domain _domain = null;

        protected abstract ExtensibleStudyEntity.DomainInfo getDomainInfo();

        @Override
        public void validateCommand(ReturnUrlForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ReturnUrlForm form, BindException errors) throws Exception
        {
            // Get domain Id
            ExtensibleStudyEntity.DomainInfo domainInfo = getDomainInfo();
            String domainURI = domainInfo.getDomainURI(getContainer());
            _domain = PropertyService.get().getDomain(getContainer(), domainURI);
            if (_domain == null)
            {
                _domain = PropertyService.get().createDomain(getContainer(), domainURI, domainInfo.getDomainName());
                _domain.save(getUser());
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ReturnUrlForm form)
        {
            // since the study additional properties domain is scoped to the project, use the domain.getContainer() here instead of getContainer()
            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(_domain.getContainer(), _domain);
            form.propagateReturnURL(url);
            return url;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class EditCohortDefinitionAction extends EditDefinitionAction
    {
        @Override
        protected ExtensibleStudyEntity.DomainInfo getDomainInfo()
        {
            return CohortImpl.DOMAIN_INFO;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class EditStudyDefinitionAction extends EditDefinitionAction
    {
        @Override
        protected ExtensibleStudyEntity.DomainInfo getDomainInfo()
        {
            return StudyImpl.DOMAIN_INFO;
        }
    }

    private abstract class ViewAction extends QueryViewAction<QueryViewAction.QueryExportForm, QueryView>
    {
        public ViewAction() {super(QueryExportForm.class);}

        protected abstract ExtensibleStudyEntity.DomainInfo getDomainInfo();

        protected abstract String getPluralName();

        @Override
        public void addNavTrail(NavTree root)
        {
            _addManageStudy(root);
            root.addChild(getPluralName());
        }

        @Override
        protected QueryView createQueryView(QueryExportForm queryExportForm, BindException errors, boolean forExport, String dataRegion)
        {
            return new ExtensibleObjectQueryView(getUser(), getStudyRedirectIfNull(), getDomainInfo(), HttpView.currentContext(), false);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CohortViewAction extends ViewAction
    {
        @Override
        protected ExtensibleStudyEntity.DomainInfo getDomainInfo()
        {
            StudyManager.getInstance().assertCohortsViewable(getContainer(), getUser());
            return CohortImpl.DOMAIN_INFO;
        }

        @Override
        protected String getPluralName()
        {
            return "Cohorts";
        }
    }
}
