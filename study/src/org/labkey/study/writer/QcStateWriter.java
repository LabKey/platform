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

import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;
import org.labkey.api.util.VirtualFile;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:43:38 AM
 */
public class QcStateWriter implements Writer<StudyImpl>
{
    public String getSelectionText()
    {
        return "QC State Settings";
    }

    public void write(StudyImpl study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        // TODO: Write other QC properties
        StudyDocument.Study.QcStates qcStatesXml = ctx.getStudyXml().addNewQcStates();
        qcStatesXml.setShowPrivateDataByDefault(study.isShowPrivateDataByDefault());
    }
}
