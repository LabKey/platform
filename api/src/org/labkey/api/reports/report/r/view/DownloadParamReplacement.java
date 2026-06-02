/*
 * Copyright (c) 2012-2026 LabKey Corporation
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
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.vfs.FileLike;

import java.io.IOException;

/**
 * User: Dax Hawkins
 * Date: Dec 14, 2012
 */
public abstract class DownloadParamReplacement extends AbstractParamReplacement
{
    protected static final String UNABLE_TO_RENDER = "Unable to render this output, no report associated with this replacement param";

    public DownloadParamReplacement(String id)
    {
        super(id);
    }

    protected final FileLike getSubstitution(FileLike directory, String extension)
    {
        String fileName;
        String tokenName = getName();
        FileLike file = null;
        if (tokenName != null)
        {
            String tokenExtension = FileUtil.getExtension(tokenName);
            if (tokenExtension != null)
                fileName = tokenName;
            else
                fileName = getName().concat(extension);

            if (directory != null)
                file = directory.resolveFile(Path.parse(fileName));
        }
        if (file != null)
            addFile(file);
        return file;
    }

    protected ScriptOutput renderAsScriptOutput(FileLike file, DownloadOutputView view, ScriptOutput.ScriptOutputType scriptOutputType) throws IOException
    {
        String downloadUrl = view.renderInternalAsString(file);

        if (null != downloadUrl)
            return new ScriptOutput(scriptOutputType, getName(), downloadUrl);

        return null;
    }

    protected ScriptOutput renderAsScriptOutputError()
    {
        return new ScriptOutput(ScriptOutput.ScriptOutputType.error, getName(),
                DownloadParamReplacement.UNABLE_TO_RENDER);
    }
}
