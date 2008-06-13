/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.common.tools.TabLoader;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.data.*;
import org.labkey.api.util.Cache;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.*;


/**
 * Created by IntelliJ IDEA.
 * User: mbellew
 * Date: Nov 14, 2005
 * Time: 9:33:11 AM
 */

public class TypesController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(TypesController.class);

    public TypesController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public static class BeginAction extends SimpleViewAction
    {
        public BeginAction(){}
        public BeginAction(ViewContext c){setViewContext(c);}

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new GroovyView("/org/labkey/experiment/types/begin.gm", "Type Administration");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Experiment", new ActionURL(ExperimentController.BeginAction.class, getViewContext().getContainer()));
            root.addChild("Types", new ActionURL(TypesController.BeginAction.class, getViewContext().getContainer()));
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public static class ImportVocabularyAction extends FormViewAction<ImportVocabularyForm>
    {
        ModelAndView successView = null;
        
        public void validateCommand(ImportVocabularyForm target, Errors errors)
        {
        }

        public ModelAndView getView(ImportVocabularyForm form, boolean reshow, BindException errors) throws Exception
        {
            HttpView view = new GroovyView<ImportVocabularyForm>("/org/labkey/experiment/types/importVocabulary.gm",form);
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
                    String tsv = new String(bytes, "UTF-8");
                    Concept[] concepts = TypesController.readVocabularyTSV(tsv);
                    TypesController.importConcepts(name, concepts);

                    successView = new HtmlView("Import Complete",
                        "Successfully imported " + concepts.length + " concepts.<br>"+
                        "<a href=\"./findConcepts.view\">"+ PageFlowUtil.buttonImg("Search") + "</a>");
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
            root.addChild("Import Vocabulary", new ActionURL(ImportVocabularyAction.class, getViewContext().getContainer()));
            return root;
        }
    }
    

/*    
    @Jpf.Action
    public Forward importTypes(ImportTypeForm form) throws Exception
    {
        requiresAdmin();

        if ("POST".equals(getRequest().getMethod()))
        {
            TabLoader loader = new TabLoader(form.tsv, true);
            Map[] maps = (Map[])loader.load();
            PropertyDescriptor[] pds = null;
            List <String> errors = new LinkedList<String>();

            if (maps.length > 0)
            {
                if (!maps[0].containsKey(form.typeColumn))
                {
                    errors.add("column not found: " + form.typeColumn);
                }
                else
                {
                    pds = OntologyManager.importTypes(form.vocabulary + "#", form.typeColumn, maps, errors, getContainer(),false);
                }
                if (errors.size() == 0 && null != pds && pds.length > 0)
                {
                    TreeSet<String> domains = new TreeSet<String>();
                    for (PropertyDescriptor pd : pds)
                    {
                        DomainDescriptor[] ddArray = OntologyManager.getDomainsForProperty(pd.getPropertyURI(), getContainer());
                        for (int i=0;i< ddArray.length; i++)
                            domains.add(ddArray[i].getDomainURI());                        
                    }
                    // CONSIDER: intermediate success page?
                    if (domains.size() == 1)
                        HttpView.throwRedirect("typeDetails.view?type=" + PageFlowUtil.encode(domains.first()));
                    else
                    {
                        HttpView.throwRedirect("types.view");
                    }
                }
            }

            if (errors.size() == 0 && (null == pds || pds.length == 0))
                errors.add("No properties were successfully imported.");
            ActionErrors actionErrors = PageFlowUtil.getActionErrors(getRequest(), true);
            for (String error : errors)
                actionErrors.add("main", new ActionError("Error", error));
        }

        // GET/reshow
        GroovyView view = new GroovyView("/org/labkey/experiment/types/importType.gm");
        view.addObject("form", form);
        return includeView(new DialogTemplate(view));
    }


    @Jpf.Action
    public Forward importData(ImportDataForm form) throws Exception
    {
        requiresAdmin();
        String[] keys = new String[] {"dfplate","ptid", "visit"};

        if ("POST".equals(getRequest().getMethod()))
        {
            TabLoader loader = new TabLoader(form.tsv, true);
            PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(form.typeURI, getContainer());
            List <String> errors = new LinkedList<String>();

            // create columns to properties map
            HashSet<String> keysNotFound = new HashSet<String>(Arrays.asList(keys));
            HashMap<String,PropertyDescriptor> propMap = new HashMap<String,PropertyDescriptor>();
            HashSet<String> matched = new HashSet<String>();
            TabLoader.ColumnDescriptor[] cols = loader.getColumns();
            for (TabLoader.ColumnDescriptor c : cols)
            {
                String name = c.name.toLowerCase();
                keysNotFound.remove(name);
                PropertyDescriptor match = null;
                PropertyDescriptor matchURI = null;
                for (PropertyDescriptor pd : pds)
                {
                    if (pd.getName().equalsIgnoreCase(name))
                    {
                        match = pd;
                        break;
                    }
                    if (pd.getPropertyURI().endsWith("#" + name))
                        matchURI = pd;
                }

                if (null == match)
                    match = matchURI;
                if (null == match)
                    continue;

                if (matched.contains(match.getPropertyURI()))
                    errors.add("Property '" + match.getPropertyURI() + "' more than once.");
                matched.add(match.getPropertyURI());
                propMap.put(c.name, match);
                c.name = match.getPropertyURI();
            }

            for (String key : keysNotFound)
                errors.add("Did not find required key '" + key + "'.");

            // fix-up key names
            for (int i=0 ; i<keys.length ; i++)
            {
                if (propMap.containsKey(keys[i]))
                    keys[i] = propMap.get(keys[i]).getPropertyURI();
            }

            String typeShortName = form.typeURI;
            typeShortName = typeShortName.substring(typeShortName.indexOf('#') + 1);
            String lsidExpr = typeShortName + "." + getContainer().getRowId();
            for (String key : keys)
                lsidExpr += ".${" + key + "}";

            Map[] maps = (Map[])loader.load();

            try
            {
                ExperimentService.get().getSchema().getScope().beginTransaction();
                OntologyManager.insertTabDelimited(getContainer(), null, new OntologyManager.SubstImportHelper(lsidExpr), pds, maps, false);
                ExperimentService.get().getSchema().getScope().commitTransaction();
            }
            finally
            {
                ExperimentService.get().getSchema().getScope().closeConnection();
            }

            if (errors.size() == 0)
            {
                getResponse().getWriter().print("" + maps.length + " rows imported.");
                return null;
            }
            ActionErrors actionErrors = PageFlowUtil.getActionErrors(getRequest(), true);
            for (String error : errors)
                actionErrors.add("main", new ActionError("Error", error));
        }

        form.keys = StringUtils.join(keys, ',');
        // GET/reshow
        GroovyView view = new GroovyView("/org/labkey/experiment/types/importData.gm");
        view.addObject("form", form);
        return includeView(new DialogTemplate(view));
    }


	// UNDONE: delete action confirm page
	@Jpf.Action
    public Forward deleteData(DeleteForm form) throws Exception
    {
		requiresAdmin();

		if (form.deleteType)
		{
			OntologyManager.deleteType(form.getTypeURI(), getContainer());
		}
		else
		{
			OntologyManager.deleteObjectsOfType(form.getTypeURI(), getContainer());
		}

		HttpView.throwRedirect("types.view");
		return null;
	}


    public static class DeleteForm extends FormData
    {
        private String typeURI;
        private boolean deleteType;

        public String getTypeURI()
        {
            return typeURI;
        }

        public void setTypeURI(String typeURI)
        {
            this.typeURI = typeURI;
        }

        public boolean isDeleteType()
        {
            return deleteType;
        }

        public void setDeleteType(boolean deleteType)
        {
            this.deleteType = deleteType;
        }
    }

    static boolean notEmpty(String s)
    {
        return null != StringUtils.trimToNull(s);
    }

    static boolean isEmpty(String s)
    {
        return null == s || null == StringUtils.trimToNull(s);
    }
*/


    @RequiresPermission(ACL.PERM_ADMIN)
    public static class TypesAction extends SimpleViewAction
    {
        public TypesAction(){}
        public TypesAction(ViewContext c){setViewContext(c);}

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DomainDescriptor[] types = OntologyManager.getDomainDescriptors(getViewContext().getContainer());
            TreeMap<String,DomainDescriptor> locals = new TreeMap<String,DomainDescriptor>();
            TreeMap<String,DomainDescriptor> globals = new TreeMap<String,DomainDescriptor>();
            Container shared = ContainerManager.getSharedContainer();
            for (DomainDescriptor t : types)
            {
                if (null == t.getContainer() || t.getContainer().equals(shared))
                    globals.put(t.getName(), t);
                else
                    locals.put(t.getName(), t);
            }

            HttpView view = new GroovyView("/org/labkey/experiment/types/types.gm");
            view.addObject("locals", locals);
            view.addObject("globals", globals);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new BeginAction(getViewContext())).appendNavTrail(root);
            root.addChild("Defined Types", new ActionURL(TypesAction.class, getViewContext().getContainer()));
            return root;
        }
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


    @RequiresPermission(ACL.PERM_ADMIN)
    public static class TypeDetailsAction extends SimpleViewAction<TypeForm>
    {
        String typeName;

        public ModelAndView getView(TypeForm form, BindException errors) throws Exception
        {
            // UNDONE: verify container against Types table when we have a Types table
            typeName = StringUtils.trimToEmpty(form.getType());             

            PropertyDescriptor properties[] = new PropertyDescriptor[0];
            if (null != typeName)
                properties = OntologyManager.getPropertiesForType(typeName, getViewContext().getContainer());

            HttpView view = new GroovyView("/org/labkey/experiment/types/typeDetails.gm");
            view.addObject("typeName", typeName);
            view.addObject("properties", properties);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new TypesAction(getViewContext())).appendNavTrail(root);
            root.addChild("Type -- " + StringUtils.defaultIfEmpty(typeName,"unspecified"), new ActionURL(TypeDetailsAction.class, getViewContext().getContainer()));
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


    @RequiresPermission(ACL.PERM_READ)
    public static class FindConceptsAction extends SimpleViewAction<SearchForm>
    {
        public ModelAndView getView(SearchForm form, BindException errors) throws Exception
        {
            DbSchema expSchema = ExperimentService.get().getSchema();
            String concat = expSchema.getSqlDialect().getConcatenationOperator();
            String like = expSchema.getSqlDialect().getCaseInsensitiveLikeOperator();

            //noinspection unchecked
            Map<String,Object>[] rows = new HashMap[0];
            ArrayList<String> params = new ArrayList<String>();

            if (notEmpty(form.query) || notEmpty(form.concept) || notEmpty(form.semanticType))
            {
//          UNDONE: how are we distinguising 'concepts' now?
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
                            where = where + and + "P.SearchTerms " + like + " '%|' " + concat + " ? " + concat + " '%'";
                        }
                        else
                        {
                            where = where + and + "P.SearchTerms " + like + " '%|' " + concat + " ? " + concat + " '|%'";
                        }
                        and = " AND ";
                    }
                }

                if (notEmpty(form.concept))
                {
                    if (-1 != form.concept.indexOf('#'))
                        where += and + "( P.PropertyURI = ? OR P.ConceptURI = ?)";
                    else
                        where += and + "( P.Name = ?  OR P.PropertyURI " + like + " '%#' " + concat + " ?)";
                    params.add(form.concept);
                    params.add(form.concept);
                    and = " AND ";
                }

                if (notEmpty(form.semanticType))
                {
                    where += and + "P.SemanticType " + like + " '%|' " + concat + " ? " + concat + " '|%'";
                    params.add(form.semanticType);
                    //noinspection UnusedAssignment
                    and = " AND ";
                }

                String sql =
                        "SELECT P.PropertyURI, P.Name, P.Label, P.SearchTerms, P.SemanticType, P.ConceptURI, P.Description, BASE.ConceptURI AS C2, BASE2.ConceptURI AS C3, 0 AS Score, '' AS Path\n"+
                        "FROM exp.PropertyDescriptor P\n" +
                        "    LEFT OUTER JOIN exp.PropertyDescriptor BASE on P.ConceptURI = BASE.PropertyURI\n" +
                        "    LEFT OUTER JOIN exp.PropertyDescriptor BASE2 on BASE.ConceptURI = BASE2.PropertyURI\n" +
                        "WHERE " + where + "\n" +
                        "ORDER BY 1\n";

                //noinspection unchecked
                rows = (Map<String,Object>[])Table.executeQuery(ExperimentService.get().getSchema(), sql, params.toArray(), Map.class);
                System.err.println(sql);
                System.err.println(params.toString());
            }

            HashMap<String,String> parentMap = new HashMap<String,String>(rows.length * 2);
            String pr, c1, c2, c3;
            for (Map<String,Object> row : rows)
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
                ArrayList<String> path = new ArrayList<String>();
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

            HttpView view = new GroovyView("/org/labkey/experiment/types/findConcepts.gm");
            view.addObject("form", form);
            view.addObject("concepts", rows);
            view.addObject("selectScript", "window.alert(uri);");
            view.addObject("conceptScript", "window.location='?concept='+escape(uri);");
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new BeginAction(getViewContext())).appendNavTrail(root);
            root.addChild("Find Concepts", new ActionURL(FindConceptsAction.class, getViewContext().getContainer()));
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


    public static class ImportTypeForm
    {
        private String vocabulary;
        private String typeColumn;
        private String tsv;

        public String getVocabulary()
        {
            return vocabulary;
        }

        public void setVocabulary(String vocabulary)
        {
            this.vocabulary = vocabulary;
        }

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

            public String getTypeColumn()
        {
                return typeColumn;
        }

            public void setTypeColumn(String typeColumn)
        {
                this.typeColumn = typeColumn;
        }
    }



    public static class ImportDataForm
    {
        private String typeURI;
        private String tsv;
        private String keys;


        public String getTypeURI()
        {
            return typeURI;
        }

        public void setTypeURI(String typeURI)
        {
            this.typeURI = typeURI;
        }

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }

        public String getKeys() {
            return keys;
        }

        public void setKeys(String keys) {
            this.keys = keys;
        }
    }


    // this class is just so I can have a constructor
    public static class VocabularyDescriptor extends TabLoader.ColumnDescriptor
    {
        public VocabularyDescriptor(String name)
        {
            this.name = name;
            this.isProperty = true;
        }
    }


    public static Concept[] readVocabularyTSV(String tsv)
            throws Exception
    {
        BufferedReader r = new BufferedReader(new StringReader(tsv));
        TabLoader loader = new TabLoader(r, true, Concept.class);
        Concept[] concepts;
        concepts = (Concept[]) loader.load();
        return concepts;
    }


    public static void importConcepts(String prefix, Concept[] concepts)
            throws SQLException
    {
        DbSchema expSchema = ExperimentService.get().getSchema();
        String concat = expSchema.getSqlDialect().getConcatenationOperator();
        String like = expSchema.getSqlDialect().getCaseInsensitiveLikeOperator();

        Map propertyMap = Table.executeValueMap(ExperimentService.get().getSchema(),
                "SELECT PropertyURI, PropertyId FROM exp.PropertyDescriptor WHERE PropertyURI " + like + " ? " + concat + " '#%'",
                new Object[]{prefix}, null);

        try
        {
            int count = 0;

            expSchema.getScope().beginTransaction();
            for (Concept concept : concepts)
            {
                PropertyDescriptor pd = concept.toPropertyDescriptor(prefix);
                try
                {
                    Integer propertyId = (Integer) propertyMap.get(pd.getPropertyURI());
                    if (null == propertyId)
                    {
                        OntologyManager.insertPropertyDescriptor(pd);
                    }
                    else
                    {
                        pd.setPropertyId(propertyId.intValue());
                        OntologyManager.updatePropertyDescriptor(pd);
                    }
                }
                catch (SQLException x)
                {
                    if (x.getMessage().contains("UNIQUE KEY"))
                    {
                        System.err.println("UNIQUE KEY VIOLATION " + pd.getPropertyURI());
                        continue;
                    }
                    throw x;
                }

                if (++count == 1000)
                {
                    expSchema.getScope().commitTransaction();
                    expSchema.getScope().beginTransaction();
                    count = 0;
                }
            }
            expSchema.getScope().commitTransaction();
        }
        finally
        {
            expSchema.getScope().closeConnection();
            Cache.getShared().remove("Experiment-TypesController.getSemanticTypes");
        }
    }

    public static String[] getSemanticTypes()
    {
        ResultSet rs = null;
        try
        {
            String[] semanticTypes = (String[])Cache.getShared().get("Experiment-TypesController.getSemanticTypes");
            if (semanticTypes == null)
            {
                TreeMap<String,String> set = new TreeMap<String,String>();
                rs = Table.executeQuery(ExperimentService.get().getSchema(), "SELECT DISTINCT SemanticType FROM exp.PropertyDescriptor" , null);
                while (rs.next())
                {
                    String value = rs.getString(1);
                    if (null == value || 0 == value.length())
                        continue;
                    String[] types = value.split("\\|");
                    for (String type : types)
                    {
                        if (null == type || 0 == type.length())
                            continue;
                        set.put(type.toLowerCase(), type);
                    }
                }
                semanticTypes = set.values().toArray(new String[set.size()]);
                Cache.getShared().put("Experiment-TypesController.getSemanticTypes", semanticTypes, Cache.HOUR);
            }
            return semanticTypes;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);    
        }
    }


    public static class SearchForm
    {
        private String concept;
        private boolean prefixMatch = false;
        private String semanticType;
        private String query;

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
}
