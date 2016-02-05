/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.ExportDirType;
import org.labkey.study.xml.SecurityType;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.TimepointType;

import java.util.Calendar;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 9:39:31 AM
 */

// This writer is largely responsible for study.xml.  It constructs the StudyDocument (xml bean used to read/write study.xml)
//  that gets added to the StudyExportContext, writes the top-level study attributes, and writes out the bean when it's complete.
//  However, each top-level writer is responsible for their respective elements in study.xml -- VisitMapWriter handles "visits"
//  element, DatasetWriter is responsible for "datasets" element, etc.  As a result, StudyXmlWriter must be invoked after all
//  writers are done modifying the StudyDocument.  Locking the StudyDocument after writing it out helps ensure this ordering.
class StudyXmlWriter implements InternalStudyWriter
{
    private static final String PROPERTIES_DIRECTORY = "properties";

    public String getDataType()
    {
        return null;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getXml();

        // Insert standard comment explaining where the data lives, who exported it, and when
        XmlBeansUtil.addStandardExportComment(studyXml, ctx.getContainer(), ctx.getUser());

        // Archive version
        studyXml.setArchiveVersion(ModuleLoader.getInstance().getCoreModule().getVersion());

        // Study attributes
        studyXml.setLabel(study.getLabel());
        studyXml.setTimepointType(TimepointType.Enum.forString(study.getTimepointType().toString()));
        studyXml.setSubjectNounSingular(study.getSubjectNounSingular());
        studyXml.setSubjectNounPlural(study.getSubjectNounPlural());
        studyXml.setSubjectColumnName(study.getSubjectColumnName());
        studyXml.setInvestigator(study.getInvestigator());
        studyXml.setGrant(study.getGrant());
        studyXml.setSpecies(study.getSpecies());
        studyXml.setAlternateIdPrefix(study.getAlternateIdPrefix());
        studyXml.setAlternateIdDigits(study.getAlternateIdDigits());
        studyXml.setDefaultTimepointDuration(study.getDefaultTimepointDuration());

        if (study.getParticipantAliasDatasetId() != null && study.getParticipantAliasProperty() != null && study.getParticipantAliasSourceProperty() != null)
        {
            StudyDocument.Study.ParticipantAliasDataset participantAliasDataset = studyXml.addNewParticipantAliasDataset();
            participantAliasDataset.setDatasetId(study.getParticipantAliasDatasetId());
            participantAliasDataset.setAliasProperty(study.getParticipantAliasProperty());
            participantAliasDataset.setSourceProperty(study.getParticipantAliasSourceProperty());
        }

        // Issue 15789: Carriage returns in protocol description are not round-tripped
        StudyDocument.Study.StudyDescription descriptionXml = studyXml.addNewStudyDescription();
        descriptionXml.setRendererType(study.getDescriptionRendererType());
        descriptionXml.setDescription(study.getDescription());

        if (ctx.isDataTypeSelected(StudyArchiveDataTypes.ASSAY_SCHEDULE) && null != study.getAssayPlan())
        {
            studyXml.setAssayPlan(study.getAssayPlan());
        }

        if (null != study.getStartDate())
        {
            Calendar startDate = Calendar.getInstance();
            startDate.setTime(study.getStartDate());
            studyXml.setStartDate(startDate);        // TODO: TimeZone?
        }

        if (null != study.getEndDate())
        {
            Calendar endDate = Calendar.getInstance();
            endDate.setTime(study.getEndDate());
            studyXml.setEndDate(endDate);        // TODO: TimeZone?
        }

        studyXml.setSecurityType(SecurityType.Enum.forString(study.getSecurityType().name()));

        ExportDirType dir = studyXml.addNewProperties();
        dir.setDir(PROPERTIES_DIRECTORY);

        // Save the study.xml file.  This gets called last, after all other writers have populated the other sections.
        vf.saveXmlBean("study.xml", ctx.getDocument());

        ctx.lockDocument();

        // export the study objectives and personnel tables
        new StudyPropertiesWriter().write(study, ctx, vf.getDir(PROPERTIES_DIRECTORY));
    }

    public static StudyDocument getStudyDocument()
    {
        StudyDocument doc = StudyDocument.Factory.newInstance();
        doc.addNewStudy();
        return doc;
    }
}
