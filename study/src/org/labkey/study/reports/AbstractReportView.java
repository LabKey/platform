/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.study.reports;

import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.AbstractReport;

import java.util.Collection;

/**
 * User: Karl Lum
 * Date: Nov 17, 2006
 */
public abstract class AbstractReportView extends AbstractReport implements ReportManager.ReportView
{
    private Integer _showWithDataset;
    private String _params;
    protected String _reportType;
    protected Collection<Report> _reports;
    protected Container _container;

    public Integer getShowWithDataset() {return _showWithDataset;}
    public void setShowWithDataset(Integer dataset) {_showWithDataset = dataset;}

    public Container getContainer(){return _container;}
    public void setContainer(Container container){_container = container;}
    
    public String getParams() {return _params;}
    public void setParams(String params) {_params = params;}

    public String getReportViewType(){return _reportType;}
    public void setReports(Collection<Report> reports){_reports = reports;}
}
