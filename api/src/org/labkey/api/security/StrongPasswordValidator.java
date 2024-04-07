package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;

import java.util.Map;

import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.id;

public class StrongPasswordValidator extends EntropyPasswordValidator
{
    private final HtmlString _fullRuleHtml;

    public StrongPasswordValidator()
    {
        _fullRuleHtml = DOM.createHtml(
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
        );
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

    private final HtmlString _tipsIntro = HtmlStringBuilder.of()
        .append("Secure passwords are long and use multiple character types. The password strength gauge will turn green when your new password meets the complexity requirements.")
        .append(HtmlString.BR)
        .getHtmlString();

    private final String _tipsLinkText = "Click to show tips for creating a secure password";
    private final String _tipsLinkOnClick = """
        const tips = document.getElementById('passwordTips');
        if (tips) tips.style.display = (tips.style.display === 'none' ? 'block' : 'none');
        this.text = (this.text.includes('show') ?
        """ + PageFlowUtil.jsString(_tipsLinkText.replace("show", "hide")) + " : " + PageFlowUtil.jsString(_tipsLinkText) + ");";
    private final LinkBuilder _tipsLink = new LinkBuilder(_tipsLinkText)
        .id("tipsLink")
        .attributes(Map.of(DOM.Attribute.tabindex.name(), "5"))
        .onClick(_tipsLinkOnClick)
        .clearClasses();

    private final HtmlString _tips = DOM.createHtml(
        DIV(id("passwordTips").at(DOM.Attribute.style, "display:none;"),
            DOM.UL(
                DOM.LI("Use " + getCharacterCountEstimate() + " characters or more"),
                DOM.LI("Include upper- and lower-case letters"),
                DOM.LI("Include symbols and numbers"),
                DOM.LI("Avoid repeated characters and sequences, personal information (email, name), and common sequences (\"abc\", \"123\")")
            ),
            "We recommend using a password manager to generate a unique password for every website."
        )
    );

    @Override
    public @NotNull HtmlString getSummaryRuleHtml()
    {
        // We have to re-render the link on every request because of per-page handling of the onClick event for CSP purposes
        return HtmlStringBuilder.of(_tipsIntro)
            .append(_tipsLink.getHtmlString())
            .append(_tips)
            .getHtmlString();
    }
}
