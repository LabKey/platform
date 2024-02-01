package org.labkey.api.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to assert that a character sequence is valid, properly encoded JavaScript. Similar to HtmlString, though this class
 * is just a simple wrapper; it doesn't (yet) provide filtering, a builder, or other useful mechanisms of HtmlString.
 */
public class JavaScriptFragment implements SafeToRender
{
    public static final JavaScriptFragment EMPTY = new JavaScriptFragment("");
    public static final JavaScriptFragment EMPTY_STRING = JavaScriptFragment.unsafe("''");
    public static final JavaScriptFragment NULL = JavaScriptFragment.unsafe(" null ");
    public static final JavaScriptFragment TRUE = JavaScriptFragment.unsafe(" true ");
    public static final JavaScriptFragment FALSE = JavaScriptFragment.unsafe(" false ");

    public static JavaScriptFragment bool(boolean b) { return b ? TRUE : FALSE;};

    private final @NotNull String _s;

    /**
     * Returns a JavaScriptFragment that wraps the passed in String.
     * @param s A String. A null value results in an empty JavaScriptFragment (equivalent of JavaScriptFragment.unsafe("")).
     * @return A JavaScriptFragment that wraps the String.
     */
    public static @NotNull JavaScriptFragment unsafe(@Nullable String s)
    {
        if (null == s)
            return EMPTY;
        // even with unsafe() a javascript fragment can never contain the sequence "</[Ss][Cc][Rr][Ii][Pp][Tt]>"
        // since </ is not legal javascript syntax we escape it
        s = StringUtils.replace(s, "</", "<\\/");
        return new JavaScriptFragment(s);
    }

    /** Create escaped javascript string literal */
    public static @NotNull JavaScriptFragment asString(String s)
    {
        if (null == s)
            return JavaScriptFragment.NULL;
        var js = PageFlowUtil.jsString(s);
        assert !StringUtils.contains(js, "</");
        return new JavaScriptFragment(js);
    }

    /** Format "Object value" as JSON and render into a JavaScriptFragment */
    public static @NotNull JavaScriptFragment asJson(Object value)
    {
        if (null == value)
            return NULL;
        try
        {
            String s = JsonUtil.DEFAULT_MAPPER.writeValueAsString(value);
            if (StringUtils.contains(s, "</"))
                throw new IllegalStateException("Error encoding JSON object");
            return new JavaScriptFragment(s);
        }
        catch (JsonProcessingException x)
        {
            throw UnexpectedException.wrap(x);
        }
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
