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
package org.labkey.study.importer;

import org.labkey.api.writer.VirtualFile;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:36:25 PM
 */
public class QcStatesImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "QC States Importer";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer());
        // TODO: Generalize to all qc state properties
        StudyDocument.Study.QcStates qcStates = ctx.getXml().getQcStates();

        if (null != qcStates)
        {
            ctx.getLogger().info("Loading QC states");
            StudyController.ManageQCStatesForm qcForm = new StudyController.ManageQCStatesForm();
            qcForm.setShowPrivateDataByDefault(qcStates.getShowPrivateDataByDefault());
            StudyController.updateQcState(study, ctx.getUser(), qcForm);
        }
    }
}
