package org.labkey.api.miniprofiler;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MemTrackable;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: kevink
 */
@JsonPropertyOrder({"id", "url", "date", "duration", "root", "objects"})
public class RequestInfo implements AutoCloseable
{
    private static final AtomicLong NEXT_ID = new AtomicLong(0);

    private final long _id = NEXT_ID.incrementAndGet(); // CONSIDER: use guid instead
    private final String _url;
    private final Date _date = new Date();
    private final Timing _root;
    private final Map<String, Integer> _objects = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // The name field is set after RequestInfo is created -- as soon as the REQUEST_CONTROLLER and REQUEST_ACTION attributes are set.
    private String _name;

    /*package*/ Timing _current;

    // TODO: Get rid of request from here
    private transient HttpServletRequest _request;

    public RequestInfo(HttpServletRequest request)
    {
        _url = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        _current = _root = new Timing(this, null, "root");
        _request = request;
    }

    @Override
    public void close()
    {
        MemTracker.getInstance().requestComplete(_request);
        _request = null;
        _root.close();
    }

    public void addObject(Object object)
    {
        if (object != null)
        {
            String s;
            if (object instanceof MemTrackable)
                s = ((MemTrackable)object).toMemTrackerString();
            else
                s = object.getClass().getName();
            Integer count = _objects.get(s);
            _objects.put(s, count == null ? 1 : count.intValue() + 1);
            _current.addObject(s);
        }
    }

    /** Create new Timing step and set it as the current step. */
    protected Timing step(String name)
    {
        return new Timing(this, _current, name);
    }

    /** Create new timed CustomTiming and add it to the current Timing step. */
    protected CustomTiming custom(String category, String msg)
    {
        CustomTiming custom = new CustomTiming(_current, category, msg, null);
        _current.addCustomTiming(category, custom);
        return custom;
    }

    /** Create a completed CustomTiming and add it to the current Timing step. */
    protected void addCustomTiming(String category, long duration, String message, @Nullable String detailsUrl, @Nullable StackTraceElement[] stackTrace)
    {
        CustomTiming custom = new CustomTiming(_current, category, duration, message, detailsUrl, stackTrace);
        _current.addCustomTiming(category, custom);
    }

    public long getId()
    {
        return _id;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getUrl()
    {
        return _url;
    }

    public Date getDate()
    {
        return _date;
    }

    public Map<String, Integer> getObjects()
    {
        return Collections.unmodifiableMap(_objects);
    }

    public long getDuration()
    {
        return _root.getDuration();
    }

    public Timing getRoot()
    {
        return _root;
    }

}
