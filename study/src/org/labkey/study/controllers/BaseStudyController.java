package org.labkey.study.controllers;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.data.Container;
import org.labkey.api.view.*;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.view.BaseStudyPage;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 13, 2007
 */
public abstract class BaseStudyController extends SpringActionController
{
    public Study getStudy(boolean allowNullStudy) throws ServletException
    {
        // UNDONE: see https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=1137
        Container c = getContainer();
        Study study = StudyManager.getInstance().getStudy(c);
        if (!allowNullStudy && study == null)
        {
            // redirect to the study home page, where admins will see a 'create study' button,
            // and non-admins will simply see a message that no study exists.
            HttpView.throwRedirect(new ActionURL(StudyController.BeginAction.class, c));
        }
        return study;
    }

    public Study getStudy() throws ServletException
    {
        return getStudy(false);
    }

    protected BaseViewAction initAction(BaseViewAction parent, BaseViewAction action)
    {
        action.setViewContext(parent.getViewContext());
        action.setPageConfig(parent.getPageConfig());

        return action;
    }

    protected NavTree _appendManageStudy(NavTree root)
    {
        try {
            Study study = getStudy();
            root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
            root.addChild("Manage Study", new ActionURL(StudyController.ManageStudyAction.class, getContainer()));
        }
        catch (ServletException e)
        {
        }
        return root;
    }

    protected NavTree _appendNavTrailDatasetAdmin(NavTree root)
    {
        _appendManageStudy(root);
        return root.addChild("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, getContainer()));
    }

    protected NavTree _appendNavTrail(NavTree root)
    {
        return _appendNavTrail(root, null);
    }

    protected NavTree _appendNavTrail(NavTree root, Integer cohortId)
    {
        try {
            Study study = getStudy();
            root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
            ActionURL overviewURL = new ActionURL(StudyController.OverviewAction.class, getContainer());
            if (cohortId != null)
                overviewURL.addParameter("cohortId", cohortId.intValue());
            root.addChild("Study Overview", overviewURL);
        }
        catch (ServletException e)
        {
        }
        return root;
    }

    protected NavTree _appendNavTrail(NavTree root, int datasetId, int visitId)
    {
        return _appendNavTrail(root, datasetId, visitId, -1);
    }

    protected NavTree _appendNavTrail(NavTree root, int datasetId, int visitId, int cohortId)
    {
        try {
            Study study = getStudy();
            root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
            root.addChild("Study Overview", new ActionURL(StudyController.OverviewAction.class, getContainer()));
            _appendDataset(root, study, datasetId, visitId, cohortId);
        }
        catch (ServletException e)
        {
        }
        return root;
    }

    protected NavTree _appendDataset(NavTree root, Study study, int datasetId, int visitRowId, int cohortId)
    {
        if (datasetId > 0)
        {
            Visit visit = null;
            if (visitRowId > 0)
                visit = StudyManager.getInstance().getVisitForRowId(study, visitRowId);

            DataSetDefinition dataSet = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
            if (dataSet != null)
            {
                StringBuilder label = new StringBuilder();
                label.append("Dataset: ");
                if (dataSet.getLabel() != null)
                    label.append(dataSet.getLabel());
                else
                    label.append("CRF/Assay ").append(dataSet.getDataSetId());

                if (visit != null)
                    label.append(", ").append(visit.getDisplayString());
                else
                    label.append(", All Visits");

                ActionURL datasetUrl = new ActionURL(StudyController.DatasetAction.class, getContainer()).
                        addParameter(DataSetDefinition.DATASETKEY, datasetId);
                if (cohortId >= 0)
                    datasetUrl.addParameter("cohortId", cohortId);
                root.addChild(label.toString(), datasetUrl.getLocalURIString());
            }
        }
        return root;
    }

    public static class StudyJspView<T> extends JspView<T>
    {
        public StudyJspView(Study study, String name, T bean, BindException errors)
        {
            super("/org/labkey/study/view/" + name, bean, errors);
            if (getPage() instanceof BaseStudyPage)
                ((BaseStudyPage)getPage()).init(study);
        }
    }

    protected static <T> boolean nullSafeEqual(T first, T second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }

    public static int[] toIntArray(Collection<String> intStrings)
    {
        if (intStrings == null)
            return null;
        int[] converted = new int[intStrings.size()];
        int index = 0;
        for (String intString : intStrings)
            converted[index++] = Integer.parseInt(intString);
        return converted;
    }

    public static int[] toIntArray(String[] intStrings)
    {
        if (intStrings == null)
            return null;
        int[] converted = new int[intStrings.length];
        int index = 0;
        for (String intString : intStrings)
            converted[index++] = Integer.parseInt(intString);
        return converted;
    }

    public static class IdForm
    {
        public enum PARAMS
        {
            id
        }
        
        private int _id;

        public IdForm()
        {
        }

        public IdForm(int id)
        {
            _id = id;
        }

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }
    }
}
