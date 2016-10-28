/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
package gwt.client.org.labkey.study.dataset.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.domain.DomainImporter;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.DomainImporterServiceAsync;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        root = StudyApplication.getRootPanel();

        VerticalPanel vPanel = new VerticalPanel();
        root.add(vPanel);

        List<String> columnsToMap = new ArrayList<String>();
        List<GWTPropertyDescriptor> baseColumnMetadata = new ArrayList<GWTPropertyDescriptor>();

        columnsToMap.add(PropertyUtil.getServerProperty("subjectColumnName"));
        baseColumnMetadata.add(new GWTPropertyDescriptor(PropertyUtil.getServerProperty("subjectColumnName"), "xsd:string"));

        String timepointTypeString = PropertyUtil.getServerProperty("timepointType");
        if ("DATE".equals(timepointTypeString))
        {
            columnsToMap.add("Visit Date");
            baseColumnMetadata.add(new GWTPropertyDescriptor("Visit Date", "xsd:datetime"));
        }
        else if ("VISIT".equals(timepointTypeString))
        {
            columnsToMap.add("Sequence Num");
            baseColumnMetadata.add(new GWTPropertyDescriptor("Sequence Num", "xsd:double"));
        }
        else
        {
            columnsToMap.add("Date");
            baseColumnMetadata.add(new GWTPropertyDescriptor("Date", "xsd:datetime"));
        }

        Set<String> baseColumnNames = new HashSet<String>();
        String baseColNamesString = PropertyUtil.getServerProperty("baseColumnNames");
        String[] baseColArray = baseColNamesString.split(",");
        for (String s : baseColArray)
            baseColumnNames.add(s);

        domainImporter = new DomainImporter(getService(), columnsToMap, baseColumnNames, baseColumnMetadata);
        vPanel.add(domainImporter.getMainPanel());
    }

    private DomainImporterServiceAsync service = null;

    private DomainImporterServiceAsync getService()
    {
        if (service == null)
        {
            service = GWT.create(DomainImporterService.class);
            ServiceUtil.configureEndpoint(service, "domainImportService");
        }
        return service;
    }
}
