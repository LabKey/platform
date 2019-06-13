package org.labkey.api.util;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterators;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.labkey.api.util.PageFlowUtil.filter;

public class DOM
{
    public interface Attributes extends Iterable<Map.Entry<Object,Object>> {}

    public static Attributes NOAT = null;

    public interface ClassNames {} // just a marker interface for better typing since this is used as .toString() at runtime

    public static ClassNames NOCLASS = null;

    public interface Renderable<STREAM extends Appendable> extends Function<STREAM, STREAM>
    {
    }

    public enum Element
    {
        a,
        abbr,
        address,
        area,
        article,
        aside,
        audio,
        b,
        base,
        bdi,
        bdo,
        big,
        blockquote,
        body,
        br,
        button,
        canvas,
        caption,
        cite,
        code,
        col,
        colgroup,
        data,
        datalist,
        dd,
        del,
        details,
        dfn,
        dialog,
        div,
        dl,
        dt,
        em,
        embed,
        fieldset,
        figcaption,
        figure,
        footer,
        form,
        h1,
        h2,
        h3,
        h4,
        h5,
        h6,
        head,
        header,
        hgroup,
        hr,
        html,
        i,
        iframe,
        img,
        input
        {
            @Override
            protected HtmlString _render(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body) throws IOException
            {
                return element(name(), attrs, classNames);
            }
            @Override
            protected Appendable _render(Appendable builder, Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body) throws IOException
            {
                return element(builder, name(), attrs, classNames, null);
            }
        },
        ins,
        kbd,
        keygen,
        label,
        legend,
        li,
        link,
        main,
        map,
        mark,
        menu,
        menuitem,
        meta,
        meter,
        nav,
        noindex,
        noscript,
        object,
        ol,
        optgroup,
        option,
        output,
        p,
        param,
        picture,
        pre,
        progress,
        q,
        rp,
        rt,
        ruby,
        s,
        samp,
        script,
        section,
        select,
        small,
        source,
        span,
        strong,
        style,
        sub,
        summary,
        sup,
        table,
        tbody,
        td,
        textarea,
        tfoot,
        th,
        thead,
        time,
        title,
        tr,
        track,
        u,
        ul,
        var,
        video,
        wbr,
        webview;

        protected HtmlString _render(Iterable<Map.Entry<Object,Object>> attrs, ClassNames classNames, Object...body) throws IOException
        {
            return element(name(), attrs, classNames, body);
        }

        final HtmlString render(Iterable<Map.Entry<Object,Object>> attrs, ClassNames classNames, Object...body)
        {
            try
            {
                return _render(attrs, classNames, body);
            }
            catch (IOException io)
            {
                throw new RuntimeException(io);
            }
        }

        protected Appendable _render(Appendable builder, Iterable<Map.Entry<Object,Object>> attrs, ClassNames classNames, Object...body) throws IOException
        {
            return element(builder, name(), attrs, classNames, body);
        }

        final Appendable render(Appendable builder, Iterable<Map.Entry<Object,Object>> attrs, ClassNames classNames, Object...body)
        {
            try
            {
                return _render(builder, attrs, classNames, body);
            }
            catch (IOException io)
            {
                throw new RuntimeException(io);
            }
        }
    }

