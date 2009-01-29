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

package org.labkey.experiment.list;

import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.view.ActionURL;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.common.tools.DataLoader;
import org.labkey.experiment.controllers.list.ListController;
import org.labkey.experiment.controllers.list.ListImportHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class ListDefinitionImpl implements ListDefinition
{
    static public ListDefinitionImpl of(ListDef def)
    {
        if (def == null)
            return null;
        return new ListDefinitionImpl(def);
    }

    boolean _new;
    ListDef _defOld;
    ListDef _def;
    Domain _domain;
    public ListDefinitionImpl(ListDef def)
    {
        _def = def;
    }

    public ListDefinitionImpl(Container container, String name)
    {
        _new = true;
        _def = new ListDef();
        _def.setContainer(container.getId());
        _def.setName(name);
        String typeURI = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":List" + ".Folder-" + container.getRowId() + ":" + name;
        _domain = PropertyService.get().createDomain(container, new Lsid(typeURI).toString(), name);
    }

    public int getListId()
    {
        return _def.getRowId();
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_def.getContainerId());
    }

    public Domain getDomain()
    {
        if (_domain == null)
        {
            _domain = PropertyService.get().getDomain(_def.getDomainId());
        }
        return _domain;
    }

    public String getName()
    {
        return _def.getName();
    }

    public String getKeyName()
    {
        return _def.getKeyName();
    }

    public void setKeyName(String name)
    {
        if (_def.getTitleColumn() != null && _def.getTitleColumn().equals(getKeyName()))
        {
            edit().setTitleColumn(name);
        }
        edit().setKeyName(name);
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public KeyType getKeyType()
    {
        return KeyType.valueOf(_def.getKeyType());
    }

    public void setKeyType(KeyType type)
    {
        _def.setKeyType(type.toString());
    }

    public DiscussionSetting getDiscussionSetting()
    {
        return _def.getDiscussionSettingEnum();
    }

    public void setDiscussionSetting(DiscussionSetting discussionSetting)
    {
        _def.setDiscussionSettingEnum(discussionSetting);
    }

    public boolean getAllowDelete()
    {
        return _def.getAllowDelete();
    }

    public void setAllowDelete(boolean allowDelete)
    {
        _def.setAllowDelete(allowDelete);
    }

    public boolean getAllowUpload()
    {
        return _def.getAllowUpload();
    }

    public void setAllowUpload(boolean allowUpload)
    {
        _def.setAllowUpload(allowUpload);
    }

    public boolean getAllowExport()
    {
        return _def.getAllowExport();
    }

    public void setAllowExport(boolean allowExport)
    {
        _def.setAllowExport(allowExport);
    }

    public void save(User user) throws Exception
    {
        boolean fTransaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                fTransaction = true;
            }
            if (_new)
            {
                _domain.save(user);
                _def.setDomainId(_domain.getTypeId());
                _def = ListManager.get().insert(user, _def);
                _new = false;
            }
            else
            {
                _def = ListManager.get().update(user, _def);
                _defOld = null;
                addAuditEvent(user, String.format("The definition of the list %s was modified", _def.getName()));
            }
            if (fTransaction)
            {
                ExperimentService.get().commitTransaction();
                fTransaction = false;
            }
        }
        finally
        {
            if (fTransaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    public ListItem createListItem()
    {
        return new ListItemImpl(this);
    }

    public ListItem getListItem(Object key)
    {
        // Convert key value to the proper type, since PostgreSQL 8.3 requires that key parameter types match their column types.
        Object typedKey = getKeyType().convertKey(key);

        return getListItem(new SimpleFilter("Key", typedKey));
    }

    public ListItem getListItemForEntityId(String entityId)
    {
        return getListItem(new SimpleFilter("EntityId", entityId));
    }

    private ListItem getListItem(SimpleFilter filter)
    {
        try
        {
            filter.addCondition("ListId", getListId());
            ListItm itm = Table.selectObject(getIndexTable(), Table.ALL_COLUMNS, filter, null, ListItm.class);
            if (itm == null)
            {
                return null;
            }
            return new ListItemImpl(this, itm);
        }
        catch (SQLException e)
        {
            return null;
        }
    }

    public void deleteListItems(User user, Collection keys) throws SQLException
    {
        boolean fTransaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                fTransaction = true;
            }
            for (Object key : keys)
            {
                ListItem item = getListItem(key);
                if (item != null)
                {
                    item.delete(user, getContainer());
                }
            }
            if (fTransaction)
            {
                ExperimentService.get().commitTransaction();
                fTransaction = false;
            }
        }
        finally
        {
            if (fTransaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    public void delete(User user) throws SQLException, DomainNotFoundException
    {
        boolean fTransaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                fTransaction = true;
            }
            SimpleFilter lstItemFilter = new SimpleFilter("ListId", getListId());
            ListItm[] itms = Table.select(getIndexTable(), Table.ALL_COLUMNS, lstItemFilter, null, ListItm.class);
            Table.delete(getIndexTable(), lstItemFilter);
            for (ListItm itm : itms)
            {
                if (itm.getObjectId() == null)
                    continue;
                ListItemImpl.deleteListItemContents(itm, getContainer(), user);
            }
            Table.delete(ListManager.get().getTinfoList(), getListId(), null);
            Domain domain = getDomain();
            domain.delete(user);
            if (fTransaction)
            {
                ExperimentService.get().commitTransaction();
                fTransaction = false;
            }
        }
        finally
        {
            if (fTransaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    public List<String> insertListItems(User user, DataLoader loader) throws IOException
    {
        List<String> errors = new ArrayList<String>();
        ArrayList<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
        Set<String> qcIndicatorColumnNames = new CaseInsensitiveHashSet();
        for (DomainProperty property : getDomain().getProperties())
        {
            pds.add(property.getPropertyDescriptor());
            if (property.isQcEnabled())
                qcIndicatorColumnNames.add(property.getName() + QcColumn.QC_INDICATOR_SUFFIX);
        }
        Map<String, PropertyDescriptor> propertiesByName = OntologyManager.createImportPropertyMap(pds.toArray(new PropertyDescriptor[pds.size()]));
        Map<String, PropertyDescriptor> foundProperties = new CaseInsensitiveHashMap<PropertyDescriptor>();
        ColumnDescriptor cdKey = null;

        Object errorValue = new Object();

        for (ColumnDescriptor cd : loader.getColumns())
        {
            PropertyDescriptor property = propertiesByName.get(cd.name);
            cd.errorValues = errorValue;

            if (property != null)
            {
                // Special handling for qc indicators -- they don't have real property descriptors.
                if (qcIndicatorColumnNames.contains(cd.name))
                {
                    cd.name = property.getPropertyURI();
                    cd.clazz = String.class;
                    cd.setQcIndicator(getContainer());
                }
                else
                {
                    cd.clazz = property.getPropertyType().getJavaType();

                    if (foundProperties.containsKey(cd.name))
                    {
                        errors.add("The field '" + property.getName() + "' appears more than once.");
                    }
                    if (foundProperties.containsValue(property) && !property.isQcEnabled())
                    {
                        errors.add("The fields '" + property.getName() + "' and '" + property.getLabel() + "' refer to the same property.");
                    }
                    foundProperties.put(cd.name, property);
                    cd.name = property.getPropertyURI();
                    if (property.isQcEnabled())
                    {
                        cd.setQcEnabled(getContainer());
                    }
                }
            }
            else if (!getKeyName().equalsIgnoreCase(cd.name))
            {
                errors.add("The field '" + cd.name + "' could not be matched to an existing field in this list.");
            }
            if (getKeyName().equalsIgnoreCase(cd.name))
            {
                if (cdKey != null)
                {
                    errors.add("The field '" + getKeyName() + "' appears more than once.");
                }
                else
                {
                    cdKey = cd;
                }
            }
        }

        if (cdKey == null && getKeyType() != ListDefinition.KeyType.AutoIncrementInteger)
        {
            errors.add("There must be a field with the name '" + getKeyName() + "'");
        }

        if (errors.size() > 0)
            return errors;

        List<Map<String, Object>> rows = loader.load();

        switch (getKeyType())
        {
            // All cdKey.clazz values are okay
            case Varchar:
                break;

            // Fine if it's missing, otherwise fall through
            case AutoIncrementInteger:
                if (null == cdKey)
                    break;

                // cdKey must be class Integer if autoincrement key exists or normal Integer key column
            case Integer:
                if (Integer.class.equals(cdKey.clazz))
                    break;

            default:
                errors.add("Expected key field \"" + cdKey.name + "\" to all be of type Integer but they are of type " + cdKey.clazz.getSimpleName());
                return errors;
        }

        Set<Object> keyValues = new HashSet<Object>();
        Set<String> missingValues = new HashSet<String>();
        Set<String> wrongTypes = new HashSet<String>();
        Set<String> noUpload = new HashSet<String>();

		DomainProperty[] domainProperties = getDomain().getProperties();
		
        for (Map row : rows)
        {
            row = new CaseInsensitiveHashMap<Object>(row);
            for (DomainProperty domainProperty : domainProperties)
            {
                Object o = row.get(domainProperty.getPropertyURI());
                String value = o == null ? null : o.toString();
                boolean valueMissing = (value == null || value.length() == 0);
                if (domainProperty.isRequired() && valueMissing && !missingValues.contains(domainProperty.getName()))
                {
                    missingValues.add(domainProperty.getName());
                    errors.add(domainProperty.getName() + " is required.");
                }
                else if (!valueMissing && domainProperty.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT && !noUpload.contains(domainProperty.getName()))   // TODO: Change to allowUpload() getter on property
                {
                    noUpload.add(domainProperty.getName());
                    errors.add("Can't upload to field " + domainProperty.getName() + " with type " + domainProperty.getType().getLabel() + ".");
                }
                else if (!valueMissing && o == errorValue && !wrongTypes.contains(domainProperty.getName()))
                {
                    wrongTypes.add(domainProperty.getName());
                    errors.add(domainProperty.getName() + " must be of type " + domainProperty.getType().getLabel() + ".");
                }
            }

            if (cdKey != null)
            {
                Object key = row.get(cdKey.name);
                if (null == key)
                {
                    errors.add("Blank values are not allowed in field " + cdKey.name);
                    return errors;
                }
                else if (!getKeyType().isValidKey(key))
                {
                    errors.add("Could not convert value \"" + key + "\" in key field \"" + cdKey.name + "\" to type " + getKeyType().getLabel());
                    return errors;
                }
                else if (!keyValues.add(key))
                {
                    errors.add("There are multiple rows with key value " + row.get(cdKey.name));
                    return errors;
                }
            }
        }

        if (errors.size() > 0)
            return errors;

        doBulkInsert(user, cdKey, getDomain(), foundProperties, rows, errors);

        return errors;
    }

    private void doBulkInsert(User user, ColumnDescriptor cdKey, Domain domain, Map<String, PropertyDescriptor> properties, List<Map<String, Object>> rows, List<String> errors)
    {
        boolean transaction = false;

        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                transaction = true;
            }

            // There's a disconnect here between the PropertyService api and OntologyManager...
            ArrayList<DomainProperty> used = new ArrayList<DomainProperty>(properties.size());
            for (DomainProperty dp : domain.getProperties())
                if (properties.containsKey(dp.getPropertyURI()))
                    used.add(dp);
            ListImportHelper helper = new ListImportHelper(user, this, used.toArray(new DomainProperty[used.size()]), cdKey);

            // our map of properties can have duplicates due to qc indicator columns (different columns, same URI)
            Set<PropertyDescriptor> propSet = new HashSet<PropertyDescriptor>(properties.values());

            PropertyDescriptor[] pds = propSet.toArray(new PropertyDescriptor[propSet.size()]);
            OntologyManager.insertTabDelimited(getContainer(), null, helper, pds, rows, true);
            if (transaction)
            {
                ExperimentService.get().commitTransaction();
                transaction = false;
            }
        }
        catch (ValidationException ve)
        {
            for (ValidationError error : ve.getErrors())
                errors.add(error.getMessage());
            return;
        }
        catch (SQLException se)
        {
            errors.add(se.getMessage());
            return;
        }
        finally
        {
            if (transaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    private void addAuditEvent(User user, String comment) throws Exception
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setComment(comment);

            Container c = getContainer();
            event.setContainerId(c.getId());
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
            event.setKey1(getDomain().getTypeURI());

            event.setEventType(ListManager.LIST_AUDIT_EVENT);
            event.setIntKey1(getListId());
            event.setKey3(getName());

            AuditLogService.get().addEvent(event);
        }
    }


    public int getRowCount()
    {
        return 0;
    }

    public String getDescription()
    {
        return _def.getDescription();
    }

    public String getTitleColumn()
    {
        return _def.getTitleColumn();
    }

    public void setTitleColumn(String titleColumn)
    {
        edit().setTitleColumn(titleColumn);
    }

    public TableInfo getTable(User user, String alias)
    {
        ListTable ret = new ListTable(user, this);
        if (alias != null)
        {
            ret.setAlias(alias);
        }
        return ret;
    }

    public ActionURL urlShowDefinition()
    {
        return urlFor(ListController.Action.showListDefinition);
    }

    public ActionURL urlEditDefinition()
    {
        return urlFor(ListController.Action.editListDefinition);
    }

    public ActionURL urlShowData()
    {
        return urlFor(ListController.Action.grid);
    }

    public ActionURL urlUpdate(Object pk, ActionURL returnUrl)
    {
        ActionURL url = urlFor(ListController.Action.update);

        // Can be null if caller will be filling in pk (e.g., grid edit column)
        if (null != pk)
            url.addParameter("pk", pk.toString());

        url.addParameter("returnUrl", returnUrl.getLocalURIString());

        return url;
    }

    public ActionURL urlDetails(Object pk)
    {
        ActionURL url = urlFor(ListController.Action.details);
        // Can be null if caller will be filling in pk (e.g., grid edit column)

        if (null != pk)
            url.addParameter("pk", pk.toString());

        return url;
    }

    public ActionURL urlShowHistory()
    {
        return urlFor(ListController.Action.history);
    }

    public ActionURL urlFor(Enum action)
    {
        ActionURL ret = getContainer().urlFor(action);
        ret.addParameter("listId", Integer.toString(getListId()));
        return ret;
    }

    private ListDef edit()
    {
        if (_new)
        {
            return _def;
        }
        if (_defOld == null)
        {
            _defOld = _def;
            _def = _defOld.clone();
        }
        return _def;

    }

    public TableInfo getIndexTable()
    {
        switch (getKeyType())
        {
            case Integer:
            case AutoIncrementInteger:
                return ListManager.get().getTinfoIndexInteger();
            case Varchar:
                return ListManager.get().getTinfoIndexVarchar();
            default:
                throw new IllegalStateException();
        }
    }
}
