/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.security.permissions.StudyPermissionExporter;
import org.labkey.study.xml.StudyDocument;
import org.labkey.studySecurityPolicy.xml.StudySecurityPolicyDocument;

public class StudySecurityPolicyWriter implements InternalStudyWriter
{
    public static final String FILENAME = "security_policy.xml";

    @Nullable
    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.STUDY_SECURITY_POLICY;
    }

    @Override
    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.StudySecurity securityXml = studyXml.addNewStudySecurity();
        securityXml.setFile(FILENAME);

        StudyPermissionExporter exporter = new StudyPermissionExporter();
        StudySecurityPolicyDocument policyDocument = exporter.getStudySecurityPolicyDocument(study, StudyWriter.includeDatasetMetadata(ctx));

        root.saveXmlBean(FILENAME, policyDocument);
    }
}
