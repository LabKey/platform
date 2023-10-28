package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.Link.LinkBuilder;

import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.id;

public class StrongPasswordValidator extends EntropyPasswordValidator
{
    private final HtmlString _fullRuleHtml;

    public StrongPasswordValidator()
    {
        _fullRuleHtml = DOM.createHtml(DOM.createHtmlFragment(
            DOM.UL(
                DOM.LI("Evaluate passwords using a modern scoring approach based on entropy, a measure of the password's " +
                    "inherent randomness. Long passwords that use multiple character types (upper case, lower case, digits, " +
                    "symbols) result in higher scores. Repeated characters, repeated sequences, trivial sequences, and personal " +
                    "information result in lower scores since they are easier to guess. A visual gauge shows the strength of the " +
                    "characters entered so far, encouraging users to choose strong passwords."),
                DOM.LI("This option requires " + getRequiredBitsOfEntropy() + " bits of entropy, which typically requires " +
                    getCharacterCountEstimate() + " or more characters without any easy-to-guess sequences."),
                PREVIOUS_PASSWORD_BULLET
            )
        ));
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

    private final HtmlString _intro = HtmlStringBuilder.of()
        .append("Secure passwords are long and use multiple character types. The password strength gauge will turn green when your new password meets the complexity requirements.")
        .append(HtmlString.BR)
        .getHtmlString();

    private final HtmlString _tips = DOM.createHtml(DOM.createHtmlFragment(
        DIV(id("passwordTips").at(DOM.Attribute.style, "display:none;"),
            DOM.UL(
                DOM.LI("Use " + getCharacterCountEstimate() + " characters or more"),
                DOM.LI("Include upper- and lower-case letters"),
                DOM.LI("Include symbols and numbers"),
                DOM.LI("Avoid repeated characters and sequences, personal information (email, name), and common sequences (\"abc\", \"123\")")
            ),
            "We recommend using a password manager to generate a unique password for every website."
        )
    ));

    @Override
    public @NotNull HtmlString getSummaryRuleHtml()
    {
        // We have to regenerate the link on every request because of per-page handling of the onClick event for CSP purposes
        String onClick = """
            const tips = document.getElementById('passwordTips');
            if (tips) tips.style.display = (tips.style.display === 'none' ? 'block' : 'none');
            const tipsLink = document.getElementById('tipsLink');
            if (tipsLink) tipsLink.text = (tipsLink.text = (tipsLink.text.includes('show') ? 'Click to hide' : 'Click to show') + ' tips for creating a secure password');
            """;

        LinkBuilder builder = new LinkBuilder("Click to show tips for creating a secure password").id("tipsLink").onClick(onClick).clearClasses();
        return HtmlStringBuilder.of(_intro)
            .append(builder.getHtmlString())
            .append(_tips)
            .getHtmlString();
    }
}
