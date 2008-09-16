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

package org.labkey.experiment.controllers.property;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.SchemaUpdateService;
import org.labkey.api.query.SchemaUpdateServiceRegistry;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;

public class PropertyController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(PropertyController.class);

    public PropertyController()
    {
        setActionResolver(_actionResolver);
    }
    
    @RequiresPermission(ACL.PERM_NONE)
    public class EditDomainAction extends SimpleViewAction<DomainForm>
    {
        private Domain _domain;

        public ModelAndView getView(DomainForm form, BindException errors) throws Exception
        {
            _domain = form.getDomain();
            if (null == _domain)
            {
                HttpView.throwNotFound();
            }
            if (null == _domain.getDomainKind() || !_domain.getDomainKind().canEditDefinition(getUser(), _domain))
            {
                HttpView.throwUnauthorized();
            }
            Map<String, String> props = new HashMap<String, String>();
            ActionURL returnURL = _domain.getDomainKind().urlEditDefinition(_domain);
            props.put("typeURI", _domain.getTypeURI());
            props.put("returnURL", returnURL.toString());
            props.put("allowFileLinkProperties", String.valueOf(form.getAllowFileLinkProperties()));
            props.put("allowAttachmentProperties", String.valueOf(form.getAllowAttachmentProperties()));

            // HttpView info = new JspView<ListDefinition>("editType.jsp", def);
            return new GWTView("org.labkey.experiment.property.Designer", props);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Edit Fields in " + _domain.getLabel());
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class PropertyServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new PropertyServiceImpl(getViewContext());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class GetDomainAction extends ApiAction<GetForm>
    {
        public ApiResponse execute(GetForm form, BindException errors) throws Exception
        {
            String queryName = form.getQueryName();
            String schemaName = form.getSchemaName();

            return convertDomainToApiResponse(getDomain(schemaName, queryName, getContainer(), getUser()));
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class SaveDomainAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm getForm, BindException errors) throws Exception
        {
            JSONObject jsonObj = getForm.getJsonObject();
            String schema = jsonObj.getString("schemaName");
            String query = jsonObj.getString("queryName");

            GWTDomain newDomain = convertJsonToDomain(jsonObj);

            GWTDomain originalDomain = getDomain(schema, query, getContainer(), getUser());

            DomainUtil.updateDomainDescriptor(originalDomain, newDomain, getContainer(), getUser());

            return new ApiSimpleResponse();
        }
    }

    @NotNull
    private static GWTDomain getDomain(String schema, String query, Container container, User user)
    {
        SchemaUpdateService service = SchemaUpdateServiceRegistry.get().getService(schema);
        if (service == null)
            throw new IllegalArgumentException("Schema '" + schema + "' is unsupported");

        String domainURI = service.getDomainURI(query, container, user);
        if (domainURI == null)
            throw new UnsupportedOperationException(query + " in " + schema + " does not support reading domain information");

        GWTDomain domain = DomainUtil.getDomainDescriptor(domainURI, container);

        if (domain == null)
            throw new RuntimeException("Could not find domain for " + domainURI);

        return domain;
    }

    public static class GetForm
    {
        private String schemaName;
        private String queryName;

        public String getQueryName() {return queryName;}
        public void setQueryName(String queryName) {this.queryName = queryName;}

        public String getSchemaName() {return schemaName;}
        public void setSchemaName(String schemaName) {this.schemaName = schemaName;}
    }

    class PropertyServiceImpl extends DomainEditorServiceBase implements org.labkey.experiment.property.client.PropertyService
    {
        public PropertyServiceImpl(ViewContext context)
        {
            super(context);
        }

        public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update)
        {
            try
            {
                return super.updateDomainDescriptor(orig, update);
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ApiResponse convertDomainToApiResponse(@NotNull GWTDomain domain)
    {
        ApiSimpleResponse response = new ApiSimpleResponse();
        try
        {
            response.putBean(domain, "domainId", "name", "domainURI", "description");
            List<GWTPropertyDescriptor> props = (List<GWTPropertyDescriptor>)domain.getPropertyDescriptors();
            response.putBeanList("fields", props);

        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    private static GWTDomain convertJsonToDomain(JSONObject obj) throws JSONException
    {
        GWTDomain domain = new GWTDomain();
        JSONObject jsonDomain = obj.getJSONObject("domainDesign");

        domain.setDomainId(jsonDomain.getInt("domainId"));

        domain.setName(jsonDomain.getString("name"));
        domain.setDomainURI(jsonDomain.getString("domainURI"));

        // Description can be null
        domain.setDescription((String)jsonDomain.getMap(true).get("description"));

        JSONArray jsonFields = jsonDomain.getJSONArray("fields");
        List<GWTPropertyDescriptor> props = new ArrayList<GWTPropertyDescriptor>();
        for (int i=0; i<jsonFields.length(); i++)
        {
            JSONObject jsonProp = jsonFields.getJSONObject(i);
            GWTPropertyDescriptor prop = convertJsonToPropertyDescriptor(jsonProp);
            props.add(prop);
        }

        Set<GWTPropertyDescriptor> requiredProps = new HashSet<GWTPropertyDescriptor>();
        for (GWTPropertyDescriptor prop : props)
        {
            if (prop.isRequired())
                requiredProps.add(prop);
        }

        domain.setPropertyDescriptors(props);
        domain.setRequiredPropertyDescriptors(requiredProps);

        return domain;
    }

    private static GWTPropertyDescriptor convertJsonToPropertyDescriptor(JSONObject obj) throws JSONException
    {
        GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
        prop.setName(obj.getString("name"));
        prop.setRangeURI(obj.getString("rangeURI"));

        // Other fields can be null
        Map<String,Object> map = obj.getMap(true);
        Integer propId = (Integer)map.get("propertyId");
        if (propId != null)
            prop.setPropertyId(propId.intValue());

        Boolean required = (Boolean)map.get("required");
        if (required != null)
            prop.setRequired(required.booleanValue());

        prop.setPropertyURI((String)map.get("propertyURI"));
        prop.setOntologyURI((String)map.get("ontologyURI"));
        prop.setDescription((String)map.get("description"));
        prop.setConceptURI((String)map.get("conceptURI"));
        prop.setLabel((String)map.get("label"));
        prop.setSearchTerms((String)map.get("searchTerms"));
        prop.setSemanticType((String)map.get("semanticType"));
        prop.setFormat((String)map.get("format"));
        prop.setLookupContainer((String)map.get("lookupContainer"));
        prop.setLookupSchema((String)map.get("lookupSchema"));
        prop.setLookupQuery((String)map.get("lookupQuery"));

        return prop;
    }
}
