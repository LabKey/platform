package org.labkey.api.exp.property;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.PropertyDescriptor;

import java.util.Map;

abstract public class DomainKind
{
    abstract public String getTypeLabel(Domain domain);
    abstract public boolean isDomainType(String domainURI);
    abstract public SQLFragment sqlObjectIdsInDomain(Domain domain);
//    abstract public String generateDomainURI(Container container, String name);
    abstract public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers);
    abstract public ActionURL urlShowData(Domain domain);
    abstract public ActionURL urlEditDefinition(Domain domain);

    // return the "system" properties for this domain
    abstract public DomainProperty[] getDomainProperties(String domainURI);
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, ACL.PERM_ADMIN);
    }

    // Do any special handling before a PropertyDescriptor is deleted -- do nothing by default
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
    }

    // CONSIDER: have DomainKind supply and IDomainInstance or similiar
    // so that it can hold instance data (e.g. a DatasetDefinition)
}
