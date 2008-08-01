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
package org.labkey.experiment.pipeline;

import org.labkey.api.exp.AbstractFileXarSource;
import org.labkey.api.pipeline.PipelineJob;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.apache.xmlbeans.XmlException;

import java.io.IOException;
import java.io.File;

/*
* User: jeckels
* Date: Jul 30, 2008
*/
public class XarGeneratorSource extends AbstractFileXarSource
{
    public XarGeneratorSource(PipelineJob job, File xarFile)
    {
        super(job);
        _xmlFile = xarFile;
    }

    public ExperimentArchiveDocument getDocument() throws XmlException, IOException
    {
        throw new UnsupportedOperationException();
    }

    public File getLogFile() throws IOException
    {
        throw new UnsupportedOperationException();
    }
}