/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;

import javax.mail.Address;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.Map;

/**
 * Represents an email address that is known to be valid according to RFC 822. Will automatically append
 * the default email domain from {@link AppProps} and do other normalization if needed.
 *
 * User: adam
 * Date: Aug 24, 2006
 */
public class ValidEmail
{
    private final InternetAddress _internetAddress;

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

                // Can only happen when default domain is empty?
                if (hasNameAndDomain())
                    return;

                throw new InvalidEmailException(rawEmail, "email addresses must be complete, for example: employee@domain.com");
            }
            catch (AddressException e)
            {
                throw new InvalidEmailException(rawEmail, "email addresses must not contain illegal characters", e);
            }
        }

        throw new InvalidEmailException(rawEmail, "email addresses must not be blank");
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
        // Trim extra spaces
        String trimmed = StringUtils.trimToNull(rawEmail);

        if (null == trimmed)
            return null;

        // If no domain, add the default domain
        if (!trimmed.contains("@"))
        {
            String domain = getDefaultDomain();

            if (null != domain && domain.length() > 0)
                return trimmed + "@" + domain;
        }

        return trimmed;
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


    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ValidEmail that = (ValidEmail) o;

        return getEmailAddress().equals(that.getEmailAddress());
    }

    @Override
    public int hashCode()
    {
        return getEmailAddress().hashCode();
    }

    public static class InvalidEmailException extends Exception
    {
        private final String _badEmail;

        public InvalidEmailException(String badEmail, String issue)
        {
            super(message(badEmail, issue));
            _badEmail = badEmail;
        }

        public InvalidEmailException(String badEmail, String issue, Throwable cause)
        {
            super(message(badEmail, issue), cause);
            _badEmail = badEmail;
        }

        private static String message(String badEmail, String issue)
        {
           return "'" + badEmail + "' is not a valid email address; " + issue + ".";
        }

        public String getBadEmail()
        {
            return _badEmail;
        }

        @Override
        public String getMessage()
        {
            Throwable t = getCause();

            return super.getMessage() + (null != t ? " Details: " + t.getMessage() : "");
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testValidEmail() throws InvalidEmailException
        {
            String[] validEmails = new String[]{
                    "xxx@test.com",
                    "   xxx@test.com    ",
                    "xxx@test.test.com",

                    // Following taken from http://en.wikipedia.org/wiki/Email_address. Note that JavaMail InternetAddress
                    // doesn't seem to support some of the more unusual valid examples (double-quoting with symbols)
                    "niceandsimple@example.com",
                    "very.common@example.com",
                    "a.little.lengthy.but.fine@dept.example.com",
                    "disposable.style.email.with+symbol@example.com",
//                    "user@[IPv6:2001:db8:1ff::a0b:dbd0]",
                    "\"much.more unusual\"@example.com",
//                    "\"very.unusual.@.unusual.com\"@example.com",
//                    "\"very.(),:;<>[]\\\".VERY.\\\"very@\\\\ \\\"very\\\".unusual\"@strange.example.com",
                    "0@a",
                    "postbox@com (top-level domains are valid hostnames)",
                    "!#$%&'*+-/=?^_`{}|~@example.org",
//                    "\"()<>[]:,;@\\\\\\\"!#$%&'*+-/=?^_`{}| ~  ? ^_`{}|~.a\"@example.org",
                    "\"\"@example.org"};

            for (String valid : validEmails)
                verifyValid(valid);

            Map<String, String> invalidEmails = PageFlowUtil.map(
                    null, "must not be blank",
                    "", "must not be blank",
                    "    ", "must not be blank",
                    " \t  \n   \t  \n ", "must not be blank",
                    "xxx @test.com", "must not contain illegal characters",
                    "xxx@test .com", "must not contain illegal characters",
                    "xxx@test@test.com", "must not contain illegal characters",
                    "xxx@test$.com", "must not contain illegal characters",
                    "x$xx@test$.com", "must not contain illegal characters",
                    "xxx@test\".com", "must not contain illegal characters",
                    "\"xxx@test.com", "must not contain illegal characters",
                    "xxx@test.com\u200E", "must not contain illegal characters"   // Left-to-right mark, see #12276
            );

            for (Map.Entry<String, String> invalid : invalidEmails.entrySet())
                verifyInvalid(invalid.getKey(), invalid.getValue());
        }

        private void verifyValid(String valid) throws InvalidEmailException
        {
            new ValidEmail(valid);
        }

        private void verifyInvalid(String invalid, String expectedMessage)
        {
            try
            {
                new ValidEmail(invalid);
                fail("Expected InvalidEmailException for '" + invalid + "'");
            }
            catch (InvalidEmailException e)
            {
                assertTrue("Incorrect error message for invalid email '" + invalid + "'", e.getMessage().contains(expectedMessage));
            }
        }
    }
}
