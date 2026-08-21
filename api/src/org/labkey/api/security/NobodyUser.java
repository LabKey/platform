package org.labkey.api.security;

public class NobodyUser extends LimitedUser
{
    public NobodyUser()
    {
        super(User.guest);
    }

    @Override
    public boolean isGuest()
    {
        return true;
    }
}
