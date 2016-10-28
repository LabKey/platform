/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

package org.labkey.api.reports.report.r.view;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.io.File;

/**
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class HrefOutput extends ImageOutput
{
    public static final String ID = "hrefout:";

    public HrefOutput()
    {
        super(ID);
    }

    @Override
    protected File getSubstitution(File directory) throws Exception
    {
        return null;
    }

    @Override
    public HttpView render(ViewContext context)
    {
        return null;
    }

    @Override
    protected boolean canDeleteFile()
    {
        Report report = getReport();

        if (report != null)
        {
            // if this report is not using knitr then follow the usual rules for deleting files
            if (RReportDescriptor.KnitrFormat.None.name().equalsIgnoreCase(report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.knitrFormat)))
                return super.canDeleteFile();

            // otherwise, don't delete the file
            return false;
        }

        return true;
    }
}
