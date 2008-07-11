/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Sep 24, 2007
 * Time: 4:14:47 PM
 *
 * This is one home brewed way to remote a simple interface.  For general purpose use and extensibility
 * consider using a standard XML remoting transport or other standard.
 */
public class RemoteControllerProxy
{
    static Logger _log = Logger.getLogger(RemoteControllerProxy.class);
    static HttpClient _client = new HttpClient(new MultiThreadedHttpConnectionManager());

    private RemoteControllerProxy() {}

    public static boolean login(String endpoint, String name, String password) throws IOException
    {
       // Test name/password
        return true;
    }

    public static Object getInterface(Class i, String endpoint, String name, String password) throws IOException
    {
        String authorization = null;
        if (name != null && password != null)
            authorization = new String(Base64.encodeBase64((name + ":" + password).getBytes("UTF-8")));
        return Proxy.newProxyInstance(i.getClassLoader(), new Class[] {i}, new _Proxy(i, endpoint, authorization));
    }


    public static Object getAsyncInterface(Class i, String endpoint, String name, String password) throws IOException
    {
        String authorization = new String(Base64.encodeBase64((name + ":" + password).getBytes("UTF-8")));
        return Proxy.newProxyInstance(i.getClassLoader(), new Class[] {i}, new _ProxyAsync(i, endpoint, authorization));
    }


    static public interface AsyncCallback
    {
        void onFailure(Throwable caught);
        void onSuccess(Object result);
    }


    static private class AsyncCallbackWrapper implements AsyncCallback
    {
        Object _callback;

        AsyncCallbackWrapper(Object callback) throws NoSuchMethodException
        {
            verifyCallback(callback);
            _callback = callback;
        }

        public void onFailure(Throwable caught)
        {
            try
            {
                _callback.getClass().getMethod("onFailure",Throwable.class).invoke(_callback, caught);
            }
            catch (Exception e)
            {
                throwRuntimeException(e);
            }
        }

        public void onSuccess(Object result)
        {
            try
            {
                _callback.getClass().getMethod("onSuccess",Object.class).invoke(_callback, result);
            }
            catch (Exception e)
            {
                throwRuntimeException(e);
            }
        }
    }


    // com.google.gwt.user.client.rpc.AsyncCallback works
    static void verifyCallback(Object o) throws NoSuchMethodException
    {
        Class c = o.getClass();
        c.getMethod("onFailure",Throwable.class);
        c.getMethod("onSuccess",Object.class);
    }


    private static class _Proxy implements java.lang.reflect.InvocationHandler
    {
        Class _interface;
        HttpHost _httpHost;
        HttpState _httpState;
        String _boundEndpoint;
        String _authorization;

        _Proxy(Class i, String endpoint, String authorization) throws URIException
        {
            String name = i.getName().substring(i.getPackage().getName().length()+1);
            name = name.substring(0,1).toLowerCase() + name.substring(1);
            if (!endpoint.endsWith("/"))
                endpoint += "/";
            _boundEndpoint = endpoint + name + ".post";
            _authorization = authorization;
            _httpHost = new HttpHost(new URI(endpoint));
            _httpState = new HttpState();
            _interface = i;
        }

        public Object invoke(Object proxy, Method m, Object[] args)
                throws Throwable
        {
            if (m.getName().equals("toString"))
                return super.toString();
            if (!m.getDeclaringClass().equals(_interface))
                return null;

            final Object[] result = new Object[1];

            _ProxyRemoteCall call = new _ProxyRemoteCall(m.getName(), args, new AsyncCallback()
            {
                public void onFailure(Throwable caught)
                {
                    result[0] = caught;
                }

                public void onSuccess(Object r)
                {
                    result[0] = r;
                }
            });
            call.run();
            if (result[0] instanceof Throwable)
            {
                ((Throwable)result[0]).printStackTrace(System.err);
                throw (Throwable)result[0];
            }
            return result[0];
        }



        class _ProxyRemoteCall implements Runnable
        {
            String _method;
            Object[] _argsDebug; 
            byte[] _argsEncoded; 
            AsyncCallback _callback;

