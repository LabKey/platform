package org.labkey.api.study.actions;

import org.apache.commons.io.IOUtils;
import org.labkey.api.qc.TsvDataExchangeHandler;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by Marty on 11/16/2015.
 */

@RequiresPermission(InsertPermission.class)
public class TransformResultsAction extends BaseAssayAction<TransformResultsAction.TransformResultsForm>
{

    @Override
    public ModelAndView getView(TransformResultsForm form, BindException errors) throws Exception
    {

        if (null != TsvDataExchangeHandler.workingDirectory)
        {
            String path =  TsvDataExchangeHandler.workingDirectory.substring(0,TsvDataExchangeHandler.workingDirectory.lastIndexOf('\\') + 1)
                   + form.getUploadAttemptId() + "\\" + form.getName();

            HttpServletResponse response = getViewContext().getResponse();
            OutputStream out;

            try(InputStream in = new FileInputStream(new File(path)))
            {
                response.setContentType("text/plain");
                response.setHeader("Content-disposition", "attachment; filename=\"" + form.getName() +"\"");

                out = response.getOutputStream();
                IOUtils.copy(in, out);
            }
        }

        return null;
    }

    public static class TransformResultsForm extends ProtocolIdForm
    {
        private String _name;
        private String _uploadAttemptId;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getUploadAttemptId()
        {
            return _uploadAttemptId;
        }

        public void setUploadAttemptId(String uploadAttemptId)
        {
            _uploadAttemptId = uploadAttemptId;
        }
    }
}
