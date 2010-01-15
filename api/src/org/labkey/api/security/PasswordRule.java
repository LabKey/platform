package org.labkey.api.security;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

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
            return "Passwords must be six characters or more and can't match your email address.";
        }

        @Override
        boolean isValid(@NotNull String password, @NotNull User user, @NotNull Collection<String> messages)
        {
            if (!passwordPattern.matcher(password).matches())
            {
                messages.add("Your password must be six characters or more.");
                return false;
            }

            String email = user.getEmail();

            if (email.equalsIgnoreCase(password))
            {
                messages.add("Your password can't match your email address.");
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
            return "Passwords must be eight characters or more, and contain all kinds of crazy characters.";
        }

        @Override
        boolean isValid(@NotNull String password, @NotNull User user, @NotNull Collection<String> messages)
        {
            if (!passwordPattern.matcher(password).matches())
            {
                messages.add("Your password must be eight characters or more.");
                return false;
            }

            if (containsPersonalInfo(password, user.getEmail(), "email address", messages) ||
                containsPersonalInfo(password, user.getEmail(), "display name", messages) ||
                containsPersonalInfo(password, user.getFirstName() + user.getLastName(), "name", messages))
            {
                return false;
            }

            if (countMatches(password, lowerCase, upperCase, digit, nonWord) < 3)
            {
                messages.add("Your password must contain some crazy symbols.");
                return false;
            }

            // TODO: Check last 10 passwords
            // TODO: Better descriptions

            return true;
        }

        private boolean containsPersonalInfo(String password, String personalInfo, String infoType, @NotNull Collection<String> messages)
        {
            if (StringUtils.isBlank(personalInfo) || personalInfo.length() < 3)
                return false;

            String lcPassword = password.toLowerCase();
            String lcInfo = personalInfo.toLowerCase();

            for (int i = 0; i < lcInfo.length() - 3; i++)
            {
                if (lcPassword.contains(lcInfo.substring(i, i + 3)))
                {
                    messages.add("Your password contained a sequence of three or more characters from your " + infoType + ".");
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

    public boolean isValid(String password1, String password2, User user, Collection<String> messages)
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

        return isValid(password1, user, messages);
    }

    abstract boolean isValid(@NotNull String password, @NotNull User user, @NotNull Collection<String> messages);

    public abstract @NotNull String getRuleHTML();
}
