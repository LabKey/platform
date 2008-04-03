package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.exp.PropertyDescriptor;

import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public interface ExpMaterial extends ExpObject
{
    public ExpSampleSet getSampleSet();

    void insert(User user) throws SQLException;

    ExpProtocolApplication getSourceApplication();

    ExpProtocol getSourceProtocol();

    ExpRun getRun();

    void setSourceApplication(ExpProtocolApplication sourceApplication);

    void setSourceProtocol(ExpProtocol protocol);

    void setRun(ExpRun run);

    List<ExpProtocolApplication> retrieveSuccessorAppList();

    List<Integer> retrieveSuccessorRunIdList();

    void storeSuccessorAppList(ArrayList<ExpProtocolApplication> protocolApplications);

    void storeSourceApp(ExpProtocolApplication protocolApplication);

    void storeSuccessorRunIdList(ArrayList<Integer> integers);

    ExpProtocolApplication retrieveSourceApp();

    public String getCpasType();
    void setCpasType(String type);

    public Map<PropertyDescriptor, Object> getPropertyValues();
}
