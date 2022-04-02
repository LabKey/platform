/*
 * Copyright (c) 2012-2018 LabKey Corporation
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

import org.labkey.api.admin.AbstractFolderContext.ExportType;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportExportContext;
import org.labkey.api.data.Container;
import org.labkey.api.study.model.ParticipantMapper;
import org.labkey.api.study.writer.SimpleStudyWriter;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StudyWriterFactory implements FolderWriterFactory
{
    private static final String DEFAULT_DIRECTORY = "study";
    public static final String DATA_TYPE = FolderArchiveDataTypes.STUDY;

    @Override
    public FolderWriter create()
    {
        return new StudyFolderWriter();
    }

    public static class StudyFolderWriter extends BaseFolderWriter
    {
        @Override
        public String getDataType()
        {
            return DATA_TYPE;
        }

        @Override
        public boolean show(Container c)
        {
            // show the Study folder export option if this container or one of its children has a study
            StudyImpl study = StudyManager.getInstance().getStudy(c);
            if (study != null)
                return true;

            if (c.hasChildren())
            {
                for (Container child : c.getChildren())
                {
                    study = StudyManager.getInstance().getStudy(child);
                    if (study != null)
                        return true;
                }
            }

            return false;
        }

        @Override
        public boolean selectedByDefault(ExportType type)
        {
            return ExportType.ALL == type || ExportType.STUDY == type;
        }

        @Override
        public void initialize(FolderExportContext ctx)
        {
            super.initialize(ctx);

            Container c = ctx.getContainer();
            StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer());

            if (null != study && ctx.getContext(StudyExportContext.class) == null)
            {
                // If we enable new study formats then push ctx.getFormat() into StudyExportContext
                StudyExportContext exportCtx = new StudyExportContext(study, ctx.getUser(), c, ctx.getDataTypes(),
                        ctx.getPhiLevel(), new ParticipantMapper(study, ctx.isShiftDates(), ctx.isAlternateIds()), ctx.isMaskClinic(), ctx.getLoggerGetter());
                ctx.addContext(StudyExportContext.class, exportCtx);
            }
        }

        @Override
        public void write(Container c, ImportExportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            StudyExportContext exportCtx = ctx.getContext(StudyExportContext.class);

            if (null != exportCtx)
            {
                ctx.getXml().addNewStudy().setDir(DEFAULT_DIRECTORY);
                VirtualFile studyDir = vf.getDir(DEFAULT_DIRECTORY);

                StudyWriter writer = new StudyWriter();
                StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer()); // TODO: Shouldn't the StudyExportContext hold onto the study?!?
                writer.write(study, exportCtx, studyDir);
            }
        }

        @Override
        public Collection<Writer> getChildren(boolean sort, boolean forTemplate)
        {
            List<Writer> children = new ArrayList<>();
            for (SimpleStudyWriter writer : StudySerializationRegistry.get().getSimpleStudyWriters())
            {
                if (!forTemplate || writer.includeWithTemplate())
                    children.add(writer);
            }
            for (InternalStudyWriter writer : StudySerializationRegistry.get().getInternalStudyWriters())
            {
                if (!forTemplate || writer.includeWithTemplate())
                    children.add(writer);
            }

            if (sort)
            {
                children.sort((o1, o2) ->
                {
                    String str1 = o1.getDataType();
                    String str2 = o2.getDataType();

                    if (str1 == null && str2 == null) return 0;
                    if (str1 == null) return 1;
                    if (str2 == null) return -1;

                    return str1.compareToIgnoreCase(str2);
                });
            }
            return children;
        }
    }
}
