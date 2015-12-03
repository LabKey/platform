package org.labkey.api.study.actions;

import org.labkey.api.qc.TsvDataExchangeHandler;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URIUtil;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;

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
            File transformFile = new File(TsvDataExchangeHandler.workingDirectory);
            String path = transformFile.getParent() + File.separator + form.getUploadAttemptId() + File.separator + form.getName();
            if(URIUtil.isDescendant(new File(transformFile.getParent()).toURI(), new File(path).toURI()))
            {
                HttpServletResponse response = getViewContext().getResponse();
                PageFlowUtil.streamFile(response, new File(path), true);
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
