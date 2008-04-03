package org.labkey.study.assay;

import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: brittp
 * Date: June 25, 2007
 * Time: 1:01:43 PM
 */
public class AssayDomainKind extends DomainKind
{
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    public boolean isDomainType(String domainURI)
    {
        return false;

        /* TODO: Enable the correct code below, BUT need to fix up sqlObjectIdsInDomain() first
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix().startsWith(ExpProtocol.ASSAY_DOMAIN_PREFIX);
        */
    }


    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        Lsid lsid = new Lsid(domain.getTypeURI());
        String columnName;
        if (lsid.getNamespacePrefix().equalsIgnoreCase("AssayDefinition"))
            columnName = "lsid";
        else if (lsid.getNamespacePrefix().equalsIgnoreCase("AssayRunProperties"))
            columnName = "RunPropertiesDomainURI";
        else
            throw new IllegalArgumentException("Expected recognized domain URI");

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT ObjectId FROM exp.Protocol P JOIN exp.Object O ON P." + columnName
                + " = O.ObjectURI WHERE O.container=? AND P.container=?");
        sql.add(domain.getContainer());
        sql.add(domain.getContainer());
        return sql;
    }

    public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers)
    {
        return null;
    }


    public ActionURL urlShowData(Domain domain)
    {
        throw new UnsupportedOperationException("NYI");
    }


    public ActionURL urlEditDefinition(Domain domain)
    {
        throw new UnsupportedOperationException("NYI");
    }

    // return the "system" properties for this domain
    // return the "system" properties for this domain
    public DomainProperty[] getDomainProperties(String domainURI)
    {
//            TableInfo t = StudySchema.getInstance().getTableInfoStudyData();
//            for (ColumnInfo c : t.getColumns())
//            {
//                if (c.getName().equalsIgnoreCase("container") || c.getName().equalsIgnoreCase("datasetid"))
//                    continue;
//                if (c.getName().startsWith("_"))
//                    continue;
//                DomainProperty p = new DomainProperty();
//                p.setName(c.getName());
//                p.setLabel(c.getCaption());
//                PropertyType pt = PropertyType.getFromClass(c.getJavaObjectClass());
//                p.setRangeURI(pt.getTypeUri());
//                p.setRequired(!c.isNullable());
//                p.setDescription(c.getDescription());
//                p.setEditable(false);
//                list.add(p);
//            }
        return new DomainProperty[0];
    }
}
