package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

import java.util.Map;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:28:38 AM
 */
public class Site extends AbstractStudyEntity<Site>
{
    private int _rowId; // INT IDENTITY(1,1),
    private Integer _scharpId; // INT,
    private Integer _ldmsLabCode; // INT,
    private String _labwareLabCode; // NVARCHAR(20),
    private String _labUploadCode; // NVARCHAR(2),
    private boolean _isSal; // Bit,
    private boolean _isClinic; // Bit,
    private boolean _isRepository; // Bit,
    private boolean _isEndpoint; // Bit,
    private String _label;

    public Site()
    {
    }

    public Site(Map<String, ? extends Object> rsRowMap)
    {
        setContainer(ContainerManager.getForId((String) rsRowMap.get("Container")));
        setEndpoint(safeBooleanEntryConvert(rsRowMap, "IsEndpoint"));
        setEntityId((String) rsRowMap.get("EntityId"));
        setLabel((String) rsRowMap.get("Label"));
        setLabUploadCode((String) rsRowMap.get("LabUploadCode"));
        setLabwareLabCode((String) rsRowMap.get("LabwareLabCode"));
        setLdmsLabCode((Integer) rsRowMap.get("LdmsLabCode"));
        setRepository(safeBooleanEntryConvert(rsRowMap, "IsRepository"));
        setRowId((Integer) rsRowMap.get("RowId"));
        setSal(safeBooleanEntryConvert(rsRowMap, "IsSal"));
        setClinic(safeBooleanEntryConvert(rsRowMap, "IsClinic"));
        setScharpId((Integer) rsRowMap.get("ScharpId"));
    }

    private boolean safeBooleanEntryConvert(Map<String, ? extends Object> rsRowMap, String booleanColName)
    {
        return rsRowMap.containsKey(booleanColName) &&
                rsRowMap.get(booleanColName) != null &&
                (Boolean) rsRowMap.get(booleanColName);
    }

    public Site(Container container, String label)
    {
        super(container);
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        verifyMutability();
        _label = label;
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public boolean isEndpoint()
    {
        return _isEndpoint;
    }

    public void setEndpoint(boolean endpoint)
    {
        verifyMutability();
        _isEndpoint = endpoint;
    }

    public boolean isRepository()
    {
        return _isRepository;
    }

    public void setRepository(boolean repository)
    {
        verifyMutability();
        _isRepository = repository;
    }

    public boolean isSal()
    {
        return _isSal;
    }

    public void setSal(boolean sal)
    {
        verifyMutability();
        _isSal = sal;
    }

    public boolean isClinic()
    {
        return _isClinic;
    }

    public void setClinic(boolean clinic)
    {
        verifyMutability();
        _isClinic = clinic;
    }

    public String getLabUploadCode()
    {
        return _labUploadCode;
    }

    public void setLabUploadCode(String labUploadCode)
    {
        verifyMutability();
        _labUploadCode = labUploadCode;
    }

    public String getLabwareLabCode()
    {
        return _labwareLabCode;
    }

    public void setLabwareLabCode(String labwareLabCode)
    {
        verifyMutability();
        _labwareLabCode = labwareLabCode;
    }

    public Integer getLdmsLabCode()
    {
        return _ldmsLabCode;
    }

    public void setLdmsLabCode(Integer ldmsLabCode)
    {
        verifyMutability();
        _ldmsLabCode = ldmsLabCode;
    }

    public Integer getScharpId()
    {
        return _scharpId;
    }

    public void setScharpId(Integer scharpId)
    {
        verifyMutability();
        _scharpId = scharpId;
    }

    public String getDisplayName()
    {
        String label = getLabel();
        if (label == null)
        {
            if (getLdmsLabCode() != null)
                return "LDMS " + getLdmsLabCode() + " (Unlabeled)";
            else if (getLabwareLabCode() != null)
                return "Labware " + getLabwareLabCode() + " (Unlabeled)";
            else
                return "(Unlabeled)";
        }
        else if (isEndpoint())
            return label + " (Endpoint Lab)";
        else if (isRepository())
            return label + " (Repository)";
        else if (isSal())
            return label + " (SAL)";
        else if (isClinic())
            return label + " (Clinic)";
        else
            return label;
    }

    @Override
    protected boolean supportsACLUpdate()
    {
        return true;
    }
}
