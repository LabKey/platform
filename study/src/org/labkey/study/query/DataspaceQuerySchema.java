/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.User;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.util.GUID;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.visualization.DataspaceVisualizationProvider;

import java.util.List;

/**
 * Created by matthew on 2/11/14.
 *
 * Don't really need a subclass, but this helps isolate the exact difference between a regular study schema, and
 * a dataspace study schema.
 */

public class DataspaceQuerySchema extends StudyQuerySchema
{
    public static final String SHARED_STUDY_CONTAINER_FILTER_KEY = "LABKEY.sharedStudyContainerFilter.";

    private List<GUID> _sharedStudyContainerFilter;

    public DataspaceQuerySchema(@NotNull StudyImpl study, User user, boolean mustCheckPermissions)
    {
        super(study, user, mustCheckPermissions);

        Container project = study.getContainer().getProject();
        List<GUID> containerIds = null;
        ViewContext context = HttpView.hasCurrentView() ? HttpView.currentContext() : null;
        if (project != null && context != null)
        {
            Object o = context.getSession().getAttribute(SHARED_STUDY_CONTAINER_FILTER_KEY + project.getRowId());
            if (o instanceof List)
                containerIds = (List)o;
        }

        _sharedStudyContainerFilter = containerIds;
    }

    public void clearSessionContainerFilter()
    {
        _sharedStudyContainerFilter = null;
    }

    /* for tables that support container filter, should they turn on support or not */
    @Override
    public boolean allowSetContainerFilter()
    {
        return false;
    }


    @Override
    public ContainerFilter getDefaultContainerFilter()
    {
        return new DataspaceContainerFilter(getUser(), _sharedStudyContainerFilter);
    }


    @Override
    public boolean isDataspace()
    {
        return true;
    }

    @Override
    public ContainerFilter getOlapContainerFilter()
    {
        return getDefaultContainerFilter();
    }

    @Nullable
    @Override
    public VisualizationProvider createVisualizationProvider()
    {
        return new DataspaceVisualizationProvider(this);
    }
}
