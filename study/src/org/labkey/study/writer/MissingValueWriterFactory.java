package org.labkey.study.writer;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.MvUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.xml.MissingValueIndicatorsType;

import java.util.Map;

/**
 * User: cnathe
 * Date: May 1, 2012
 */
public class MissingValueWriterFactory implements FolderWriterFactory
{
    @Override
    public FolderWriter create()
    {
        return new MissingValueWriter();
    }

    public class MissingValueWriter extends BaseFolderWriter
    {
        @Override
        public String getSelectionText()
        {
            return "Missing value indicators";
        }

        @Override
        public boolean includeInType(AbstractFolderContext.ExportType type)
        {
            return AbstractFolderContext.ExportType.ALL == type || AbstractFolderContext.ExportType.STUDY == type;
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            Map<String, String> mvMap = MvUtil.getIndicatorsAndLabels(c);
            MissingValueIndicatorsType mvXml = ctx.getXml().addNewMissingValueIndicators();
            for (Map.Entry<String, String> mv : mvMap.entrySet())
            {
                MissingValueIndicatorsType.MissingValueIndicator indXml = mvXml.addNewMissingValueIndicator();
                indXml.setIndicator(mv.getKey());
                indXml.setLabel(mv.getValue());
            }            
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return true;
        }
    }
}
