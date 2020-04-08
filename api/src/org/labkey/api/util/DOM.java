package org.labkey.api.util;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterators;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.jsp.taglib.ErrorsTag;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.context.NoSuchMessageException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import javax.servlet.jsp.PageContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.labkey.api.util.HtmlString.unsafe;
import static org.labkey.api.util.PageFlowUtil.filter;

public class DOM
{
    public interface Attributes extends Iterable<Map.Entry<Object,Object>> {}

    public static Attributes NOAT = null;

    public interface ClassNames {} // just a marker interface for better typing since this is used as .toString() at runtime

    public interface Renderable
    {
        Appendable appendTo(Appendable sb);
    }

    public enum Element
    {
        a,
        abbr,
        address,
        area(true),
        article,
        aside,
        audio,
        b,
        base(true),
        bdi,
        bdo,
        big,
        blockquote,
        body,
        br(true),
        button,
        canvas,
        caption,
        cite,
        code,
        col(true),
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
        embed(true),
        fieldset,
        figcaption,
        figure,
        footer,
        font,
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
        hr(true),
        html,
        i,
        iframe,
        img(true),
        input(true),
        ins,
        kbd,
        keygen(true),
        label,
        legend,
        li,
        link(true),
        main,
        map,
        mark,
        menu,
        menuitem(true),
        meta(true),
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
        param(true),
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
        source(true),
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
        track(true),
        u,
        ul,
        var,
        video,
        wbr(true),
        webview;

        final boolean _selfClosing;

        Element()
        {
            _selfClosing = false;
        }

        Element(boolean b)
        {
            _selfClosing = b;
        }

        protected Appendable _render(Appendable builder, Iterable<Map.Entry<Object,Object>> attrs, Object...body) throws IOException
        {
            return appendElement(builder, name(), _selfClosing, attrs, body);
        }

