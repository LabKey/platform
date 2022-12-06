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
package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
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
        private final Pattern passwordPattern = Pattern.compile("^\\S{6,}$");  // At least six, non-whitespace characters

        @NotNull
        @Override
        public HtmlString getFullRuleHTML()
        {
            return HtmlString.of("Passwords must be six non-whitespace characters or more and must not match your email address.");
        }

        @NotNull
        @Override
        public HtmlString getSummaryRuleHTML()
        {
            return HtmlString.of("Your password must be at least six characters and cannot contain spaces or match your email address.");
        }
        
        @Override
        public boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
        {
            return isValidToStore(password, user, messages);
        }

        @Override
        boolean isValidToStore(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
        {
            if (!passwordPattern.matcher(password).matches())
            {
                if (null != messages)
                    messages.add("Your password must be at least six characters and cannot contain spaces.");

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
        private final Pattern passwordPattern = Pattern.compile("^\\S{8,}$");  // At least eight, non-whitespace characters
        private final Pattern lowerCase = Pattern.compile("[A-Z]");
        private final Pattern upperCase = Pattern.compile("[a-z]");
        private final Pattern digit = Pattern.compile("\\d");
        private final Pattern nonWord = Pattern.compile("[^A-Za-z\\d]");

        @NotNull
        @Override
        public HtmlString getFullRuleHTML()
        {
            return DOM.createHtml(DOM.createHtmlFragment(
                    "Passwords follow these rules:",
                    DOM.UL(
                            DOM.LI("Must be eight characters or more."),
                            DOM.LI("Must contain three of the following: lowercase letter (a-z), uppercase letter (A-Z), digit (0-9), or symbol (e.g., ! # $ % & / < = > ? @)."),
                            DOM.LI("Must not contain a sequence of three or more characters from your email address, display name, first name, or last name."),
                            DOM.LI("Must not match any of your 10 previously used passwords.")
                    )));
        }

        @NotNull
        @Override
        public HtmlString getSummaryRuleHTML()
        {
            return HtmlString.of("Your password must be at least eight characters, include a mix of letters, digits, and symbols, and cannot include your email address.");
        }

        @Override
        public boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
        {
            if (!passwordPattern.matcher(password).matches())
            {
                if (null != messages)
                    messages.add("Your password must be eight characters or more.");

                return false;
            }

            if (containsPersonalInfo(password, user.getEmail(), "email address", messages) ||
                containsPersonalInfo(password, user.getFriendlyName(), "display name", messages) ||
                containsPersonalInfo(password, StringUtils.trimToEmpty(user.getFirstName()) + StringUtils.trimToEmpty(user.getLastName()), "name", messages))
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

    // We check the password rule at each login and when storing in the database. These rules may differ.

    public abstract boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages);

    abstract boolean isValidToStore(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages);

    public abstract @NotNull HtmlString getFullRuleHTML();

    public abstract @NotNull HtmlString getSummaryRuleHTML();

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

        LogManager.getLogger(PasswordRule.class).info("Total number: " + passwords.length + " Allowed by Weak: " + weak + " Allowed by Strong: " + strong);
    }
}
