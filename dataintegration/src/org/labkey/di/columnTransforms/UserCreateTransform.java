/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.di.columnTransforms;

import net.sourceforge.jtds.jdbc.ClobImpl;
import org.labkey.api.di.columnTransform.ColumnTransform;
import org.labkey.api.di.columnTransform.ColumnTransformException;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * This transform is called in an ETL to handle createdBy and modifiedBy columns.
 * Compares incoming values with existing user email prefixes and if they exist pass on their
 * user id to populate the column.  If they don't exist, create a new deactivated user and pass
 * along the new user id to populate the column.
 */
public class UserCreateTransform extends ColumnTransform
{

    // To minimize queries, keep a temporary storage of users already looked up
    private transient Map<String, Integer> _userMap = new HashMap<>();

    private ValidEmail createEmail(String user) throws ValidEmail.InvalidEmailException
    {
        return new ValidEmail(user + "@" + getConstant("transformEmailDomain"));
    }

    /**
     * Search for the user by the email prefix (ie. jdoe@labkey.com matches jdoe).
     * Return null if user not found.
     */
    private Integer getUserByEmailPrefix(String user)
    {
        int index;
        Map<ValidEmail, User> emails = UserManager.getUserEmailMap();
        for (Map.Entry<ValidEmail, User> email : emails.entrySet())
        {
            index = email.getKey().getEmailAddress().indexOf("@");
            if(email.getKey().getEmailAddress().substring(0,index).equalsIgnoreCase(user))
            {
                return email.getValue().getUserId();
            }
        }

        return null;
    }

    @Override
    public void reset()
    {
        _userMap = new HashMap<>();
    }

    /**
     * Performs user lookup or creation of new user. Returns the user id to populate the
     * column with.
     */
    @Override
    protected Object doTransform(Object inputValue)
    {
        if(null == inputValue)
            return null;

        String input;
        if( inputValue instanceof ClobImpl){
            try
            {
                input = ((ClobImpl) inputValue).getSubString(1, (int) ((ClobImpl) inputValue).length());
            }
            catch (SQLException e)
            {
                return null;
            }
        }
        else{
            input = inputValue.toString().toLowerCase();
        }
        // comparing with and making this an email name so case insensitive

        // See if user has already been found anywhere
        Integer userId = _userMap.get(input);

        // Attempt to look up user in user table
        if(null == userId)
        {
            userId = getUserByEmailPrefix(input);
        }

        // First time encountering user, create deactivated account
        if (null == userId)
        {
            try
            {
                ValidEmail email = createEmail(input);
                User user = SecurityManager.addUser(email, getContainerUser().getUser(), false).getUser();
                UserManager.setUserActive(getContainerUser().getUser(), user, false);
                userId = user.getUserId();
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                throw new ColumnTransformException("Unable to create email address for user: " + input, e);
            }
            catch (SecurityManager.UserManagementException e)
            {
                throw new ColumnTransformException("Unable to add user:" + input, e);
            }
        }

        _userMap.put(input, userId);

        return userId;
    }
}
