/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;
import org.labkey.api.view.InsertView;

import java.util.Collection;
import java.util.Map;

/**
 * User: jeckels
 * Date: Sep 20, 2007
 */
public class StudyParticipantVisitResolverType implements ParticipantVisitResolverType
{
    @Override
    public ParticipantVisitResolver createResolver(ExpRun run, Container targetStudyContainer, User user)
    {
        return new StudyParticipantVisitResolver(run.getContainer(), targetStudyContainer, user);
    }


    @Override
    public ParticipantVisitResolver createResolver(Collection<ExpMaterial> inputMaterials,
                                                   Collection<ExpData> inputDatas,
                                                   Collection<ExpMaterial> outputMaterials,
                                                   Collection<ExpData> outputDatas,
                                                   Container runContainer,
                                                   Container targetStudyContainer, User user)
    {
        return new StudyParticipantVisitResolver(runContainer, targetStudyContainer, user);
    }

    @Override
    public String getName()
    {
        return "SampleInfo";
    }

    @Override
    public String getDescription()
    {
        return "Sample information in the data file (may be blank).";
    }

    @Override
    public void render(RenderContext ctx) throws Exception
    {
    }

    @Override
    public void addHiddenFormFields(AssayRunUploadContext<?> form, InsertView view)
    {
        // Don't need to add any form fields - the data's already all there
    }

    @Override
    public void configureRun(AssayRunUploadContext<?> context, ExpRun run, Map<ExpData, String> inputDatas)
    {
        // Don't need to do anything - the data's already all there
    }

    @Override
    public boolean collectPropertyOnUpload(AssayRunUploadContext<?> uploadContext, String propertyName)
    {
        return true;
    }
}
