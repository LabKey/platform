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

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.PanelButton;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.ExtensibleStudyEntity;
import org.labkey.study.model.StudyImpl;

import java.util.List;

/**
 * Query view for objects with extended properties using Ontology manager
 *
 * User: jgarms
 * Date: Jul 23, 2008
 * Time: 2:13:04 PM
 */
public class ExtensibleObjectQueryView extends QueryView
{
    private final boolean allowEditing;

    public ExtensibleObjectQueryView(
        User user,
        StudyImpl study,
        ExtensibleStudyEntity.DomainInfo domainInfo,
        ViewContext context,
        boolean allowEditing)
    {
        super(StudyQuerySchema.createSchema(study, user, true));
        this.allowEditing = allowEditing;
        setShadeAlternatingRows(true);
        setShowBorders(true);
        QuerySettings settings = getSchema().getSettings(context, domainInfo.getDomainName());
        settings.setQueryName(getQueryName(domainInfo));
        setSettings(settings);
    }

    protected String getQueryName(ExtensibleStudyEntity.DomainInfo domainInfo)
    {
        return domainInfo.getDomainName();
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        return view;
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        List<String> recordSelectorColumns = view.getDataRegion().getRecordSelectorValueColumns();
        bar.add(createExportButton(recordSelectorColumns));
    }

    public boolean allowEditing()
    {
        return allowEditing;
    }
}
