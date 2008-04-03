package org.labkey.experiment.list;

import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.list.ListItemAttachmentParent;

import java.sql.SQLException;
import java.util.Map;

public class ListDomainType extends DomainKind
{
    static public final ListDomainType instance = new ListDomainType();

    public String getTypeLabel(Domain domain)
    {
        return "List '" + domain.getName() + "'";
    }

    public boolean isDomainType(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return "List".equals(lsid.getNamespacePrefix());
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        ListDefinitionImpl listDef = (ListDefinitionImpl) ListService.get().getList(domain);
        if (listDef == null)
            return null;
        SQLFragment ret = new SQLFragment("SELECT IndexTable.ObjectId FROM ");
        ret.append(listDef.getIndexTable().getFromSQL("IndexTable"));
        ret.append(" WHERE IndexTable.listId = " + listDef.getListId());
        return ret;
    }

//    public String generateDomainURI(Container container, String name)
//    {
//        String str = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":List" + ".Folder-" + container.getRowId() + ":" + name;
//        return new Lsid(str).toString();
//    }

    public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers)
    {
        return null;
    }

    public ActionURL urlShowData(Domain domain)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (listDef == null)
            return null;
        return listDef.urlShowData();
    }

    public ActionURL urlEditDefinition(Domain domain)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (listDef == null)
            return null;
        return listDef.urlShowDefinition();
    }

    // return the "system" properties for this domain
    public DomainProperty[] getDomainProperties(String domainURI)
    {
        return new DomainProperty[0];
    }

    @Override
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
        if (pd.getPropertyType() != PropertyType.ATTACHMENT)
            return;

        ListDefinition list = ListService.get().getList(domain);
        Container c = list.getContainer();
        TableInfo tinfo = list.getTable(user, null);
        DomainProperty prop = domain.getPropertyByName(pd.getName());

        try
        {
            Object[] keys = Table.executeArray(tinfo, tinfo.getColumn(list.getKeyName()), null, null, Object.class);

            for (Object key : keys)
            {
                ListItem item = list.getListItem(key);
                Object file = item.getProperty(prop);

                if (null != file)
                {
                    AttachmentParent parent = new ListItemAttachmentParent(item, c);
                    AttachmentService.get().deleteAttachment(parent, file.toString());
                }

            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