            _ProxyRemoteCall(String method, Object[] args, AsyncCallback callback) throws IOException, NoSuchMethodException
            {
                 // encode the args here to avoid threading issues (deep copy)
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(buf);
                oos.writeObject(args);
                oos.close();

                _method = method;
                _argsEncoded = buf.toByteArray();
                _argsDebug = args;  // for debugging only
                _callback = callback;
            }


            public void run()
            {
                Object result;
                InputStream in = null;
                PostMethod post = null;
                try
                {
                    if (_log.isDebugEnabled())
                        _logDebug();

                    String methodUrl = _boundEndpoint + "?_method=" + _method;
                    post = new PostMethod(methodUrl);
                    if (null != _authorization)
                        post.setRequestHeader("Authorization", "Basic " + _authorization);
                    post.setRequestHeader("Content-Type","application/octet-stream");
                    post.setRequestEntity(new ByteArrayRequestEntity(_argsEncoded));
                    _client.executeMethod(null, post, _httpState);
                    if (post.getStatusCode() == HttpStatus.SC_OK)
                    {
                        in = post.getResponseBodyAsStream();
                        result = new ObjectInputStream(in).readObject();
                    }
                    else
                    {
                        result = new Exception(post.getStatusLine().toString() + " " + methodUrl);
                    }
                    post.releaseConnection();
                }
                catch (Throwable x)
                {
                    result = x;
                }
                finally
                {
                    if (in != null)
                        try {in.close();}catch(IOException x){}
                    if (post != null)
                        post.releaseConnection();
                }

                if (result instanceof Throwable)
                    _callback.onFailure((Throwable)result);
                else
                    _callback.onSuccess(result);
            }

            void _logDebug()
            {
                StringBuffer msg = new StringBuffer(100);
                msg.append("remote call: " ).append(_method).append("(");
                String and = "";
                for (Object o : _argsDebug)
                {
                    msg.append(and);
                    if (o instanceof String) msg.append('"');
                    msg.append(String.valueOf(o));
                    if (o instanceof String) msg.append('"');
                    and = ",";
                }
                msg.append(")");
                _log.debug(msg);
            }
        }
    }


    private static class _ProxyAsync extends _Proxy
    {
        _ProxyAsync(Class i, String endpoint, String authorization) throws URIException
        {
            super(i,endpoint,authorization);
        }

        public Object invoke(Object proxy, Method m, Object[] argsAsync)
                throws Throwable
        {
            if (m.getName().equals("toString"))
                return super.toString();
            if (!m.getDeclaringClass().equals(_interface))
                return null;

            Object[] args = new Object[argsAsync.length-1];
            System.arraycopy(argsAsync,0,args,0,args.length);
            Object callbackOBJ = argsAsync[argsAsync.length-1];
            AsyncCallback callback;
            if (callbackOBJ instanceof AsyncCallback)
                callback = (AsyncCallback)callbackOBJ;
            else
                callback = new AsyncCallbackWrapper(callbackOBJ);

            _ProxyRemoteCall call = new _ProxyRemoteCall(m.getName(), args, callback);
            new Thread(call).run();
            return null;
        }
    }


    public static void addCookie(Object p, String name, String value)
    {
        if (!(p instanceof Proxy))
            throw new IllegalArgumentException(p.getClass().getName());
        _Proxy proxy = (_Proxy)Proxy.getInvocationHandler(p);
        ArrayList<Cookie> cookies = new ArrayList<Cookie>();
        cookies.addAll(Arrays.asList(proxy._httpState.getCookies()));
        for (Iterator i = cookies.iterator(); i.hasNext() ; )
        {
            Cookie c = (Cookie)i.next();
            if (c.getName().equals(name))
                i.remove();
        }
        cookies.add(new Cookie(proxy._httpHost.getHostName(), name, value, "/", null, false));
        proxy._httpState.clearCookies();
        proxy._httpState.addCookies(cookies.toArray(new Cookie[cookies.size()]));
    }


    static private void throwRuntimeException(Throwable t) throws RuntimeException
    {
        Logger.getLogger(RemoteControllerProxy.class).error("Unexpected error", t);
        if (t instanceof RuntimeException)
            throw (RuntimeException)t;
        throw new RuntimeException(t);
    }
}