    public enum Attribute
    {
        accept,
        accesskey,
        action,
        align,
        alt,
        async,
        autocomplete,
        autofocus,
        autoplay,
        bgcolor,
        border,
        charset,
        checked
        {
            @Override
            Appendable render(Appendable builder, Object value) throws IOException
            {
                if (value != Boolean.FALSE)
                    builder.append(" checked");
                return builder;
            }
        },
        cite,
//        className
//        {
//            Joiner joyner = Joiner.on(" ").useForNull("");
//            @Override
//            HtmlStringBuilder render(HtmlStringBuilder builder, Object value)
//            {
//                String classNames = "";
//                if (value instanceof Array)
//                    classNames = joyner.join((Object[])value);
//                else if (value instanceof Collection)
//                    classNames = joyner.join((Collection)value);
//                else if (value != null)
//                    classNames = String.valueOf(value);
//                return appendAttribute(builder, "class", classNames);
//            }
//        },
        color,
        cols,
        colspan,
        content,
        contenteditable,
        controls,
        coords,
        data,
        datetime,
        defaultValue,
        defer,
        dir,
        dirname,
        disabled,
        download,
        draggable,
        dropzone,
        enctype,
        form,
        formaction,
        headers,
        height,
        hidden,
        high,
        href,
        hreflang,
        id,
        ismap,
        kind,
        label,
        lang,
        list,
        loop,
        low,
        max,
        maxlength,
        media,
        method,
        min,
        multiple,
        muted,
        name,
        novalidate,
        onabort,
        onafterprint,
        onbeforeprint,
        onbeforeunload,
        onblur,
        oncanplay,
        oncanplaythrough,
        onchange,
        onclick,
        oncontextmenu,
        oncopy,
        oncuechange,
        oncut,
        ondblclick,
        ondrag,
        ondragend,
        ondragenter,
        ondragleave,
        ondragover,
        ondragstart,
        ondrop,
        ondurationchange,
        onemptied,
        onended,
        onerror,
        onfocus,
        onhashchange,
        oninput,
        oninvalid,
        onkeydown,
        onkeypress,
        onkeyup,
        onload,
        onloadeddata,
        onloadedmetadata,
        onloadstart,
        onmousedown,
        onmousemove,
        onmouseout,
        onmouseover,
        onmouseup,
        onmousewheel,
        onoffline,
        ononline,
        onpagehide,
        onpageshow,
        onpaste,
        onpause,
        onplay,
        onplaying,
        onpopstate,
        onprogress,
        onratechange,
        onreset,
        onresize,
        onscroll,
        onsearch,
        onseeked,
        onseeking,
        onselect,
        onstalled,
        onstorage,
        onsubmit,
        onsuspend,
        ontimeupdate,
        ontoggle,
        onunload,
        onvolumechange,
        onwaiting,
        onwheel,
        open,
        optimum,
        pattern,
        placeholder,
        poster,
        preload,
        readonly,
        rel,
        required,
        reversed,
        rows,
        rowspan,
        sandbox,
        scope,
        selected
        {
            @Override
            Appendable render(Appendable builder, Object value) throws IOException
            {
                if (value != Boolean.FALSE)
                    builder.append(" selected");
                return builder;
            }
        },
        shape,
        size,
        sizes,
        span,
        spellcheck,
        src,
        srcdoc,
        srclang,
        srcset,
        start,
        step,
        style
        {
            @Override
            Appendable render(Appendable builder, Object value) throws IOException
            {
                if (value instanceof Map)
                {
                    throw new NotImplementedException("not yet");
                }
                return super.render(builder,value);
            }
        },
        tabindex,
        target,
        title,
        translate,
        type,
        usemap,
        value,
        width,
        wrap;

        Appendable render(Appendable builder, Object value) throws IOException
        {
            return appendAttribute(builder, this, value);
        }

        static boolean isa(String name)
        {
            try
            {
                Attribute.valueOf(name);
                return true;
            }
            catch (IllegalArgumentException x)
            {
                return false;
            }
        }
    }

    public static class _Attributes implements Attributes
    {
        ArrayList<Map.Entry<Attribute,Object>> attrs = new ArrayList<>();
        ArrayList<Map.Entry<String,Object>> expandos = null;

        _Attributes(Map<Attribute,Object> map)
        {
            attrs.addAll( map.entrySet() );
        }
        _Attributes(Object... keyvalues)
        {
            putAll(keyvalues);
        }
        _Attributes putAll(Object... keyvalues)
        {
            assert keyvalues.length % 2 == 0;
            for (int i=0 ; i<keyvalues.length ; i+=2)
                put((Attribute)keyvalues[i], keyvalues[i+1]);
            return this;
        }
        _Attributes put(Attribute key, Object value)
        {
            attrs.add(new Pair<>(key,value));
            return this;
        }
        _Attributes id(String id)
        {
            put(Attribute.id, id);
            return this;
        }
        _Attributes data(String datakey, Object value)
        {
            if (null == expandos)
                expandos = new ArrayList<>();
            expandos.add(new Pair<>("data-"+datakey,value));
            return this;
        }

        @NotNull
        @Override
        public Iterator<Map.Entry<Object, Object>> iterator()
        {
            var it = (Iterator<Map.Entry<Object, Object>>)(Iterator)attrs.iterator();
            if (null == expandos)
                return it;
            var exp = (Iterator<Map.Entry<Object, Object>>)(Iterator)expandos.iterator();
            return Iterators.concat(it,exp);
        }
    }

    public static _Attributes at(Map map)
    {
        var ret = new _Attributes();
        map.forEach( (k,v) -> {
            Attribute a = k instanceof Attribute ? (Attribute)k : Attribute.valueOf((String)k);
            ret.put(a, v);
        });
        return ret;
    }

    public static _Attributes at(Attribute firstKey, Object firstValue, Object... keyvalues)
    {
        var ret = new _Attributes(firstKey,firstValue);
        ret.putAll(keyvalues);
        return ret;
    }

