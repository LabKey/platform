/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.view.InsertView;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.security.User;

import java.util.Map;
import java.util.Collection;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Sep 20, 2007
 */
public class StudyParticipantVisitResolverType implements ParticipantVisitResolverType
{
    public ParticipantVisitResolver createResolver(ExpRun run, Container targetStudyContainer, User user)
    {
        return new StudyParticipantVisitResolver(run.getContainer(), targetStudyContainer, user);
    }


    public ParticipantVisitResolver createResolver(Collection<ExpMaterial> inputMaterials,
                                                   Collection<ExpData> inputDatas,
                                                   Collection<ExpMaterial> outputMaterials,
                                                   Collection<ExpData> outputDatas,
                                                   Container runContainer,
                                                   Container targetStudyContainer, User user) throws IOException, ExperimentException
    {
        return new StudyParticipantVisitResolver(runContainer, targetStudyContainer, user);
    }

    public String getName()
    {
        return "SampleInfo";
    }

    public String getDescription()
    {
        return "Sample information in the data file (may be blank).";
    }

    public void render(RenderContext ctx) throws Exception
    {
    }

    public void addHiddenFormFields(AssayRunUploadContext<?> form, InsertView view)
    {
        // Don't need to add any form fields - the data's already all there
    }

    public void configureRun(AssayRunUploadContext<?> context, ExpRun run, Map<ExpData, String> inputDatas)
    {
        // Don't need to do anything - the data's already all there
    }

    public boolean collectPropertyOnUpload(AssayRunUploadContext<?> uploadContext, String propertyName)
    {
        return true;
    }
}
