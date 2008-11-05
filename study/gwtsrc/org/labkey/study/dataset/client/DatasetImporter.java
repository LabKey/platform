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
package org.labkey.study.dataset.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import org.labkey.api.gwt.client.ui.domain.DomainImporter;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.DomainImporterServiceAsync;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jgarms
 * Date: Nov 3, 2008
 */
public class DatasetImporter implements EntryPoint
{
    private RootPanel root;
    private DomainImporter domainImporter;

    public void onModuleLoad()
    {
        root = RootPanel.get("org.labkey.study.dataset.DatasetImporter-Root");

        VerticalPanel vPanel = new VerticalPanel();
        root.add(vPanel);

        // TODO: determine if we're date or visit-based
        List<String> columnsToMap = new ArrayList<String>();
        columnsToMap.add("Participant ID");

        String dateBasedString = PropertyUtil.getServerProperty("dateBased");
        boolean isDateBased = Boolean.parseBoolean(dateBasedString);
        if (isDateBased)
            columnsToMap.add("Date");
        else
            columnsToMap.add("Sequence Num");

        domainImporter = new DomainImporter(getService(), columnsToMap);
        vPanel.add(domainImporter.getMainPanel());
    }

    private DomainImporterServiceAsync service = null;

    private DomainImporterServiceAsync getService()
    {
        if (service == null)
        {
            service = (DomainImporterServiceAsync) GWT.create(DomainImporterService.class);
            ServiceUtil.configureEndpoint(service, "domainImportService");
        }
        return service;
    }
}
