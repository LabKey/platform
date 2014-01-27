package org.labkey.core.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
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

import java.sql.SQLException;

/**
 * User: jeckels
 * Date: 1/23/14
 */
public class ShortURLServiceImpl implements ShortURLService
{
    @Override @Nullable
    public ShortURLRecord resolveShortURL(@NotNull String shortURL)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM ");
        sql.append(CoreSchema.getInstance().getTableInfoShortURL(), "s");
        sql.append(" WHERE LOWER(ShortURL) = LOWER(?)");
        sql.add(shortURL);
        return new SqlSelector(CoreSchema.getInstance().getSchema(), sql).getObject(ShortURLRecord.class);
    }

    public void deleteShortURL(@NotNull ShortURLRecord record, @NotNull User user)
    {
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(record);
        if (!policy.hasPermission(user, DeletePermission.class))
        {
            throw new UnauthorizedException("You are not authorized to delete the short URL '" + record.getShortURL() + "'");
        }
        try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            SecurityPolicyManager.deletePolicy(record);
            Table.delete(CoreSchema.getInstance().getTableInfoShortURL(), record.getRowId());
            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
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
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
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
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    }

    private String validateShortURL(String shortURL) throws ValidationException
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
}
