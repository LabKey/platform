/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.experiment.types;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.DOM;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.B;
import static org.labkey.api.util.DOM.CODE;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.H2;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TBODY;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.THEAD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;


/**
 * User: mbellew
 * Date: Nov 14, 2005
 * Time: 9:33:11 AM
 */

public class TypesController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TypesController.class);

    public TypesController()
    {
        setActionResolver(_actionResolver);
    }


    @RequiresPermission(AdminPermission.class)
    public static class BeginAction extends SimpleViewAction
    {
        @SuppressWarnings({"UnusedDeclaration"}) // Constructed via reflection
        public BeginAction(){}

        public BeginAction(ViewContext c){setViewContext(c);}

        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            JspView jspView = new JspView("/org/labkey/experiment/types/begin.jsp");
            jspView.setTitle("Type Administration");
            return jspView;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Experiment", new ActionURL(ExperimentController.BeginAction.class, getContainer()));
            root.addChild("Types", new ActionURL(TypesController.BeginAction.class, getContainer()));
        }
    }


    public static class RepairForm
    {
        public String uri;
        public Domain domain;
        public StorageProvisioner.ProvisioningReport.DomainReport report;

        public String getDomainUri()
        {
            return uri;
        }

        public void setDomainUri(String uri)
        {
            this.uri = uri;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class RepairAction extends FormViewAction<RepairForm>
    {
        @Override
        public ModelAndView getView(RepairForm form, boolean reshow, BindException errors)
        {
            validateCommand(form, errors);
            StorageProvisioner.ProvisioningReport report = StorageProvisioner.getProvisioningReport(form.getDomainUri());
            if (report.getProvisionedDomains().size() == 1)
                form.report = report.getProvisionedDomains().iterator().next();
            return new JspView<>("/org/labkey/experiment/types/repair.jsp", form, errors);
        }

        @Override
        public void validateCommand(RepairForm form, Errors errors)
        {
            if (null == form.getDomainUri())
                throw new NotFoundException();
            form.domain = PropertyService.get().getDomain(getContainer(), form.getDomainUri());
            if (null == form.domain)
                throw new NotFoundException();
        }

        @Override
        public boolean handlePost(RepairForm form, BindException errors)
        {
            return StorageProvisioner.repairDomain(form.domain.getContainer(), form.getDomainUri(), errors);
        }

        @Override
        public URLHelper getSuccessURL(RepairForm repairForm)
        {
            ActionURL u = getViewContext().cloneActionURL();
            u.replaceParameter("domainUri", repairForm.getDomainUri());
            return u;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    public static class TypesForm
    {
        private String _domainKind;

        public String getDomainKind()
        {
            return _domainKind;
        }

        public void setDomainKind(String domainKind)
        {
            _domainKind = domainKind;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class TypesAction extends SimpleViewAction<TypesForm>
    {
        @SuppressWarnings({"UnusedDeclaration"}) // Constructed via reflection
        public TypesAction(){}

        public TypesAction(ViewContext c){setViewContext(c);}

        @Override
        public ModelAndView getView(TypesForm form, BindException errors)
        {
            Container container = getContainer();
            Collection<? extends Domain> types = PropertyService.get().getDomains(container, getUser(), true);
            TypeBean bean = new TypeBean();
            bean.domainKind = form.getDomainKind();
            Container shared = ContainerManager.getSharedContainer();

            getContainer().getContainersFor(ContainerType.DataType.domainDefinitions);
            Container project = !container.isProject() ? container.getProject() : null;
            for (Domain t : types)
            {
                DomainKind kind = t.getDomainKind();
                if (kind != null)
                    bean.domainKinds.put(kind.getKindName(), kind);

                if (bean.domainKind == null || (kind != null && bean.domainKind.equals(kind.getKindName())))
                {
                    if (null == t.getContainer() || t.getContainer().equals(shared))
                        bean.globals.put(t.getName(), t);
                    else if (project != null && t.getContainer().equals(project))
                        bean.project.put(t.getName(), t);
                    else
                        bean.locals.put(t.getName(), t);
                }
            }

            if (bean.domainKind != null && !bean.domainKinds.containsKey(bean.domainKind))
                throw new NotFoundException("Domain kind not found: " + bean.domainKind);

            return new JspView<>("/org/labkey/experiment/types/types.jsp", bean);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            (new BeginAction(getViewContext())).addNavTrail(root);
            root.addChild("Defined Types", new ActionURL(TypesAction.class, getContainer()));
        }
    }


    public static class TypeBean
    {
        public TreeMap<String, Domain> locals = new TreeMap<>();
        public TreeMap<String, Domain> globals = new TreeMap<>();
        public TreeMap<String, Domain> project = new TreeMap<>();
        public TreeMap<String, DomainKind> domainKinds = new TreeMap<>();
        public String domainKind = null;
    }


    public static class TypeForm
    {
        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        String type;
    }


    @RequiresPermission(AdminPermission.class)
    public static class TypeDetailsAction extends SimpleViewAction<TypeForm>
    {
        public String typeName;
        public DomainDescriptor dd;
        public Domain d;
        public DomainKind kind;
        public List<PropertyDescriptor> properties = Collections.emptyList();

        @Override
        public ModelAndView getView(TypeForm form, BindException errors)
        {
            // UNDONE: verify container against Types table when we have a Types table
            typeName = StringUtils.trimToNull(form.getType());

            if (null != typeName)
            {
                dd = OntologyManager.getDomainDescriptor(typeName, getContainer());
                properties = OntologyManager.getPropertiesForType(typeName, getContainer());

                d = dd != null ? d = PropertyService.get().getDomain(dd.getDomainId()) : null;
                kind = d != null ? d.getDomainKind() : null;
            }

            return new JspView<>("/org/labkey/experiment/types/typeDetails.jsp", this);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            (new TypesAction(getViewContext())).addNavTrail(root);
            root.addChild("Type -- " + StringUtils.defaultIfEmpty(dd != null ? dd.getName() : typeName,"unspecified"), new ActionURL(TypeDetailsAction.class, getContainer()));
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class CheckResolveAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            List<String> objectURIs = new TableSelector(OntologyManager.getTinfoObject(), Set.of("ObjectURI"),
                    SimpleFilter.createContainerFilter(getContainer()), new Sort("ObjectId")).getArrayList(String.class);

            return new HtmlView("Check Resolve LSIDs",
                    DIV(H2("Resolving " + objectURIs.size() + " objects"),
                            TABLE(THEAD(
                                    TR(TD(B("Name")),
                                            TD(B("Query")),
                                            TD(B("Class")),
                                            TD(B("LSID")))),
                                    TBODY(objectURIs.stream().map(this::resolve)))));
        }

        // render a row in the table
        public DOM.Renderable renderRow(String objectUri, Identifiable obj)
        {
            if (obj == null)
            {
                return TR(cl("text-warning bg-warning"),
                        TD(B("Unresolved")),
                        TD(),
                        TD(),
                        TD(lsid(objectUri)));
            }
            else
            {
                ActionURL url = obj.detailsURL();
                QueryRowReference ref = obj.getQueryRowReference();
                return TR(
                        TD(url != null ? A(at(href, url), obj.getName()) : obj.getName()),
                        TD(ref != null ? A(at(href, ref.toExecuteQueryURL()), ref.toString()) : null),
                        TD(obj.getClass().getSimpleName()),
                        TD(lsid(objectUri)));
            }
        }


        public DOM.Renderable resolve(String objectURI)
        {
            try
            {
                Identifiable obj = LsidManager.get().getObject(objectURI);
                return renderRow(objectURI, obj);
            }
            catch (Exception e)
            {
                return TR(cl("text-danger bg-danger"),
                        TD("Error"),
                        TD(),
                        TD(),
                        TD(lsid(objectURI), ": ", e.getMessage()));
            }
        }

        public DOM.Renderable lsid(String lsid)
        {
            return CODE(cl("small"), lsid);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }
}
