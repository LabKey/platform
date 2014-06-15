/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.ParticipantMapper;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudySnapshot.SnapshotSettings;
import org.labkey.study.writer.StudyExportContext;
import org.labkey.study.writer.StudyWriterFactory;

import java.util.Collections;
import java.util.Set;

/**
 * User: adam
 * Date: 9/27/12
 * Time: 10:17 PM
 */
public class SpecimenRefreshPipelineJob extends AbstractStudyPiplineJob
{
    private final SnapshotSettings _settings;

    public SpecimenRefreshPipelineJob(Container source, Container destination, User user, ActionURL url, PipeRoot root, SnapshotSettings snapshotSettings)
    {
        super(source, user, url, root);
        _settings = snapshotSettings;
        _dstContainer = destination;
    }

    @Override
    protected String getLogName()
    {
        return "specimenRefresh";
    }

    @Override
    public String getDescription()
    {
        return "Refresh Specimen Data";
    }

    @Override
    public boolean run(ViewContext context)
    {
        try
        {
            _sourceStudy = StudyManager.getInstance().getStudy(getContainer());

            if (null == _sourceStudy)
                throw new NotFoundException("Source study no longer exists");

            StudyImpl destStudy = StudyManager.getInstance().getStudy(_dstContainer);

            if (null == destStudy)
                throw new NotFoundException("Destination study no longer exists");

            setStatus("REFRESHING SPECIMENS");
            MemoryVirtualFile vf = new MemoryVirtualFile();
            User user = getUser();
            Set<String> dataTypes = PageFlowUtil.set(StudyWriterFactory.DATA_TYPE, "Specimens");  // TODO: Define a constant for specimens

            FolderExportContext ctx = new FolderExportContext(user, _sourceStudy.getContainer(), dataTypes, "new", false,
                    _settings.isRemoveProtectedColumns(), _settings.isShiftDates(), _settings.isUseAlternateParticipantIds(),
                    _settings.isMaskClinic(), new PipelineJobLoggerGetter(this));

            Set<DataSetDefinition> datasets = Collections.emptySet();

            StudyExportContext studyCtx = new StudyExportContext(_sourceStudy, user, _sourceStudy.getContainer(),
                    false, dataTypes, _settings.isRemoveProtectedColumns(),
                    new ParticipantMapper(_sourceStudy, _settings.isShiftDates(), _settings.isUseAlternateParticipantIds()),
                    _settings.isMaskClinic(), datasets, new PipelineJobLoggerGetter(this)
            );

            studyCtx.setVisitIds(_settings.getVisits());
            studyCtx.setParticipants(_settings.getParticipants());

            ctx.addContext(StudyExportContext.class, studyCtx);

            // export specimens from the parent study
            info("Exporting specimen data from parent study.");
            exportFromParentStudy(ctx, vf);

            // import the specimen data
            info("Importing specimen data into child study.");
            importSpecimenData(destStudy, vf);
        }
        catch (Exception e)
        {
            error("Specimen refresh failed", e);
            return false;
        }

        return true;
    }
}
