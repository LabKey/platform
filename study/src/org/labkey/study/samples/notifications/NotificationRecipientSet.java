/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.samples.notifications;

import org.labkey.api.security.ValidEmail;

import javax.mail.Address;

/**
 * User: brittp
* Date: May 4, 2007
* Time: 3:35:26 PM
*/
public class NotificationRecipientSet
{
    private String[] _addresses;

    public NotificationRecipientSet(Address[] addresses) throws ValidEmail.InvalidEmailException
    {
        _addresses = new String[addresses.length];
        int i = 0;
        for (Address address : addresses)
            _addresses[i++] = address.toString();
    }

    protected NotificationRecipientSet(String[] addresses)
    {
        _addresses = new String[addresses.length];
        System.arraycopy(addresses, 0, _addresses, 0, addresses.length);
    }

    public String[] getEmailAddresses()
    {
        return _addresses;
    }

    public String getShortRecipientDescription()
    {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String email : _addresses)
        {
            if (!first)
                builder.append(", ");
            builder.append(email);
            first = false;
        }
        return builder.toString();
    }

    public String getLongRecipientDescription()
    {
        return getShortRecipientDescription();
    }

    public String getEmailAddresses(String separator)
    {
        StringBuilder emailList = new StringBuilder();
        boolean first = true;
        for (String email : getEmailAddresses())
        {
            if (!first)
                emailList.append(separator);
            emailList.append(email);
            first = false;
        }
        return emailList.toString();
    }
}
