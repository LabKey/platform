package org.labkey.api.exp.api;

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.security.User;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;

public interface ExpRun extends ExpObject
{
    public ExpExperiment[] getExperiments();
    public ExpProtocol getProtocol();
    public ExpData[] getOutputDatas(DataType type);
    public ExpData[] getInputDatas(PropertyDescriptor inputRole, ExpProtocol.ApplicationType appType);
    public String getFilePathRoot();
    public void setFilePathRoot(File filePathRoot);
    public Date getCreated();
    public User getCreatedBy();
    public void save(User user) throws Exception;
    public void setProtocol(ExpProtocol protocol);
    public ExpProtocolApplication addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType type) throws Exception;

    String getComments();

    void setComments(String comments);

    void setEntityId(String entityId);

    Map<ExpMaterial, String> getMaterialInputs();

    Map<ExpData, String> getDataInputs();

    List<ExpMaterial> getMaterialOutputs();

    List<ExpData> getDataOutputs();

    ExpProtocolApplication[] getProtocolApplications();

    /**
     * @return Map from PropertyURI to ObjectProperty
     */
    Map<String, ObjectProperty> getObjectProperties();
}
