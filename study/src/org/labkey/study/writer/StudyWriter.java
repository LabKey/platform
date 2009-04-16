/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.writer;

import org.apache.log4j.Logger;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.User;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * User: adam
 * Date: Apr 14, 2009
 * Time: 7:29:32 PM
 */
public class StudyWriter
{
    private static final Logger _log = Logger.getLogger(StudyWriter.class);

    private final Study _study;
    private final User _user;
    private final File _directory;

    public StudyWriter(Study study, User user, File directory)
    {
        _study = study;
        _user = user;
        _directory = directory;
    }

    public void write() throws IOException, SQLException, ServletException
    {
        _directory.mkdir();

        VisitMapWriter vm = new DataFaxVisitMapWriter(_study, _directory);
        vm.write();

        NumberFormat dsf = new DecimalFormat("plate000.tsv");
        DataSetDefinition[] datasets = _study.getDataSets();

        for (DataSetDefinition def : datasets)
        {
            File file = new File(_directory, dsf.format(def.getDataSetId()));
            TableInfo ti = def.getTableInfo(_user);
            ResultSet rs = Table.select(ti, ti.getColumns(), null, null);
            TSVGridWriter writer = new TSVGridWriter(rs);
            writer.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
            writer.write(file);
        }

        for (Report report : ReportService.get().getReports(null, _study.getContainer()))
        {
            _log.info("I'm exporting report " + report.getDescriptor().getReportName());
        }
    }
}
