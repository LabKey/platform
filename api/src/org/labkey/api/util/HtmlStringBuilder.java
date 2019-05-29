package org.labkey.api.util;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterators;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

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


public class HtmlStringBuilder implements HtmlStream, HasHtmlString
{
    private final StringBuilder _sb = new StringBuilder();

    public static HtmlStringBuilder of(String s)
    {
        return new HtmlStringBuilder().append(s);
    }

    public static HtmlStringBuilder of(HtmlString hs)
    {
        return new HtmlStringBuilder().append(hs);
    }

    public static HtmlStringBuilder of(HasHtmlString hhs)
    {
        return new HtmlStringBuilder().append(hhs);
    }

    @Override
    public HtmlStringBuilder append(String s)
    {
        _sb.append(h(s));
        return this;
    }

    @Override
    public HtmlStringBuilder append(HtmlString hs)
    {
        _sb.append(hs.toString());
        return this;
    }

    @Override
    public HtmlStringBuilder append(HasHtmlString hhs)
    {
        _sb.append(hhs.getHtmlString());
        return this;
    }

    @Override
    public HtmlString getHtmlString()
    {
        return HtmlString.unsafe(_sb.toString());
    }

    private static String h(String s)
    {
        return PageFlowUtil.filter(s);
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
            HtmlStream render(HtmlStream builder, Object value)
            {
                if (value != Boolean.FALSE)
                    builder.append(HtmlString.unsafe(" checked"));
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
            HtmlStream render(HtmlStream builder, Object value)
            {
                if (value != Boolean.FALSE)
                    builder.append(HtmlString.unsafe(" selected"));
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
            HtmlStream render(HtmlStream builder, Object value)
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

        HtmlStream render(HtmlStream builder, Object value)
        {
            return appendAttribute(builder, name(), value);
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
    };

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
            HtmlString render(Iterable<Map.Entry<Object, Object>> attrs, DOM.ClassNames classNames, Object... body)
            {
                return element(name(), attrs, classNames);
            }
            @Override
            <H extends HtmlStream>
            H render(H builder, Iterable<Map.Entry<Object, Object>> attrs, DOM.ClassNames classNames, Object... body)
            {
                return (H)element(builder, name(), attrs, classNames, null);
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

        HtmlString render(Iterable<Map.Entry<Object,Object>> attrs, DOM.ClassNames classNames, Object...body)
        {
            return element(name(), attrs, classNames, body);
        }

        <H extends HtmlStream>
        H render(H builder, Iterable<Map.Entry<Object,Object>> attrs, DOM.ClassNames classNames, Object...body)
        {
            return (H)element(builder, name(), attrs, classNames, body);
        }
    };


    private static HtmlStream appendAttribute(HtmlStream html, String key, Object value)
    {
        if (null==value)
            return html;
        html.append(HtmlString.unsafe(" "));
        if (StringUtils.containsAny(key," \t\"\'<>"))
            throw new IllegalArgumentException(key);
        html.append(key);
        html.append(HtmlString.unsafe("=\""));
        String s = String.valueOf(value);
        if (StringUtils.isNotBlank(s))
            html.append(s);
        html.append(HtmlString.unsafe("\""));
        return html;
    }

    private static HtmlStream appendBody(HtmlStream builder, Object body)
    {
        if (null == body)
            return builder;
        else if (body instanceof HtmlString)
            builder.append((HtmlString) body);
        else if (body instanceof CharSequence)
            builder.append(body.toString());
        else if (body instanceof Function)
            ((Function<HtmlStream, HtmlStream>) body).apply(builder);
        else if (body instanceof Array)
            for (var i : (Object[]) body)
                appendBody(builder, i);
        else if (body instanceof Stream)
            ((Stream)body).forEach(i -> appendBody(builder,i));
        else
            throw new IllegalArgumentException(body.getClass().getName());
        return builder;
    }

    private static HtmlString element(String tagName, Iterable<Map.Entry<Object, Object>> attrs, DOM.ClassNames classNames, Object... body)
    {
        HtmlStringBuilder builder = new HtmlStringBuilder();
        element(builder, tagName, attrs, classNames, body);
        return builder.getHtmlString();
    }

    private static HtmlStream element(HtmlStream builder, String tagName, Iterable<Map.Entry<Object, Object>> attrs, DOM.ClassNames classNames, Object[] body)
    {
        if (null != tagName)
        {
            assert (h(tagName).equals(tagName));
            builder.append(HtmlString.unsafe("<")).append(tagName);
            if (null != attrs)
            {
                attrs.forEach(entry ->
                {
                    Object key = entry.getKey();
                    if (key instanceof Attribute)
                        ((Attribute)key).render(builder, entry.getValue());
                    else if (key instanceof String)
                    {
                        appendAttribute(builder, (String)key, entry.getValue());
                    }
                    else
                    {
                        throw new IllegalArgumentException(String.valueOf(key));
                    }
                });
            }
            if (null != classNames)
            {
                String clsValue = classNames.toString();
                if (!isBlank(clsValue))
                appendAttribute(builder, "class", clsValue);
            }
            builder.append(HtmlString.unsafe(">"));
        }
        /* NOTE: we could have lots of overrides for different bodies, but it would get out of hand! */
        if (null != body)
            for (var item : body)
                appendBody(builder, item);
        if (null != tagName)
        {
            builder.append(HtmlString.unsafe("</")).append(tagName).append(HtmlString.unsafe(">"));
        }
        return builder;
    }


    public static class DOM
    {
        public static class Attributes implements Iterable<Map.Entry<Object,Object>>
        {
            ArrayList<Map.Entry<Attribute,Object>> attrs = new ArrayList<>();
            ArrayList<Map.Entry<String,Object>> expandos = null;

            Attributes(Map<Attribute,Object> map)
            {
                attrs.addAll( map.entrySet() );
            }
            Attributes(Object... keyvalues)
            {
                putAll(keyvalues);
            }
            Attributes putAll(Object... keyvalues)
            {
                assert keyvalues.length % 2 == 0;
                for (int i=0 ; i<keyvalues.length ; i+=2)
                    put((Attribute)keyvalues[i], keyvalues[i+1]);
                return this;
            }
            Attributes put(Attribute key, Object value)
            {
                attrs.add(new Pair<>(key,value));
                return this;
            }
            Attributes data(String datakey, Object value)
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

        public static Attributes at(Map map)
        {
            var ret = new Attributes();
            map.forEach( (k,v) -> {
                Attribute a = k instanceof Attribute ? (Attribute)k : Attribute.valueOf((String)k);
                ret.put(a, v);
            });
            return ret;
        }

        public static Attributes at(Attribute firstKey, Object firstValue, Object... keyvalues)
        {
            var ret = new Attributes(firstKey,firstValue);
            ret.putAll(keyvalues);
            return ret;
        }

        public static Attributes NOAT = null;

        public interface ClassNames {}; // just a marker interface for better typing

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

        public static ClassNames NOCLASS = null;

        public static Function<HtmlStringBuilder,HtmlStringBuilder> A(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
        {
            return (html) -> Element.a.render(html, attrs, classNames, body);
        }

        public static Function<HtmlStringBuilder,HtmlStringBuilder> DIV(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
        {
            return (html) -> Element.div.render(html, attrs, classNames, body);
        }

        public static Function<HtmlStringBuilder,HtmlStringBuilder> H1(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
        {
            return (html) -> Element.h1.render(html, attrs, classNames, body);
        }

        public static Function<HtmlStringBuilder,HtmlStringBuilder> H2(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
        {
            return (html) -> Element.h2.render(html, attrs, classNames, body);
        }

        public static Function<HtmlStringBuilder,HtmlStringBuilder> I(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
        {
            return (html) -> Element.i.render(html, attrs, classNames, body);
        }

        public static Function<HtmlStringBuilder,HtmlStringBuilder> INPUT(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
        {
            return (html) -> Element.input.render(html, attrs, classNames, body);
        }

        public static Function<HtmlStringBuilder,HtmlStringBuilder> OPTION(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
        {
            return (html) -> Element.option.render(html, attrs, classNames, body);
        }

        public static Function<HtmlStringBuilder,HtmlStringBuilder> SELECT(final Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, final Object... body)
        {
            return (html) -> Element.select.render(html, attrs, classNames, body);
        }

        public static Function<HtmlStringBuilder,HtmlStringBuilder> SPAN(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
        {
            return (html) -> Element.span.render(html, attrs, classNames, body);
        }

        public static Function<HtmlStringBuilder,HtmlStringBuilder> el(Element element, Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
        {
            return (html) -> element.render(html, attrs, classNames, body);
        }

        public static HtmlString createHtml(Function<HtmlStringBuilder,HtmlStringBuilder> fn)
        {
            return fn.apply(new HtmlStringBuilder()).getHtmlString();
        }

        public static HtmlStream createHtml(HtmlStream html, Function<HtmlStream,HtmlStream> fn)
        {
            fn.apply(html);
            return html;
        }

        public static HtmlString createHtmlFragment(Object... body)
        {
            return element(null, null, null, body);
        }

        public static HtmlStream createHtmlFragment(HtmlStream html, Object... body)
        {
            return element(html, null, null, null, body);
        }
    }

    public static class DOMx
    {
        public static Function<HtmlStringBuilder,HtmlStringBuilder> FA(String icon)
        {
            return (html) -> Element.i.render(html, null, DOM.cl("fa", "fa-"+icon));
        }
    }
}