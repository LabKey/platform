/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

public class TopLevelStudyPropertiesImporter implements InternalStudyImporter
{
    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.TOP_LEVEL_STUDY_PROPERTIES;
    }

    @Override
    public String getDescription()
    {
        return getDataType().toLowerCase() + " (label, start/end date, description, etc.)";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        // TODO: update CreateChildStudyAction.createNewStudy usage to use this importer

        StudyImpl origStudy = StudyManager.getInstance().getStudy(ctx.getContainer());
        if (origStudy != null)
        {
            ctx.getLogger().info("Loading " + getDescription());

            StudyImpl study = origStudy.createMutable();
            StudyDocument.Study studyXml = ctx.getXml();

            // TODO: Change these props and save only if values have changed
            if (studyXml.isSetLabel() && !StringUtils.isEmpty(studyXml.getLabel()))
                study.setLabel(studyXml.getLabel());

            if (studyXml.isSetSecurityType())
                study.setSecurityType(SecurityType.valueOf(studyXml.getSecurityType().toString()));

            if (studyXml.isSetDefaultTimepointDuration())
                study.setDefaultTimepointDuration(studyXml.getDefaultTimepointDuration());

            if (studyXml.isSetStartDate())
                study.setStartDate(studyXml.getStartDate().getTime());

            if (studyXml.isSetEndDate())
                study.setEndDate(studyXml.getEndDate().getTime());

            if (studyXml.getSubjectColumnName() != null)
                study.setSubjectColumnName(studyXml.getSubjectColumnName());

            if (studyXml.getSubjectNounSingular() != null)
                study.setSubjectNounSingular(studyXml.getSubjectNounSingular());

            if (studyXml.getSubjectNounPlural() != null)
                study.setSubjectNounPlural(studyXml.getSubjectNounPlural());

            if (studyXml.isSetInvestigator())
                study.setInvestigator(studyXml.getInvestigator());

            if (studyXml.isSetGrant())
                study.setGrant(studyXml.getGrant());

            if (studyXml.isSetSpecies())
                study.setSpecies(studyXml.getSpecies());

            if (ctx.isDataTypeSelected(StudyArchiveDataTypes.ASSAY_SCHEDULE) && studyXml.isSetAssayPlan())
                study.setAssayPlan(studyXml.getAssayPlan());

            // Issue 15789: Carriage returns in protocol description are not round-tripped
            if (studyXml.isSetDescription())
                study.setDescription(studyXml.getDescription());
            else if (studyXml.isSetStudyDescription() && studyXml.getStudyDescription().isSetDescription())
                study.setDescription(studyXml.getStudyDescription().getDescription());

            if (studyXml.isSetParticipantAliasDataset())
            {
                StudyDocument.Study.ParticipantAliasDataset participantAliasDataset = studyXml.getParticipantAliasDataset();
                study.setParticipantAliasDatasetId(participantAliasDataset.getDatasetId());
                study.setParticipantAliasProperty(participantAliasDataset.getAliasProperty());
                study.setParticipantAliasSourceProperty(participantAliasDataset.getSourceProperty());
            }

            // Issue 15789: Carriage returns in protocol description are not round-tripped
            if (studyXml.isSetDescriptionRendererType())
                study.setDescriptionRendererType(studyXml.getDescriptionRendererType());
            else if (studyXml.isSetStudyDescription() && studyXml.getStudyDescription().isSetRendererType())
                study.setDescriptionRendererType(studyXml.getStudyDescription().getRendererType());

            if (studyXml.getAlternateIdPrefix() != null)
                study.setAlternateIdPrefix(studyXml.getAlternateIdPrefix());

            if (studyXml.isSetAlternateIdDigits())
                study.setAlternateIdDigits(studyXml.getAlternateIdDigits());

            StudyManager.getInstance().updateStudy(ctx.getUser(), study);

            ctx.getLogger().info("Done importing " + getDescription());
        }
    }
}
