/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Aug 12, 2007
 */

/**
 * Represents an object which can process R scripts and return results
 */
public interface RScriptRunner
{
    public void setReport(RReport report);
    public void setViewContext(ViewContext context);
    public void setSourceData(File data);

    /**
     * Specify whether temporary files should be deleted when the
     * view is rendered.
     * @param deleteTempFiles
     */
    public void setDeleteTempFiles(boolean deleteTempFiles);

    /**
     * Execute the script and return the list of output file mappings.
     * @param outputSubstitutions : the mapping of generated output files to view type id's in
     * which to display them.
     * @return
     * @throws Exception
     */
    public boolean runScript(VBox view, List<ParamReplacement> outputSubstitutions);
}

