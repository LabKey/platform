package org.labkey.experiment.list.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import org.labkey.api.gwt.client.ui.domain.DomainImporter;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.DomainImporterServiceAsync;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
public class ListImporter implements EntryPoint
{
    private RootPanel root;
    private DomainImporter domainImporter;

    public void onModuleLoad()
    {
        root = RootPanel.get("org.labkey.experiment.list.ListImporter-Root");

        VerticalPanel vPanel = new VerticalPanel();
        root.add(vPanel);

        List<String> columnsToMap = Collections.emptyList();

        Set<String> baseColumnNames = new HashSet<String>();
        String baseColNamesString = PropertyUtil.getServerProperty("baseColumnNames");
        if (baseColNamesString == null)
            baseColNamesString = "";
        String[] baseColArray = baseColNamesString.split(",");
        for (String s : baseColArray)
            baseColumnNames.add(s);

        domainImporter = new DomainImporter(getService(), columnsToMap, baseColumnNames);
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
