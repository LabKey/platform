package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.ProtocolParameter;

import java.util.Map;
import java.util.Date;
import java.sql.SQLException;

public interface ExpProtocol extends ExpObject
{
    Map<String, ObjectProperty> retrieveObjectProperties();

    public static final String ASSAY_DOMAIN_PREFIX = "AssayDomain-";
    public static final String ASSAY_DOMAIN_RUN = ASSAY_DOMAIN_PREFIX + "Run";
    public static final String ASSAY_DOMAIN_UPLOAD_SET = ASSAY_DOMAIN_PREFIX + "Batch";
    public static final String ASSAY_DOMAIN_DATA = ASSAY_DOMAIN_PREFIX + "Data";

    void storeObjectProperties(Map<String, ObjectProperty> props);

    Map<String, ProtocolParameter> retrieveProtocolParameters() throws SQLException;

    String getInstrument();

    String getSoftware();

    String getContact();


    enum ApplicationType
    {
        ExperimentRun,
        ProtocolApplication,
        ExperimentRunOutput,
    }

    public ExpProtocolAction[] getSteps();
    public ApplicationType getApplicationType();
    public ProtocolImplementation getImplementation();
    public String getDescription();
    Integer getMaxInputMaterialPerInstance();
    String getProtocolDescription();
    void setProtocolDescription(String description);
    void setMaxInputMaterialPerInstance(Integer maxMaterials);
    void setMaxInputDataPerInstance(Integer i);

    public ExpProtocolAction addStep(User user, ExpProtocol childProtocol, int actionSequence) throws Exception;

    public void setApplicationType(ApplicationType type);
    public void setDescription(String description);
    public void save(User user);
}
