/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.api.reports.report.r;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.reports.Report;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Handles substitution parameters for R reports by mapping symbolic names to
 * files on generation, as well as rendering the results.
 *
 * User: Karl Lum
 * Date: May 5, 2008
 */
public interface ParamReplacement
{
    /** unique identifier for this replacement type */
    public String getId();

    /** the name portion of the output replacement, must be unique within the R script */
    public String getName();
    public void setName(String name);

    /** optional replacement parameter properties */
    public Map<String, String> getProperties();
    public void setProperties(Map<String, String> properties);

    /** optional annotation to see if a remote executable (Rserve) is creating the parameter */
    public boolean isRemote();
    public void setRemote(boolean isRemote);

    /**
     * Convert the substitution to it's eventual generated file.
     * @param directory - the parent directory to create the generated file (if any, can be null)
     */
    public File convertSubstitution(File directory) throws Exception;

    public File getFile();
    public void setFile(File file);
    public String toString();

    public void setReport(Report report);
    public Report getReport();

    public void setHeaderVisible(boolean visible);
    public boolean getHeaderVisible();

    public HttpView render(ViewContext context);
    public @Nullable Thumbnail renderThumbnail(ViewContext context) throws IOException;
    public ScriptOutput renderAsScriptOutput() throws Exception;
}
