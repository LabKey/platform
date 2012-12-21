/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.r.AbstractParamReplacement;
import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Dax Hawkins
 * Date: Dec 14, 2012
 */
public abstract class DownloadParamReplacement extends AbstractParamReplacement
{
    protected static final String UNABlE_TO_RENDER = "Unable to render this output, no report associated with this replacement param";

    public DownloadParamReplacement(String id)
    {
        super(id);
    }

    protected File convertSubstitution(File directory, String extension)
    {
        if (directory != null)
            _file = new File(directory, getName().concat(extension));

        return _file;
    }

    protected ScriptOutput renderAsScriptOutput(DownloadOutputView view, ScriptOutput.ScriptOutputType scriptOutputType) throws Exception
    {
        String downloadUrl  = view.renderInternalAsString();
        return new ScriptOutput(scriptOutputType, getName(), downloadUrl);
    }

    protected ScriptOutput renderAsScriptOutputError()
    {
        return new ScriptOutput(ScriptOutput.ScriptOutputType.error, getName(),
                DownloadParamReplacement.UNABlE_TO_RENDER);
    }
}
