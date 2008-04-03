package org.labkey.api.exp.property;

import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;

import java.sql.SQLException;

public interface Domain extends IPropertyType
{
    Container getContainer();
    DomainKind getDomainKind();
    String getName();
    String getDescription();
    int getTypeId();

    Container[] getInstanceContainers();
    Container[] getInstanceContainers(User user, int perm);

    void setDescription(String description);
    DomainProperty[] getProperties();
    DomainProperty getProperty(int id);
    DomainProperty getPropertyByName(String name);
    ActionURL urlEditDefinition(boolean allowFileLinkProperties, boolean allowAttachmentProperties);
    ActionURL urlShowData();

    DomainProperty addProperty();

    void delete(User user) throws DomainNotFoundException;
    void save(User user) throws ChangePropertyDescriptorException;
}
