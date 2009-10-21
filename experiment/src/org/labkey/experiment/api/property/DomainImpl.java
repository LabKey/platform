/*
 * Copyright (c) 2007-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.experiment.api.property;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.experiment.controllers.property.PropertyController;
import org.labkey.experiment.list.DomainAuditViewFactory;

import java.sql.SQLException;
import java.util.*;

public class DomainImpl implements Domain
{
    static final private Logger _log = Logger.getLogger(DomainImpl.class);
    boolean _new;
    DomainDescriptor _ddOld;
    DomainDescriptor _dd;
    List<DomainPropertyImpl> _properties;


    public DomainImpl(DomainDescriptor dd)
    {
        _dd = dd;
        PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(getTypeURI(), getContainer());
        _properties = new ArrayList<DomainPropertyImpl>(pds.length);
        for (PropertyDescriptor pd : pds)
        {
            _properties.add(new DomainPropertyImpl(this, pd));
        }
    }
    public DomainImpl(Container container, String uri, String name)
    {
        _new = true;
        _dd = new DomainDescriptor();
        _dd.setContainer(container);
        _dd.setProject(container.getProject());
        _dd.setDomainURI(uri);
        _dd.setName(name);
        _properties = new ArrayList<DomainPropertyImpl>();
    }

    public Container getContainer()
    {
        return _dd.getContainer();
    }

    public DomainKind getDomainKind()
    {
        DomainKind kind = PropertyService.get().getDomainKind(getTypeURI());
        if (null == kind)
            _log.warn("DomainKind not found: " + getTypeURI());
        return kind;
    }

    public String getName()
    {
        return _dd.getName();
    }

    public String getLabel()
    {
        DomainKind kind = getDomainKind();
        if (kind == null)
        {
            return "Domain '" + getName() + "'";
        }
        else
        {
            return getDomainKind().getTypeLabel(this);
        }
    }

    public String getLabel(Container container)
    {
        String ret = getLabel();
        if (!getContainer().equals(container))
        {
            ret += "(" + getContainer().getPath() + ")";
        }
        return ret;
    }

    public Container[] getInstanceContainers()
    {
        SQLFragment sqlObjectIds = getDomainKind().sqlObjectIdsInDomain(this);
        if (sqlObjectIds == null)
            return new Container[0];
        SQLFragment sql = new SQLFragment("SELECT DISTINCT exp.object.container FROM exp.object WHERE exp.object.objectid IN ");
        sql.append(sqlObjectIds);
        try
        {
            String[] ids = Table.executeArray(ExperimentService.get().getSchema(), sql, String.class);
            Container[] ret = new Container[ids.length];
            for (int i = 0; i < ids.length; i ++)
            {
                ret[i] = ContainerManager.getForId(ids[i]);
            }
            return ret;
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public Container[] getInstanceContainers(User user, int perm)
    {
        Container[] all = getInstanceContainers();
        List<Container> ret = new ArrayList<Container>();
        for (Container c : all)
        {
            if (c.hasPermission(user, perm))
            {
                ret.add(c);
            }
        }
        return ret.toArray(new Container[0]);
    }

    public String getDescription()
    {
        return _dd.getDescription();
    }

    public int getTypeId()
    {
        return _dd.getDomainId();
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public DomainPropertyImpl[] getProperties()
    {
        return _properties.toArray(new DomainPropertyImpl[_properties.size()]);
    }

    public void setPropertyIndex(DomainProperty prop, int index)
    {
        if (index < 0 || index >= _properties.size())
        {
            throw new IndexOutOfBoundsException();
        }
        if (!_properties.remove((DomainPropertyImpl)prop))
        {
            throw new IllegalArgumentException("The property is not part of this domain");
        }
        _properties.add(index, (DomainPropertyImpl)prop);
    }

    public ActionURL urlEditDefinition(boolean allowFileLinkProperties, boolean allowAttachmentProperties, boolean showDefaultValueSettings)
    {
        ActionURL ret = new ActionURL(PropertyController.EditDomainAction.class, getContainer());
        ret.addParameter("domainId", Integer.toString(getTypeId()));
        if (allowAttachmentProperties)
            ret.addParameter("allowAttachmentProperties", "1");
        if (allowFileLinkProperties)
            ret.addParameter("allowFileLinkProperties", "1");
        if (showDefaultValueSettings)
            ret.addParameter("showDefaultValueSettings", "1");
        return ret;
    }

    public ActionURL urlShowData()
    {
        return getDomainKind().urlShowData(this);
    }

    public void delete(User user) throws DomainNotFoundException
    {
        OntologyManager.deleteDomain(getTypeURI(), getContainer());
        addAuditEvent(user, String.format("The domain %s was deleted", _dd.getName()));
    }

    private boolean isNew()
    {
        return _new;
    }

    public void save(User user) throws ChangePropertyDescriptorException
    {
        boolean transaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                transaction = true;
            }
            boolean keyColumnsChanged = false;
            if (isNew())
            {
                _dd = Table.insert(user, OntologyManager.getTinfoDomainDescriptor(), _dd);
                addAuditEvent(user, String.format("The domain %s was created", _dd.getName()));
            }
            else if (_ddOld != null)
            {
                _dd = Table.update(user, OntologyManager.getTinfoDomainDescriptor(), _dd, _dd.getDomainId());
                addAuditEvent(user, String.format("The descriptor of domain %s was updated", _dd.getName()));
            }
            boolean propChanged = false;
            int sortOrder = 0;
            for (DomainPropertyImpl impl : _properties)
            {
                if (impl._deleted)
                {
                    impl.delete(user);
                    propChanged = true;
                }
                else if (impl.isNew())
                {
                    if (impl._pd.isRequired())
                        keyColumnsChanged = true;
                    impl.save(user, _dd, sortOrder++);
                    propChanged = true;
                }
                else
                {
                    propChanged |= impl.isDirty();
                    if ((impl._pdOld != null && !impl._pdOld.isRequired()) && impl._pd.isRequired())
                        keyColumnsChanged = true;
                    impl.save(user, _dd, sortOrder++);
                }
            }
            _new = false;
            if (propChanged)
                addAuditEvent(user, String.format("The column(s) of domain %s were modified", _dd.getName()));

            if (keyColumnsChanged)
            {
                DomainKind kind = getDomainKind();
                if (null != kind)
                {
                    SQLFragment sqlObjectIds = kind.sqlObjectIdsInDomain(this);
                    SQLFragment sqlCount = new SQLFragment("SELECT COUNT(exp.object.objectId) AS value FROM exp.object WHERE exp.object.objectid IN (");
                    sqlCount.append(sqlObjectIds);
                    sqlCount.append(")");
                    Map[] maps = Table.executeQuery(ExperimentService.get().getSchema(), sqlCount.getSQL(), sqlCount.getParams().toArray(), Map.class);
                    if (((Number) maps[0].get("value")).intValue() != 0)
                    {
                        throw new IllegalStateException("The required property cannot be changed when rows already exist.");
                    }
                }
            }
            if (transaction)
            {
                ExperimentService.get().commitTransaction();
                transaction = false;
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (transaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    private void addAuditEvent(User user, String comment)
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setComment(comment);

            Container c = getContainer();
            event.setContainerId(c.getId());
            if (_dd.getProject() != null)
                event.setProjectId(_dd.getProject().getId());

            event.setKey1(getTypeURI());
            event.setKey3(getName());
            event.setEventType(DomainAuditViewFactory.DOMAIN_AUDIT_EVENT);

            AuditLogService.get().addEvent(event);
        }
    }

    public Map<String, DomainProperty> createImportMap(boolean includeMVIndicators)
    {
        DomainPropertyImpl[] properties = getProperties();
        HashMap<String, DomainProperty> m = new CaseInsensitiveHashMap<DomainProperty>(properties.length * 3);
        DomainPropertyImpl[] reversedProperties = new DomainPropertyImpl[properties.length];
        int index = 0;
        // Reverse the order of the descriptors so that we can preserve the right priority for resolving by names, aliases, etc
        for (DomainPropertyImpl prop : properties)
        {
            reversedProperties[reversedProperties.length - (index++) - 1] = prop;
        }

        // PropertyURI is lowest priority, so put it in the map first so it will be overwritten by higher priority usages
        for (DomainPropertyImpl property : reversedProperties)
        {
            m.put(property.getPropertyURI(), property);
        }
        // Then aliases
        for (DomainPropertyImpl property : reversedProperties)
        {
            for (String alias : property.getImportAliasSet())
            {
                m.put(alias, property);
            }
        }
        // Then labels
        for (DomainPropertyImpl property : reversedProperties)
        {
            if (null != property.getLabel())
                m.put(property.getLabel(), property);
            else
                m.put(ColumnInfo.labelFromName(property.getName()), property); // If no label, columns will create one for captions
        }
        if (includeMVIndicators)
        {
            // Then missing value-specific columns
            for (DomainPropertyImpl property : reversedProperties)
            {
                if (property.isMvEnabled())
                    m.put(property.getName() + MvColumn.MV_INDICATOR_SUFFIX, property);
            }
        }
        // Finally, names have the highest priority
        for (DomainPropertyImpl property : reversedProperties)
        {
            m.put(property.getName(), property);
        }
        return m;
    }

    public DomainProperty addProperty()
    {
        PropertyDescriptor pd = new PropertyDescriptor();
        pd.setContainer(getContainer());
        pd.setRangeURI(PropertyType.STRING.getTypeUri());
        DomainPropertyImpl ret = new DomainPropertyImpl(this, pd);
        _properties.add(ret);
        return ret;
    }

    public String getTypeURI()
    {
        return _dd.getDomainURI();
    }

    public DomainPropertyImpl getProperty(int id)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getPropertyId() == id)
                return prop;
        }
        return null;
    }

    public DomainPropertyImpl getPropertyByURI(String uri)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getPropertyURI().equals(uri))
            {
                return prop;
            }
        }
        return null;
    }

    public DomainPropertyImpl getPropertyByName(String name)
    {
        for (DomainPropertyImpl prop : getProperties())
        {
            if (prop.getName().equals(name))
                return prop;
        }
        return null;
    }

    public void initColumnInfo(final User user, final Container container, ColumnInfo columnInfo)
    {
        final Domain domain = this;
        final DomainKind type = getDomainKind();
        if (type == null)
            return;
        columnInfo.setFk(new AbstractForeignKey()
        {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                ColumnInfo displayColumn;
                Map.Entry<TableInfo, ColumnInfo> entry = getTableInfo();
                if (entry == null)
                    return null;
                if (displayField == null)
                {
                    displayColumn = entry.getKey().getColumn(entry.getKey().getTitleColumn());
                }
                else
                {
                    displayColumn = entry.getKey().getColumn(displayField);
                }
                return new LookupColumn(parent, entry.getValue(), displayColumn);
            }

            public TableInfo getLookupTableInfo()
            {
                Map.Entry<TableInfo, ColumnInfo> entry = getTableInfo();
                if (entry == null)
                    return null;
                return entry.getKey();
            }

            private Map.Entry<TableInfo, ColumnInfo> getTableInfo()
            {
                return type.getTableInfo(user, domain, getInstanceContainers(user, ACL.PERM_READ));
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                Map.Entry<TableInfo, ColumnInfo> entry = getTableInfo();
                if (entry == null)
                    return null;
                return LookupForeignKey.getDetailsURL(parent, entry.getKey(), getName());
            }
        });
    }

    public ColumnInfo[] getColumns(TableInfo sourceTable, ColumnInfo lsidColumn, User user)
    {
        DomainProperty[] domainProperties = getProperties();
        ColumnInfo[] columns = new ColumnInfo[domainProperties.length];
        for (int i=0; i<domainProperties.length; i++)
        {
            DomainProperty property = domainProperties[i];
            ColumnInfo column = new PropertyColumn(property.getPropertyDescriptor(), lsidColumn, null, user);
            columns[i] = column;
        }
        return columns;
    }

    private DomainDescriptor edit()
    {
        if (_new)
        {
            return _dd;
        }
        if (_ddOld == null)
        {
            _ddOld = _dd;
            _dd = _ddOld.clone();
        }
        return _dd;
    }

    @Override
    public int hashCode()
    {
        return _dd.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof DomainImpl))
            return false;
        // once a domain  has been edited, it no longer equals any other domain:
        if (_ddOld != null || ((DomainImpl) obj)._ddOld != null)
            return false;
        return (_dd.equals(((DomainImpl) obj)._dd));
    }
}
