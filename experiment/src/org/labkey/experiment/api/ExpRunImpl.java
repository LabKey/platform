package org.labkey.experiment.api;

import org.apache.log4j.Logger;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.*;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

public class ExpRunImpl extends ExpIdentifiableBaseImpl<ExperimentRun> implements ExpRun
{
    static final private Logger _log = Logger.getLogger(ExpRunImpl.class);

    private boolean _populated;

    private ExpProtocolApplicationImpl[] _protocolSteps;
    private Map<ExpMaterial, String> _materialInputs = new HashMap<ExpMaterial, String>();
    private Map<ExpData, String> _dataInputs = new HashMap<ExpData, String>();
    private List<ExpMaterial> _materialOutputs = new ArrayList<ExpMaterial>();
    private List<ExpData> _dataOutputs = new ArrayList<ExpData>();

    static public ExpRun[] fromRuns(ExperimentRun[] runs)
    {
        ExpRun[] ret = new ExpRun[runs.length];
        for (int i = 0; i < runs.length; i ++)
        {
            ret[i] = new ExpRunImpl(runs[i]);
        }
        return ret;
    }
    public ExpRunImpl(ExperimentRun run)
    {
        super(run);
    }

    public URLHelper detailsURL()
    {
        return new ActionURL(ExperimentController.ShowRunGraphAction.class, getContainer()).addParameter("rowId", getRowId());
    }

