/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
package org.labkey.study.view;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

/**
 * User: markigra
 * Date: 10/28/11
 * Time: 3:17 PM
 */
public class StudyListQueryView extends QueryView
{
    public StudyListQueryView(ViewContext ctx)
    {
        super(QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), "study"));
        QuerySettings settings = getSchema().getSettings(ctx, "qwpStudies", "StudyProperties");
        settings.setBaseSort(new Sort("Label"));
        setSettings(settings);
        setShowUpdateColumn(false);
        setShowBorders(true);
        setShadeAlternatingRows(true);
        setShowSurroundingBorder(false);
    }

    @Override
    protected @Nullable ContainerFilter getContainerFilter()
    {
        return ContainerFilter.Type.CurrentAndSubfolders.create(getUser());
    }
}
