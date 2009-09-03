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

package org.labkey.biotrue.objectmodel;

import org.labkey.biotrue.datamodel.Server;
import org.labkey.biotrue.datamodel.BtManager;
import org.labkey.biotrue.controllers.BtController;
import org.labkey.biotrue.soapmodel.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.apache.axis.client.Service;
import org.apache.axis.client.Call;
import org.apache.axis.encoding.ser.BeanSerializerFactory;
import org.apache.axis.encoding.ser.BeanDeserializerFactory;
import org.springframework.web.servlet.mvc.Controller;

import javax.xml.namespace.QName;
import java.io.File;
import java.sql.SQLException;

public class BtServer extends BtObject
{
    public Server _server;
    Service _service;

    public enum Method
    {
        Login,
        Logout,
        Browse,
        SOAP_Download
    }

    static public BtServer fromId(int id)
    {
        Server server = BtManager.get().getServer(id);
        if (server == null)
        {
            return null;
        }
        return new BtServer(server);
    }

    public BtServer(Server server)
    {
        _server = server;
    }

    public BtServer(Container container)
    {
        _server = new Server();
        _server.setContainer(container.getId());
    }

    public String getWsdlURL()
    {
        return _server.getWsdlURL();
    }

    public void setWsdlURL(String url)
    {
        _server.setWsdlURL(url);
    }

    public String getServiceNamespaceURI()
    {
        return _server.getServiceNamespaceURI();
    }

    public void setServiceNamespaceURI(String uri)
    {
        _server.setServiceNamespaceURI(uri);
    }

    public String getServiceLocalPart()
    {
        return _server.getServiceLocalPart();
    }

    public void setServiceLocalPart(String localPart)
    {
        _server.setServiceLocalPart(localPart);
    }

    public String getUserName()
    {
        return _server.getUserName();
    }

    public String getPassword()
    {
        return _server.getPassword();
    }

    public File getPhysicalRoot()
    {
        return new File(_server.getPhysicalRoot());
    }

    private void registerType(Service service, String localName, Class clazz)
    {
        QName qname = new QName(_server.getServiceNamespaceURI(), localName);
        service.getTypeMappingRegistry().getDefaultTypeMapping().register(clazz, qname, new BeanSerializerFactory(clazz, qname), new BeanDeserializerFactory(clazz, qname));
    }

    public Service getService() throws Exception
    {
        Service ret = new Service(_server.getWsdlURL(), new QName(_server.getServiceNamespaceURI(), _server.getServiceLocalPart()));
        registerType(ret, "browse_data", Browse_data.class);
        registerType(ret, "browse_request", Browse_request.class);
        registerType(ret, "browse_response", Browse_response.class);
        registerType(ret, "entityinfo", Entityinfo.class);
        registerType(ret, "download_data", Download_data.class);
        registerType(ret, "download_response", Download_response.class);
        registerType(ret, "login", Login.class);
        registerType(ret, "logout", Logout.class);
        registerType(ret, "logout_response", Logout_response.class);
        registerType(ret, "metadata", Metadata.class);
        return ret;
    }

    public Call getCall(Method method) throws Exception
    {
        Call call = (Call) getService().createCall(new QName(_server.getServiceNamespaceURI(), "cdms_browsePort"),
                qname(method));
        return call;
    }

    public QName qname(Method method)
    {
        return new QName(_server.getServiceNamespaceURI(), method.toString());
    }

    public int getRowId()
    {
        return _server.getRowId();
    }

    public Browse_response login() throws Exception
    {
        return loginBrowse(null);
    }

    public Browse_response loginBrowse(BtEntity entity) throws Exception
    {
        return (Browse_response) login(entity, "view");
    }

    public Object login(BtEntity entity, String op) throws Exception
    {
        Login login = new Login();
        login.setUser_name(_server.getUserName());
        login.setPassword(_server.getPassword());
        if (entity != null)
        {
            login.setInitial_ent(entity.getBioTrue_Ent());
            login.setInitial_id(entity.getBioTrue_Id());
            login.setInitial_mod("cdms");
            login.setInitial_op(op);
        }
        Call call = getCall(Method.Login);
        return call.invoke(new Object[] { login } );
    }

    public Logout_response logout(String sessionid) throws Exception
    {
        Logout logout = new Logout();
        logout.setSession_id(sessionid);
        Call call = getCall(Method.Logout);
        return (Logout_response) call.invoke(new Object[] { logout} );
    }

    public Browse_response browse(String sessionId, BtEntity data) throws Exception
    {
        Browse_request browse_request = new Browse_request();
        browse_request.setSession_id(sessionId);
        browse_request.setMod("dms");
        browse_request.setOp("view");
        if (data != null)
        {
            browse_request.setEnt(data.getBioTrue_Ent());
            browse_request.setId(Integer.valueOf(data.getBioTrue_Id()));
        }
        Call call = getCall(Method.Browse);
        return (Browse_response) call.invoke(new Object[] { browse_request });
    }

    public Download_response download(String sessionId, BtEntity data) throws Exception
    {
        Browse_request browse_request = new Browse_request();
        browse_request.setSession_id(sessionId);
        browse_request.setMod("dms");
        browse_request.setOp("download");
        browse_request.setEnt(data.getBioTrue_Ent());
        browse_request.setId(Integer.valueOf(data.getBioTrue_Id()));
        Call call = getCall(Method.SOAP_Download);
        return (Download_response) call.invoke(new Object[] { browse_request });
    }

    public File getTempDirectory()
    {
        File ret = new File(new File(_server.getPhysicalRoot()), "temp");
        ret.mkdir();
        return ret;
    }

    public String getName()
    {
        return _server.getName();
    }

    static public BtServer[] getForContainer(Container container)
    {
        try
        {
            Server[] servers = BtManager.get().getServers(container);
            BtServer[] ret = new BtServer[servers.length];
            for (int i = 0; i < servers.length; i ++)
            {
                ret[i] = new BtServer(servers[i]);
            }
            return ret;
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_server.getContainer());
    }

    public String getLabel()
    {
        return getName();
    }

    public ActionURL detailsURL()
    {
        return urlFor(BtController.ShowServerAction.class);
    }

    public ActionURL urlFor(Class<? extends Controller> actionClass)
    {
        ActionURL ret = new ActionURL(actionClass, getContainer());
        ret.addParameter("serverId", Integer.toString(getRowId()));
        return ret;
    }
}
