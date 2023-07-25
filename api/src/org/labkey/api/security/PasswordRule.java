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
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.HttpView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum PasswordRule
{
    Weak
    {
        @Override
        protected int getMinimumLength()
        {
            return 6;
        }

        @Override
        protected boolean isLowerCaseEnabled()
        {
            return false;
        }

        @Override
        protected boolean isUpperCaseEnabled()
        {
            return false;
        }

        @Override
        protected boolean isDigitEnabled()
        {
            return false;
        }

        @Override
        protected boolean isSymbolEnabled()
        {
            return false;
        }

        @Override
        protected int getRequiredCharacterTypeCount()
        {
            return 0;
        }

        @Override
        protected boolean isPreviousPasswordForbidden()
        {
            return false;
        }

        @NotNull
        @Override
        public HtmlString getSummaryRuleHtml()
        {
            return HtmlString.of("Your password must be at least " + getMinimumLengthText() + " characters and cannot contain spaces or match your email address.");
        }

        @Override
        protected boolean containsPersonalInfo(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
        {
            String email = user.getEmail();

            if (email.equalsIgnoreCase(password))
            {
                if (null != messages)
                    messages.add("Your password must not match your email address.");

                return true;
            }

            return false;
        }

        @Override
        protected String getPersonalInfoRule()
        {
            return "Must not match the user's email address.";
        }
    },

    Strong
    {
        @Override
        protected int getMinimumLength()
        {
            return 8;
        }

        @Override
        protected boolean isLowerCaseEnabled()
        {
            return true;
        }

        @Override
        protected boolean isUpperCaseEnabled()
        {
            return true;
        }

        @Override
        protected boolean isDigitEnabled()
        {
            return true;
        }

        @Override
        protected boolean isSymbolEnabled()
        {
            return true;
        }

        @Override
        protected int getRequiredCharacterTypeCount()
        {
            return 3;
        }
    };

    private static final Pattern LOWER_CASE = Pattern.compile("[A-Z]");
    private static final Pattern UPPER_CASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern NON_WORD = Pattern.compile("[^A-Za-z\\d]");

    static
    {
        // It would be natural to init() each constant when constructed, but the statics above aren't initialized by then
        Arrays.stream(values()).forEach(PasswordRule::init);
    }

    private Pattern _lengthPattern;
    private Pattern[] _patternsToCheck;
    private @Nullable String _patternRequirement = null;
    private HtmlString _fullRuleHtml;
    private HtmlString _summaryRuleHtml;

    public void init()
    {
        // Build and stash the length regex & validation pattern
        String lengthRegex = "^\\S{" + getMinimumLength() + ",}$";
        _lengthPattern = Pattern.compile(lengthRegex);

        // Collect and stash the character type validation patterns and instructions based on enabled character types
        ArrayList<Pattern> patterns = new ArrayList<>();
        List<String> descriptions = new LinkedList<>();
        if (isLowerCaseEnabled())
            addPattern(patterns, LOWER_CASE, descriptions, "lowercase letter (a-z)");
        if (isUpperCaseEnabled())
            addPattern(patterns, UPPER_CASE, descriptions, "uppercase letter (A-Z)");
        if (isDigitEnabled())
            addPattern(patterns, DIGIT, descriptions, "digit (0-9)");
        if (isSymbolEnabled())
            addPattern(patterns, NON_WORD, descriptions, "symbol (e.g., ! # $ % & / < = > ? @)");
        _patternsToCheck = patterns.toArray(new Pattern[]{});
        String shortPatternRequirement = null;
        if (getRequiredCharacterTypeCount() > 0 && !patterns.isEmpty())
        {
            _patternRequirement = "contain " + StringUtilsLabKey.spellOut(getRequiredCharacterTypeCount()) + " of the following: " + StringUtils.join(descriptions, ", ") + ".";
            List<String> shortDescriptions = descriptions.stream()
                .map(desc -> desc.substring(0, desc.indexOf('(') - 1) + "s")
                .toList();
            shortPatternRequirement = StringUtilsLabKey.joinWithConjunction(shortDescriptions, "and");
            if (shortDescriptions.size() > 1)
                shortPatternRequirement = "a mix of " + shortPatternRequirement;
        }
        StringBuilder builder = new StringBuilder("Your password must be at least ").append(getMinimumLengthText()).append(" non-whitespace characters");
        if (null != shortPatternRequirement)
            builder.append(", include ").append(shortPatternRequirement).append(",");
        builder.append(" and cannot include portions of your personal information.");
        _summaryRuleHtml = HtmlString.of(builder);

        _fullRuleHtml = DOM.createHtml(DOM.createHtmlFragment(
            DOM.UL(
                DOM.LI("Must be " + getMinimumLengthText() + " non-whitespace characters or more."),
                _patternRequirement != null ? DOM.LI("Must " + _patternRequirement) : null,
                DOM.LI(getPersonalInfoRule()),
                isPreviousPasswordForbidden() ? DOM.LI("Must not match any of the user's 10 previously used passwords.") : null
            )));
    }

    private void addPattern(List<Pattern> patterns, Pattern pattern, List<String> patternDescriptions, String patternDescription)
    {
        patterns.add(pattern);
        patternDescriptions.add(patternDescription);
    }

    public boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
    {
        if (!_lengthPattern.matcher(password).matches())
        {
            if (null != messages)
                messages.add("Your password must be at least " + getMinimumLengthText() + " characters and cannot contain spaces.");

            return false;
        }

        if (containsPersonalInfo(password, user, messages))
            return false;

        if (_patternsToCheck.length > 0 && getRequiredCharacterTypeCount() > 0 && countMatches(password, _patternsToCheck) < getRequiredCharacterTypeCount())
        {
            if (null != messages)
                messages.add("Your password must " + _patternRequirement);

            return false;
        }

        return true;
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

    private static boolean containsPersonalInfo(String password, String personalInfo, String infoType, @Nullable Collection<String> messages)
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isValidToStore(String password1, String password2, User user, @NotNull Collection<String> messages)
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

        if (isPreviousPasswordForbidden() && SecurityManager.matchesPreviousPassword(password1, user))
        {
            messages.add("Your password must not match a recently used password.");
            return false;
        }

        return isValidForLogin(password1, user, messages);
    }

    // We check the password rules at each login and when storing in the database. The storing rules may be a superset
    // of the login rules.

    protected abstract int getMinimumLength();
    protected abstract boolean isLowerCaseEnabled();
    protected abstract boolean isUpperCaseEnabled();
    protected abstract boolean isDigitEnabled();
    protected abstract boolean isSymbolEnabled();
    protected abstract int getRequiredCharacterTypeCount();
    protected boolean isPreviousPasswordForbidden()
    {
        return true;
    }

    protected String getMinimumLengthText()
    {
        return StringUtilsLabKey.spellOut(getMinimumLength());
    }

    protected boolean containsPersonalInfo(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
    {
        return
            containsPersonalInfo(password, user.getEmail(), "email address", messages) &&
            containsPersonalInfo(password, user.getFriendlyName(), "display name", messages) &&
            containsPersonalInfo(password, StringUtils.trimToEmpty(user.getFirstName()) + StringUtils.trimToEmpty(user.getLastName()), "name", messages);
    }

    protected String getPersonalInfoRule()
    {
        return "Must not contain a sequence of three or more characters from the user's email address, display name, first name, or last name.";
    }

    /**
     * Returns HTML that describes the password rules in detail. This is presented to administrators when they
     * configure or review the password rules, so it is written in third person (e.g., "the user's email address").
     * @return HtmlString providing a detailed description of the rules
     */
    @NotNull
    public HtmlString getFullRuleHtml()
    {
        return _fullRuleHtml;
    }

    /**
     * Returns HTML that describes a summary of the password rules. This is presented to individual users when they
     * pick or change a password, so it is written in second person (e.g., "your email address").
     * @return HtmlString providing a summary of the rules
     */
    @NotNull
    public HtmlString getSummaryRuleHtml()
    {
        return _summaryRuleHtml;
    }

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
