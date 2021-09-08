/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
package org.labkey.experiment.pipeline;

import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.labkey.api.exp.AbstractFileXarSource;
import org.labkey.api.pipeline.PipelineJob;

import java.io.File;
import java.nio.file.Path;

/*
* User: jeckels
* Date: Jul 30, 2008
*/
public class XarGeneratorSource extends AbstractFileXarSource
{
    public XarGeneratorSource(PipelineJob job, Path xarFile)
    {
        super(job);
        _xmlFile = xarFile;
    }

    @Override
    public ExperimentArchiveDocument getDocument()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getLogFile()
    {
        throw new UnsupportedOperationException();
    }
}
