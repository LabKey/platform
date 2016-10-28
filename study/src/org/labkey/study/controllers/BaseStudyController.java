/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.template.PageConfig;
import org.labkey.study.AssayFolderType;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudyModule;
import org.labkey.study.controllers.specimen.SpecimenUtils;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.security.permissions.ManageStudyPermission;
import org.labkey.study.view.BaseStudyPage;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.util.Collection;

/**
 * User: Karl Lum
 * Date: Dec 13, 2007
 */
public abstract class BaseStudyController extends SpringActionController
{
    StudyImpl _study = null;

    public enum SharedFormParameters
    {
        QCState
    }

    public static ActionURL getStudyOverviewURL(Container c)
    {
        return new ActionURL(StudyController.OverviewAction.class, c);
    }

    protected ActionURL getStudyOverviewURL()
    {
        return getStudyOverviewURL(getContainer());
    }

    protected SpecimenUtils getUtils()
    {
        return new SpecimenUtils(this);
    }

    public PageConfig defaultPageConfig()
    {
        PageConfig page =  super.defaultPageConfig();
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

    protected BaseViewAction initAction(BaseViewAction parent, BaseViewAction action)
    {
        action.setViewContext(parent.getViewContext());
        action.setPageConfig(parent.getPageConfig());

        return action;
    }

    protected NavTree _appendManageStudy(NavTree root)
    {
        return _appendManageStudy(root, getContainer(), getUser());
    }

    public static NavTree _appendManageStudy(NavTree root, Container container, User user)
    {
        try
        {
            appendRootNavTrail(root, container, user);

            if (container.hasPermission(user, ManageStudyPermission.class))
                root.addChild("Manage Study", new ActionURL(StudyController.ManageStudyAction.class, container));
        }
        catch (ServletException e)
        {
        }
        return root;
    }

    @NotNull
    protected Study appendRootNavTrail(NavTree root) throws ServletException
    {
        return appendRootNavTrail(root, getContainer(), getUser());
    }

    @NotNull
    public static Study appendRootNavTrail(NavTree root, Container container, User user) throws ServletException
    {
        Study study = getStudyRedirectIfNull(container);
        ActionURL rootURL;
        FolderType folderType = container.getFolderType();
        // AssayFolderType is defined in the study module, but it's not really a folder of type study
        if (folderType.getDefaultModule() instanceof StudyModule && !(folderType instanceof AssayFolderType))
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

    protected NavTree _appendNavTrailDatasetAdmin(NavTree root)
    {
        _appendManageStudy(root);
        return root.addChild("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, getContainer()));
    }

    protected NavTree _appendNavTrail(NavTree root)
    {
        try
        {
            appendRootNavTrail(root);
        }
        catch (ServletException ignored)
        {
        }
        return root;
    }

    protected NavTree _appendNavTrail(NavTree root, int datasetId, int visitId)
    {
        return _appendNavTrail(root, datasetId, visitId, null, null);
    }

    protected NavTree _appendNavTrail(NavTree root, int datasetId, int visitId, CohortFilter cohortFilter, String qcStateSetFormValue)
    {
        try
        {
            Study study = appendRootNavTrail(root);
            _appendDataset(root, study, datasetId, visitId, cohortFilter, qcStateSetFormValue);
        }
        catch (ServletException e)
        {
        }
        return root;
    }

    protected NavTree _appendDataset(NavTree root, Study study, int datasetId, int visitRowId, @Nullable CohortFilter cohortFilter, String qcStateSetFormValue)
    {
        if (datasetId > 0)
        {
            Visit visit = null;
            if (visitRowId > 0)
                visit = StudyManager.getInstance().getVisitForRowId(study, visitRowId);

            Dataset dataset = study.getDataset(datasetId);
            if (dataset != null)
            {
                StringBuilder label = new StringBuilder();
                label.append("Dataset: ");
                if (dataset.getLabel() != null)
                    label.append(dataset.getLabel());
                else
                    label.append("CRF/Assay ").append(dataset.getDatasetId());

                if (visit != null)
                    label.append(", ").append(visit.getDisplayString());
                else if (study.getTimepointType() != TimepointType.CONTINUOUS)
                    label.append(", All Visits");

                ActionURL datasetUrl = new ActionURL(StudyController.DatasetAction.class, getContainer()).
                        addParameter(DatasetDefinition.DATASETKEY, datasetId);
                if (cohortFilter != null)
                    cohortFilter.addURLParameters(study, datasetUrl, "Dataset");
                if (qcStateSetFormValue != null)
                    datasetUrl.addParameter(SharedFormParameters.QCState, qcStateSetFormValue);
                root.addChild(label.toString(), datasetUrl.getLocalURIString());
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
        public StudyJspView(StudyImpl study, String name, T bean, BindException errors)
        {
            super("/org/labkey/study/view/" + name, bean, errors);
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

    public static long[] toLongArray(Collection<String> intStrings)
    {
        if (intStrings == null)
            return null;
        long[] converted = new long[intStrings.size()];
        int index = 0;
        for (String intString : intStrings)
            converted[index++] = Long.parseLong(intString);
        return converted;
    }

    public static long[] toLongArray(String[] intStrings)
    {
        if (intStrings == null)
            return null;
        long[] converted = new long[intStrings.length];
        int index = 0;
        for (String intString : intStrings)
            converted[index++] = Long.parseLong(intString);
        return converted;
    }

    public static class IdForm
    {
        public enum PARAMS
        {
            id
        }
        
        private int _id;

        public IdForm()
        {
        }

        public IdForm(int id)
        {
            _id = id;
        }

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }
    }

    public static class BulkEditForm
    {
        private String _newLabel;
        private String _newId;
        private String _nextPage;
        private String _order;
        private int[] _ids;
        private String[] _labels;

        public String getNewLabel()
        {
            return _newLabel;
        }

        public void setNewLabel(String newLabel)
        {
            _newLabel = newLabel;
        }

        public String getNextPage()
        {
            return _nextPage;
        }

        public void setNextPage(String nextPage)
        {
            _nextPage = nextPage;
        }

        public String getOrder()
        {
            return _order;
        }

        public void setOrder(String order)
        {
            _order = order;
        }

        public String[] getLabels()
        {
            return _labels;
        }

        public void setLabels(String[] labels)
        {
            _labels = labels;
        }

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public String getNewId()
        {
            return _newId;
        }

        public void setNewId(String newId)
        {
            _newId = newId;
        }
    }
}
