package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-02-01
 * Time: 12:00 PM
 */

public class SecurityLogger extends Logger
{
    private static final Logger _log = Logger.getLogger(SecurityManager.class);

    static SecurityLogger instance = new SecurityLogger("SecurityLogger");


    // PUBLIC


    public static Logger getLogger()
    {
        return instance;
    }


    public static void log(String msg, @Nullable UserPrincipal p, @Nullable SecurityPolicy sp, @Nullable Boolean result)
    {
        String full = indentMsg(msg) +
                (null==p?"":" " + p.getName()) +
                (null==sp?"":" "+sp.getResourceClass() + "/" +sp.getResourceId()) +
                (null==result?"":result?" TRUE":" FALSE");
        instance.debug(full);
    }

    public static void pushSecurityContext(String description, User user)
    {
        ArrayList<ThreadSecurityContext> a = threadsecuritycontexts.get();
        a.add(new ThreadSecurityContext(user,description,a.get(a.size()-1)));
        if (null != description)
            indent("<" + description + (null == user ? "" : " " + user.getName()) + ">");
    }


    public static void popSecurityContext()
    {
        ArrayList<ThreadSecurityContext> a = threadsecuritycontexts.get();
        if (a.size()==1)
            return;
        outdent();
        String desc = a.get(a.size()-1).description;
        if (null != desc)
            instance.debug("</" + desc + ">");
        a.remove(a.size()-1);
    }


    public static boolean indent(@Nullable String msg)
    {
        if (null != msg)
            instance.debug(msg);
        ArrayList<ThreadSecurityContext> a = threadsecuritycontexts.get();
        a.get(a.size()-1).indent++;
        return true;
    }


    public static boolean outdent()
    {
        ArrayList<ThreadSecurityContext> a = threadsecuritycontexts.get();
        if (a.get(a.size()-1).indent > 0)
            a.get(a.size()-1).indent--;
        return true;
    }



    // IMPL


    private SecurityLogger(String name)
    {
        super(name);
    }



    private static class ThreadSecurityContext
    {
        final User user;
        final String description;
        int indent=0;

        ThreadSecurityContext(User user, String desc, ThreadSecurityContext prev)
        {
            this.user = user;
            this.description = desc;
            if (null != prev)
            this.indent = prev.indent;
        }
    }


    static ThreadLocal<ArrayList<ThreadSecurityContext>> threadsecuritycontexts = new ThreadLocal<ArrayList<ThreadSecurityContext>>()
    {
        @Override
        protected ArrayList<ThreadSecurityContext> initialValue()
        {
            ArrayList<ThreadSecurityContext> ret = new ArrayList<ThreadSecurityContext>();
            ret.add(new ThreadSecurityContext(null,"<root>",null));
            return ret;
        }
    };


    private static Object indentMsg(Object msg)
    {
        ArrayList<ThreadSecurityContext> a = threadsecuritycontexts.get();
        int indent = a.get(a.size()-1).indent;
        if (0 >= indent)
            return msg;
        return StringUtils.repeat(' ', indent * 2) + String.valueOf(msg);
    }



    // LoggerWrapper

    // useful when trying to add/improve security logging (don't checkin superVerbose=true
    static boolean superVerbose = false;

    @Override
    public void debug(Object message)
    {
        if (_log.isDebugEnabled())
            if (superVerbose)
                _log.debug(indentMsg(message), new Throwable("here i am"));
            else
                _log.debug(indentMsg(message));
    }

    @Override
    public void debug(Object message, Throwable t)
    {
        if (_log.isDebugEnabled())
            _log.debug(indentMsg(message), t);
    }

    @Override
    public void error(Object message)
    {
        _log.error(indentMsg(message));
    }

    @Override
    public void error(Object message, Throwable t)
    {
        _log.error(indentMsg(message), t);
    }

    @Override
    public void fatal(Object message)
    {
        _log.fatal(indentMsg(message));
    }

    @Override
    public void fatal(Object message, Throwable t)
    {
        _log.fatal(indentMsg(message), t);
    }

    @Override
    public void info(Object message)
    {
        _log.info(message);
    }

    @Override
    public void info(Object message, Throwable t)
    {
        _log.info(indentMsg(message), t);
    }

    @Override
    public boolean isDebugEnabled()
    {
        return _log.isDebugEnabled();
    }

    @Override
    public boolean isEnabledFor(Priority level)
    {
        return _log.isEnabledFor(level);
    }

    @Override
    public void warn(Object message)
    {
        super.warn(indentMsg(message));
    }

    @Override
    public void warn(Object message, Throwable t)
    {
        super.warn(indentMsg(message), t);
    }
}
