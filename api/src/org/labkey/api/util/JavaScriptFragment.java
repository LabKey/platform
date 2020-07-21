package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to assert that a character sequence is valid, properly encoded JavaScript. Similar to HtmlString, though this class
 * is just a simple wrapper; it doesn't (yet) provide filtering, a builder, or other useful mechanisms of HtmlString.
 */
public class JavaScriptFragment
{
    public static final JavaScriptFragment NULL = JavaScriptFragment.unsafe(" null ");
    public static final JavaScriptFragment TRUE = JavaScriptFragment.unsafe(" true ");
    public static final JavaScriptFragment FALSE = JavaScriptFragment.unsafe(" false ");
    public static final JavaScriptFragment bool(boolean b) { return b ? TRUE : FALSE;};

    private final @NotNull String _s;

    /**
     * Returns a JavaScriptFragment that wraps the passed in String.
     * @param s A String. A null value results in an empty JavaScriptFragment (equivalent of JavaScriptFragment.unsafe("")).
     * @return A JavaScriptFragment that wraps the String.
     */
    public static @NotNull JavaScriptFragment unsafe(@Nullable String s)
    {
        return new JavaScriptFragment(s);
    }

    // Callers use factory method unsafe() instead
    private JavaScriptFragment(String s)
    {
        _s = null == s ? "" : s;
    }

    @Override
    public String toString()
    {
        return _s;
    }
}
