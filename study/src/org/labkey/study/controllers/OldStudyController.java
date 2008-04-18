package org.labkey.study.controllers;

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.AliasManager;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.view.BaseStudyPage;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.net.URISyntaxException;


@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class OldStudyController extends BaseController
{
    static Logger _log = Logger.getLogger(OldStudyController.class);

    public Forward _renderInTemplate(HttpView view, String title, NavTree... navtrail) throws Exception
    {
        return _renderInTemplate(view, title, null, navtrail);
    }

    /** make compatible with HomeTemplate.getNavTrailView() */
    public Forward _renderInTemplate(HttpView view, String title, String helpTopic, NavTree... navtrail) throws Exception
    {
        // add study link automatically
        Forward begin = forwardBegin();
        if (navtrail.length == 0 || !begin.toString().equals(navtrail[0].second))
        {
            NavTree[] temp = new NavTree[navtrail.length+1];
            temp[0] = new NavTree(null == getStudy(true) ? "New Study" : getStudy().getLabel(), forwardBegin());
            System.arraycopy(navtrail,0,temp,1,navtrail.length);
            navtrail=temp;
        }
        return super._renderInTemplate(view, title, helpTopic, navtrail);
    }


    private ViewForward forwardBegin()
    {
        try {return new ViewForward(studyURL("begin"));} catch (Exception x) {throw new RuntimeException(x);}
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_READ)
    protected Forward begin() throws Exception
    {
        return null;
    }

    private ViewForward forwardManageStudy() throws Exception
    {
        return new ViewForward(studyURL("manageStudy"));
    }

    private ViewForward forwardManageTypes() throws Exception
    {
        return new ViewForward(studyURL("manageTypes"));
    }


    public static class StudyJspView<T> extends JspView<T>
    {
        public StudyJspView(Study study, String name, T bean)
        {
            super("/org/labkey/study/view/" + name, bean);
            if (getPage() instanceof BaseStudyPage)
                ((BaseStudyPage)getPage()).init(study);
        }
    }

    public static class ImportDataSetForm extends FormData
    {
        private int datasetId = 0;
        private String typeURI;
        private String tsv;
        private String keys;
        private Container container;


        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

        public String getKeys()
        {
            return keys;
        }

        public void setKeys(String keys)
        {
            this.keys = keys;
        }

        public String getTypeURI()
        {
            return typeURI;
        }

        public void setTypeURI(String typeURI)
        {
            this.typeURI = typeURI;
        }

        public Container getContainer() {
            return container;
        }

        public void setContainer(Container container) {
            this.container = container;
        }
    }

    private ViewForward typeNotFound(int datasetId)
            throws URISyntaxException
    {
        return new ViewForward(getActionURL().relativeUrl("typeNotFound", "id=" + datasetId));
    }

    private ActionURL studyURL(String action, String... args) throws ServletException
    {
        return studyURL(getContainer(), action, args);
    }


    private static ActionURL studyURL(Container c, String action, String... args)
    {
        ActionURL url = new ActionURL("Study", action, c);
        if (null != args)
        {
            for (int i=0 ; i<args.length ; i+=2)
                url.addParameter(args[i], args[i+1]);
        }
        return url;
    }

    private StudyManager getStudyManager()
    {
        return StudyManager.getInstance();
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward confirmDeleteDataset(IdForm form) throws Exception
    {
        int datasetId = form.getId();
        DataSetDefinition def = getStudyManager().getDataSetDefinition(getStudy(), datasetId);
        HttpView view = new HtmlView(
                "<form method=post action=\"deleteDataset.post?id=" + datasetId + "\">" +
                "Are you sure you want to delete dataset '" + def.getDisplayString() + "'?  All related data and visitmap entries will also be deleted.<p />" +
                "<input type=image src=\"" + PageFlowUtil.buttonSrc("Delete Dataset") + "\" value=\"Delete\">&nbsp;\n" +
                "<input type=image src=\"" + PageFlowUtil.buttonSrc("Cancel") + "\" value=\"Cancel\" onclick=\"javascript:window.history.back(); return false;\">" +
                "</form>"
        );
        includeView(new DialogTemplate(view));
        return null;
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward deleteDataset(IdForm form) throws Exception
    {
        if (!"POST".equals(getRequest().getMethod()))
            return confirmDeleteDataset(form);

        DataSetDefinition ds = getStudyManager().getDataSetDefinition(getStudy(), form.getId());
        if (null == ds)
            return typeNotFound(form.getId());

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.beginTransaction();
            getStudyManager().deleteDataset(getStudy(), getUser(), ds);
            scope.commitTransaction();
            return forwardManageTypes();
        }
        finally
        {
            if (scope.isTransactionActive())
                scope.rollbackTransaction();
        }
    }

    public Forward fowardParticipant(String ptid)
    {
        try {return new ViewForward(studyURL("participant", "participantId", ptid));} catch (Exception x) {throw new RuntimeException(x);}
    }

    public static class DataSetForm extends ViewForm
    {
        private String _name;
        private String _label;
        private int _datasetId;
        private String _category;
        private boolean _showByDefault;
        private String _visitDatePropertyName;
        private String[] _visitStatus;
        private int[] _visitRowIds;
        private String _description;
        private Integer _cohortId;
        private boolean _demographicData;
        private boolean _create;

        public ActionErrors validate(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            if (_datasetId < 1)
                addActionError("DatasetId must be greater than zero.");
            if (null == StringUtils.trimToNull(_label))
                addActionError("Label is required.");
            return getActionErrors();
        }


        public boolean isShowByDefault()
        {
            return _showByDefault;
        }

        public void setShowByDefault(boolean showByDefault)
        {
            _showByDefault = showByDefault;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }

        public String getDatasetIdStr()
        {
            return _datasetId > 0 ? String.valueOf(_datasetId) : "";
        }

        /**
         * Don't blow up when posting bad value
         * @param dataSetIdStr
         */
        public void setDatasetIdStr(String dataSetIdStr)
        {
            try
            {
                if (null == StringUtils.trimToNull(dataSetIdStr))
                    _datasetId = 0;
                else
                    _datasetId = Integer.parseInt(dataSetIdStr);
            }
            catch (Exception x)
            {
                _datasetId = 0;
            }
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String[] getVisitStatus()
        {
            return _visitStatus;
        }

        public void setVisitStatus(String[] visitStatus)
        {
            _visitStatus = visitStatus;
        }

        public int[] getVisitRowIds()
        {
            return _visitRowIds;
        }

        public void setVisitRowIds(int[] visitIds)
        {
            _visitRowIds = visitIds;
        }

        public String getVisitDatePropertyName()
        {
            return _visitDatePropertyName;
        }

        public void setVisitDatePropertyName(String _visitDatePropertyName)
        {
            this._visitDatePropertyName = _visitDatePropertyName;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public boolean isDemographicData()
        {
            return _demographicData;
        }

        public void setDemographicData(boolean demographicData)
        {
            _demographicData = demographicData;
        }

        public boolean isCreate()
        {
            return _create;
        }

        public void setCreate(boolean create)
        {
            _create = create;
        }

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward manageSnapshot(SnapshotForm form) throws Exception
    {
        Study study = getStudy();
        NavTree[] navTrail = new NavTree[]{
                new NavTree(study.getLabel(), forwardBegin()),
                new NavTree("Manage Study", forwardManageStudy()),
                new NavTree("Manage Snapshot"),};

        return _renderInTemplate(new StudyJspView<SnapshotForm>(study, "snapshotData.jsp", form), "Snapshot Study Data", navTrail);
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path="manageSnapshot.do", name = "validate")) @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward snapshot(SnapshotForm form) throws Exception
    {
        StudyManager.getInstance().createSnapshot(getUser(), form.getBean());
        form.setComplete(true);
        return manageSnapshot(form);
    }

    public static class SnapshotForm extends FormData
    {
        private boolean confirm;
        private boolean complete;
        private String message;
        private StudyManager.SnapshotBean snapshotBean;
        private String[] sourceName;
        private String[] destName;
        private String[] category;
        private boolean[] snapshot;

        public boolean isConfirm()
        {
            return confirm;
        }

        public void setConfirm(boolean confirm)
        {
            this.confirm = confirm;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        @Override
        public void reset(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            try
            {
                snapshotBean = StudyManager.getInstance().getSnapshotInfo(HttpView.currentContext().getUser(), HttpView.currentContext().getContainer());
            }
            catch (ServletException e)
            {
                throw new RuntimeException(e);
            }

            int tableCount = 0;
            for (String category : snapshotBean.getCategories())
                tableCount += snapshotBean.getSourceNames(category).size();

            category = new String[tableCount];
            destName = new String[tableCount];
            sourceName = new String[tableCount];
            snapshot = new boolean[tableCount];
        }


        @Override
        public ActionErrors validate(ActionMapping mapping, HttpServletRequest request)
        {
            ActionErrors errors = new ActionErrors();
            String schemaName = StringUtils.trimToNull(getSchemaName());
            if (null == schemaName)
                errors.add("main", new ActionMessage("Error", "You must supply a schema name."));
            else if (!AliasManager.isLegalName(schemaName))
                errors.add("main", new ActionMessage("Error", "Schema name must be a legal database identifier"));
            else
            {
                boolean badName = false;
                for (Module module : ModuleLoader.getInstance().getModules())
                {
                    for (String schema : module.getSchemaNames())
                        if (schemaName.equalsIgnoreCase(schema))
                        {
                            errors.add("main", new ActionMessage("Error", "The schema name " + schema + " is already in use by the " + module.getName() + " module. Please pick a new name"));
                            badName = true;
                            break;
                        }
                    if (badName)
                        break;
                }
                if (schemaName.equalsIgnoreCase("temp"))
                    errors.add("main", new ActionMessage("Error", "'Temp' is a reserved schema name. Please choose a new name"));
            }

            CaseInsensitiveHashSet names = new CaseInsensitiveHashSet();
            StudyManager.SnapshotBean bean = getBean();
            for (String category : bean.getCategories())
                for (String sourceName : bean.getSourceNames(category))
                {
                    String destTableName = bean.getDestTableName(category, sourceName);
                    if (!AliasManager.isLegalName(destTableName))
                        errors.add("main", new ActionMessage("Error", "Not a legal table name: " + destTableName));
                    if (bean.isSaveTable(category, sourceName) && !names.add(destTableName))
                        errors.add("main", new ActionMessage("Error", "Duplicate table name: " + destTableName));
                }

            return errors;
        }

        public String getSchemaName()
        {
            return snapshotBean.getSchemaName();
        }

        public void setSchemaName(String schemaName)
        {
            snapshotBean.setSchemaName(schemaName);
        }

        public StudyManager.SnapshotBean getBean()
        {
            for (int i = 0; i < category.length; i++)
            {
                if (null != category[i] && null != sourceName[i])
                {
                    snapshotBean.setSnapshot(category[i], sourceName[i], snapshot[i]);
                    snapshotBean.setDestTableName(category[i], sourceName[i], destName[i]);
                }
            }

            return snapshotBean;
        }

        public String[] getSourceName()
        {
            return sourceName;
        }

        public void setSourceName(String[] sourceName)
        {
            this.sourceName = sourceName;
        }

        public String[] getDestName()
        {
            return destName;
        }

        public void setDestName(String[] destName)
        {
            this.destName = destName;
        }

        public String[] getCategory()
        {
            return category;
        }

        public void setCategory(String[] category)
        {
            this.category = category;
        }

        public boolean[] getSnapshot()
        {
            return snapshot;
        }

        public void setSnapshot(boolean[] snapshot)
        {
            this.snapshot = snapshot;
        }

        public boolean isComplete()
        {
            return complete;
        }

        public void setComplete(boolean complete)
        {
            this.complete = complete;
        }
    }

}
