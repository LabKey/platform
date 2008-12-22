/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.experiment.api;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.DotGraph;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

public class ExpRunImpl extends ExpIdentifiableBaseImpl<ExperimentRun> implements ExpRun
{
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

    public ExpData[] getInputDatas(String inputRole, ExpProtocol.ApplicationType applicationType)
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
                sql.append("\nAND exp.DataInput.Role = ? ");
                sql.add(inputRole);
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

    public void save(User user)
    {
        try
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
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void setProtocol(ExpProtocol protocol)
    {
        _object.setProtocolLSID(protocol.getLSID());
    }

    public ExpProtocolApplicationImpl addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType appType, String name)
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
        pa.setName(name);
        pa.setLSID(ExperimentService.get().generateGuidLSID(getContainer(), ExpProtocolApplication.class));
        pa.setCpasType(appType.toString());
        pa.setRunId(getRowId());
        try
        {
            pa = Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolApplication(), pa);
            return new ExpProtocolApplicationImpl(pa);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
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
            return OntologyManager.getPropertyObjects(getContainer(), getLSID());
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

    public void trimRunTree(Integer id, String type) throws ExperimentException
    {
        List<ExpProtocolApplication> listPA = new ArrayList<ExpProtocolApplication>();
        List<ExpMaterial> listM = new ArrayList<ExpMaterial>();
        List<ExpData> listD = new ArrayList<ExpData>();
        List<ExpProtocolApplication> ancestorPAStack = new ArrayList<ExpProtocolApplication>();
        List<ExpProtocolApplication> descendantPAStack = new ArrayList<ExpProtocolApplication>();
        ExpProtocolApplication [] apps = getProtocolApplications();

        boolean found = false;

        // support focus on a starting material that is not part of the run
        if (type.equals(DotGraph.TYPECODE_MATERIAL))
        {
            for (ExpMaterial m : getMaterialInputs().keySet())
                if (m.getRowId() == id.intValue())
                {
                    found = true;
                    listM.add(m);
                    listPA.addAll(m.getSuccessorApps());
                    descendantPAStack.addAll(m.getSuccessorApps());
                    break;
                }
        }
        if (type.equals(DotGraph.TYPECODE_DATA))
        {
            for (ExpData d : getDataInputs().keySet())
                if (d.getRowId() == id.intValue())
                {
                    found = true;
                    listD.add(d);
                    listPA.addAll(d.getSuccessorApps());
                    descendantPAStack.addAll(d.getSuccessorApps());
                    break;
                }
        }
        if (!found)
        {
            for (ExpProtocolApplication app : apps)
            {
                if (type.equals(DotGraph.TYPECODE_MATERIAL))
                {
                    List<ExpMaterial> outputMat = app.getOutputMaterials();
                    for (ExpMaterial m : outputMat)
                        if (m.getRowId() == id.intValue())
                        {
                            found = true;
                            listM.add(m);
                            listPA.addAll(m.getSuccessorApps());
                            descendantPAStack.addAll(m.getSuccessorApps());
                            if (null != m.getSourceApplication() && m.getRun() != null && getRowId() == m.getRun().getRowId())
                            {
                                listPA.add(m.getSourceApplication());
                                ancestorPAStack.add(m.getSourceApplication());
                            }
                            break;
                        }
                }
                if (type.equals(DotGraph.TYPECODE_DATA))
                {
                    for (ExpData d : app.getOutputDatas())
                    {
                        if (d.getRowId() == id.intValue())
                        {
                            found = true;
                            listD.add(d);
                            listPA.addAll(d.getSuccessorApps());
                            descendantPAStack.addAll(d.getSuccessorApps());
                            if (null != d.getSourceApplication() && d.getRun() != null && getRowId() == d.getRun().getRowId())
                            {
                                listPA.add(d.getSourceApplication());
                                ancestorPAStack.add(d.getSourceApplication());
                            }
                            break;
                        }
                    }
                }
                if (type.equals(DotGraph.TYPECODE_PROT_APP))
                {
                    if (app.getRowId() == id.intValue())
                    {
                        found = true;
                        listPA.add(app);
                        ancestorPAStack.add(app);
                        descendantPAStack.add(app);
                        break;
                    }
                }
                if (found)
                    break;
            }
        }
        if (!found)
            throw new ExperimentException("Specified node not found in Experiment Run");


        while (descendantPAStack.size() > 0)
        {
            ExpProtocolApplication pa = descendantPAStack.get(0);
            for (ExpMaterial m : pa.getOutputMaterials())
            {
                listM.add(m);
                descendantPAStack.addAll(m.getSuccessorApps());
            }
            for (ExpData d : pa.getOutputDatas())
            {
                listD.add(d);
                descendantPAStack.addAll(d.getSuccessorApps());
            }
            descendantPAStack.remove(pa);
            listPA.add(pa);
        }


        while (ancestorPAStack.size() > 0)
        {
            ExpProtocolApplication pa = ancestorPAStack.get(0);
            if (pa.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun)
                break;
            for (ExpMaterial m : pa.getInputMaterials())
            {
                listM.add(m);
                if (getMaterialInputs().containsKey(m))
                {
                    ExpProtocolApplication runNode = getProtocolApplications()[0];
                    assert runNode.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun;
                    listPA.add(runNode);
                    continue;
                }
                if (null != m.getSourceApplication() && m.getRun() != null && getRowId() == m.getRun().getRowId())
                    ancestorPAStack.add(m.getSourceApplication());
            }
            for (ExpData d : pa.getInputDatas())
            {
                listD.add(d);
                if (getDataInputs().containsKey(d))
                {
                    ExpProtocolApplication runNode = getProtocolApplications()[0];
                    assert runNode.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun;
                    listPA.add(runNode);
                    continue;
                }
                if (null != d.getSourceApplication() && d.getRun() != null && getRowId() == d.getRun().getRowId())
                    ancestorPAStack.add(d.getSourceApplication());
            }
            ancestorPAStack.remove(pa);
            listPA.add(pa);
        }

        ArrayList<ExpProtocolApplication> allPA = new ArrayList<ExpProtocolApplication>();
        ArrayList<ExpProtocolApplication> deletePA;
        ArrayList<ExpMaterial> deleteM;
        ArrayList<ExpData> deleteD;

        setProtocolApplications(null);

        for (ExpProtocolApplication app : apps)
        {
            if (listPA.contains(app))
            {
                allPA.add(app);
                deleteM = new ArrayList<ExpMaterial>();
                for (ExpMaterial m : app.getInputMaterials())
                {
                    if (listM.contains(m))
                    {
                        deletePA = new ArrayList<ExpProtocolApplication>();
                        for (ExpProtocolApplication p : m.getSuccessorApps())
                            if (!listPA.contains(p))
                                deletePA.add(p);
                        for (ExpProtocolApplication p : deletePA)
                            m.getSuccessorApps().remove(p);
                    }
                    else
                        deleteM.add(m);
                }
                for (ExpMaterial m : deleteM)
                {
                    app.getInputMaterials().remove(m);
                    getMaterialInputs().remove(m);
                }

                deleteD = new ArrayList<ExpData>();
                for (ExpData d : app.getInputDatas())
                {
                    if (listD.contains(d))
                    {
                        deletePA = new ArrayList<ExpProtocolApplication>();
                        for (ExpProtocolApplication p : d.getSuccessorApps())
                            if (!listPA.contains(p))
                                deletePA.add(p);
                        for (ExpProtocolApplication p : deletePA)
                            d.getSuccessorApps().remove(p);
                    }
                    else
                        deleteD.add(d);
                }
                for (ExpData d : deleteD)
                {
                    app.getInputDatas().remove(d);
                    getDataInputs().remove(d);
                }

                deleteM = new ArrayList<ExpMaterial>();
                for (ExpMaterial m : app.getOutputMaterials())
                {
                    if (!listM.contains(m))
                        deleteM.add(m);
                }
                for (ExpMaterial m : deleteM)
                    app.getOutputMaterials().remove(m);


                deleteD = new ArrayList<ExpData>();
                for (ExpData d : app.getOutputDatas())
                {
                    if (!listD.contains(d))
                        deleteD.add(d);
                }
                for (ExpData d : deleteD)
                    app.getOutputDatas().remove(d);
            }
        }
        setProtocolApplications(allPA.toArray(new ExpProtocolApplicationImpl[allPA.size()]));
    }


}
