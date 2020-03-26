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
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.iterator.BeanIterator;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.DOM;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.CODE;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.H2;
import static org.labkey.api.util.DOM.LI;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.UL;
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

        public ModelAndView getView(Object o, BindException errors)
        {
            JspView jspView = new JspView("/org/labkey/experiment/types/begin.jsp");
            jspView.setTitle("Type Administration");
            return jspView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Experiment", new ActionURL(ExperimentController.BeginAction.class, getContainer()));
            root.addChild("Types", new ActionURL(TypesController.BeginAction.class, getContainer()));
            return root;
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
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class ImportVocabularyAction extends FormViewAction<ImportVocabularyForm>
    {
        ModelAndView successView = null;
        
        public void validateCommand(ImportVocabularyForm target, Errors errors)
        {
        }

        public ModelAndView getView(ImportVocabularyForm form, boolean reshow, BindException errors)
        {
            HttpView view = new JspView<>("/org/labkey/experiment/types/importVocabulary.jsp",form);
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return view;
        }

        public boolean handlePost(ImportVocabularyForm o, BindException errors) throws Exception
        {
            Map<String, MultipartFile> fileMap = getFileMap();

            if (!fileMap.isEmpty())
            {
                //noinspection unchecked
                Map.Entry<String, MultipartFile> entry = fileMap.entrySet().iterator().next();
                String name = entry.getKey();
                MultipartFile file = entry.getValue();
                byte[] bytes = file.getBytes();

                if (null != bytes && bytes.length > 0)
                {
                    String tsv = new String(bytes, StringUtilsLabKey.DEFAULT_CHARSET);
                    CloseableIterator<Concept> concepts = TypesController.readVocabularyTSV(tsv);
                    int count = TypesController.importConcepts(name, concepts);

                    successView = new HtmlView("Import Complete",
                        "Successfully imported " + count + " concepts.<br>" +
                                PageFlowUtil.button("Search").href("./findConcepts.view"));
                    return true;
                }
            }

            return false;
        }

        @Override
        public ModelAndView getSuccessView(ImportVocabularyForm importVocabularyForm)
        {
            return successView;
        }

        public ActionURL getSuccessURL(ImportVocabularyForm o)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new BeginAction(getViewContext())).appendNavTrail(root);
            root.addChild("Import Vocabulary", new ActionURL(ImportVocabularyAction.class, getContainer()));
            return root;
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

        public NavTree appendNavTrail(NavTree root)
        {
            (new BeginAction(getViewContext())).appendNavTrail(root);
            root.addChild("Defined Types", new ActionURL(TypesAction.class, getContainer()));
            return root;
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

        public NavTree appendNavTrail(NavTree root)
        {
            (new TypesAction(getViewContext())).appendNavTrail(root);
            root.addChild("Type -- " + StringUtils.defaultIfEmpty(dd != null ? dd.getName() : typeName,"unspecified"), new ActionURL(TypeDetailsAction.class, getContainer()));
            return root;
        }
    }


    private static boolean notEmpty(String s)
    {
        return s != null && s.length() > 0;
    }

    private static boolean isEmpty(String s)
    {
        return s == null || s.length() == 0;
    }


    @RequiresPermission(ReadPermission.class)
    public static class FindConceptsAction extends SimpleViewAction<SearchForm>
    {
        public ModelAndView getView(SearchForm form, BindException errors)
        {
            DbSchema expSchema = ExperimentService.get().getSchema();
            String like = expSchema.getSqlDialect().getCaseInsensitiveLikeOperator();

            //noinspection unchecked
            Map<String,Object>[] rows = new HashMap[0];
            List<String> params = new ArrayList<>();

            if (notEmpty(form.query) || notEmpty(form.concept) || notEmpty(form.semanticType))
            {
//          UNDONE: how are we distinguishing 'concepts' now?
//            String where = "P.DomainURI IS NULL";
//            String and = " AND ";
                String where = "";
                String and = "";

                if (notEmpty(form.query))
                {
                    String[] terms = form.query.split(" ");
                    for (String term : terms)
                    {
                        if (isEmpty(term))
                            continue;
                        params.add(term);
                        if (form.prefixMatch)
                        {
                            where = where + and + "P.SearchTerms " + like + " " + expSchema.getSqlDialect().concatenate("'%|'", "?", "'%'");
                        }
                        else
                        {
                            where = where + and + "P.SearchTerms " + like + " " + expSchema.getSqlDialect().concatenate("'%|'", "?", "'|%'");
                        }
                        and = " AND ";
                    }
                }

                if (notEmpty(form.concept))
                {
                    if (-1 != form.concept.indexOf('#'))
                        where += and + "( P.PropertyURI = ? OR P.ConceptURI = ?)";
                    else
                        where += and + "( P.Name = ?  OR P.PropertyURI " + like + " " + expSchema.getSqlDialect().concatenate("'%#'", "?") + ")";
                    params.add(form.concept);
                    params.add(form.concept);
                    and = " AND ";
                }

                if (notEmpty(form.semanticType))
                {
                    where += and + "P.SemanticType " + like + " " + expSchema.getSqlDialect().concatenate("'%|'", "?", "'|%'");
                    params.add(form.semanticType);
                    //noinspection UnusedAssignment
                    and = " AND ";
                }

                SQLFragment sql = new SQLFragment(
                        "SELECT P.PropertyURI, P.Name, P.Label, P.SearchTerms, P.SemanticType, P.ConceptURI, P.Description, BASE.ConceptURI AS C2, BASE2.ConceptURI AS C3, 0 AS Score, '' AS Path\n"+
                        "FROM exp.PropertyDescriptor P\n" +
                        "    LEFT OUTER JOIN exp.PropertyDescriptor BASE on P.ConceptURI = BASE.PropertyURI\n" +
                        "    LEFT OUTER JOIN exp.PropertyDescriptor BASE2 on BASE.ConceptURI = BASE2.PropertyURI\n" +
                        "WHERE " + where + "\n" +
                        "ORDER BY 1");
                sql.addAll(params);

                rows = new SqlSelector(ExperimentService.get().getSchema(), sql).getMapArray();
            }

            HashMap<String, String> parentMap = new HashMap<>(rows.length * 2);
            String pr, c1, c2, c3;

            for (Map<String, Object> row : rows)
            {
                pr = (String)row.get("PropertyURI");
                c1 = (String)row.get("ConceptURI");
                c2 = (String)row.get("C2");
                c3 = (String)row.get("C3");
                if (notEmpty(c1)) parentMap.put(pr, c1);
                if (notEmpty(c2)) parentMap.put(c1, c2);
                if (notEmpty(c3)) parentMap.put(c2, c3);
            }

            for (Map<String,Object> row : rows)
            {
                // SCORE
                int score = 0;
                String propertyURI = _toString((String)row.get("PropertyURI"));
                String name = _toString((String)row.get("Name")).toLowerCase();
                String searchTerms = _toString((String)row.get("SearchTerms")).toLowerCase();

                for (String p : params)
                {
                    if (name.equals(form.concept) || propertyURI.equals(form.concept))
                         score += 500;
                    else if (name.equals(p))
                        score += 104;
                    else if (name.startsWith(p))
                        score += 103;
                    else if (searchTerms.contains('|' + p + '|'))
                        score += 102;
                    else if (name.contains(p))
                        score += 101;
                    else if (searchTerms.contains('|' + p))
                        score += 100;
                }

                row.put("Score", score);

                // PATH
                String conceptURI = (String)row.get("PropertyURI");
                ArrayList<String> path = new ArrayList<>();
                while (null != (conceptURI = parentMap.get(conceptURI)))
                    path.add(0, conceptURI);
                row.put("Path", path);
            }

            Arrays.sort(rows, new CompareScore());

            if (rows.length > 1000)
            {
                errors.reject(ERROR_MSG, "Warning: Results truncated after 1000 hits.");
                //noinspection unchecked
                Map<String,Object>[] t = new Map[1000];
                System.arraycopy(rows, 0, t, 0, 1000);
                rows = t;
            }

            form.concepts = rows;
            return new JspView<>("/org/labkey/experiment/types/findConcepts.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new BeginAction(getViewContext())).appendNavTrail(root);
            root.addChild("Find Concepts", new ActionURL(FindConceptsAction.class, getContainer()));
            return root;
        }
    }

    
    static String _toString(String s)
    {
        if (null == s)
            return "";
        return s;
    }


    static class CompareScore implements Comparator<Map>
    {
        public int compare(Map a, Map b)
        {
            return (Integer)b.get("Score") - (Integer)a.get("Score");
        }
    }


    public static class ImportVocabularyForm
    {
        private String name;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }


    public static CloseableIterator<Concept> readVocabularyTSV(String tsv)
    {
        TabLoader loader = new TabLoader(tsv);
        return new BeanIterator<>(loader.iterator(), Concept.class);
    }


    public static int importConcepts(String prefix, CloseableIterator<Concept> concepts) throws SQLException, IOException
    {
        DbSchema expSchema = ExperimentService.get().getSchema();
        String like = expSchema.getSqlDialect().getCaseInsensitiveLikeOperator();

        int conceptCount = 0;

        try (DbScope.Transaction transaction = expSchema.getScope().ensureTransaction())
        {
            Map<String, Integer> propertyMap = new SqlSelector(ExperimentService.get().getSchema(),
                    "SELECT PropertyURI, PropertyId FROM exp.PropertyDescriptor WHERE PropertyURI " + like + " "
                    + expSchema.getSqlDialect().concatenate("?", "'#%'"), prefix).getValueMap();

            List<PropertyDescriptor> inserts = new ArrayList<>();
            List<PropertyDescriptor> updates = new ArrayList<>();

            while (concepts.hasNext())
            {
                conceptCount++;
                Concept concept =  concepts.next();
                PropertyDescriptor pd = concept.toPropertyDescriptor(prefix);
                Integer propertyId = propertyMap.get(pd.getPropertyURI());
                if (null == propertyId)
                {
                    inserts.add(pd);
                }
                else
                {
                    pd.setPropertyId(propertyId);
                    updates.add(pd);
                }
            }

            OntologyManager.insertPropertyDescriptors(inserts);
            OntologyManager.updatePropertyDescriptors(updates);
            transaction.commit();
        }

        SEMANTIC_TYPES_CACHE.remove("Experiment-TypesController.getSemanticTypes");
        concepts.close();

        OntologyManager.indexConcepts(null);

        return conceptCount;
    }

    private static final Cache<String, String[]> SEMANTIC_TYPES_CACHE = CacheManager.getSharedCache();

    public static String[] getSemanticTypes()
    {
        String[] semanticTypes = SEMANTIC_TYPES_CACHE.get("Experiment-TypesController.getSemanticTypes");

        if (semanticTypes == null)
        {
            final TreeMap<String,String> set = new TreeMap<>();

            new SqlSelector(ExperimentService.get().getSchema(), "SELECT DISTINCT SemanticType FROM exp.PropertyDescriptor").forEach(rs -> {
                String value = rs.getString(1);
                if (null == value || 0 == value.length())
                    return;
                String[] types = value.split("\\|");
                for (String type : types)
                {
                    if (null == type || 0 == type.length())
                        continue;
                    set.put(type.toLowerCase(), type);
                }
            });

            semanticTypes = set.values().toArray(new String[set.size()]);
            SEMANTIC_TYPES_CACHE.put("Experiment-TypesController.getSemanticTypes", semanticTypes, CacheManager.HOUR);
        }

        return semanticTypes;
    }


    public static class SearchForm
    {
        private String concept;
        private boolean prefixMatch = false;
        private String semanticType;
        private String query;

        public Map<String,Object>[] concepts;

        public String getQuery()
        {
            return query;
        }

        public void setQuery(String query)
        {
            this.query = query;
        }

        public String getSemanticType()
        {
            return semanticType;
        }

        public void setSemanticType(String semanticType)
        {
            this.semanticType = semanticType;
        }

        public String getConcept()
        {
            return concept;
        }

        public void setConcept(String concept)
        {
            this.concept = concept;
        }

        public boolean isPrefixMatch()
        {
            return prefixMatch;
        }

        public void setPrefixMatch(boolean prefixMatch)
        {
            this.prefixMatch = prefixMatch;
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
                            UL(objectURIs.stream().map(this::resolve))));
        }

        public DOM.Renderable resolve(String objectURI)
        {
            try
            {
                Identifiable obj = LsidManager.get().getObject(objectURI);
                if (obj == null)
                    return LI(cl("text-warning bg-warning"),
                            "Failed to resolve: ", lsid(objectURI));
                else
                {
                    ActionURL url = LsidManager.get().getDisplayURL(objectURI);
                    return LI("Resolved '", lsid(objectURI), "' to object (" + obj.getClass().getSimpleName() + "): ",
                            url != null ? A(at(href, url), obj.getName()) : obj.getName());
                }
            }
            catch (Exception e)
            {
                return LI(cl("text-danger bg-danger"),
                        "Error when resolving '", lsid(objectURI), "': ", e.getMessage());
            }
        }

        public DOM.Renderable lsid(String lsid)
        {
            return CODE(cl("small"), lsid);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

}
