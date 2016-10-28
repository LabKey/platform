/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.core.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.util.GUID;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.UnauthorizedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: jeckels
 * Date: 1/23/14
 */
public class ShortURLServiceImpl implements ShortURLService
{
    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<ShortURLListener> _listeners = new CopyOnWriteArrayList<>();

    @Override @Nullable
    public ShortURLRecord resolveShortURL(@NotNull String shortURL)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM ");
        sql.append(CoreSchema.getInstance().getTableInfoShortURL(), "s");
        sql.append(" WHERE LOWER(ShortURL) = LOWER(?)");
        sql.add(shortURL);
        return new SqlSelector(CoreSchema.getInstance().getSchema(), sql).getObject(ShortURLRecord.class);
    }

    @NotNull
    @Override
    public List<ShortURLRecord> getAllShortURLs()
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM ");
        sql.append(CoreSchema.getInstance().getTableInfoShortURL(), "s");
        sql.append(" ORDER BY LOWER(ShortURL)");
        return new SqlSelector(CoreSchema.getInstance().getSchema(), sql).getArrayList(ShortURLRecord.class);
    }

    public void deleteShortURL(@NotNull ShortURLRecord record, @NotNull User user) throws ValidationException
    {
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(record);
        if (!policy.hasPermission(user, DeletePermission.class))
        {
            throw new UnauthorizedException("You are not authorized to delete the short URL '" + record.getShortURL() + "'");
        }

        List<ShortURLListener> listeners = getListeners();
        List<String> allErrors = new ArrayList<>();
        for(ShortURLListener listener: listeners)
        {
            allErrors.addAll(listener.canDelete(record));
        }

        if (!allErrors.isEmpty())
        {
            ValidationException exception = new ValidationException();
            for (String error : allErrors)
            {
                exception.addError(new SimpleValidationError(error));
            }
            throw exception;
        }

        try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            SecurityPolicyManager.deletePolicy(record);
            Table.delete(CoreSchema.getInstance().getTableInfoShortURL(), record.getRowId());
            transaction.commit();
        }
    }

    @NotNull
    @Override
    public ShortURLRecord saveShortURL(@NotNull String shortURL, @NotNull URLHelper fullURL, @NotNull User user) throws ValidationException
    {
        if (user.isGuest())
        {
            throw new UnauthorizedException("Guests may not save short URLs");
        }

        shortURL = validateShortURL(shortURL);

        ShortURLRecord existingRecord = resolveShortURL(shortURL);
        if (existingRecord == null)
        {
            ShortURLRecord newRecord = new ShortURLRecord();
            newRecord.setShortURL(shortURL);
            newRecord.setFullURL(fullURL.toString());
            newRecord.setEntityId(new GUID());
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                newRecord = Table.insert(user, CoreSchema.getInstance().getTableInfoShortURL(), newRecord);
                MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(newRecord));
                // By default, the user who created it can manage it
                policy.addRoleAssignment(user, EditorRole.class);
                SecurityPolicyManager.savePolicy(policy);
                transaction.commit();
                return newRecord;
            }
        }
        else
        {
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(existingRecord);
            if (!policy.hasPermission(user, UpdatePermission.class))
            {
                throw new UnauthorizedException("You are not authorized to edit the short URL '" + shortURL + "'");
            }
            existingRecord.setFullURL(fullURL.toString());

            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                ShortURLRecord result = Table.update(user, CoreSchema.getInstance().getTableInfoShortURL(), existingRecord, existingRecord.getRowId());
                transaction.commit();
                return result;
            }
        }
    }

    public String validateShortURL(String shortURL) throws ValidationException
    {
        if (shortURL == null || shortURL.trim().isEmpty())
        {
            throw new ValidationException("URLs must not be empty");
        }
        shortURL = shortURL.trim();
        if (shortURL.contains("/") || shortURL.contains("\\") || shortURL.contains("."))
        {
            throw new ValidationException("URLs must not contain '\\', '/', or '.'");
        }
        return shortURL;
    }

    @Override
    public ShortURLRecord getForEntityId(@NotNull String entityId)
    {
        return new TableSelector(CoreSchema.getInstance().getTableInfoShortURL(),
                                 new SimpleFilter(FieldKey.fromParts("EntityId"), entityId), null).getObject(ShortURLRecord.class);
    }

    public void addListener(ShortURLListener listener)
    {
        _listeners.add(listener);
    }


    public void removeListener(ShortURLListener listener)
    {
        _listeners.remove(listener);
    }


    private List<ShortURLListener> getListeners()
    {
        List<ShortURLListener> toReturn = new ArrayList<>(_listeners.size());
        toReturn.addAll(_listeners);

        return toReturn;
    }
}
