/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.filecontent;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.labkey.api.data.*;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.writer.ContainerUser;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 17, 2010
 * Time: 1:38:56 PM
 */
public class FileQueryUpdateService extends AbstractQueryUpdateService
{
    private Container _container;
    private Set<String> _columns;
    private Domain _domain;

    public static final String KEY_COL_ID = "id";
    public static final String KEY_COL_DAV = "davUrl";
    public static final String KEY_COL_FILE = "filePath";


    public FileQueryUpdateService(TableInfo queryTable, Container container)
    {
        super(queryTable);
        _container = container;
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Filter filter = getQueryFilter(keys);

        Map<String, Object> rowMap = Table.selectObject(getQueryTable(), getQueryColumns(container), filter, null, Map.class);
        if (rowMap != null)
        {
            Domain domain = getFileProperties(container);
            if (domain != null)
            {
                for (DomainProperty prop : domain.getProperties())
                {
                    Object o = rowMap.get(prop.getName());
                    if (o != null)
                    {
                        try {
                            rowMap.put(prop.getName(), DomainUtil.getFormattedDefaultValue(user, prop, o));
                        }
                        catch (Exception e)
                        {
                            //throw new QueryUpdateServiceException(e);
                        }
                    }
                }
            }
        }
        return rowMap;
    }

    IntegerConverter _converter = new IntegerConverter();

    private Filter getQueryFilter(Map<String, Object> keys) throws QueryUpdateServiceException
    {
        Filter filter;

        if (keys.containsKey(ExpDataTable.Column.RowId.name()))
        {
            Object o = keys.get(ExpDataTable.Column.RowId.name());
            filter = new SimpleFilter(ExpDataTable.Column.RowId.name(), _converter.convert(Integer.class, o));
        }
        else if (keys.containsKey(ExpDataTable.Column.LSID.name()))
        {
            Object o = keys.get(ExpDataTable.Column.LSID.name());
            filter = new SimpleFilter(ExpDataTable.Column.LSID.name(), o);
        }
        else if (keys.containsKey(ExpDataTable.Column.DataFileUrl.name()))
        {
            Object o = keys.get(ExpDataTable.Column. DataFileUrl.name());
            filter = new SimpleFilter("DataFileUrl", o);
        }
        else
            throw new QueryUpdateServiceException("Either RowId, LSID, or DataFileURL is required to get ExpData.");

        return filter;
    }

    private Set<String> getQueryColumns(Container c)
    {
        if (_columns == null)
        {
            _columns = new HashSet<String>();

            _columns.add(ExpDataTable.Column.Flag.name());
            _columns.add(ExpDataTable.Column.DataFileUrl.name());

            Domain domain = getFileProperties(c);
            if (domain != null)
            {
                for (DomainProperty prop : domain.getProperties())
                    _columns.add(prop.getName());
            }
        }
        return _columns;
    }

    private Domain getFileProperties(Container container)
    {
        if (_domain == null)
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            String uri = svc.getDomainURI(container);
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, container);

            if (dd != null)
                _domain = PropertyService.get().getDomain(dd.getDomainId());
        }
        return _domain;
    }

    @Override
    protected boolean hasPermission(User user, Class<? extends Permission> acl)
    {
        return _container.hasPermission(user, acl);
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        return _setRow(user, container, row, false);
    }

    private Map<String, Object> _setRow(final User user, final Container container, Map<String, Object> row, boolean isUpdate) throws ValidationException
    {
        String dataFileUrl = null;

        if (!row.containsKey(ExpDataTable.Column.DataFileUrl.name()))
        {
            try {
                Filter filter = getQueryFilter(row);
                Map<String, Object> rowMap = Table.selectObject(getQueryTable(), getQueryColumns(container), filter, null, Map.class);
                if (rowMap != null)
                    dataFileUrl = String.valueOf(rowMap.get(ExpDataTable.Column.DataFileUrl.name()));
            }
            catch (Exception e)
            {
                throw new ValidationException("Unable to get the DataFileUrl");
            }
        }
        else
            dataFileUrl = String.valueOf(row.get(ExpDataTable.Column.DataFileUrl.name()));

        ExpData data = ExperimentService.get().getExpDataByURL(dataFileUrl, container);

        if (data != null)
        {
            Domain domain = getFileProperties(container);
            WebdavResource resource = davResourceFromKeys(row);

            if (domain != null)
            {
                StringBuilder sb = new StringBuilder("annotations updated: ");
                String delim = "";

                for (DomainProperty prop : domain.getProperties())
                {
                    if (row.containsKey(prop.getName()))
                    {
                        Object value = row.get(prop.getName());

                        data.setProperty(user, prop.getPropertyDescriptor(), value);
                        sb.append(delim).append(prop.getLabel() != null ? prop.getLabel() : prop.getName()).append('=').append(String.valueOf(value));
                        delim = ",";
                    }
                }
                if (resource != null)
                    resource.notify(new ContainerUser(){
                        public User getUser(){
                            return user;
                        }
                        public Container getContainer(){
                            return container;
                        }
                    }, sb.toString());
            }
            return row;
        }
        return null;
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        return _setRow(user, container, row, true);
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("DeleteRow not supported");
    }

    private WebdavResource davResourceFromKeys(Map<String, Object> keys)
    {
        if (keys.containsKey(KEY_COL_DAV))
        {
            return getResource(String.valueOf(keys.get(KEY_COL_DAV)));
        }
        else if (keys.containsKey(KEY_COL_ID))
        {
            return getResource(String.valueOf(keys.get(KEY_COL_ID)));
        }
        return null;
    }

    private WebdavResource getResource(String uri)
    {
        Path path = Path.decode(uri);

        if (!path.startsWith(WebdavService.getPath()) && path.contains(WebdavService.getPath().getName()))
        {
            String newPath = path.toString();
            int idx = newPath.indexOf(WebdavService.getPath().toString());

            if (idx != -1)
            {
                newPath = newPath.substring(idx);
                path = Path.parse(newPath);
            }
        }
        return WebdavService.get().getResolver().lookup(path);
    }
}
