/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
package org.labkey.search.umls;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.search.SearchService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.*;
import org.labkey.api.util.Filter;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.SimpleDocumentResource;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 8, 2010
 * Time: 5:27:20 PM
 */
public class RRF_Loader extends Job
{
    RRF_Reader _reader;
    PollingUtil.PollKey _pollStatus;
    DbSchema _umls;

    public RRF_Loader(File directory) throws IOException
    {
        _reader = new RRF_Reader(directory);
        _pollStatus = PollingUtil.createKey(null, null, this);

        JSONObject o = new JSONObject();
        o.put("count",0);
        o.put("status", "pending");
        o.put("done",false);
        _pollStatus.setJson(o);
    }


    PollingUtil.PollKey getPollKey()
    {
        return _pollStatus;
    }
    

    public void run()
    {
        try
        {
            _umls = DbSchema.get("umls");

            JSONObject o = new JSONObject();
            o.put("count",0);
            o.put("status", "running");
            o.put("done",false);
            _pollStatus.setJson(o);

            loadSqlTables();
            int count = loadTextIndex();

            o = new JSONObject();
            o.put("count",count);
            o.put("done",true);
            _pollStatus.setJson(o);
        }
        catch (SQLException x)
        {
            Logger.getLogger(RRF_Loader.class).debug(x.getMessage(),x);
            JSONObject o = new JSONObject();
            o.put("done",true);
            o.put("status","failed");
            o.put("message", x.getMessage());
            _pollStatus.setJson(o);
        }
        catch (InterruptedException x)
        {
            JSONObject o = new JSONObject();
            o.put("done",true);
            o.put("status","cancelled");
            _pollStatus.setJson(o);
        }
    }



    private void loadSqlTables() throws SQLException
    {
        try
        {
            String exec = _umls.getSqlDialect().execute(_umls, "dropIndexes", "");
            Table.execute(_umls, exec);
        }
        catch (SQLException x)
        {
            ExceptionUtil.logExceptionToMothership(null, x);    
        }

        try
        {
            load("MRREL");
            load("MRCONSO");
            load("MRDEF");
            load("MRSTY");
        }
        catch (SQLException x)
        {
            ExceptionUtil.logExceptionToMothership(null, x);
        }
        finally
        {
            try
            {
                String exec = _umls.getSqlDialect().execute(_umls, "createIndexes", "");
                Table.execute(_umls, exec);
            }
            catch (SQLException x)
            {
                ExceptionUtil.logExceptionToMothership(null, x);
            }
        }
    }



    void load(String name) throws SQLException
    {
        String integerTypeName = "integer"; // _umls.getSqlDialect().getIntType();
        TableInfo ti = _umls.getTable(name);
        int colCount = ti.getColumns().size();
        Iterator<String[]> it = _reader.iterator(name);

        // DELETE
        Table.execute(_umls, "DELETE FROM " + ti.toString());

        // POPULATE
        StringBuilder sbInsert = new StringBuilder();
        StringBuilder sbValues = new StringBuilder();
        sbInsert.append("INSERT INTO ").append(ti).append(" (");
        sbValues.append(" VALUES (");
        String comma = "";
        for (ColumnInfo col : ti.getColumns())
        {
            sbInsert.append(comma).append(col.getSelectName());
            if (col.getJavaClass() == Integer.TYPE || col.getJavaClass() == Integer.class)
                sbValues.append(comma).append("CAST(? AS " + integerTypeName + ")");
            else
                sbValues.append(comma).append("?");
            comma = ",";
        }
        sbInsert.append(") ");
        sbInsert.append(sbValues);
        sbInsert.append(")");

        String sqlInsert = sbInsert.toString();
        int count=0;
        ArrayList<Collection<String>> paramList = new ArrayList<Collection<String>>();
        while (it.hasNext())
        {
            String[] strs = it.next();
            ArrayList<String> params = new ArrayList<String>(colCount);
            for (int i=0 ; i<colCount ; i++)
                params.add(i<strs.length ? StringUtils.trimToNull(strs[i]) : null);
            
            paramList.add(params);
            count++;
            if (0 == (count % 1000))
            {
                Table.batchExecute(_umls, sqlInsert, paramList);
                paramList.clear();

                JSONObject o = new JSONObject();
                o.put("count", count);
                o.put("status", name);
                o.put("done", false);
                _pollStatus.setJson(o);
            }
            if (0 == (count % 50000))
            {
                Table.execute(_umls, _umls.getSqlDialect().getAnalyzeCommandForTable(ti.toString()));
            }
        }
        Table.batchExecute(_umls, sqlInsert, paramList);
        Table.execute(_umls, _umls.getSqlDialect().getAnalyzeCommandForTable(ti.toString()));
    }


