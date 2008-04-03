package org.labkey.api.data;

import org.labkey.api.util.UnexpectedException;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.sql.Statement;
import java.sql.SQLException;
import java.io.IOException;
import java.util.concurrent.Callable;

public class AsyncQueryRequest<T>
{
    static private final Logger _log = Logger.getLogger(AsyncQueryRequest.class);
    static private class CancelledException extends RuntimeException
    {
    }

    HttpServletResponse _response;
    HttpServletResponse _rootResponse;
    boolean _cancelled;
    Statement _statement;

    T _result;
    Throwable _exception;

    public AsyncQueryRequest(HttpServletResponse response)
    {
        _response = response;
        _rootResponse = response;
        while (_rootResponse instanceof HttpServletResponseWrapper)
        {
            _rootResponse = (HttpServletResponse) ((HttpServletResponseWrapper) _rootResponse).getResponse();
        }
    }

    synchronized public void setStatement(Statement statement)
    {
        if (_cancelled)
            throw new CancelledException();
        _statement = statement;
    }

    synchronized public T waitForResult(final Callable<T> callable) throws SQLException, IOException
    {
        Runnable runnable = new Runnable()
        {
            public void run()
            {
                try
                {
                    setResult(callable.call());
                }
                catch (Throwable t)
                {
                    setException(t);
                }
            }
        };

        Thread thread = new Thread(runnable, "AsyncQueryRequest: " + Thread.currentThread().getName());
        thread.start();
        while(true)
        {
            if (_result == null && _exception == null)
            {
                try
                {
                    wait(2000);
                }
                catch (InterruptedException ie)
                {
                    throw UnexpectedException.wrap(ie);
                }
            }
            if (_result != null)
            {
                return _result;
            }
            if (_exception != null)
            {
                if (_exception instanceof SQLException)
                {
                    SQLException sqlE = (SQLException) _exception;
                    SQLException sqlE2 = new SQLException(sqlE.getMessage(), sqlE.getSQLState(), sqlE.getErrorCode());
                    sqlE2.setNextException(sqlE);
                    throw sqlE2;
                }
                throw new UnexpectedException(_exception);
            }
            checkCancelled();
        }
    }

    synchronized public void setResult(T result)
    {
        _result = result;
        notify();
    }
    
    synchronized public void setException(Throwable exception)
    {
        _exception = exception;
        notify();
    }

    synchronized private void cancel()
    {
        _cancelled = true;
        if (_statement != null)
        {
            try
            {
                _statement.cancel();
            }
            catch (SQLException e)
            {
                _log.error("Error cancelling statement", e);
            }
        }
    }

    private void checkCancelled() throws IOException
    {
        try
        {
            _rootResponse.getWriter().write(" ");
            _rootResponse.flushBuffer();
        }
        catch (IOException ioe)
        {
            cancel();
            throw ioe;
        }
    }
}
