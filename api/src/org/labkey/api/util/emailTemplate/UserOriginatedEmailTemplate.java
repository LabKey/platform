package org.labkey.api.util.emailTemplate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by xingyang on 12/7/15.
 */
public abstract class UserOriginatedEmailTemplate extends EmailTemplate
{
    private List<ReplacementParam> _replacements = new ArrayList<>();
    protected User _originatingUser;

    public UserOriginatedEmailTemplate(@NotNull String name)
    {
        this(name, "", "", "", ContentType.Plain);
    }

    public UserOriginatedEmailTemplate(@NotNull String name, String subject, String body, String description)
    {
        this(name, subject, body, description, ContentType.Plain);
    }

    public UserOriginatedEmailTemplate(@NotNull String name, String subject, String body, String description, @NotNull ContentType contentType)
    {
        this(name, subject, body, description, contentType, DEFAULT_SENDER);
    }

    public UserOriginatedEmailTemplate(@NotNull String name, String subject, String body, String description, @NotNull ContentType contentType, @Nullable String senderDisplayName)
    {
        super(name, subject, body, description, contentType, senderDisplayName);
        _replacements.add(new ReplacementParam<String>("userFirstName", String.class, "First name of the user originated the action"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getFirstName();
            }
        });
        _replacements.add(new ReplacementParam<String>("userLastName", String.class, "Last name of the user originated the action"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getLastName();
            }
        });
        _replacements.add(new ReplacementParam<String>("userDisplayName", String.class, "Display name of the user originated the action"){
            public String getValue(Container c) {
                return _originatingUser == null ? null : _originatingUser.getFriendlyName();
            }
        });

        _replacements.addAll(super.getValidReplacements());
    }
    public List<ReplacementParam> getValidReplacements(){return _replacements;}
    public void setOriginatingUser(User user){_originatingUser = user;}
}
