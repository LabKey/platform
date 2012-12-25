/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.MVDisplayColumnFactory;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditViewFactory;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        DomainPropertyManager.ConditionalFormatWithPropertyId[] allFormats = DomainPropertyManager.get().getConditionalFormats(this);
        for (PropertyDescriptor pd : pds)
        {
            List<ConditionalFormat> formats = new ArrayList<ConditionalFormat>();
            for (DomainPropertyManager.ConditionalFormatWithPropertyId format : allFormats)
            {
                if (format.getPropertyId() == pd.getPropertyId())
                {
                    formats.add(format);
                }
            }
            DomainPropertyImpl property = new DomainPropertyImpl(this, pd, formats);
            _properties.add(property);
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

    private DomainKind _kind = null;

    public synchronized DomainKind getDomainKind()
    {
        if (null == _kind)
            _kind = PropertyService.get().getDomainKind(getTypeURI());
        return _kind;
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


    public String getStorageTableName()
    {
        return _dd.getStorageTableName();
    }


    public Container[] getInstanceContainers()
    {
        SQLFragment sqlObjectIds = getDomainKind().sqlObjectIdsInDomain(this);
        if (sqlObjectIds == null)
            return new Container[0];
        SQLFragment sql = new SQLFragment("SELECT DISTINCT exp.object.container FROM exp.object WHERE exp.object.objectid IN ");
        sql.append(sqlObjectIds);
        String[] ids = new SqlSelector(ExperimentService.get().getSchema(), sql).getArray(String.class);
        Container[] ret = new Container[ids.length];
        for (int i = 0; i < ids.length; i ++)
        {
            ret[i] = ContainerManager.getForId(ids[i]);
        }
        return ret;
    }

    public Container[] getInstanceContainers(User user, Class<? extends Permission> perm)
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
        return ret.toArray(new Container[ret.size()]);
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
        if (!_properties.remove(prop))
        {
            throw new IllegalArgumentException("The property is not part of this domain");
        }
        _properties.add(index, (DomainPropertyImpl)prop);
    }

    public ActionURL urlShowData(ContainerUser context)
    {
        return getDomainKind().urlShowData(this, context);
    }

    public void delete(@Nullable User user) throws DomainNotFoundException
    {
        DefaultValueService.get().clearDefaultValues(getContainer(), this);
        OntologyManager.deleteDomain(getTypeURI(), getContainer());
        StorageProvisioner.drop(this);
        addAuditEvent(user, String.format("The domain %s was deleted", _dd.getName()));
    }

    private boolean isNew()
    {
        return _new;
    }

    // TODO: throws SQLException instead of RuntimeSQLException (e.g., constraint violation due to duplicate domain name) 
    public void save(User user) throws ChangePropertyDescriptorException
    {
        try
        {
            ExperimentService.get().ensureTransaction();

            List<DomainProperty> newlyRequiredProps = new ArrayList<DomainProperty>();
            if (isNew())
            {
                _dd = Table.insert(user, OntologyManager.getTinfoDomainDescriptor(), _dd);
// CONSIDER put back if we want automatic provisioning for serveral DomainKinds
//                StorageProvisioner.create(this);
                addAuditEvent(user, String.format("The domain %s was created", _dd.getName()));
            }
            else if (_ddOld != null)
            {
                _dd = Table.update(user, OntologyManager.getTinfoDomainDescriptor(), _dd, _dd.getDomainId());
                addAuditEvent(user, String.format("The descriptor of domain %s was updated", _dd.getName()));
            }
            boolean propChanged = false;
            int sortOrder = 0;

            List<DomainProperty> propsDropped = new ArrayList<DomainProperty>();
            List<DomainProperty> propsAdded = new ArrayList<DomainProperty>();

            // Delete first #8978
            for (DomainPropertyImpl impl : _properties)
            {
                if (impl._deleted)
                {
                    impl.delete(user);
                    propsDropped.add(impl);
                    propChanged = true;
                }
            }

            // Now add and update #8978
            for (DomainPropertyImpl impl : _properties)
            {
                if (!impl._deleted)
                {
                    if (impl.isNew())
                    {
                        if (impl._pd.isRequired())
                            newlyRequiredProps.add(impl);
                        impl.save(user, _dd, sortOrder++);
                        propsAdded.add(impl);
                        propChanged = true;
                    }
                    else
                    {
                        propChanged |= impl.isDirty();

                        if ((impl._pdOld != null && !impl._pdOld.isRequired()) && impl._pd.isRequired())
                            newlyRequiredProps.add(impl);
                        impl.save(user, _dd, sortOrder++);
                    }
                }
            }

            _new = false;

            DomainKind kind = getDomainKind();
            boolean hasProvisioner = null != kind && null != kind.getStorageSchemaName();

            if (propChanged)
            {
                if (!propsDropped.isEmpty() && hasProvisioner)
                {
                    StorageProvisioner.dropProperties(this, propsDropped);
                }

                if (!propsAdded.isEmpty() && hasProvisioner)
                {
                    StorageProvisioner.addProperties(this, propsAdded);
                }

                addAuditEvent(user, String.format("The column(s) of domain %s were modified", _dd.getName()));
            }

            if (!newlyRequiredProps.isEmpty() && null != kind)
            {
                for (DomainProperty prop : newlyRequiredProps)
                {
                    boolean hasRows = kind.hasNullValues(this, prop);
                    if (hasRows)
                    {
                        throw new IllegalStateException("The property \"" + prop.getName() + "\" cannot be set to required when rows with blank values already exist.");
                    }
                }
            }

            ExperimentService.get().commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
    }

    private void addAuditEvent(@Nullable User user, String comment)
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
        List<DomainProperty> properties = new ArrayList<DomainProperty>(_properties);
        return ImportAliasable.Helper.createImportMap(properties, includeMVIndicators);
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
            if (prop.getPropertyURI().equalsIgnoreCase(uri))
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
            if (prop.getName().equalsIgnoreCase(name))
                return prop;
        }
        return null;
    }

    public List<ColumnInfo> getColumns(TableInfo sourceTable, ColumnInfo lsidColumn, Container container, User user)
    {
        List<ColumnInfo> result = new ArrayList<ColumnInfo>();
        for (DomainProperty property : getProperties())
        {
            ColumnInfo column = new PropertyColumn(property.getPropertyDescriptor(), lsidColumn, container, user, false);
            result.add(column);
            if (property.isMvEnabled())
            {
                column.setMvColumnName(new FieldKey(null, column.getName() + MvColumn.MV_INDICATOR_SUFFIX));
                result.addAll(MVDisplayColumnFactory.createMvColumns(column, property.getPropertyDescriptor(), lsidColumn));
            }
        }
        return result;
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
        // once a domain has been edited, it no longer equals any other domain:
        if (_ddOld != null || ((DomainImpl) obj)._ddOld != null)
            return false;
        return (_dd.equals(((DomainImpl) obj)._dd));
    }

    @Override
    public String toString()
    {
        return getTypeURI();
    }
}
