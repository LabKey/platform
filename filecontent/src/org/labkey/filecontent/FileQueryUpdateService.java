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

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
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
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 17, 2010
 * Time: 1:38:56 PM
 */
public class FileQueryUpdateService extends AbstractQueryUpdateService
{
    private Container _container;
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
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        String uri = svc.getDomainURI(container);
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, container);
        ExpData data = dataRowFromKeys(user, container, keys, true);

        if (dd != null && data != null)
        {
            Map<String, Object> rowMap = new HashMap<String, Object>();
            Domain domain = PropertyService.get().getDomain(dd.getDomainId());
            if (domain != null)
            {
                for (DomainProperty prop : domain.getProperties())
                {
                    Object o = data.getProperty(prop);
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
            return rowMap;
        }
        return null;
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
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        ExpData data = dataRowFromKeys(user, container, row, isUpdate);

        if (data != null)
        {
            String uri = svc.getDomainURI(container);
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, container);
            WebdavResource resource = davResourceFromKeys(row);


            if (dd != null)
            {
                Domain domain = PropertyService.get().getDomain(dd.getDomainId());
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
                            sb.append(delim).append(prop.getLabel()).append('=').append(String.valueOf(value));
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

    private ExpData dataRowFromKeys(User user, Container container, Map<String, Object> keys, boolean isUpdate)
    {
        if (keys.containsKey(KEY_COL_DAV))
        {
            WebdavResource resource = getResource(String.valueOf(keys.get(KEY_COL_DAV)));
            return FileContentServiceImpl.getDataObject(resource, container, user, !isUpdate);
        }
        else if (keys.containsKey(KEY_COL_ID))
        {
            WebdavResource resource = getResource(String.valueOf(keys.get(KEY_COL_ID)));
            return FileContentServiceImpl.getDataObject(resource, container, user, !isUpdate);
        }
        else if (keys.containsKey(KEY_COL_FILE))
        {
            File file = new File(String.valueOf(keys.get(KEY_COL_FILE)));
            if (file.exists())
                return ExperimentService.get().getExpDataByURL(file, container);
        }
        return null;
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
