/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:43:38 AM
 */
public class QcStateWriter implements InternalStudyWriter
{
    public static final String DATA_TYPE = "QC State Settings";

    public String getSelectionText()
    {
        return DATA_TYPE;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        // TODO: Write out the actual QC states
        StudyDocument.Study.QcStates qcStatesXml = ctx.getXml().addNewQcStates();
        qcStatesXml.setShowPrivateDataByDefault(study.isShowPrivateDataByDefault());
    }
}
