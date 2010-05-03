/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.list.model;

import org.apache.axis.utils.StringUtils;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.list.client.GWTList;
import org.labkey.list.client.ListEditorService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: matthewb
 * Date: Mar 23, 2010
 * Time: 1:15:41 PM
 */
public class ListEditorServiceImpl extends DomainEditorServiceBase implements ListEditorService
{
    public ListEditorServiceImpl(ViewContext context)
    {
        super(context);
    }

    public GWTList createList(GWTList list) throws DuplicateNameException
    {
        if (!getContainer().hasPermission(getUser(), DesignListPermission.class))
            throw new UnauthorizedException();
        if (list.getListId() != 0)
            throw new IllegalArgumentException();

        try
        {
            ListDefinition definition = ListService.get().createList(getContainer(), list.getName());
            update(definition, list);
            definition.save(getUser());
            return getList(definition.getListId());
        }
        catch (SQLException x)
        {
            if (SqlDialect.isConstraintException(x))
                throw new DuplicateNameException(list.getName());
            throw new RuntimeSQLException(x);
        }
        catch (RuntimeException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }


    public List<String> getListNames()
    {
        Map<String,ListDefinition> m = ListService.get().getLists(getContainer());
        ArrayList ret = new ArrayList(m.keySet());
        return ret;
    }


    public GWTList getList(int id)
    {
        if (id == 0)
            return null;
        
        ListDefinition def =  ListService.get().getList(getContainer(), id); //ListManager.get().getList(getContainer(), id);
        if (def == null)
            return null;

        GWTList gwt = new GWTList();
        gwt._listId(id);
        gwt.setName(def.getName());
        gwt.setAllowDelete(def.getAllowDelete());
        gwt.setAllowExport(def.getAllowExport());
        gwt.setAllowUpload(def.getAllowUpload());
        gwt.setIndexMetaData(def.getIndexMetaData());
        gwt.setDescription(def.getDescription());
        gwt.setDiscussionSetting(def.getDiscussionSetting().getValue());
        gwt.setKeyPropertyName(def.getKeyName());
        gwt.setKeyPropertyType(def.getKeyType().name());
        gwt.setTitleField(def.getTitleColumn());
        gwt._typeURI(def.getDomain().getTypeURI());

        if (StringUtils.isEmpty(gwt.getTitleField()))
        {
            try
            {
                String title = def.getTable(getUser()).getTitleColumn();
                gwt._defaultTitleField(title);
            }
            catch (Exception x)
            {
                /* */     
            }
        }
        return gwt;
    }


    private void update(ListDef def, GWTList gwt)
    {
        def.setName(gwt.getName());
        def.setAllowDelete(gwt.getAllowDelete());
        def.setAllowExport(gwt.getAllowExport());
        def.setAllowUpload(gwt.getAllowUpload());
        def.setIndexMetaData(gwt.getIndexMetaData());
        def.setDescription(gwt.getDescription());
        def.setDiscussionSetting(gwt.getDiscussionSetting());
        def.setKeyName(gwt.getKeyPropertyName());
        def.setKeyType(gwt.getKeyPropertyType());
        def.setTitleColumn(gwt.getTitleField());
    }


    private void update(ListDefinition def, GWTList gwt)
    {
        def.setAllowDelete(gwt.getAllowDelete());
        def.setAllowExport(gwt.getAllowExport());
        def.setAllowUpload(gwt.getAllowUpload());
        def.setIndexMetaData(gwt.getIndexMetaData());
        def.setDescription(gwt.getDescription());
        def.setDiscussionSetting(ListDefinition.DiscussionSetting.getForValue(gwt.getDiscussionSetting()));
        def.setKeyName(gwt.getKeyPropertyName());
        def.setKeyType(ListDefinition.KeyType.valueOf(gwt.getKeyPropertyType()));
        def.setTitleColumn(gwt.getTitleField());
    }


    public List<String> updateListDefinition(GWTList list, GWTDomain orig, GWTDomain dd) throws ListEditorService.DuplicateNameException
    {
        if (!getContainer().hasPermission(getUser(), DesignListPermission.class))
            throw new UnauthorizedException();

        DbScope scope = ListManager.get().getSchema().getScope();
        
        ListDef def = ListManager.get().getList(getContainer(), list.getListId());
        if (def.getDomainId() != orig.getDomainId() || def.getDomainId() != dd.getDomainId() || !orig.getDomainURI().equals(dd.getDomainURI()))
            throw new IllegalArgumentException();

        // handle key column name change
        GWTPropertyDescriptor key = findField(def.getKeyName(), orig.getFields());
        if (null != key)
        {
            int id = key.getPropertyId();
            GWTPropertyDescriptor newKey = findField(id, dd.getFields());
            if (null != newKey)
                list.setKeyPropertyName(newKey.getName());
        }

        try
        {
            scope.beginTransaction();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        
        try
        {
            try
            {
                super.updateDomainDescriptor(orig, dd);
            }
            catch (ChangePropertyDescriptorException e)
            {
                return Collections.singletonList(e.getMessage());
            }

            boolean changedName = !def.getName().equals(list.getName());
            update(def, list);
            try
            {
                ListManager.get().update(getUser(), def);
            }
            catch (SQLException x)
            {
                if (changedName && SqlDialect.isConstraintException(x))
                    throw new DuplicateNameException(def.getName());
                throw x;
            }
            scope.commitTransaction();
        }
        catch (SQLException x)
        {

        }
        finally
        {
            if (scope.isTransactionActive())
                scope.rollbackTransaction();
        }
        return new ArrayList<String>(); // GWT error Collections.emptyList();
    }


    public GWTDomain getDomainDescriptor(GWTList list) throws SQLException
    {
        ListDef def = ListManager.get().getList(getContainer(), list.getListId());
        if (null == def)
            return null;

        GWTDomain<GWTPropertyDescriptor> domain = _getDomainDescriptor(def);
        if (null==domain)
            return null;
        
        GWTPropertyDescriptor key = findField(list.getKeyPropertyName(), domain.getFields());
        if (null == key)
        {
            // we need to create this property now, so that it doesn't look like an 'added' property in the designer
            key = new GWTPropertyDescriptor(def.getKeyName(), PropertyType.INTEGER.getTypeUri());
            try {
                key.setRangeURI(ListDefinition.KeyType.valueOf(def.getKeyType()).getPropertyType().getTypeUri());
            } catch (Exception x) {/* */}

            GWTDomain<GWTPropertyDescriptor> update = new GWTDomain<GWTPropertyDescriptor>(domain);
            List<GWTPropertyDescriptor> fields = new ArrayList<GWTPropertyDescriptor>(domain.getFields());
            fields.add(0,key);
            update.setFields(fields);
            try
            {
                updateListDefinition(list, domain, update);
            }
            catch (DuplicateNameException x)
            {
                throw new RuntimeException(x);
            }

            domain = _getDomainDescriptor(def);
        }

        return domain;
    }
    

    public GWTDomain<GWTPropertyDescriptor> _getDomainDescriptor(ListDef def)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(def.getDomainId());
        if (null == dd)
            return null;
        GWTDomain<GWTPropertyDescriptor> domain = DomainUtil.getDomainDescriptor(getUser(), dd.getDomainURI(), dd.getContainer());
        return domain;
    }


    private GWTPropertyDescriptor findField(String name, List<GWTPropertyDescriptor> fields)
    {
        for (GWTPropertyDescriptor f : fields)
        {
            if (name.equalsIgnoreCase(f.getName()))
                return f;
        }
        return null;
    }

    private GWTPropertyDescriptor findField(int id, List<GWTPropertyDescriptor> fields)
    {
        if (id > 0)
        {
            for (GWTPropertyDescriptor f : fields)
            {
                if (id == f.getPropertyId())
                    return f;
            }
        }
        return null;
    }
}
