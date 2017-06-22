/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.StudyPropertiesController;
import org.labkey.study.model.ExtensibleStudyEntity;
import org.labkey.study.model.StudyImpl;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jgarms
 * Date: Aug 7, 2008
 * Time: 5:42:15 PM
 */
public class StudyPropertiesQueryView extends ExtensibleObjectQueryView
{
    public static final String QUERY_NAME = "StudyProperties";

    public StudyPropertiesQueryView(User user, StudyImpl study, ViewContext viewContext, boolean allowEditing)
    {
        super(user, study, StudyImpl.DOMAIN_INFO, viewContext, allowEditing);
        setShadeAlternatingRows(false);
    }

    protected String getQueryName(ExtensibleStudyEntity.DomainInfo domainInfo)
    {
        return QUERY_NAME;
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        // no buttons
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        if (allowEditing() && getUser().hasRootAdminPermission())
        {
            view.getDataRegion().addDisplayColumn(0, new EditColumn(view.getRenderContext().getContainer()));
        }
        return view;
    }

    private class EditColumn extends SimpleDisplayColumn
    {
        private final Container container;

        public EditColumn(Container container)
        {
            this.container = container;
            setWidth(null);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            ActionURL actionURL = new ActionURL(StudyPropertiesController.UpdateAction.class, container);
            out.write(PageFlowUtil.textLink("edit", actionURL));
        }
    }
}