    public static _Attributes id(String id)
    {
        return new _Attributes("id", id);
    }

    public static class _ClassNames implements ClassNames
    {
        static Joiner j = Joiner.on(" ").skipNulls();
        TreeSet<String> classes = new TreeSet<>();

        _ClassNames(String...names)
        {
            add(names);
        }
        public _ClassNames add(String...names)
        {
            if (null != names)
                Arrays.stream(names).filter(Objects::nonNull).forEach(name -> classes.add(name));
            return this;
        }
        public _ClassNames add(boolean test, String className)
        {
            if (test && null!=className)
                classes.add(className);
            return this;
        }
        public _ClassNames add(boolean test, String trueName, String falseName)
        {
            if (test && null != trueName)
                classes.add(trueName);
            else if (!test && null != falseName)
                classes.add(falseName);
            return this;
        }
        public String toString()
        {
            return j.join(classes);
        }
    }

    public static _ClassNames cl(String ...names)
    {
        return new _ClassNames(names);
    }

    public static Renderable<Appendable> el(Element element, Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> element.render(html, attrs, classNames, body);
    }

    public static HtmlString createHtml(Renderable<Appendable> fn)
    {
        return HtmlString.unsafe(fn.apply(new StringBuilder()).toString());
    }

    public static Appendable createHtml(Appendable html, Renderable<Appendable> fn)
    {
        fn.apply(html);
        return html;
    }