    public String getContainerId()
    {
        return _object.getContainer();
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public ExpExperimentImpl[] getExperiments()
    {
        return ExperimentServiceImpl.get().getExpExperimentsForRun(_object.getLSID());
    }

    public ExpProtocolImpl getProtocol()
    {
        return ExperimentServiceImpl.get().getExpProtocol(_object.getProtocolLSID());
    }

    public ExpData[] getOutputDatas(DataType type)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("RunId", getRowId());
            if (type != null)
            {
                filter.addWhereClause(Lsid.namespaceFilter("LSID", type.getNamespacePrefix()), null);
            }
            return ExpDataImpl.fromDatas(Table.select(ExperimentServiceImpl.get().getTinfoData(), Table.ALL_COLUMNS, filter, null, Data.class));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpData[] getInputDatas(PropertyDescriptor inputRole, ExpProtocol.ApplicationType applicationType)
    {
        try
        {
            SQLFragment sql = new SQLFragment();
            sql.append("SELECT exp.Data.* FROM exp.Data WHERE exp.Data.RowId IN ");
            sql.append("\n(SELECT exp.DataInput.DataId FROM exp.DataInput ");
            sql.append("\nINNER JOIN exp.ProtocolApplication ON exp.DataInput.TargetApplicationId = exp.ProtocolApplication.RowId");
            sql.append("\nWHERE exp.ProtocolApplication.RunId = ?");
            sql.add(getRowId());
            if (inputRole != null)
            {
                sql.append("\nAND exp.DataInput.PropertyId = ? ");
                sql.add(inputRole.getPropertyId());
            }
            if (applicationType != null)
            {
                sql.append("\nAND exp.ProtocolApplication.CpasType = ");
                sql.appendStringLiteral(applicationType.toString());
            }
            sql.append(")");
            return ExpDataImpl.fromDatas(Table.executeQuery(ExperimentService.get().getSchema(), sql.getSQL(), sql.getParamsArray(), Data.class));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public String getFilePathRoot()
    {
        return _object.getFilePathRoot();
    }

    public void setFilePathRoot(File file)
    {
        _object.setFilePathRoot(file.getAbsolutePath());
    }

    public Date getCreated()
    {
        return _object.getCreated();
    }

    public User getCreatedBy()
    {
        return UserManager.getUser(_object.getCreatedBy());
    }

    static final public String s_urlFlagRun = AppProps.getInstance().getContextPath() + "/Experiment/flagRun.gif";
    static final public String s_urlUnflagRun = AppProps.getInstance().getContextPath() + "/Experiment/unflagRun.gif";

    public String urlFlag(boolean flagged)
    {
        return flagged ? s_urlFlagRun : s_urlUnflagRun;
    }

    public void save(User user) throws Exception
    {
        if (_object.getRowId() == 0)
        {
            _object = Table.insert(user, ExperimentServiceImpl.get().getTinfoExperimentRun(), _object);
        }
        else
        {
            _object = Table.update(user, ExperimentServiceImpl.get().getTinfoExperimentRun(), _object, _object.getRowId(), null);
        }
    }

    public void setProtocol(ExpProtocol protocol)
    {
        _object.setProtocolLSID(protocol.getLSID());
    }

    public ExpProtocolApplicationImpl addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType appType) throws Exception
    {
        ProtocolApplication pa = new ProtocolApplication();
        if (action == null)
        {
            if (appType != ExpProtocol.ApplicationType.ExperimentRun)
            {
                throw new IllegalArgumentException("Only the ExperimentRun protocol application type is allowed to have a null protocol action.");
            }
            pa.setProtocolLSID(getProtocol().getLSID());
        }
        else
        {
            pa.setProtocolLSID(action.getChildProtocol().getLSID());
            pa.setActionSequence(action.getActionSequence());
        }
        pa.setLSID(ExperimentService.get().generateGuidLSID(getContainer(), ExpProtocolApplication.class));
        pa.setCpasType(appType.toString());
        pa.setRunId(getRowId());
        pa = Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolApplication(), pa);
        return new ExpProtocolApplicationImpl(pa);
    }

    public String getComments()
    {
        return _object.getComments();
    }

    public void setComments(String comments)
    {
        _object.setComments(comments);
    }

    public void setEntityId(String entityId)
    {
        _object.setEntityId(entityId);
    }


    public void setContainerId(String containerId)
    {
        _object.setContainer(containerId);
    }

    public void setProtocolApplications(ExpProtocolApplicationImpl[] protocolSteps)
    {
        _protocolSteps = protocolSteps;
    }

    public Map<ExpMaterial, String> getMaterialInputs()
    {
        ensureFullyPopulated();
        return _materialInputs;
    }

    public Map<ExpData, String> getDataInputs()
    {
        ensureFullyPopulated();
        return _dataInputs;
    }

    public List<ExpMaterial> getMaterialOutputs()
    {
        ensureFullyPopulated();
        return _materialOutputs;
    }

    public List<ExpData> getDataOutputs()
    {
        ensureFullyPopulated();
        return _dataOutputs;
    }

    public ExpProtocolApplicationImpl[] getProtocolApplications()
    {
        ensureFullyPopulated();
        return _protocolSteps;
    }

    /**
     * @return Map from PropertyURI to ObjectProperty
     */
    public Map<String, ObjectProperty> getObjectProperties()
    {
        try
        {
            return OntologyManager.getPropertyObjects(getContainer().getId(), getLSID());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private synchronized void ensureFullyPopulated()
    {
        if (!_populated)
        {
            _populated = true;
            ExperimentServiceImpl.get().populateRun(this);
            Collections.sort(_materialOutputs, ExpObject.NAME_COMPARATOR);
            Collections.sort(_dataOutputs, ExpObject.NAME_COMPARATOR);

            Map<ExpMaterial, String> sortedMaterialInputs = new TreeMap<ExpMaterial, String>(ExpObject.NAME_COMPARATOR);
            sortedMaterialInputs.putAll(_materialInputs);
            _materialInputs = sortedMaterialInputs;

            Map<ExpData, String> sortedDataInputs = new TreeMap<ExpData, String>(ExpObject.NAME_COMPARATOR);
            sortedDataInputs.putAll(_dataInputs);
            _dataInputs = sortedDataInputs;

            for (ExpProtocolApplicationImpl step : _protocolSteps)
            {
                Collections.sort(step.getInputDatas(), ExpObject.NAME_COMPARATOR);
                Collections.sort(step.getOutputDatas(), ExpObject.NAME_COMPARATOR);
                Collections.sort(step.getInputMaterials(), ExpObject.NAME_COMPARATOR);
                Collections.sort(step.getOutputMaterials(), ExpObject.NAME_COMPARATOR);
            }
        }
    }


}
