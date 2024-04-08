package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.StringUtilsLabKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.labkey.api.util.DOM.cl;

abstract class RuleBasedPasswordValidator implements PasswordValidator
{
    private final Pattern _lengthPattern;
    private final Pattern[] _patternsToCheck;
    private @Nullable String _patternRequirement = null;
    private final HtmlString _fullRuleHtml;
    private final HtmlString _summaryRuleHtml;

    private static final Pattern LOWER_CASE = Pattern.compile("\\p{javaUpperCase}");
    private static final Pattern UPPER_CASE = Pattern.compile("\\p{javaLowerCase}");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    // Note: This is not completely consistent with the above patterns since \W is simply "not ASCII word character",
    // which means any non-ASCII upper-case or lower-case letter will also qualify as a "symbol".
    private static final Pattern NON_WORD = Pattern.compile("\\W");

    protected RuleBasedPasswordValidator()
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

        _fullRuleHtml = DOM.createHtml(
            DOM.UL(
                DOM.LI("Must be " + getMinimumLengthText() + " non-whitespace characters or more."),
                _patternRequirement != null ? DOM.LI("Must " + _patternRequirement) : null,
                DOM.LI(getPersonalInfoRule()),
                isPreviousPasswordForbidden() ? PREVIOUS_PASSWORD_BULLET : null,
                isDeprecated() ? DOM.LI(cl("labkey-error"), "This password strength is not appropriate for production deployments and will be removed in the next major release.") : null
            )
        );
    }

    private void addPattern(List<Pattern> patterns, Pattern pattern, List<String> patternDescriptions, String patternDescription)
    {
        patterns.add(pattern);
        patternDescriptions.add(patternDescription);
    }

    protected String getMinimumLengthText()
    {
        return StringUtilsLabKey.spellOut(getMinimumLength());
    }

    protected String getPersonalInfoRule()
    {
        return "Must not contain a sequence of three or more characters from the user's email address, display name, first name, or last name.";
    }

    @Override
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

        for (int i = 0; i < lcInfo.length() - 2; i++)
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

    protected boolean containsPersonalInfo(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
    {
        return
            containsPersonalInfo(password, user.getEmail(), "email address", messages) ||
            containsPersonalInfo(password, user.getFriendlyName(), "display name", messages) ||
            containsPersonalInfo(password, StringUtils.trimToEmpty(user.getFirstName()) + StringUtils.trimToEmpty(user.getLastName()), "name", messages);
    }

    protected abstract int getMinimumLength();

    protected abstract boolean isLowerCaseEnabled();

    protected abstract boolean isUpperCaseEnabled();

    protected abstract boolean isDigitEnabled();

    protected abstract boolean isSymbolEnabled();

    protected abstract int getRequiredCharacterTypeCount();

    @Override
    @NotNull
    public HtmlString getFullRuleHtml()
    {
        return _fullRuleHtml;
    }

    @Override
    @NotNull
    public HtmlString getSummaryRuleHtml()
    {
        return _summaryRuleHtml;
    }

    @Override
    public boolean shouldShowPasswordGuidance()
    {
        return false;
    }
}
