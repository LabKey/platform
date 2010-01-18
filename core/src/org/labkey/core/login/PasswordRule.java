package org.labkey.core.login;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Jan 13, 2010
 * Time: 12:39:21 PM
 */
public enum PasswordRule
{
    Weak
    {
        private Pattern passwordPattern = Pattern.compile("^\\S{6,}$");  // At least six, non-whitespace characters

        @NotNull
        @Override
        public String getRuleHTML()
        {
            return "Passwords must be six characters or more and must not match your email address.";
        }

        @Override
        boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
        {
            return isValidToStore(password, user, messages);
        }

        @Override
        boolean isValidToStore(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
        {
            if (!passwordPattern.matcher(password).matches())
            {
                if (null != messages)
                    messages.add("Your password must be six characters or more.");

                return false;
            }

            String email = user.getEmail();

            if (email.equalsIgnoreCase(password))
            {
                if (null != messages)
                    messages.add("Your password must not match your email address.");

                return false;
            }

            return true;
        }
    },

    Strong
    {
        private Pattern passwordPattern = Pattern.compile("^\\S{8,}$");  // At least eight, non-whitespace characters
        private Pattern lowerCase = Pattern.compile("[A-Z]");
        private Pattern upperCase = Pattern.compile("[a-z]");
        private Pattern digit = Pattern.compile("\\d");
        private Pattern nonWord = Pattern.compile("[^A-Za-z\\d]");

        @NotNull
        @Override
        public String getRuleHTML()
        {
            return "Passwords follow these rules:<ul>\n" +
                    "<li>Must be eight characters or more.</li>\n" +
                    "<li>Must contain three of the following: lowercase letter (a-z), uppercase letter (A-Z), digit (0-9), or symbol (e.g., ! # $ % & / < = > ? @).</li>\n" +
                    "<li>Must not contain a sequence of three or more characters from your email address, display name, first name, or last name.</li>\n" +
                    "<li>Must not match any of your 10 previously used passwords.</li>\n</ul>\n";
        }

        @Override
        boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
        {
            if (!passwordPattern.matcher(password).matches())
            {
                if (null != messages)
                    messages.add("Your password must be eight characters or more.");

                return false;
            }

            if (containsPersonalInfo(password, user.getEmail(), "email address", messages) ||
                containsPersonalInfo(password, user.getFriendlyName(), "display name", messages) ||
                containsPersonalInfo(password, user.getFirstName() + user.getLastName(), "name", messages))
            {
                return false;
            }

            if (countMatches(password, lowerCase, upperCase, digit, nonWord) < 3)
            {
                if (null != messages)
                    messages.add("Your password must contain three of the following: lowercase letter (a-z), uppercase letter (A-Z), digit (0-9), or symbol (e.g., ! # $ % & / < = > ? @).");

                return false;
            }

            return true;
        }

        @Override
        boolean isValidToStore(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
        {
            if (!isValidForLogin(password, user, messages))
                return false;

            if (SecurityManager.matchesPreviousPassword(password, user))
            {
                if (null != messages)
                    messages.add("Your password must not match a recently used password.");

                return false;
            }

            return true;
        }

        private boolean containsPersonalInfo(String password, String personalInfo, String infoType, @Nullable Collection<String> messages)
        {
            if (StringUtils.isBlank(personalInfo) || personalInfo.length() < 3)
                return false;

            String lcPassword = password.toLowerCase();
            String lcInfo = personalInfo.toLowerCase();

            for (int i = 0; i < lcInfo.length() - 3; i++)
            {
                if (lcPassword.contains(lcInfo.substring(i, i + 3)))
                {
                    if (null != messages)
                        messages.add("Your password must not contain a sequence of three or more characters from your " + infoType + ".");

                    return true;
                }
            }

            return false;
        }

        private int countMatches(String password, Pattern... patterns)
        {
            int count = 0;

            for (Pattern pattern : patterns)
            {
                Matcher m = pattern.matcher(password);

                if (m.find())
                    count++;
            }

            return count;
        }
    };

    public boolean isValidToStore(String password1, String password2, User user, Collection<String> messages)
    {
        if (StringUtils.isBlank(password1) || StringUtils.isBlank(password2))
        {
            messages.add("You must enter two passwords.");
            return false;
        }

        if (!password1.equals(password2))
        {
            messages.add("Your password entries didn't match.");
            return false;
        }

        return isValidToStore(password1, user, messages);
    }

    // We check the password rule at each login and when storing in the database.  The rule can
    abstract boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages);

    abstract boolean isValidToStore(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages);

    public abstract @NotNull String getRuleHTML();
}
