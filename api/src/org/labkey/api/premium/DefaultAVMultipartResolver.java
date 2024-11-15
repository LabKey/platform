package org.labkey.api.premium;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.FileUtil;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.IOException;

public class DefaultAVMultipartResolver extends StandardServletMultipartResolver
{
    @Override
    public @NotNull MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException
    {
        try
        {
            for (Part part : request.getParts())
            {
                // Filter to just file uploads
                if (part.getSubmittedFileName() != null)
                {
                    FileUtil.checkAllowedFileName(part.getSubmittedFileName(), true);
                    validate(part);
                }
            }
        }
        catch (IOException | ServletException e)
        {
            throw new MultipartException("Couldn't get uploaded files", e);
        }
        return new StandardMultipartHttpServletRequest(request, false);
    }

    protected void validate(Part part)
    {
        //do nothing by default, but give subclasses a chance to override
    }
}
