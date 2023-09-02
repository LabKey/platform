package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;

public class StrongPasswordValidator extends EntropyPasswordValidator
{
    private final HtmlString _fullRuleHtml;
    private final HtmlString _summaryRuleHtml;

    public StrongPasswordValidator()
    {
        _fullRuleHtml = DOM.createHtml(DOM.createHtmlFragment(
            DOM.UL(
                DOM.LI("Evaluate passwords using a modern scoring approach based on entropy, a measure of the password's " +
                    "inherent randomness. Long passwords that use multiple character types (upper case, lower case, digits, " +
                    "symbols) result in higher scores. Repeated characters, repeated sequences, trivial sequences, and personal " +
                    "information result in lower scores since they are easier to guess."),
//                    "A visual gauge shows the strength of the " +
//                    "characters entered so far, encouraging users to choose strong passwords."),
                DOM.LI("This option requires " + getRequiredBitsOfEntropy() + " bits of entropy, which typically requires " +
                    getCharacterCountEstimate() + " or more characters without any easy-to-guess sequences."),
                PREVIOUS_PASSWORD_BULLET
            )
        ));

        _summaryRuleHtml = HtmlString.of("Your password should be long and use multiple character types");
    }

    @Override
    protected int getRequiredBitsOfEntropy()
    {
        return 60;
    }

    @Override
    public @NotNull HtmlString getFullRuleHtml()
    {
        return _fullRuleHtml;
    }

    @Override
    public @NotNull HtmlString getSummaryRuleHtml()
    {
        return _summaryRuleHtml;
    }
}
