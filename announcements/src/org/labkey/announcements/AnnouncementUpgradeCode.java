/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.announcements;

import org.apache.log4j.Logger;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.UserManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 1/17/14
 */
public class AnnouncementUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(AnnouncementUpgradeCode.class);
    private static final CommSchema comm = CommSchema.getInstance();

    // Invoked by comm-13.30-13.31.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void convertEmailListToUserList(ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            DbScope scope = comm.getSchema().getScope();
            Map<Integer, Set<String>> emailLists = getEmailListsForMessages(scope);
            if (!emailLists.isEmpty())
            {
                Map<Integer, List<Integer>> userListRecords = parseIntoUserListEntries(emailLists);
                if (!userListRecords.isEmpty())
                {
                    insertUserListRecords(scope, userListRecords);
                }
            }
        }
    }

    private Map<Integer, Set<String>> getEmailListsForMessages(DbScope scope)
    {
        Map<Integer, Set<String>> emailLists = new HashMap<>();
        SQLFragment sql = new SQLFragment("SELECT a.rowId, p.rowId AS parentId, a.emailList " +
                "FROM comm.announcements a LEFT JOIN comm.announcements p ON a.parent = p.entityId " +
                "WHERE a.emailList IS NOT NULL");

        try (ResultSet rs = new SqlSelector(scope, sql).getResultSet())
        {
            int targetId;
            Set<String> existingEmails;

            while (rs.next())
            {
                targetId = rs.getInt("parentId") == 0 ? rs.getInt("rowId") : rs.getInt("parentId");
                Set<String> emails = new HashSet<>(Arrays.asList(rs.getString("emailList").split("\n")));

                // At times its been possible to add members to the emailList field to a response in a thread.
                // These should be merged up into the list for the parent message
                existingEmails = emailLists.get(targetId);
                if (existingEmails != null)
                    emails.addAll(existingEmails);
                emailLists.put(targetId, emails);
            }
        }
        catch (SQLException e)
        {
           _log.error("Error retrieving working list of messages from comm.announcments", e);
            throw new RuntimeSQLException(e);
        }

        return emailLists;
    }

    private Map<Integer, List<Integer>> parseIntoUserListEntries(Map<Integer, Set<String>> emailLists)
    {
        List<String> parsedMembers;
        Map<Integer, List<Integer>> userListRecords = new HashMap<>();

        for (int messageId : emailLists.keySet())
        {
            List<Integer> userIds = new ArrayList<>();
            parsedMembers = UserManager.parseUserListInput(emailLists.get(messageId));
            for (String userEntry : parsedMembers)
            {
                try
                {
                    userIds.add(Integer.parseInt(userEntry));
                }
                catch (NumberFormatException nfe)
                {
                    // TODO: Log somewhere, or drop on the floor?
                }
            }
            if (!userIds.isEmpty())
                userListRecords.put(messageId, userIds);

        }
        return userListRecords;
    }

    private void insertUserListRecords(DbScope scope, Map<Integer, List<Integer>> userListRecords)
    {
        // Insert records into UserList table, if they don't already exist.
        // Don't delete pre-existing records which aren't included in the migration from the emailList field.
        // Wrap the entire operation in a single transaction so we won't leave db in a partially converted state on error.
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            for (int messageId : userListRecords.keySet())
            {
                for (int userId : userListRecords.get(messageId))
                {
                    SQLFragment sql = new SQLFragment("INSERT INTO ").append(comm.getTableInfoUserList(), "");
                    sql.append(" (messageId, userId)");
                    sql.append(" SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM ").append(comm.getTableInfoUserList(), "");
                    sql.append(" WHERE messageId = ? AND userId = ?)");
                    sql.addAll(Arrays.asList(messageId, userId, messageId, userId));
                    new SqlExecutor(scope).execute(sql);
                }
            }
            transaction.commit();
        }
    }

}