        final Appendable render(Appendable builder, Iterable<Map.Entry<Object,Object>> attrs, Object...body)
        {
            try
            {
                return _render(builder, attrs, body);
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
        cellpadding,
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
        valign,
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
        static Joiner j = Joiner.on(" ").skipNulls();
        ArrayList<Map.Entry<Attribute,Object>> attrs = new ArrayList<>();
        Set<String> classes = new TreeSet<>();
        ArrayList<Map.Entry<String,Object>> expandos = null;
        Consumer<Appendable> callback = null;

        _Attributes()
        {
        }
        _Attributes(Attribute firstKey, Object firstValue, Object... keyvalues)
        {
            at(firstKey, firstValue, keyvalues);
        }
        public _Attributes at(Attribute firstKey, Object firstValue, Object... keyvalues)
        {
            at(firstKey, firstValue);
            assert keyvalues.length % 2 == 0;
            for (int i=0 ; i<keyvalues.length ; i+=2)
                at((Attribute)keyvalues[i], keyvalues[i+1]);
            return this;
        }
        public _Attributes at(Attribute key, Object value)
        {
            attrs.add(new Pair<>(key,value));
            return this;
        }
        public _Attributes at(boolean test, Attribute key, Object value)
        {
            if (test)
                attrs.add(new Pair<>(key,value));
            return this;
        }
        public _Attributes at(boolean test, Attribute key, Object ifValue, Object elseValue)
        {
            if (test)
                attrs.add(new Pair<>(key,ifValue));
            else
                attrs.add(new Pair<>(key,elseValue));
            return this;
        }
        public _Attributes id(String id)
        {
            at(Attribute.id, id);
            return this;
        }
        public _Attributes data(String datakey, Object value)
        {
            if (null == expandos)
                expandos = new ArrayList<>();
            expandos.add(new Pair<>("data-"+datakey,value));
            return this;
        }
        public _Attributes data(boolean condition, String datakey, Object value)
        {
            if (condition)
            {
                if (null == expandos)
                    expandos = new ArrayList<>();
                expandos.add(new Pair<>("data-"+datakey,value));
            }
            return this;
        }

        public _Attributes cl(String...names)
        {
            if (null != names)
                Arrays.stream(names).filter(Objects::nonNull).forEach(name -> classes.add(name));
            return this;
        }
        public _Attributes cl(boolean test, String className)
        {
            if (test && null!=className)
                classes.add(className);
            return this;
        }
        public _Attributes cl(boolean test, String trueName, String falseName)
        {
            if (test && null != trueName)
                classes.add(trueName);
            else if (!test && null != falseName)
                classes.add(falseName);
            return this;
        }

        // horrible hack for temporary backward compatibility
        public _Attributes callback(Consumer<Appendable> fn)
        {
            this.callback = fn;
            return this;
        }

        @NotNull
        @Override
        public Iterator<Map.Entry<Object, Object>> iterator()
        {
            var it = (Iterator<Map.Entry<Object, Object>>)(Iterator)attrs.iterator();
            if (!classes.isEmpty())
                it = Iterators.concat(it, Iterators.singletonIterator(new Pair<>("class", j.join(classes))));
            if (null != expandos)
                it = Iterators.concat(it,(Iterator<Map.Entry<Object, Object>>)(Iterator)expandos.iterator());
            return it;
        }
    }

    public static _Attributes at(Map map)
    {
        var ret = new _Attributes();
        map.forEach( (k,v) -> {
            if (k instanceof Attribute)
            {
                ret.at((Attribute) k, v);
            }
            else
            {
                if (!(k instanceof String))
                    throw new IllegalStateException("expected Attribute or String");
                if (((String)k).startsWith("data-"))
                    ret.data(((String)k).substring("data-".length()), v);
                else
                    ret.at(Attribute.valueOf((String)k), v);
            }
        });
        return ret;
    }

    /* copy attributes, useful for extended/custom elements */
    public static _Attributes at(Attributes attrsIn)
    {
        if (!(attrsIn instanceof _Attributes))
            throw new UnsupportedOperationException();
        _Attributes in = (_Attributes)attrsIn;
        _Attributes copy = new _Attributes();
        copy.attrs.addAll(in.attrs);
        copy.classes.addAll(in.classes);
        if (in.expandos != null)
        {
            copy.expandos = new ArrayList<>();
            copy.expandos.addAll(in.expandos);
        }
        copy.callback = in.callback;
        return copy;
    }

    public static _Attributes at(Attribute firstKey, Object firstValue, Object... keyvalues)
    {
        var ret = new _Attributes(firstKey,firstValue,keyvalues);
        return ret;
    }

    public static _Attributes cl(boolean f, String className)
    {
        return new _Attributes().cl(f, className);
    }

    public static _Attributes cl(boolean f, String trueName, String falseName)
    {
        return new _Attributes().cl(f, trueName, falseName);
    }

    public static _Attributes cl(String... classNames)
    {
        var ret = new _Attributes();
        Arrays.stream(classNames).filter(Objects::nonNull).forEach(ret::cl);
        return ret;
    }

    public static _Attributes id(String value)
    {
        return new _Attributes(Attribute.id, value);
    }

    // TODO parse css selector style strings e.g. css("#header1.bold")
    public static _Attributes css(String selector)
    {
        // TODO
        return new _Attributes();
    }

    public static Renderable el(Element element, Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
    {
        return (html) -> element.render(html, attrs, classNames, body);
    }

    public static HtmlString createHtml(Renderable fn)
    {
        return unsafe(fn.appendTo(new StringBuilder()).toString());
    }

    public static Appendable createHtml(Appendable html, Renderable fn)
    {
        fn.appendTo(html);
        return html;
    }

    public static HtmlString createHtmlFragment(Object... body)
    {
        try
        {
            return unsafe(appendElement(new StringBuilder(), null, false, null, body).toString());
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
            return appendElement(html, null, false, null, body);
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    // LabKey extensions, any helpers that are not directly representations of native browser DOM
    public static class LK
    {
        public static Renderable CHECKBOX(Attributes attrs)
        {
            // find name attribute
            String name = null;
            for (var attr : attrs)
            {
                if (attr.getKey()==Attribute.name)
                {
                    name = String.valueOf(attr.getValue());
                    break;
                }
            }
            return createHtmlFragment(
                    DOM.INPUT((at(attrs).at(Attribute.type,"checkbox"))),
                    null==name?null:DOM.INPUT(at(Attribute.type,"hidden", Attribute.name, SpringActionController.FIELD_MARKER+name))
            );
        }

        /** font-awesome */
        public static Renderable FA(String icon)
        {
            return (html) -> Element.i.render(html, cl("fa", "fa-"+icon));
        }

        public static Renderable FORM(Object... body)
        {
            return FORM(at(Attribute.method,"GET"), body);
        }

        public static Renderable FORM(Attributes attrs, Object... body)
        {
            boolean isPost = false;
            if (null != attrs)
                for (var attr : attrs)
                {
                    if (attr.getKey() == Attribute.method && "POST".equalsIgnoreCase(String.valueOf(attr.getValue())))
                        isPost = true;
                }
            var csrfInput = !isPost ? null : DOM.INPUT(at(
                    Attribute.type,"hidden",
                    Attribute.name,CSRFUtil.csrfName,
                    Attribute.value,CSRFUtil.getExpectedToken(HttpView.currentContext())));
            return DOM.FORM(attrs, body, csrfInput);
        }

        public static Renderable ERRORS(PageContext pageContext)
        {
            int count=0;
            Enumeration<String> e = pageContext.getAttributeNamesInScope(PageContext.REQUEST_SCOPE);
            List<Renderable> list = new ArrayList<>();
            while (e.hasMoreElements())
            {
                String s = e.nextElement();
                if (s.startsWith(BindingResult.MODEL_KEY_PREFIX))
                {
                    Object o = pageContext.getAttribute(s, PageContext.REQUEST_SCOPE);
                    if (o instanceof BindingResult)
                        list.add(ERRORS((BindingResult)o));
                }
            }
            if (list.isEmpty())
                return null;
            return createHtmlFragment(list.toArray());
        }


        public static Renderable ERRORS(BindingResult errors)
        {
            return ERRORS(errors.getAllErrors());
        }

        public static Renderable ERRORS(List<ObjectError> z)
        {
            if (null == z || z.isEmpty())
                return HtmlString.unsafe("");
            final ViewContext context = HttpView.getRootContext();
            return DIV(cl("labkey-error"),
                z.stream().map(error ->
                {
                    try
                    {
                        if (error instanceof LabKeyError)
                            return createHtmlFragment((((LabKeyError)error).renderToHTML(context)),BR());
                        else
                            return createHtmlFragment(HtmlString.unsafe(PageFlowUtil.filter(context.getMessage(error), true)),BR());
                    }
                    catch (NoSuchMessageException nsme)
                    {
                        ExceptionUtil.logExceptionToMothership(context.getRequest(), nsme);
                        Logger log = Logger.getLogger(ErrorsTag.class);
                        log.error("Failed to find a message: " + error, nsme);
                        return createHtmlFragment("Unknown error: " + error, BR());
                    }
                })
            );
        }
    }

    @Deprecated /* use LK */
    public static class X extends LK
    {
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

    // don't throw checked exception, because it makes using lambdas a big pain
    private static Appendable appendBody(Appendable builder, Object body)
    {
        if (null == body)
            return builder;
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
        else if (body instanceof DOM.Renderable)
        {
            ((DOM.Renderable) body).appendTo(builder);
        }
        else if (body.getClass().isArray())
        {
            for (var i : (Object[]) body)
                appendBody(builder, i);
        }
        else if (body instanceof Stream)
        {
            ((Stream<Object>) body).forEach(i -> appendBody(builder, i));
        }
        else
        {
            if (body instanceof Attribute)
                throw new IllegalArgumentException("Unexpected type in element contents: " + body.getClass().getName() + ".  Did you forget to wrap your attributes with 'at()'?");
            throw new IllegalArgumentException("Unexpected type in element contents: " + body.getClass().getName());
        }
        return builder;
    }

    private static Appendable appendElement(Appendable builder, String tagName, boolean selfClosing, Iterable<Map.Entry<Object, Object>> attrs, Object... body) throws IOException
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
                        ((DOM.Attribute) key).render(builder, entry.getValue());
                    else if (key instanceof String)
                    {
                        appendAttribute(builder, (String) key, entry.getValue());
                    }
                    else
                    {
                        throw new IllegalArgumentException(String.valueOf(key));
                    }
                }
                // TODO again horrible hack, make this go away
                if (attrs instanceof _Attributes && null != ((_Attributes) attrs).callback)
                {
                    builder.append(" ");
                    ((_Attributes) attrs).callback.accept(builder);
                }
            }
            if (selfClosing)
            {
                builder.append(" />");
                return builder;
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
    public static Renderable AREA(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.area.render(html, attrs);
    }
    public static Renderable AREA()
    {
        return (html) -> Element.area.render(html, NOAT);
    }
    public static Renderable BASE(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.base.render(html, attrs);
    }
    public static Renderable BASE()
    {
        return (html) -> Element.base.render(html, NOAT);
    }
    public static Renderable BR(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.br.render(html, attrs);
    }
    public static Renderable BR()
    {
        return (html) -> Element.br.render(html, NOAT);
    }
    public static Renderable COL(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.col.render(html, attrs);
    }
    public static Renderable COL()
    {
        return (html) -> Element.col.render(html, NOAT);
    }
    public static Renderable EMBED(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.embed.render(html, attrs);
    }
    public static Renderable EMBED()
    {
        return (html) -> Element.embed.render(html, NOAT);
    }
    public static Renderable HR(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.hr.render(html, attrs);
    }
    public static Renderable HR()
    {
        return (html) -> Element.hr.render(html, NOAT);
    }
    public static Renderable IMG(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.img.render(html, attrs);
    }
    public static Renderable IMG()
    {
        return (html) -> Element.img.render(html, NOAT);
    }
    public static Renderable INPUT(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.input.render(html, attrs);
    }
    public static Renderable INPUT()
    {
        return (html) -> Element.input.render(html, NOAT);
    }
    public static Renderable KEYGEN(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.keygen.render(html, attrs);
    }
    public static Renderable KEYGEN()
    {
        return (html) -> Element.keygen.render(html, NOAT);
    }
    public static Renderable LINK(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.link.render(html, attrs);
    }
    public static Renderable LINK()
    {
        return (html) -> Element.link.render(html, NOAT);
    }
    public static Renderable META(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.meta.render(html, attrs);
    }
    public static Renderable META()
    {
        return (html) -> Element.meta.render(html, NOAT);
    }
    public static Renderable PARAM(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.param.render(html, attrs);
    }
    public static Renderable PARAM()
    {
        return (html) -> Element.param.render(html, NOAT);
    }
    public static Renderable SOURCE(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.source.render(html, attrs);
    }
    public static Renderable SOURCE()
    {
        return (html) -> Element.source.render(html, NOAT);
    }
    public static Renderable TRACK(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.track.render(html, attrs);
    }
    public static Renderable TRACK()
    {
        return (html) -> Element.track.render(html, NOAT);
    }
    public static Renderable WBR(Iterable<Map.Entry<Object, Object>> attrs)
    {
        return (html) -> Element.wbr.render(html, attrs);
    }
    public static Renderable WBR()
    {
        return (html) -> Element.wbr.render(html, NOAT);
    }
    public static Renderable A(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.a.render(html, attrs, body);
    }
    public static Renderable A(Object... body)
    {
        return (html) -> Element.a.render(html, NOAT, body);
    }
    public static Renderable ABBR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.abbr.render(html, attrs, body);
    }
    public static Renderable ABBR(Object... body)
    {
        return (html) -> Element.abbr.render(html, NOAT, body);
    }
    public static Renderable ADDRESS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.address.render(html, attrs, body);
    }
    public static Renderable ADDRESS(Object... body)
    {
        return (html) -> Element.address.render(html, NOAT, body);
    }
    public static Renderable AREA(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.area.render(html, attrs, body);
    }
    public static Renderable AREA(Object... body)
    {
        return (html) -> Element.area.render(html, NOAT, body);
    }
    public static Renderable ARTICLE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.article.render(html, attrs, body);
    }
    public static Renderable ARTICLE(Object... body)
    {
        return (html) -> Element.article.render(html, NOAT, body);
    }
    public static Renderable ASIDE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.aside.render(html, attrs, body);
    }
    public static Renderable ASIDE(Object... body)
    {
        return (html) -> Element.aside.render(html, NOAT, body);
    }
    public static Renderable AUDIO(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.audio.render(html, attrs, body);
    }
    public static Renderable AUDIO(Object... body)
    {
        return (html) -> Element.audio.render(html, NOAT, body);
    }
    public static Renderable B(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.b.render(html, attrs, body);
    }
    public static Renderable B(Object... body)
    {
        return (html) -> Element.b.render(html, NOAT, body);
    }
    public static Renderable BASE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.base.render(html, attrs, body);
    }
    public static Renderable BASE(Object... body)
    {
        return (html) -> Element.base.render(html, NOAT, body);
    }
    public static Renderable BDI(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.bdi.render(html, attrs, body);
    }
    public static Renderable BDI(Object... body)
    {
        return (html) -> Element.bdi.render(html, NOAT, body);
    }
    public static Renderable BDO(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.bdo.render(html, attrs, body);
    }
    public static Renderable BDO(Object... body)
    {
        return (html) -> Element.bdo.render(html, NOAT, body);
    }
    public static Renderable BIG(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.big.render(html, attrs, body);
    }
    public static Renderable BIG(Object... body)
    {
        return (html) -> Element.big.render(html, NOAT, body);
    }
    public static Renderable BLOCKQUOTE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.blockquote.render(html, attrs, body);
    }
    public static Renderable BLOCKQUOTE(Object... body)
    {
        return (html) -> Element.blockquote.render(html, NOAT, body);
    }
    public static Renderable BODY(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.body.render(html, attrs, body);
    }
    public static Renderable BODY(Object... body)
    {
        return (html) -> Element.body.render(html, NOAT, body);
    }
    public static Renderable BR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.br.render(html, attrs, body);
    }
    public static Renderable BR(Object... body)
    {
        return (html) -> Element.br.render(html, NOAT, body);
    }
    public static Renderable BUTTON(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.button.render(html, attrs, body);
    }
    public static Renderable BUTTON(Object... body)
    {
        return (html) -> Element.button.render(html, NOAT, body);
    }
    public static Renderable CANVAS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.canvas.render(html, attrs, body);
    }
    public static Renderable CANVAS(Object... body)
    {
        return (html) -> Element.canvas.render(html, NOAT, body);
    }
    public static Renderable CAPTION(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.caption.render(html, attrs, body);
    }
    public static Renderable CAPTION(Object... body)
    {
        return (html) -> Element.caption.render(html, NOAT, body);
    }
    public static Renderable CITE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.cite.render(html, attrs, body);
    }
    public static Renderable CITE(Object... body)
    {
        return (html) -> Element.cite.render(html, NOAT, body);
    }
    public static Renderable CODE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.code.render(html, attrs, body);
    }
    public static Renderable CODE(Object... body)
    {
        return (html) -> Element.code.render(html, NOAT, body);
    }
    public static Renderable COL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.col.render(html, attrs, body);
    }
    public static Renderable COL(Object... body)
    {
        return (html) -> Element.col.render(html, NOAT, body);
    }
    public static Renderable COLGROUP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.colgroup.render(html, attrs, body);
    }
    public static Renderable COLGROUP(Object... body)
    {
        return (html) -> Element.colgroup.render(html, NOAT, body);
    }
    public static Renderable DATA(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.data.render(html, attrs, body);
    }
    public static Renderable DATA(Object... body)
    {
        return (html) -> Element.data.render(html, NOAT, body);
    }
    public static Renderable DATALIST(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.datalist.render(html, attrs, body);
    }
    public static Renderable DATALIST(Object... body)
    {
        return (html) -> Element.datalist.render(html, NOAT, body);
    }
    public static Renderable DD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.dd.render(html, attrs, body);
    }
    public static Renderable DD(Object... body)
    {
        return (html) -> Element.dd.render(html, NOAT, body);
    }
    public static Renderable DEL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.del.render(html, attrs, body);
    }
    public static Renderable DEL(Object... body)
    {
        return (html) -> Element.del.render(html, NOAT, body);
    }
    public static Renderable DETAILS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.details.render(html, attrs, body);
    }
    public static Renderable DETAILS(Object... body)
    {
        return (html) -> Element.details.render(html, NOAT, body);
    }
    public static Renderable DFN(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.dfn.render(html, attrs, body);
    }
    public static Renderable DFN(Object... body)
    {
        return (html) -> Element.dfn.render(html, NOAT, body);
    }
    public static Renderable DIALOG(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.dialog.render(html, attrs, body);
    }
    public static Renderable DIALOG(Object... body)
    {
        return (html) -> Element.dialog.render(html, NOAT, body);
    }
    public static Renderable DIV(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.div.render(html, attrs, body);
    }
    public static Renderable DIV(Object... body)
    {
        return (html) -> Element.div.render(html, NOAT, body);
    }
    public static Renderable DL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.dl.render(html, attrs, body);
    }
    public static Renderable DL(Object... body)
    {
        return (html) -> Element.dl.render(html, NOAT, body);
    }
    public static Renderable DT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.dt.render(html, attrs, body);
    }
    public static Renderable DT(Object... body)
    {
        return (html) -> Element.dt.render(html, NOAT, body);
    }
    public static Renderable EM(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.em.render(html, attrs, body);
    }
    public static Renderable EM(Object... body)
    {
        return (html) -> Element.em.render(html, NOAT, body);
    }
    public static Renderable EMBED(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.embed.render(html, attrs, body);
    }
    public static Renderable EMBED(Object... body)
    {
        return (html) -> Element.embed.render(html, NOAT, body);
    }
    public static Renderable FIELDSET(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.fieldset.render(html, attrs, body);
    }
    public static Renderable FIELDSET(Object... body)
    {
        return (html) -> Element.fieldset.render(html, NOAT, body);
    }
    public static Renderable FIGCAPTION(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.figcaption.render(html, attrs, body);
    }
    public static Renderable FIGCAPTION(Object... body)
    {
        return (html) -> Element.figcaption.render(html, NOAT, body);
    }
    public static Renderable FIGURE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.figure.render(html, attrs, body);
    }
    public static Renderable FIGURE(Object... body)
    {
        return (html) -> Element.figure.render(html, NOAT, body);
    }
    public static Renderable FOOTER(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.footer.render(html, attrs, body);
    }
    public static Renderable FOOTER(Object... body)
    {
        return (html) -> Element.footer.render(html, NOAT, body);
    }
    public static Renderable FONT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.font.render(html, attrs, body);
    }
    public static Renderable FONT(Object... body)
    {
        return (html) -> Element.font.render(html, NOAT, body);
    }
    public static Renderable FORM(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.form.render(html, attrs, body);
    }
    public static Renderable FORM(Object... body)
    {
        return (html) -> Element.form.render(html, NOAT, body);
    }
    public static Renderable H1(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.h1.render(html, attrs, body);
    }
    public static Renderable H1(Object... body)
    {
        return (html) -> Element.h1.render(html, NOAT, body);
    }
    public static Renderable H2(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.h2.render(html, attrs, body);
    }
    public static Renderable H2(Object... body)
    {
        return (html) -> Element.h2.render(html, NOAT, body);
    }
    public static Renderable H3(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.h3.render(html, attrs, body);
    }
    public static Renderable H3(Object... body)
    {
        return (html) -> Element.h3.render(html, NOAT, body);
    }
    public static Renderable H4(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.h4.render(html, attrs, body);
    }
    public static Renderable H4(Object... body)
    {
        return (html) -> Element.h4.render(html, NOAT, body);
    }
    public static Renderable H5(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.h5.render(html, attrs, body);
    }
    public static Renderable H5(Object... body)
    {
        return (html) -> Element.h5.render(html, NOAT, body);
    }
    public static Renderable H6(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.h6.render(html, attrs, body);
    }
    public static Renderable H6(Object... body)
    {
        return (html) -> Element.h6.render(html, NOAT, body);
    }
    public static Renderable HEAD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.head.render(html, attrs, body);
    }
    public static Renderable HEAD(Object... body)
    {
        return (html) -> Element.head.render(html, NOAT, body);
    }
    public static Renderable HEADER(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.header.render(html, attrs, body);
    }
    public static Renderable HEADER(Object... body)
    {
        return (html) -> Element.header.render(html, NOAT, body);
    }
    public static Renderable HGROUP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.hgroup.render(html, attrs, body);
    }
    public static Renderable HGROUP(Object... body)
    {
        return (html) -> Element.hgroup.render(html, NOAT, body);
    }
    public static Renderable HR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.hr.render(html, attrs, body);
    }
    public static Renderable HR(Object... body)
    {
        return (html) -> Element.hr.render(html, NOAT, body);
    }
    public static Renderable HTML(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.html.render(html, attrs, body);
    }
    public static Renderable HTML(Object... body)
    {
        return (html) -> Element.html.render(html, NOAT, body);
    }
    public static Renderable I(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.i.render(html, attrs, body);
    }
    public static Renderable I(Object... body)
    {
        return (html) -> Element.i.render(html, NOAT, body);
    }
    public static Renderable IFRAME(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.iframe.render(html, attrs, body);
    }
    public static Renderable IFRAME(Object... body)
    {
        return (html) -> Element.iframe.render(html, NOAT, body);
    }
    public static Renderable IMG(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.img.render(html, attrs, body);
    }
    public static Renderable IMG(Object... body)
    {
        return (html) -> Element.img.render(html, NOAT, body);
    }
    public static Renderable INPUT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.input.render(html, attrs, body);
    }
    public static Renderable INPUT(Object... body)
    {
        return (html) -> Element.input.render(html, NOAT, body);
    }
    public static Renderable INS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.ins.render(html, attrs, body);
    }
    public static Renderable INS(Object... body)
    {
        return (html) -> Element.ins.render(html, NOAT, body);
    }
    public static Renderable KBD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.kbd.render(html, attrs, body);
    }
    public static Renderable KBD(Object... body)
    {
        return (html) -> Element.kbd.render(html, NOAT, body);
    }
    public static Renderable KEYGEN(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.keygen.render(html, attrs, body);
    }
    public static Renderable KEYGEN(Object... body)
    {
        return (html) -> Element.keygen.render(html, NOAT, body);
    }
    public static Renderable LABEL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.label.render(html, attrs, body);
    }
    public static Renderable LABEL(Object... body)
    {
        return (html) -> Element.label.render(html, NOAT, body);
    }
    public static Renderable LEGEND(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.legend.render(html, attrs, body);
    }
    public static Renderable LEGEND(Object... body)
    {
        return (html) -> Element.legend.render(html, NOAT, body);
    }
    public static Renderable LI(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.li.render(html, attrs, body);
    }
    public static Renderable LI(Object... body)
    {
        return (html) -> Element.li.render(html, NOAT, body);
    }
    public static Renderable LINK(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.link.render(html, attrs, body);
    }
    public static Renderable LINK(Object... body)
    {
        return (html) -> Element.link.render(html, NOAT, body);
    }
    public static Renderable MAIN(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.main.render(html, attrs, body);
    }
    public static Renderable MAIN(Object... body)
    {
        return (html) -> Element.main.render(html, NOAT, body);
    }
    public static Renderable MAP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.map.render(html, attrs, body);
    }
    public static Renderable MAP(Object... body)
    {
        return (html) -> Element.map.render(html, NOAT, body);
    }
    public static Renderable MARK(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.mark.render(html, attrs, body);
    }
    public static Renderable MARK(Object... body)
    {
        return (html) -> Element.mark.render(html, NOAT, body);
    }
    public static Renderable MENU(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.menu.render(html, attrs, body);
    }
    public static Renderable MENU(Object... body)
    {
        return (html) -> Element.menu.render(html, NOAT, body);
    }
    public static Renderable MENUITEM(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.menuitem.render(html, attrs, body);
    }
    public static Renderable MENUITEM(Object... body)
    {
        return (html) -> Element.menuitem.render(html, NOAT, body);
    }
    public static Renderable META(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.meta.render(html, attrs, body);
    }
    public static Renderable META(Object... body)
    {
        return (html) -> Element.meta.render(html, NOAT, body);
    }
    public static Renderable METER(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.meter.render(html, attrs, body);
    }
    public static Renderable METER(Object... body)
    {
        return (html) -> Element.meter.render(html, NOAT, body);
    }
    public static Renderable NAV(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.nav.render(html, attrs, body);
    }
    public static Renderable NAV(Object... body)
    {
        return (html) -> Element.nav.render(html, NOAT, body);
    }
    public static Renderable NOINDEX(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.noindex.render(html, attrs, body);
    }
    public static Renderable NOINDEX(Object... body)
    {
        return (html) -> Element.noindex.render(html, NOAT, body);
    }
    public static Renderable NOSCRIPT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.noscript.render(html, attrs, body);
    }
    public static Renderable NOSCRIPT(Object... body)
    {
        return (html) -> Element.noscript.render(html, NOAT, body);
    }
    public static Renderable OBJECT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.object.render(html, attrs, body);
    }
    public static Renderable OBJECT(Object... body)
    {
        return (html) -> Element.object.render(html, NOAT, body);
    }
    public static Renderable OL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.ol.render(html, attrs, body);
    }
    public static Renderable OL(Object... body)
    {
        return (html) -> Element.ol.render(html, NOAT, body);
    }
    public static Renderable OPTGROUP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.optgroup.render(html, attrs, body);
    }
    public static Renderable OPTGROUP(Object... body)
    {
        return (html) -> Element.optgroup.render(html, NOAT, body);
    }
    public static Renderable OPTION(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.option.render(html, attrs, body);
    }
    public static Renderable OPTION(Object... body)
    {
        return (html) -> Element.option.render(html, NOAT, body);
    }
    public static Renderable OUTPUT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.output.render(html, attrs, body);
    }
    public static Renderable OUTPUT(Object... body)
    {
        return (html) -> Element.output.render(html, NOAT, body);
    }
    public static Renderable P(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.p.render(html, attrs, body);
    }
    public static Renderable P(Object... body)
    {
        return (html) -> Element.p.render(html, NOAT, body);
    }
    public static Renderable PARAM(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.param.render(html, attrs, body);
    }
    public static Renderable PARAM(Object... body)
    {
        return (html) -> Element.param.render(html, NOAT, body);
    }
    public static Renderable PICTURE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.picture.render(html, attrs, body);
    }
    public static Renderable PICTURE(Object... body)
    {
        return (html) -> Element.picture.render(html, NOAT, body);
    }
    public static Renderable PRE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.pre.render(html, attrs, body);
    }
    public static Renderable PRE(Object... body)
    {
        return (html) -> Element.pre.render(html, NOAT, body);
    }
    public static Renderable PROGRESS(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.progress.render(html, attrs, body);
    }
    public static Renderable PROGRESS(Object... body)
    {
        return (html) -> Element.progress.render(html, NOAT, body);
    }
    public static Renderable Q(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.q.render(html, attrs, body);
    }
    public static Renderable Q(Object... body)
    {
        return (html) -> Element.q.render(html, NOAT, body);
    }
    public static Renderable RP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.rp.render(html, attrs, body);
    }
    public static Renderable RP(Object... body)
    {
        return (html) -> Element.rp.render(html, NOAT, body);
    }
    public static Renderable RT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.rt.render(html, attrs, body);
    }
    public static Renderable RT(Object... body)
    {
        return (html) -> Element.rt.render(html, NOAT, body);
    }
    public static Renderable RUBY(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.ruby.render(html, attrs, body);
    }
    public static Renderable RUBY(Object... body)
    {
        return (html) -> Element.ruby.render(html, NOAT, body);
    }
    public static Renderable S(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.s.render(html, attrs, body);
    }
    public static Renderable S(Object... body)
    {
        return (html) -> Element.s.render(html, NOAT, body);
    }
    public static Renderable SAMP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.samp.render(html, attrs, body);
    }
    public static Renderable SAMP(Object... body)
    {
        return (html) -> Element.samp.render(html, NOAT, body);
    }
    public static Renderable SCRIPT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.script.render(html, attrs, body);
    }
    public static Renderable SCRIPT(Object... body)
    {
        return (html) -> Element.script.render(html, NOAT, body);
    }
    public static Renderable SECTION(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.section.render(html, attrs, body);
    }
    public static Renderable SECTION(Object... body)
    {
        return (html) -> Element.section.render(html, NOAT, body);
    }
    public static Renderable SELECT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.select.render(html, attrs, body);
    }
    public static Renderable SELECT(Object... body)
    {
        return (html) -> Element.select.render(html, NOAT, body);
    }
    public static Renderable SMALL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.small.render(html, attrs, body);
    }
    public static Renderable SMALL(Object... body)
    {
        return (html) -> Element.small.render(html, NOAT, body);
    }
    public static Renderable SOURCE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.source.render(html, attrs, body);
    }
    public static Renderable SOURCE(Object... body)
    {
        return (html) -> Element.source.render(html, NOAT, body);
    }
    public static Renderable SPAN(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.span.render(html, attrs, body);
    }
    public static Renderable SPAN(Object... body)
    {
        return (html) -> Element.span.render(html, NOAT, body);
    }
    public static Renderable STRONG(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.strong.render(html, attrs, body);
    }
    public static Renderable STRONG(Object... body)
    {
        return (html) -> Element.strong.render(html, NOAT, body);
    }
    public static Renderable STYLE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.style.render(html, attrs, body);
    }
    public static Renderable STYLE(Object... body)
    {
        return (html) -> Element.style.render(html, NOAT, body);
    }
    public static Renderable SUB(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.sub.render(html, attrs, body);
    }
    public static Renderable SUB(Object... body)
    {
        return (html) -> Element.sub.render(html, NOAT, body);
    }
    public static Renderable SUMMARY(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.summary.render(html, attrs, body);
    }
    public static Renderable SUMMARY(Object... body)
    {
        return (html) -> Element.summary.render(html, NOAT, body);
    }
    public static Renderable SUP(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.sup.render(html, attrs, body);
    }
    public static Renderable SUP(Object... body)
    {
        return (html) -> Element.sup.render(html, NOAT, body);
    }
    public static Renderable TABLE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.table.render(html, attrs, body);
    }
    public static Renderable TABLE(Object... body)
    {
        return (html) -> Element.table.render(html, NOAT, body);
    }
    public static Renderable TBODY(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.tbody.render(html, attrs, body);
    }
    public static Renderable TBODY(Object... body)
    {
        return (html) -> Element.tbody.render(html, NOAT, body);
    }
    public static Renderable TD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.td.render(html, attrs, body);
    }
    public static Renderable TD(Object... body)
    {
        return (html) -> Element.td.render(html, NOAT, body);
    }
    public static Renderable TEXTAREA(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.textarea.render(html, attrs, body);
    }
    public static Renderable TEXTAREA(Object... body)
    {
        return (html) -> Element.textarea.render(html, NOAT, body);
    }
    public static Renderable TFOOT(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.tfoot.render(html, attrs, body);
    }
    public static Renderable TFOOT(Object... body)
    {
        return (html) -> Element.tfoot.render(html, NOAT, body);
    }
    public static Renderable TH(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.th.render(html, attrs, body);
    }
    public static Renderable TH(Object... body)
    {
        return (html) -> Element.th.render(html, NOAT, body);
    }
    public static Renderable THEAD(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.thead.render(html, attrs, body);
    }
    public static Renderable THEAD(Object... body)
    {
        return (html) -> Element.thead.render(html, NOAT, body);
    }
    public static Renderable TIME(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.time.render(html, attrs, body);
    }
    public static Renderable TIME(Object... body)
    {
        return (html) -> Element.time.render(html, NOAT, body);
    }
    public static Renderable TITLE(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.title.render(html, attrs, body);
    }
    public static Renderable TITLE(Object... body)
    {
        return (html) -> Element.title.render(html, NOAT, body);
    }
    public static Renderable TR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.tr.render(html, attrs, body);
    }
    public static Renderable TR(Object... body)
    {
        return (html) -> Element.tr.render(html, NOAT, body);
    }
    public static Renderable TRACK(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.track.render(html, attrs, body);
    }
    public static Renderable TRACK(Object... body)
    {
        return (html) -> Element.track.render(html, NOAT, body);
    }
    public static Renderable U(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.u.render(html, attrs, body);
    }
    public static Renderable U(Object... body)
    {
        return (html) -> Element.u.render(html, NOAT, body);
    }
    public static Renderable UL(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.ul.render(html, attrs, body);
    }
    public static Renderable UL(Object... body)
    {
        return (html) -> Element.ul.render(html, NOAT, body);
    }
    public static Renderable VAR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.var.render(html, attrs, body);
    }
    public static Renderable VAR(Object... body)
    {
        return (html) -> Element.var.render(html, NOAT, body);
    }
    public static Renderable VIDEO(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.video.render(html, attrs, body);
    }
    public static Renderable VIDEO(Object... body)
    {
        return (html) -> Element.video.render(html, NOAT, body);
    }
    public static Renderable WBR(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.wbr.render(html, attrs, body);
    }
    public static Renderable WBR(Object... body)
    {
        return (html) -> Element.wbr.render(html, NOAT, body);
    }
    public static Renderable WEBVIEW(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
    {
        return (html) -> Element.webview.render(html, attrs, body);
    }
    public static Renderable WEBVIEW(Object... body)
    {
        return (html) -> Element.webview.render(html, NOAT, body);
    }
    //-- end generated code --
}
