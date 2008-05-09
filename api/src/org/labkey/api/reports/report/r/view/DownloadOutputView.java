package org.labkey.api.reports.report.r.view;

import org.apache.struts.upload.FormFile;
import org.labkey.api.attachments.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;

import java.io.*;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 7, 2008
 */
public abstract class DownloadOutputView extends ROutputView
{
    private String _fileType;
    private AttachmentParent _parent;

    DownloadOutputView(ParamReplacement param, AttachmentParent parent, String fileType)
    {
        super(param);
        _parent = parent;
        _fileType = fileType;
        setLabel("Attachment output");
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        if (getFile() != null && getFile().exists() && (getFile().length() > 0))
        {
            if (_parent.getEntityId() != null)
            {
                MimeMap mimeMap = new MimeMap();
                DownloadFormFile form = new DownloadFormFile(getFile());
                form.setContentType(mimeMap.getContentTypeFor(getFile().getName()));
                AttachmentService.get().deleteAttachment(_parent, getFile().getName());
                AttachmentService.get().addAttachments(getViewContext().getUser(), _parent, Collections.singletonList((AttachmentFile)new StrutsAttachmentFile(form)));
            }
            out.write("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"1\">");
            renderTitle(model, out);
            if (isCollapse())
                out.write("<tr style=\"display:none\"><td>");
            else
                out.write("<tr><td>");

            if (_parent.getEntityId() != null)
            {
                for (Attachment a : AttachmentService.get().getAttachments(_parent))
                {
                    if (getFile().getName().equals(a.getName()))
                    {
                        out.write("<a href=\"");
                        out.write(a.getDownloadUrl("reports"));
                        out.write("\">");
                        out.write(_fileType);
                        out.write(" output file (click to download)</a>");
                        break;
                    }
                }
            }
            else
            {
                // file hasn't been saved yet
                String key = "temp:" + GUID.makeGUID();
                getViewContext().getRequest().getSession(true).setAttribute(key, getFile());

                out.write("<a href=\"");
                out.write(PageFlowUtil.urlProvider(ReportUrls.class).urlStreamFile(getViewContext().getContainer()).
                        addParameters(PageFlowUtil.map("sessionKey", key, "deleteFile", "false", "attachment", "true")).getLocalURIString());
                out.write("\">");
                out.write(_fileType);
                out.write(" output file (click to download)</a>");
            }
            out.write("</td></tr>");
            out.write("</table>");
        }
    }

    public static class DownloadFormFile implements FormFile
    {
        private File _file;
        private byte[] _contentData;
        private String _contentType = "text/plain";

        public DownloadFormFile(File file)
        {
            _file = file;
            try {
                _contentData = PageFlowUtil.getStreamContentsAsString(new FileInputStream(file)).getBytes();
            }
            catch (FileNotFoundException fnf)
            {
                _contentData = new byte[0];
            }
        }

        public String getContentType()
        {
            return _contentType;
        }

        public void setContentType(String contentType)
        {
            _contentType = contentType;
        }

        public int getFileSize()
        {
            return _contentData.length;
        }

        public void setFileSize(int i)
        {
            throw new UnsupportedOperationException();
        }

        public String getFileName()
        {
            return _file.getName();
        }

        public void setFileName(String string)
        {
            throw new UnsupportedOperationException();
        }

        public byte[] getFileData() throws IOException
        {
            return _contentData;
        }

        public InputStream getInputStream() throws IOException
        {
            return new BufferedInputStream(new ByteArrayInputStream(_contentData));
        }

        public void destroy()
        {
            throw new UnsupportedOperationException();
        }
    }
}
