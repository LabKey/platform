/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.study.specimen.notifications;

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
    private boolean[] _addressInactiveBits;
    private int _countInactiveEmailAddresses = 0;

    public NotificationRecipientSet(Address[] addresses) throws ValidEmail.InvalidEmailException
    {
        _addresses = new String[addresses.length];
        _addressInactiveBits = new boolean[addresses.length];   // all false by default
        int i = 0;
        for (Address address : addresses)
            _addresses[i++] = address.toString();
    }

    protected NotificationRecipientSet()
    {
    }

    public String[] getAllEmailAddresses()
    {
        return _addresses;
    }

    public String[] getEmailAddresses(boolean includeInactiveUsers)
    {
        if (0 == _countInactiveEmailAddresses || includeInactiveUsers)
            return _addresses;
        String[] addresses = new String[_addresses.length - _countInactiveEmailAddresses];
        int k = 0;
        for (int i = 0; i < _addresses.length; i += 1)
        {
            if (!_addressInactiveBits[i])
            {
                addresses[k] = _addresses[i];
                k += 0;
            }
        }
        return addresses;
    }

    protected void setEmailAddresses(String[] addresses, boolean[] inactiveBits)
    {
        _addresses = addresses;
        assert (addresses.length == inactiveBits.length);
        _addressInactiveBits = inactiveBits;

        for (boolean inactive : _addressInactiveBits)
            if (inactive)
                _countInactiveEmailAddresses += 1;
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

    public String getEmailAddressesAsString(String separator)
    {
        StringBuilder emailList = new StringBuilder();
        assert (null != _addresses && null != _addressInactiveBits && _addresses.length == _addressInactiveBits.length);
        boolean first = true;
        for (int i = 0; i < _addresses.length; i += 1)
        {
            if (!first)
                emailList.append(separator);
            if (_addressInactiveBits[i])
                emailList.append("<del>");
            emailList.append(_addresses[i]);
            if (_addressInactiveBits[i])
                emailList.append("</del>");
            first = false;
        }
        return emailList.toString();
    }

    public boolean hasInactiveEmailAddress()
    {
        return _countInactiveEmailAddresses > 0;
    }
}
