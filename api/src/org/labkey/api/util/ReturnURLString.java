package org.labkey.api.util;

import org.labkey.api.data.ConvertHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.RedirectException;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 3, 2009
 * Time: 12:22:55 PM
 */
public class ReturnURLString extends HString
{
    public static ReturnURLString EMPTY = new ReturnURLString("",false)
    {
        @Override
        public boolean isEmpty()
        {
            return true;
        }

        @Override
        public int length()
        {
            return 0;
        }
    };

    
    public ReturnURLString(CharSequence s)
    {
        super(s);
    }


    public ReturnURLString(CharSequence s, boolean tainted)
    {
        super(s, tainted);
    }


    @Override
    public String getSource()
    {
        return super.getSource();
    }

    @Override
    public String toString()
    {
        if (!isTainted())
            return _source;
        if (null == _safe)
            _safe = PageFlowUtil.filter(_source);
        return _safe;
    }


    @Nullable
    public ActionURL getActionURL()
    {
        try
        {
            return new ActionURL(this);
        }
        catch (Exception x)
        {
            return null;
        }
    }


    @Nullable
    public URLHelper getURLHelper()
    {
        try
        {
            return new URLHelper(this);
        }
        catch (Exception x)
        {
            return null;
        }
    }


    public void throwRedirect() throws RedirectException
    {
        HttpView.throwRedirect(this);
    }


    public static class Converter implements org.apache.commons.beanutils.Converter
    {
		private static ConvertHelper.DateFriendlyStringConverter _impl = new ConvertHelper.DateFriendlyStringConverter();

        public Object convert(Class type, Object value)
        {
            if (value == null)
                return ReturnURLString.EMPTY;
            if (value instanceof ReturnURLString)
                return value;
            if (value instanceof CharSequence)
                return new ReturnURLString((CharSequence)value, true);
            return new ReturnURLString((String)_impl.convert(String.class, value), true);
        }
    }
}
