package org.labkey.study.writer;

import org.apache.commons.io.IOUtils;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 27, 2011
 * Time: 1:14:06 PM
 */
public class ProtocolDocumentWriter implements InternalStudyWriter
{
    public static final String DATA_TYPE = "Protocol Documents";
    public static final String DOCUMENT_FOLDER = "protocolDocs";

    @Override
    public String getSelectionText()
    {
        return DATA_TYPE;
    }

    @Override
    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        List<Attachment> documents = study.getProtocolDocuments();

        if (!documents.isEmpty())
        {
            VirtualFile folder = vf.getDir(DOCUMENT_FOLDER);
            AttachmentParent parent = new StudyImpl.ProtocolDocumentAttachmentParent(study.getContainer(), study.getProtocolDocumentEntityId());

            StudyDocument.Study.ProtocolDocs protocolXml = ctx.getStudyXml().addNewProtocolDocs();
            protocolXml.setDir(DOCUMENT_FOLDER);

            for (Attachment doc : documents)
            {
                InputStream is = null;
                OutputStream os = null;

                try
                {
                    is = AttachmentService.get().getInputStream(parent, doc.getName());
                    os = folder.getOutputStream(doc.getName());
                    FileUtil.copyData(is, os);
                }
                finally
                {
                    IOUtils.closeQuietly(is);
                    IOUtils.closeQuietly(os);
                }
            }
        }
    }
}
