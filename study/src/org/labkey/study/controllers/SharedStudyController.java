/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.study.Study;
import org.labkey.api.util.GUID;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataspaceQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by matthew on 5/28/15.
 *
 * Actions that only make sense in the context of a shared/dataspace study
 */
public class SharedStudyController extends BaseStudyController
{
    private static final Logger _log = Logger.getLogger(SharedStudyController.class);

    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(SharedStudyController.class);

    public SharedStudyController()
    {
        setActionResolver(ACTION_RESOLVER);
    }


    @RequiresPermission(ReadPermission.class)
    public class StudyFilterAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new StudyFilterWebPart();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class StudyFilterWebPartFactory extends BaseWebPartFactory
    {
        public StudyFilterWebPartFactory()
        {
            super("Shared Study Filter", WebPartFactory.LOCATION_RIGHT);
        }

        @Override
        public boolean isAvailable(Container c, String location)
        {
            if (!super.isAvailable(c, location))
                return false;

            Container project = c.getProject();
            if (project == null)
                return false;

            Study study = StudyManager.getInstance().getStudy(project);
            return study != null && study.getShareDatasetDefinitions();
        }

        @Override
        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
        {
            JspView<Object> view = new StudyFilterWebPart();
            view.setTitle(getName());
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }
    }

    public static class StudyFilterWebPart extends JspView<Object>
    {
        public StudyFilterWebPart()
        {
            super("/org/labkey/study/view/dataspace_studyfilter.jsp");
        }
    }


    private static class SharedStudyContainerFilterForm
    {
        private List<GUID> _containers;

        public List<GUID> getContainers()
        {
            return _containers;
        }

        public void setContainers(List<GUID> containers)
        {
            _containers = containers == null ? Collections.emptyList() : Collections.unmodifiableList(containers);
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class SharedStudyContainerFilterAction extends ApiAction<SharedStudyContainerFilterForm>
    {
        private Study _study = null;

        public SharedStudyContainerFilterAction()
        {
            super();
            setSupportedMethods(new String[] { "GET", "POST", "DELETE" });
        }

        @Override
        public void validateForm(SharedStudyContainerFilterForm form, Errors errors)
        {
            if (getContainer().isRoot())
                throw new UnsupportedOperationException();

            // validate the current container is the project and has a shared study
            _study = StudyManager.getInstance().getStudy(getContainer().getProject());
            if (_study == null || !_study.getShareDatasetDefinitions())
                errors.reject(ERROR_MSG, "Shared study project required");

            if (isPost())
            {
                // validate all container ids are study containers under the project
                for (GUID guid : form.getContainers())
                {
                    Container c = ContainerManager.getForId(guid);
                    if (c == null)
                    {
                        errors.reject(ERROR_MSG, "Container doesn't exist");
                        continue;
                    }

                    if (!c.isDescendant(_study.getContainer()))
                    {
                        errors.reject(ERROR_MSG, "Container is not member of study's project");
                        continue;
                    }

                    Study study = StudyManager.getInstance().getStudy(c);
                    if (study == null)
                    {
                        errors.reject(ERROR_MSG, "Container is not a study container");
                        continue;
                    }
                }
            }
        }

        @Override
        public Object execute(SharedStudyContainerFilterForm form, BindException errors) throws Exception
        {
            String key = DataspaceQuerySchema.SHARED_STUDY_CONTAINER_FILTER_KEY + getContainer().getProject().getRowId();
            if (isPost())
            {
                getViewContext().getSession().setAttribute(key, form.getContainers());
                return success(form);
            }
            else if (isDelete())
            {
                getViewContext().getSession().removeAttribute(key);
                return success();
            }
            else
            {
                SharedStudyContainerFilterForm data = new SharedStudyContainerFilterForm();
                Object o = getViewContext().getSession().getAttribute(key);
                if (o instanceof List)
                {
                    data.setContainers((List)o);
                }
                else
                {
                    DataspaceContainerFilter dcf = new DataspaceContainerFilter(getUser(), getContainer());
                    Collection<GUID> c = dcf.getIds(getContainer());
                    if (null != c)
                        data.setContainers(new ArrayList<>(dcf.getIds(getContainer())));
                }
                return success(data);
            }
        }
    }
}
