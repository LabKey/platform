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


    // Normalize an email address entered in the UI -- trim whitespace, add default domain, and lower case
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

        // Convert to all lowercase
        return sb.toString().toLowerCase();
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
