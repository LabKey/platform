package org.labkey.study.importer;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 27, 2011
 * Time: 1:36:40 PM
 */
public class ProtocolDocumentImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "protocol documents";
    }

    @Override
    public void process(StudyImpl study, ImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        StudyDocument.Study.ProtocolDocs protocolXml = ctx.getStudyXml().getProtocolDocs();

        if (null != protocolXml)
        {
            VirtualFile folder = root.getDir(protocolXml.getDir());
            List<AttachmentFile> attachments = new ArrayList<AttachmentFile>();
            List<String> existing = new ArrayList<String>();

            for (Attachment attachment : study.getProtocolDocuments())
                existing.add(attachment.getName());

            for (String fileName : folder.list())
            {
                ctx.getLogger().info("importing protocol document: " + fileName);

                if (existing.contains(fileName))
                    study.removeProtocolDocument(fileName, ctx.getUser());
                
                attachments.add(new InputStreamAttachmentFile(folder.getInputStream(fileName), fileName));
            }

            study.attachProtocolDocument(attachments, ctx.getUser());
            ctx.getLogger().info("finished importing protocol documents");
        }
    }
}
