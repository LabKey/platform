package org.mitre.dsmiley.httpproxy;

// Class SM no longer exists in HttpClient 5.x, so define constants we need here. These header names are likely all
// deprecated, so perhaps stop using them?
public class SM
{
    static final String COOKIE            = "Cookie";
    static final String SET_COOKIE        = "Set-Cookie";
    static final String SET_COOKIE2       = "Set-Cookie2";
}
