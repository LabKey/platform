package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.exp.PropertyDescriptor;

import java.util.Date;
import java.util.List;

public interface ExpProtocolApplication extends ExpObject
{
    public ExpDataInput[] getDataInputs();
    public List<ExpData> getInputDatas();
    public List<ExpData> getOutputDatas();
    public ExpMaterialInput[] getMaterialInputs();
    public List<ExpMaterial> getInputMaterials();

    List<ExpMaterial> getOutputMaterials();

    public ExpProtocol getProtocol();

    /**
     * Add a data input
     * @param user
     * @param input
     * @param inputRole optional argument specifying the input role name.  Will be ignored if pd is not null
     * @param pd optional property descriptor identifying the input role.
     * @return The property descriptor corresponding to the input role.  Will be null if inputRole is null
     * @throws Exception
     */
    public PropertyDescriptor addDataInput(User user, ExpData input, String inputRole, PropertyDescriptor pd) throws Exception;
    public void removeDataInput(User user, ExpData data) throws Exception;
    public PropertyDescriptor addMaterialInput(User user, ExpMaterial material, String inputRole, PropertyDescriptor pd) throws Exception;
    public void removeMaterialInput(User user, ExpMaterial material) throws Exception;

    public ExpRun getRun();
    public int getActionSequence();
    public ExpProtocol.ApplicationType getApplicationType();

    Date getActivityDate();

    String getComments();

    String getCpasType();
}
