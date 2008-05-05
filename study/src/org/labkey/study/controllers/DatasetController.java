package org.labkey.study.controllers;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.StudyService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateAction extends InsertUpdateAction
    {

        protected boolean isInsert()
        {
            return false;
        }

        protected NavTree appendExtraNavTrail(NavTree root)
        {
            return root.addChild("Update Dataset Entry");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class InsertAction extends InsertUpdateAction
    {

        protected boolean isInsert()
        {
            return true;
        }

        protected NavTree appendExtraNavTrail(NavTree root)
        {
            return root.addChild("Insert new Dataset Entry");
        }
    }

    public abstract class InsertUpdateAction extends FormViewAction<EditDatasetRowForm>
    {
        protected abstract boolean isInsert();
        protected abstract NavTree appendExtraNavTrail(NavTree root);

        public ModelAndView getView(EditDatasetRowForm form, boolean reshow, BindException errors) throws Exception
        {
            DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
            if (null == ds)
            {
                redirectTypeNotFound(form.getDatasetId());
                return null;
            }

            TableInfo datasetTable = ds.getTableInfo(getUser());
            DatasetQueryUpdateForm updateForm = new DatasetQueryUpdateForm(datasetTable, getViewContext().getRequest());

            UpdateView view = new UpdateView(updateForm, errors);
            DataRegion dataRegion = view.getDataRegion();
            dataRegion.addHiddenFormField("datasetId", Integer.toString(form.getDatasetId()));
            ActionURL cancelURL = new ActionURL(StudyController.DatasetAction.class, getContainer());
            cancelURL.addParameter("datasetId", form.getDatasetId());
            ButtonBar buttonBar = dataRegion.getButtonBar(DataRegion.MODE_UPDATE);
            buttonBar = new ButtonBar(buttonBar); // need to copy since the original is read-only
            buttonBar.add(1, new ActionButton(cancelURL.getLocalURIString(), "Cancel", DataRegion.MODE_UPDATE, ActionButton.Action.GET));
            dataRegion.setButtonBar(buttonBar);

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
            int datasetId = form.getDatasetId();
            DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
            if (null == ds)
            {
                redirectTypeNotFound(form.getDatasetId());
                return false;
            }

            TableInfo datasetTable = ds.getTableInfo(getUser());
            DatasetQueryUpdateForm updateForm = new DatasetQueryUpdateForm(datasetTable, getViewContext().getRequest());
            updateForm.populateValues(errors);

            if (errors.hasErrors())
                return false;

            // The form we have stores our new values in a map with "quf_" as a prefix. Clean that up,
            // and only use those fields. This excludes read-only data.
            Map<String,Object> data = getDataMap(updateForm);
            List<String> importErrors = new ArrayList<String>();

            String newLsid;
            if (isInsert())
            {
                newLsid = StudyService.get().insertDatasetRow(getContainer(), datasetId, data, importErrors);
            }
            else
            {
                newLsid = StudyService.get().updateDatasetRow(getContainer(), datasetId, form.getLsid(), data, importErrors);
            }

            if (importErrors.size() > 0)
            {
                for (String error : importErrors)
                {
                    errors.reject("update", error);
                }
                return false;
            }
            // If this results in a change to participant ID or the visit itself,
            // we need to recompute the participant-visit map
            if (isInsert() || !newLsid.equals(form.getLsid()))
            {
                StudyManager.getInstance().recomputeStudyDataVisitDate(getStudy());
                StudyManager.getInstance().getVisitManager(getStudy()).updateParticipantVisits();
            }

            return true;
        }

        // query update forms have all user data stored with the prefix "quf_".
        // Clear that off and return only the user data
        private Map<String,Object> getDataMap(DatasetQueryUpdateForm form)
        {
            Map<String,Object> data = new HashMap<String,Object>();
            for (Map.Entry<String,Object> entry : form.getTypedValues().entrySet())
            {
                String key = entry.getKey();
                if (key.startsWith("quf_"))
                {
                    key = key.substring(4);
                    data.put(key, entry.getValue());
                }
            }
            return data;
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
