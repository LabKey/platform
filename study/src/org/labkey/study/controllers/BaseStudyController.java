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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.template.PageConfig;
import org.labkey.study.StudyModule;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.view.BaseStudyPage;
import org.springframework.validation.BindException;

public abstract class BaseStudyController extends SpringActionController
{
    protected StudyImpl _study = null;

    public enum SharedFormParameters
    {
        QCState
    }

    public static ActionURL getStudyOverviewURL(Container c)
    {
        return new ActionURL(StudyController.OverviewAction.class, c);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig page = super.defaultPageConfig();
        String template = getViewContext().getRequest().getHeader("template");
        if (null == template)
            template = getViewContext().getRequest().getParameter("_template");
        if ("custom".equals(template))
            page.setTemplate(PageConfig.Template.Custom);
        return page;
    }

    @NotNull
    public static StudyImpl getStudyRedirectIfNull(Container c)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        if (null == study)
        {
            // redirect to the study home page, where admins will see a 'create study' button,
            // and non-admins will simply see a message that no study exists.
            throw new RedirectException(new ActionURL(StudyController.BeginAction.class, c));
        }
        return study;
    }

    @NotNull
    public StudyImpl getStudyRedirectIfNull()
    {
        return getStudyRedirectIfNull(getContainer());
    }

    @NotNull
    public static StudyImpl getStudyThrowIfNull(Container c) throws IllegalStateException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        if (null == study)
        {
            // We expected to find a study
            throw new NotFoundException("No study found.");
        }
        return study;
    }

    @NotNull
    public StudyImpl getStudyThrowIfNull() throws IllegalStateException
    {
        return getStudyThrowIfNull(getContainer());
    }

    @Nullable
    public static StudyImpl getStudy(Container container)
    {
        return StudyManager.getInstance().getStudy(container);
    }

    @Nullable
    public StudyImpl getStudy()
    {
        if (null == _study)
            _study = getStudy(getContainer());
        return _study;
    }

    protected BaseViewAction<?> initAction(BaseViewAction<?> parent, BaseViewAction<?> action)
    {
        action.setViewContext(parent.getViewContext());
        action.setPageConfig(parent.getPageConfig());

        return action;
    }

    protected void _addManageStudy(NavTree root)
    {
        _addManageStudy(root, getContainer(), getUser());
    }

    public static void _addManageStudy(NavTree root, Container container, User user)
    {
        addRootNavTrail(root, container, user);

        if (container.hasPermission(user, ManageStudyPermission.class))
            root.addChild("Manage Study", new ActionURL(StudyController.ManageStudyAction.class, container));
    }

    @NotNull
    protected Study addRootNavTrail(NavTree root)
    {
        return addRootNavTrail(root, getContainer(), getUser());
    }

    @NotNull
    public static Study addRootNavTrail(NavTree root, Container container, User user)
    {
        Study study = getStudyRedirectIfNull(container);
        ActionURL rootURL;
        FolderType folderType = container.getFolderType();
        if (folderType.getDefaultModule() instanceof StudyModule)
        {
            rootURL = folderType.getStartURL(container, user);
        }
        else
        {
            rootURL = new ActionURL(StudyController.BeginAction.class, container);
        }
        root.addChild(study.getLabel(), rootURL);
        return study;
    }

    protected void _addNavTrailDatasetAdmin(NavTree root)
    {
        _addManageStudy(root);
        root.addChild("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, getContainer()));
    }

    protected void _addNavTrail(NavTree root)
    {
        addRootNavTrail(root);
    }

    protected NavTree _addNavTrail(NavTree root, int datasetId, @Nullable ActionURL datasetUrl)
    {
        Study study = addRootNavTrail(root);
        if (datasetId > 0)
        {
            Dataset dataset = study.getDataset(datasetId);
            if (dataset != null)
            {
                StringBuilder label = new StringBuilder();
                label.append("Dataset: ");
                if (dataset.getLabel() != null)
                    label.append(dataset.getLabel());
                else
                    label.append("CRF/Assay ").append(dataset.getDatasetId());

                if (null == datasetUrl)
                {
                    datasetUrl = getViewContext().cloneActionURL()
                        .setAction(StudyController.DatasetAction.class)
                        .setContainer(getContainer())
                        .deleteParameter("participantId");

                    if (datasetUrl.getParameter(Dataset.DATASET_KEY) == null)
                        datasetUrl.addParameter(Dataset.DATASET_KEY, datasetId);
                }

                root.addChild(label.toString(), datasetUrl);
            }
        }
        return root;
    }

    protected void redirectTypeNotFound(int datasetId) throws RedirectException
    {
        throw new RedirectException(new ActionURL(StudyController.TypeNotFoundAction.class, getContainer()).addParameter("id", datasetId));
    }

    /** Redirect to the project shared visit study if shared visits is turned on in the shared study. */
    protected void redirectToSharedVisitStudy(Study study, ActionURL url) throws RedirectException
    {
        Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);
        if (sharedStudy != null && sharedStudy.getShareVisitDefinitions() == Boolean.TRUE)
            throw new RedirectException(url.clone().setContainer(sharedStudy.getContainer()));
    }

    public static class StudyJspView<T> extends JspView<T>
    {
        public StudyJspView(StudyImpl study, String jspPath, T bean, BindException errors)
        {
            super(jspPath, bean, errors);
            if (getPage() instanceof BaseStudyPage)
                ((BaseStudyPage)getPage()).init(study);
        }
    }

    public static <T> boolean nullSafeEqual(T first, T second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }
}
