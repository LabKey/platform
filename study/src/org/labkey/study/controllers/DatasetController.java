package org.labkey.study.controllers;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UpdateView;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * User: jgarms
 */
public class DatasetController extends BaseStudyController
{
    private static ActionResolver ACTION_RESOLVER = new DefaultActionResolver(DatasetController.class);

    public DatasetController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class UpdateAction extends FormViewAction<EditDatasetRowForm>
    {
        public ModelAndView getView(EditDatasetRowForm form, boolean reshow, BindException errors) throws Exception
        {
            DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
            if (null == ds)
                redirectTypeNotFound(form.getDatasetId());

            TableInfo datasetTable = ds.getTableInfo(getUser());
            DatasetQueryUpdateForm updateForm = new DatasetQueryUpdateForm(datasetTable, getViewContext().getRequest());

            UpdateView view = new UpdateView(updateForm, errors);
            view.getDataRegion().addHiddenFormField("datasetId", Integer.toString(form.getDatasetId()));
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                Study study = getStudy();
                root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
                root.addChild("Study Overview", new ActionURL(StudyController.OverviewAction.class, getContainer()));
            }
            catch (ServletException e) {}
            return root;
        }

        public void validateCommand(EditDatasetRowForm target, Errors errors) {}

        public boolean handlePost(EditDatasetRowForm form, BindException errors) throws Exception
        {
            DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
            if (null == ds)
                redirectTypeNotFound(form.getDatasetId());

            TableInfo datasetTable = ds.getTableInfo(getUser());
            DatasetQueryUpdateForm updateForm = new DatasetQueryUpdateForm(datasetTable, getViewContext().getRequest());
            updateForm.populateValues(errors);

            if (errors.hasErrors())
                return false;

            // Delete the existing row, and then insert a new one. This guarantees
            // that our new lsid is correct, since it is a concatenation of dataset row data
            Study study = getStudy();
            String lsid = form.getLsid();
            StudyManager.getInstance().deleteDatasetRows(study, ds, Collections.singletonList(lsid));

            // The form we have stores our new values in a map with "quf_" as a prefix. Clean that up,
            // and only use those fields. This excludes read-only data.
            String tsv = createTSV(updateForm);
            List<String> importErrors = new ArrayList<String>();
            StudyManager.getInstance().importDatasetTSV(study, ds, tsv, System.currentTimeMillis(),
                    Collections.<String,String>emptyMap(), importErrors, true);
            if (importErrors.size() > 0)
            {
                for (String error : importErrors)
                {
                    errors.reject("update", error);
                }
                return false;
            }


            return true;
            
        }

        private String createTSV(DatasetQueryUpdateForm form)
        {
            Map<String,Object> map = new LinkedHashMap<String,Object>();
            StringBuilder sb = new StringBuilder();

            for (Map.Entry<String,Object> entry : form.getTypedValues().entrySet())
            {
                String key = entry.getKey();
                if (key.startsWith("quf_"))
                {
                    key = key.substring(4);
                    map.put(key, entry.getValue());
                    sb.append(key).append('\t');
                }
            }
            sb.append(System.getProperty("line.separator"));

            for (Object val : map.values())
            {
                sb.append(val).append('\t');
            }
            return sb.toString();
        }

        public ActionURL getSuccessURL(EditDatasetRowForm form)
        {
            ActionURL url = new ActionURL(StudyController.DatasetAction.class, getContainer());
            url.addParameter("datasetId", form.getDatasetId());
            return url;
        }

    }

    public static class EditDatasetRowForm
    {
        private String lsid;
        private int datasetId;

        public String getLsid() {return lsid;}
        public void setLsid(String lsid) {this.lsid = lsid;}
        public int getDatasetId() {return datasetId;}
        public void setDatasetId(int datasetId) {this.datasetId = datasetId;}
    }

    public static class DatasetQueryUpdateForm extends QueryUpdateForm
    {
        public DatasetQueryUpdateForm(TableInfo table, HttpServletRequest request)
        {
            super(table, request);
        }
    }

}
