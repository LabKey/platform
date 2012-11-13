/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
package org.labkey.core.login;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;

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
        public String getFullRuleHTML()
        {
            return "Passwords must be six non-whitespace characters or more and must not match your email address.";
        }

        @NotNull
        @Override
        public String getSummaryRuleHTML()
        {
            return "six non-whitespace characters or more, cannot match email address";
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
                    messages.add("Your password must be six non-whitespace characters or more.");

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
        public String getFullRuleHTML()
        {
            return "Passwords follow these rules:<ul>\n" +
                    "<li>Must be eight characters or more.</li>\n" +
                    "<li>Must contain three of the following: lowercase letter (a-z), uppercase letter (A-Z), digit (0-9), or symbol (e.g., ! # $ % & / < = > ? @).</li>\n" +
                    "<li>Must not contain a sequence of three or more characters from your email address, display name, first name, or last name.</li>\n" +
                    "<li>Must not match any of your 10 previously used passwords.</li>\n</ul>\n";
        }

        @NotNull
        @Override
        public String getSummaryRuleHTML()
        {
            return "must be at least eight characters, include a mix of letters, digits, and symbols, and cannot include your email address";
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
            messages.add("You must enter a password.");
            return false;
        }

        if (StringUtils.isBlank(password1) || StringUtils.isBlank(password2))
        {
            messages.add("You must enter your password twice.");
            return false;
        }

        if (!password1.equals(password2))
        {
            messages.add("Your password entries didn't match.");
            return false;
        }

        return isValidToStore(password1, user, messages);
    }

    // We check the password rule at each login and when storing in the database.  The rules may differ.

    abstract boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages);

    abstract boolean isValidToStore(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages);

    public abstract @NotNull String getFullRuleHTML();

    public abstract @NotNull String getSummaryRuleHTML();

    // 100 most-used passwords on RockYou
    private static final String weakPasswords = "123456\n"+
        "12345\n"+
        "123456789\n"+
        "password\n"+
        "iloveyou\n"+
        "princess\n"+
        "1234567\n"+
        "rockyou\n"+
        "12345678\n"+
        "abc123\n"+
        "nicole\n"+
        "daniel\n"+
        "babygirl\n"+
        "monkey\n"+
        "lovely\n"+
        "jessica\n"+
        "654321\n"+
        "michael\n"+
        "ashley\n"+
        "qwerty\n"+
        "111111\n"+
        "iloveu\n"+
        "000000\n"+
        "michelle\n"+
        "tigger\n"+
        "sunshine\n"+
        "chocolate\n"+
        "password1\n"+
        "soccer\n"+
        "anthony\n"+
        "friends\n"+
        "butterfly\n"+
        "purple\n"+
        "angel\n"+
        "jordan\n"+
        "liverpool\n"+
        "justin\n"+
        "loveme\n"+
        "fuckyou\n"+
        "123123\n"+
        "football\n"+
        "secret\n"+
        "andrea\n"+
        "carlos\n"+
        "jennifer\n"+
        "joshua\n"+
        "bubbles\n"+
        "1234567890\n"+
        "superman\n"+
        "hannah\n"+
        "amanda\n"+
        "loveyou\n"+
        "pretty\n"+
        "basketball\n"+
        "andrew\n"+
        "angels\n"+
        "tweety\n"+
        "flower\n"+
        "playboy\n"+
        "hello\n"+
        "elizabeth\n"+
        "hottie\n"+
        "tinkerbell\n"+
        "charlie\n"+
        "samantha\n"+
        "barbie\n"+
        "chelsea\n"+
        "lovers\n"+
        "teamo\n"+
        "jasmine\n"+
        "brandon\n"+
        "666666\n"+
        "shadow\n"+
        "melissa\n"+
        "eminem\n"+
        "matthew\n"+
        "robert\n"+
        "danielle\n"+
        "forever\n"+
        "family\n"+
        "jonathan\n"+
        "987654321\n"+
        "computer\n"+
        "whatever\n"+
        "dragon\n"+
        "vanessa\n"+
        "cookie\n"+
        "naruto\n"+
        "summer\n"+
        "sweety\n"+
        "spongebob\n"+
        "joseph\n"+
        "junior\n"+
        "softball\n"+
        "taylor\n"+
        "yellow\n"+
        "daniela\n"+
        "lauren\n"+
        "mickey\n"+
        "princesa";

    public static void testWeakPasswords()
    {
        int weak = 0;
        int strong = 0;

        User user = HttpView.currentContext().getUser();
        String[] passwords = weakPasswords.split("\n");

        for (String password : passwords)
        {
            if (Weak.isValidForLogin(password, user, null))
                weak++;

            if (Strong.isValidForLogin(password, user, null))
                strong++;
        }

        Logger.getLogger(PasswordRule.class).info("Total number: " + passwords.length + " Allowed by Weak: " + weak + " Allowed by Strong: " + strong);
    }
}