    public static HtmlString createHtmlFragment(Object... body)
    {
        try
        {
            return element(null, null, null, body);
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static Appendable createHtmlFragment(Appendable html, Object... body)
    {
        try
        {
            return element(html, null, null, null, body);
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static class X
    {
        public static Renderable<Appendable> FA(String icon)
        {
            return (html) -> Element.i.render(html, null, cl("fa", "fa-"+icon));
        }
    }

    private static Appendable appendAttribute(Appendable html, String key, Object value) throws IOException
    {
        if (null==value)
            return html;
        html.append(" ");
        if (StringUtils.containsAny(key," \t\"\'<>"))
            throw new IllegalArgumentException(key);
        html.append(filter(key));
        html.append("=\"");
        String s = String.valueOf(value);
        if (StringUtils.isNotBlank(s))
            html.append(filter(s));
        html.append("\"");
        return html;
    }

    private static Appendable appendAttribute(Appendable html, Attribute key, Object value) throws IOException
    {
        if (null==value)
            return html;
        html.append(" ");
        html.append(key.name());
        html.append("=\"");
        String s = String.valueOf(value);
        if (StringUtils.isNotBlank(s))
            html.append(filter(s));
        html.append("\"");
        return html;
    }

    private static Appendable appendBody(Appendable builder, Object body)
    {
        if (null == body)
            return builder;
        else if (body instanceof HtmlString)
        {
            try
            {
                builder.append(body.toString());
            }
            catch (IOException io)
            {
                throw new RuntimeException(io);
            }
        }
        else if (body instanceof CharSequence)
        {
            try
            {
                builder.append(filter(body));
            }
            catch (IOException io)
            {
                throw new RuntimeException(io);
            }
        }
        else if (body instanceof Renderable)
            return ((Renderable<Appendable>) body).apply(builder);
        else if (body instanceof Array)
            for (var i : (Object[]) body)
                appendBody(builder, i);
        else if (body instanceof Stream)
            ((Stream)body).forEach(i -> appendBody(builder,i));
        else
            throw new IllegalArgumentException(body.getClass().getName());
        return builder;
    }

    private static HtmlString element(String tagName, Iterable<Map.Entry<Object, Object>> attrs, DOM.ClassNames classNames, Object... body) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        element(builder, tagName, attrs, classNames, body);
        return HtmlString.unsafe(builder.toString());
    }

    private static Appendable element(Appendable builder, String tagName, Iterable<Map.Entry<Object, Object>> attrs, DOM.ClassNames classNames, Object[] body) throws IOException
    {
        if (null != tagName)
        {
            assert (filter(tagName).equals(tagName));
            builder.append("<").append(tagName);
            if (null != attrs)
            {
                for (var entry : attrs)
                {
                    Object key = entry.getKey();
                    if (key instanceof DOM.Attribute)
                        ((DOM.Attribute)key).render(builder, entry.getValue());
                    else if (key instanceof String)
                    {
                        appendAttribute(builder, (String)key, entry.getValue());
                    }
                    else
                    {
                        throw new IllegalArgumentException(String.valueOf(key));
                    }
                }
            }
            if (null != classNames)
            {
                String clsValue = classNames.toString();
                if (!isBlank(clsValue))
                    appendAttribute(builder, "class", clsValue);
            }
            builder.append(">");
        }
        /* NOTE: we could have lots of overrides for different bodies, but it would get out of hand! */
        if (null != body)
            for (var item : body)
                appendBody(builder, item);
        if (null != tagName)
        {
            builder.append("</").append(tagName).append(">");
        }
        return builder;
    }




    //-- generated code here --
    public static Renderable<Appendable> AREA(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> AREA(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> AREA(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> AREA()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> BASE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> BASE(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> BASE(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> BASE()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> BR(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> BR(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> BR(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> BR()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> COL(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> COL(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> COL(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> COL()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> EMBED(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> EMBED(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> EMBED(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> EMBED()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> HR(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> HR(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> HR(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> HR()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> IMG(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> IMG(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> IMG(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> IMG()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> INPUT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> INPUT(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> INPUT(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> INPUT()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> KEYGEN(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> KEYGEN(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> KEYGEN(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> KEYGEN()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> LINK(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> LINK(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> LINK(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> LINK()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> META(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> META(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> META(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> META()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> PARAM(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> PARAM(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> PARAM(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> PARAM()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> SOURCE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> SOURCE(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> SOURCE(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> SOURCE()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> TRACK(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> TRACK(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> TRACK(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> TRACK()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> WBR(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
    {
        return (html) -> Element.option.render(html, attrs, classNames);
    }
    public static Renderable<Appendable> WBR(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS);
    }
    public static Renderable<Appendable> WBR(ClassNames classNames)
    {
        return (html) -> Element.option.render(html, NOAT, classNames);
    }
    public static Renderable<Appendable> WBR()
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS);
    }
    public static Renderable<Appendable> A(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> A(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> A(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> A(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> ABBR(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> ABBR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> ABBR(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> ABBR(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> ADDRESS(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> ADDRESS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> ADDRESS(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> ADDRESS(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> AREA(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> AREA(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> AREA(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> AREA(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> ARTICLE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> ARTICLE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> ARTICLE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> ARTICLE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> ASIDE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> ASIDE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> ASIDE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> ASIDE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> AUDIO(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> AUDIO(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> AUDIO(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> AUDIO(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> B(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> B(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> B(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> B(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> BASE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> BASE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> BASE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> BASE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> BDI(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> BDI(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> BDI(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> BDI(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> BDO(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> BDO(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> BDO(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> BDO(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> BIG(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> BIG(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> BIG(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> BIG(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> BLOCKQUOTE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> BLOCKQUOTE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> BLOCKQUOTE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> BLOCKQUOTE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> BODY(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> BODY(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> BODY(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> BODY(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> BR(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> BR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> BR(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> BR(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> BUTTON(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> BUTTON(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> BUTTON(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> BUTTON(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> CANVAS(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> CANVAS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> CANVAS(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> CANVAS(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> CAPTION(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> CAPTION(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> CAPTION(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> CAPTION(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> CITE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> CITE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> CITE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> CITE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> CODE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> CODE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> CODE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> CODE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> COL(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> COL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> COL(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> COL(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> COLGROUP(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> COLGROUP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> COLGROUP(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> COLGROUP(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DATA(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DATA(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DATA(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DATA(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DATALIST(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DATALIST(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DATALIST(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DATALIST(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DD(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DD(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DD(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DEL(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DEL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DEL(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DEL(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DETAILS(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DETAILS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DETAILS(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DETAILS(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DFN(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DFN(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DFN(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DFN(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DIALOG(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DIALOG(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DIALOG(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DIALOG(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DIV(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DIV(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DIV(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DIV(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DL(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DL(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DL(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> DT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> DT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> DT(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> DT(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> EM(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> EM(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> EM(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> EM(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> EMBED(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> EMBED(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> EMBED(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> EMBED(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> FIELDSET(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> FIELDSET(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> FIELDSET(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> FIELDSET(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> FIGCAPTION(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> FIGCAPTION(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> FIGCAPTION(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> FIGCAPTION(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> FIGURE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> FIGURE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> FIGURE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> FIGURE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> FOOTER(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> FOOTER(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> FOOTER(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> FOOTER(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> FORM(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> FORM(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> FORM(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> FORM(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> H1(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> H1(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> H1(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> H1(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> H2(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> H2(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> H2(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> H2(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> H3(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> H3(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> H3(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> H3(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> H4(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> H4(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> H4(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> H4(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> H5(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> H5(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> H5(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> H5(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> H6(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> H6(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> H6(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> H6(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> HEAD(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> HEAD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> HEAD(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> HEAD(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> HEADER(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> HEADER(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> HEADER(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> HEADER(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> HGROUP(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> HGROUP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> HGROUP(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> HGROUP(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> HR(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> HR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> HR(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> HR(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> HTML(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> HTML(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> HTML(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> HTML(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> I(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> I(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> I(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> I(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> IFRAME(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> IFRAME(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> IFRAME(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> IFRAME(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> IMG(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> IMG(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> IMG(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> IMG(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> INPUT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> INPUT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> INPUT(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> INPUT(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> INS(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> INS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> INS(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> INS(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> KBD(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> KBD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> KBD(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> KBD(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> KEYGEN(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> KEYGEN(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> KEYGEN(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> KEYGEN(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> LABEL(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> LABEL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> LABEL(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> LABEL(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> LEGEND(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> LEGEND(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> LEGEND(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> LEGEND(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> LI(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> LI(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> LI(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> LI(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> LINK(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> LINK(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> LINK(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> LINK(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> MAIN(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> MAIN(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> MAIN(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> MAIN(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> MAP(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> MAP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> MAP(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> MAP(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> MARK(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> MARK(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> MARK(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> MARK(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> MENU(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> MENU(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> MENU(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> MENU(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> MENUITEM(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> MENUITEM(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> MENUITEM(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> MENUITEM(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> META(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> META(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> META(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> META(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> METER(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> METER(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> METER(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> METER(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> NAV(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> NAV(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> NAV(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> NAV(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> NOINDEX(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> NOINDEX(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> NOINDEX(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> NOINDEX(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> NOSCRIPT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> NOSCRIPT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> NOSCRIPT(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> NOSCRIPT(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> OBJECT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> OBJECT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> OBJECT(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> OBJECT(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> OL(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> OL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> OL(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> OL(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> OPTGROUP(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> OPTGROUP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> OPTGROUP(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> OPTGROUP(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> OPTION(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> OPTION(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> OPTION(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> OPTION(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> OUTPUT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> OUTPUT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> OUTPUT(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> OUTPUT(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> P(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> P(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> P(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> P(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> PARAM(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> PARAM(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> PARAM(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> PARAM(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> PICTURE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> PICTURE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> PICTURE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> PICTURE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> PRE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> PRE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> PRE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> PRE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> PROGRESS(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> PROGRESS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> PROGRESS(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> PROGRESS(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> Q(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> Q(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> Q(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> Q(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> RP(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> RP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> RP(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> RP(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> RT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> RT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> RT(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> RT(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> RUBY(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> RUBY(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> RUBY(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> RUBY(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> S(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> S(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> S(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> S(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SAMP(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SAMP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SAMP(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SAMP(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SCRIPT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SCRIPT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SCRIPT(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SCRIPT(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SECTION(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SECTION(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SECTION(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SECTION(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SELECT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SELECT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SELECT(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SELECT(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SMALL(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SMALL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SMALL(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SMALL(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SOURCE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SOURCE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SOURCE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SOURCE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SPAN(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SPAN(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SPAN(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SPAN(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> STRONG(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> STRONG(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> STRONG(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> STRONG(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> STYLE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> STYLE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> STYLE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> STYLE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SUB(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SUB(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SUB(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SUB(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SUMMARY(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SUMMARY(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SUMMARY(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SUMMARY(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> SUP(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> SUP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> SUP(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> SUP(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TABLE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TABLE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TABLE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TABLE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TBODY(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TBODY(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TBODY(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TBODY(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TD(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TD(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TD(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TEXTAREA(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TEXTAREA(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TEXTAREA(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TEXTAREA(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TFOOT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TFOOT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TFOOT(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TFOOT(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TH(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TH(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TH(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TH(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> THEAD(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> THEAD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> THEAD(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> THEAD(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TIME(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TIME(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TIME(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TIME(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TITLE(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TITLE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TITLE(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TITLE(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TR(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TR(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TR(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> TRACK(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> TRACK(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> TRACK(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> TRACK(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> U(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> U(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> U(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> U(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> UL(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> UL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> UL(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> UL(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> VAR(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> VAR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> VAR(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> VAR(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> VIDEO(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> VIDEO(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> VIDEO(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> VIDEO(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> WBR(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> WBR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> WBR(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> WBR(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }
    public static Renderable<Appendable> WEBVIEW(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, classNames, body);
    }
    public static Renderable<Appendable> WEBVIEW(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, NOCLASS, body);
    }
    public static Renderable<Appendable> WEBVIEW(ClassNames classNames, Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, classNames, body);
    }
    public static Renderable<Appendable> WEBVIEW(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, NOCLASS, body);
    }


    //-- end generated code --
}
