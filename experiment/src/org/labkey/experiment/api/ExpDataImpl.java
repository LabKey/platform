package org.labkey.experiment.api;

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.*;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.apache.log4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.File;

public class ExpDataImpl extends ExpIdentifiableBaseImpl<Data> implements ExpData
{
    static final private Logger _log = Logger.getLogger(ExpDataImpl.class);

    /**
     * Temporary mapping until experiment.xml contains the mime type
     */
    private static MimeMap MIME_MAP = new MimeMap();

    static public ExpDataImpl[] fromDatas(Data[] datas)
    {
        ExpDataImpl[] ret = new ExpDataImpl[datas.length];
        for (int i = 0; i < datas.length; i ++)
        {
            ret[i] = new ExpDataImpl(datas[i]);
        }
        return ret;
    }

    public ExpDataImpl(Data data)
    {
        super(data);
    }

    public String getContainerId()
    {
        return _object.getContainer();
    }

    public URLHelper detailsURL()
    {
        return getDataType().getDetailsURL(this);
    }

    public ExpProtocolApplication getSourceApplication()
    {
        if (_object.getSourceApplicationId() == null)
            return null;
        return ExperimentService.get().getExpProtocolApplication(_object.getSourceApplicationId());
    }

    public ExpProtocolApplication[] getTargetApplications()
    {
        ResultSet rs = null;
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("DataId", getRowId());
            rs = Table.select(ExperimentServiceImpl.get().getTinfoDataInput(), Collections.singleton("TargetApplicationId"), filter, null);
            List<ExpProtocolApplication> ret = new ArrayList();
            while (rs.next())
            {
                ret.add(ExperimentService.get().getExpProtocolApplication(rs.getInt(1)));
            }
            return ret.toArray(new ExpProtocolApplication[0]);
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return new ExpProtocolApplication[0];
        }
        finally
        {
            if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
        }
    }

    public ExpRun[] getTargetRuns()
    {
        try
        {
            SQLFragment sql = new SQLFragment("SELECT exp.ExperimentRun.* FROM exp.ExperimentRun" +
                    "\nWHERE exp.ExperimentRun.RowId IN " +
                    "\n(SELECT exp.ProtocolApplication.RunId" +
                    "\nFROM exp.ProtocolApplication" +
                    "\nINNER JOIN exp.DataInput ON exp.ProtocolApplication.RowId = exp.DataInput.TargetApplicationId AND exp.DataInput.DataId = ?)");
            sql.add(getRowId());
            ExperimentRun[] runs = Table.executeQuery(ExperimentService.get().getSchema(), sql.getSQL(), sql.getParams().toArray(new Object[0]), ExperimentRun.class);
            return ExpRunImpl.fromRuns(runs);
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return new ExpRun[0];
        }

    }

    public DataType getDataType()
    {
        return ExperimentService.get().getDataType(getLSIDNamespacePrefix());
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public void setDataFileURI(URI uri)
    {
        if (uri != null && !uri.isAbsolute())
        {
            throw new IllegalArgumentException("URI must be absolute.");
        }
        _object.setDataFileUrl(uri == null ? null : uri.toString());
    }

    public void setSourceApplication(ExpProtocolApplication app)
    {
        _object.setSourceApplicationId(app.getRowId());
        _object.setSourceProtocolLSID(app.getProtocol().getLSID());
        _object.setContainer(getContainer().getId());
        _object.setRunId(app.getRun().getRowId());
    }

    public void setRun(ExpRun run)
    {
        if (run == null)
        {
            _object.setRunId(null);
        }
        else
        {
            _object.setRunId(run.getRowId());
        }
    }

    public void save(User user)
    {
        try
        {
            if (getRowId() == 0)
            {
                _object = Table.insert(user, ExperimentServiceImpl.get().getTinfoData(), _object);
            }
            else
            {
                _object = Table.update(user, ExperimentServiceImpl.get().getTinfoData(), _object, getRowId(), null);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpRunImpl getRun()
    {
        if (_object.getRunId() == null)
        {
            return null;
        }
        return ExperimentServiceImpl.get().getExpRun(_object.getRunId());
    }

    public URI getDataFileURI()
    {
        String url = _object.getDataFileUrl();
        if (url == null)
            return null;
        try
        {
            return new URI(_object.getDataFileUrl());
        }
        catch (URISyntaxException use)
        {
            return null;
        }
    }

    public File getDataFile()
    {
        return _object.getFile();
    }

    public Date getCreated()
    {
        return _object.getCreated();
    }

    public ExperimentDataHandler findDataHandler()
    {
        return Handler.Priority.findBestHandler(ExperimentService.get().getExperimentDataHandlers(), (ExpData)this);
    }

    public String getDataFileUrl()
    {
        return _object.getDataFileUrl();
    }

    public File getFile()
    {
        return _object.getFile();
    }

    public ExpProtocolApplication retrieveSourceApp()
    {
        return _object.retrieveSourceApp();
    }

    public List<ExpProtocolApplication> retrieveSuccessorAppList()
    {
        return _object.retrieveSuccessorAppList();
    }

    public void storeSourceApp(ExpProtocolApplication expProtocolApplication)
    {
        _object.storeSourceApp(expProtocolApplication);
    }

    public void storeSuccessorAppList(ArrayList<ExpProtocolApplication> expProtocolApplications)
    {
        _object.storeSuccessorAppList(expProtocolApplications);
    }

    public void storeSuccessorRunIdList(ArrayList<Integer> integers)
    {
        _object.storeSuccessorRunIdList(integers);
    }

    public List<Integer> retrieveSuccessorRunIdList()
    {
        return _object.retrieveSuccessorRunIdList();
    }

    public boolean isInlineImage()
    {
        return null != getDataFileUrl() && MIME_MAP.isInlineImageFor(getDataFileUrl());
    }

    public String urlFlag(boolean flagged)
    {
        String ret = null;
        DataType type = getDataType();
        if (type != null)
        {
            ret = type.urlFlag(flagged);
        }
        if (ret != null)
            return ret;
        return super.urlFlag(flagged);
    }

    public User getCreatedBy()
    {
        ExpRunImpl run = getRun();
        return null != run ? run.getCreatedBy() : null;
    }

    public void delete(User user) throws Exception
    {
        ExperimentService.get().deleteDataByRowIds(getContainer(), getRowId());
    }
    
    public void setContainerId(String container)
    {
        _object.setContainer(container);
    }

    public String getMimeType()
    {
        if (null != getDataFileUrl())
            return MIME_MAP.getContentTypeFor(getDataFileUrl());
        else
            return null;
    }

    public boolean isFileOnDisk()
    {
        File f = getFile();
        return f != null && NetworkDrive.exists(f) && f.isFile();
    }

    public void setDataFileUrl(String s)
    {
        _object.setDataFileUrl(s);
    }

    public void insert(User user)
    {
        try
        {
            _object = ExperimentServiceImpl.get().insertData(user, _object);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpProtocolImpl getSourceProtocol()
    {
        if (_object.getSourceProtocolLSID() == null)
        {
            return null;
        }
        return ExperimentServiceImpl.get().getExpProtocol(_object.getSourceProtocolLSID());
    }

    public void setSourceProtocol(ExpProtocol expProtocol)
    {
        _object.setSourceProtocolLSID(expProtocol.getLSID());
    }

    public Integer getSourceApplicationId()
    {
        return _object.getSourceApplicationId();
    }
}