    private int loadTextIndex() throws InterruptedException
    {
        SearchService ss = ServiceRegistry.get(SearchService.class);
        Container shared = ContainerManager.getSharedContainer();

        Iterator<Definition> defs = _reader.getDefinitions(null); // new Filter<RRF_Reader.Definition>(){ public boolean accept(RRF_Reader.Definition def) { return !"Y".equals(def.SUPPRESS); } });
        Iterator<SemanticType> types = _reader.getTypes(null);
        Iterator<ConceptName> names = _reader.getNames(new Filter<ConceptName>()
        {
            public boolean accept(ConceptName c)
            {
                return "ENG".equals(c.LAT); // && !"Y".equals(c.SUPPRESS);
            }
        });

        CaseInsensitiveHashSet nameSet = new CaseInsensitiveHashSet();
        ArrayList<NavTree> links = new ArrayList<NavTree>();

        RRF_Reader.MergeIterator concept = new RRF_Reader.MergeIterator(names, defs, types);
        int count = 0;
        while (concept.hasNext())
        {
            if (Thread.interrupted())
                throw new InterruptedException();
            String CUI = null;
            String STR = null;
            String preferredSTR = null;

            nameSet.clear();
            links.clear();

            StringBuilder sbSemanticTypes = new StringBuilder();
            StringBuilder sbDefinition = new StringBuilder();
            ArrayList list = concept.next();

            for (Object o : list)
            {
                if (o instanceof Definition)
                {
                    Definition d = (Definition) o;
                    if ("Y".equals(d.SUPPRESS))
                        continue;
                    CUI = d.CUI;
                    sbDefinition.append(d.DEF).append("\n");
                }

                if (o instanceof ConceptName)
                {
                    ConceptName n = (ConceptName) o;
                    if (!"Y".equals(n.SUPPRESS) && !StringUtils.isEmpty(n.STR))
                    {
                        if (null == preferredSTR && "Y".equals(n.ISPREF))
                            preferredSTR = n.STR;
                        if (null == STR)
                            STR = n.STR;
                    }
                }

                if (o instanceof SemanticType)
                {
                    SemanticType s = (SemanticType) o;
                    CUI = s.CUI;
                    sbSemanticTypes.append(s.STN).append(" ").append(s.STY).append("\n");

                    if (SemanticTree.geographicArea.STN.equals(s.STN) && preferredSTR != null)
                    {
                        links.add(new NavTree("map", "http://maps.google.com/maps?q=" + PageFlowUtil.encode(preferredSTR)));
                    }
                }
            }

            String title = CUI + " " + StringUtils.defaultString(preferredSTR, STR);
            String body = sbSemanticTypes.toString() + "\n" + sbDefinition.toString();

            Map<String, Object> map = new HashMap<String, Object>();
            map.put(SearchService.PROPERTY.categories.toString(), UmlsController.umlsCategory.toString());
            map.put(SearchService.PROPERTY.title.toString(), title);
            if (!links.isEmpty())
            {
                String nav = NavTree.toJS(links, null, false).toString();
                map.put(SearchService.PROPERTY.navtrail.toString(), nav);
            }
            SimpleDocumentResource r = new SimpleDocumentResource(
                    new Path("CUI", CUI),
                    "umls:" + CUI,
                    shared.getId(),
                    "text/plain", body.getBytes(),
                    new ActionURL(UmlsController.ConceptAction.class, shared).addParameter("cui", CUI),
                    map
            );

            count++;
            if (0 == (count % 1000))
            {
                JSONObject o = new JSONObject();
                o.put("status", "indexing");
                o.put("count", count);
                o.put("estimate", 2178000); // umls 2009
                _pollStatus.setJson(o);
                if (ss.isBusy())
                    try {ss.waitForIdle();}catch(InterruptedException x){};
            }
            ss.defaultTask().addResource(r, SearchService.PRIORITY.item);
        }
        return count;
    }
}
