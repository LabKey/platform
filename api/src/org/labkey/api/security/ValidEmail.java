/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.security;

import org.labkey.api.util.AppProps;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.AddressException;
import javax.mail.Address;

/**
 * User: adam
 * Date: Aug 24, 2006
 * Time: 3:38:37 PM
 */
public class ValidEmail
{
    private InternetAddress _internetAddress;

    public ValidEmail(String rawEmail) throws InvalidEmailException
    {
        String normalizedEmail = normalize(rawEmail);

        if (null != normalizedEmail)
        {
            try
            {
                // Checks for valid email address.  Must follow RFC 822 and be in form name@domain
                _internetAddress = new InternetAddress(normalizedEmail);

                // Email addresses are always lowercase.  Convert here (vs. normalize) so we don't touch the personal name.
                _internetAddress.setAddress(_internetAddress.getAddress().toLowerCase());

                if (hasNameAndDomain())
                    return;
            }
            catch (AddressException e)
            {
                //
            }
        }

        throw new InvalidEmailException(rawEmail);
    }


    public String getEmailAddress()
    {
        return _internetAddress.getAddress();
    }


    public String getPersonal()
    {
        return _internetAddress.getPersonal();
    }


    public String toString()
    {
        return getEmailAddress();
    }


    public Address getAddress()
    {
        return _internetAddress;
    }


    // Normalize an email address entered in the UI -- trim whitespace and add default domain
    private String normalize(String rawEmail)
    {
        if (null == rawEmail)
            return null;

        // Trim extra spaces
        StringBuilder sb = new StringBuilder(rawEmail.trim());

        // If no domain, add the default domain
        if (sb.indexOf("@") == -1)
        {
            String domain = getDefaultDomain();

            if (null != domain && domain.length() > 0)
                sb.append("@").append(domain);
        }

        return sb.toString();
    }


    private boolean hasNameAndDomain()
    {
        String[] tokens = getEmailAddress().split("@");

        return tokens.length == 2 && tokens[0].length() > 0 && tokens[1].length() > 0;
    }


    public static String getDefaultDomain()
    {
        return AppProps.getInstance().getDefaultDomain();
    }


    public static class InvalidEmailException extends Exception
    {
        private final String _badEmail;

        public InvalidEmailException(String badEmail)
        {
            _badEmail = badEmail;
        }

        public String getBadEmail()
        {
            return _badEmail;
        }
    }
}
