package org.labkey.api.security;

// Lets callers perform permission checks on the underlying user. Primary purpose is to give linked filtered schemas
// access to subfolder data when the associated table definitions reside at the project or /Shared level. Use this
// interface sparingly and carefully, since it allows complete bypass of LimitedUser constraints.
public interface WrappedUser
{
    User unwrap();
}
