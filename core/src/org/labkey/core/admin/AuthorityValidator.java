package org.labkey.core.admin;

import org.apache.commons.validator.routines.UrlValidator;

class AuthorityValidator extends UrlValidator
{
    public AuthorityValidator(long options)
    {
        super(options);
    }

    @Override
    public boolean isValidAuthority(String authority)
    {
        String base = authority.startsWith("*.") ? authority.substring(2) : authority;
        return super.isValidAuthority(base);
    }
}
