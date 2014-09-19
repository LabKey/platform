/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.query.olap;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections15.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.query.olap.metadata.CachedCube;
import org.olap4j.OlapException;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Dimension;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Measure;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.MetadataElement;
import org.olap4j.metadata.NamedList;
import org.olap4j.metadata.Schema;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: matthew
 * Date: 11/5/13
 * Time: 10:32 AM
 *
 * This is a datastructure used to represent an MDX query in a JSON-like way
 */
public class QubeQuery
{
    enum OP
    {
        XINTERSECT,     // need to explain this one
        INTERSECT,
        UNION,
        CROSSJOIN,
        MEMBERS
    }

    // TODO explicitly separate the count distinct measure filters and the regular slice filters

    Cube cube;
    // pretty standard MDX query specification
    boolean showEmpty;
    QubeExpr onRows;
    QubeExpr onColumns;
    QubeExpr filters;               // for regular slices, e.g. [Species].[Homo Sapiens]
    // for magic count(distinct) handling
//    QubeExpr distinctMeasureFilters;       // for filtering the count distinct measure
    Level countDistinctLevel;       // [Subject].[Subject]
    Member countDistinctMember;     // [Measures].[ParticipantCount]
    Member countRowsMember;         // [Measures].[RowCount]

    // CONSIDER using List<QueryParseException>, but BindException is fine for now
    BindException errors = null;

    CaseInsensitiveHashMap<MetadataElement> uniqueNameMap = new CaseInsensitiveHashMap<>();

    public QubeQuery(Cube cube)
    {
        this.cube = cube;

        // infer likely measures... Should be declared somewhere, or annotated
        Measure rows=null, distinct=null;
        for (Measure m : cube.getMeasures())
        {
            if (m.getUniqueName().contains("Row"))
                rows = m;
            else if (m.getUniqueName().contains("Participant") || m.getUniqueName().contains("Subject") || m.getUniqueName().contains("Distinct"))
                distinct = m;
        }
        countRowsMember = rows;
        countDistinctMember = distinct;
    }


    Cube getCube()
    {
        return cube;
    }


    public static class QubeExpr
    {
        QubeExpr(OP op)
        {
            this.op = op;
        }
        QubeExpr(OP op, ArrayList<QubeExpr> arguments)
        {
            this.op = op;
            this.arguments = arguments;
        }
        final OP op;
        ArrayList<QubeExpr> arguments;
    }


    public static class QubeMembersExpr extends QubeExpr
    {
        QubeMembersExpr(Hierarchy h, Level l, BindException errors) throws BindException
        {
            super(OP.MEMBERS);

            if (null != h && null != l)
            {
                if (!h.getUniqueName().equals(l.getHierarchy().getUniqueName()))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Level is not in hierarchy: level=" + l.getUniqueName() + " hierarchy=" + h.getUniqueName());
                    throw errors;
                }
            }
            else if (null == h && null != l)
            {
                h = l.getHierarchy();
            }

            this.hierarchy = h;
            this.level = l;
        }
        Hierarchy hierarchy;
        Level level;

        // one of these should be non-null/true
        boolean membersMember;
        boolean childrenMember;
        Set<Member> membersSet;
        QubeExpr membersQuery;       // subset the memberlist based on intersection with this orthoganal query
        String sql;                  // SQL query with one column per level matching the keyExpression for each level
        JSONObject getData;
    }






    // cache Hierarchies
    Map<String,Hierarchy> _cubeHierarchyMap = new CaseInsensitiveMap<>();
    // cache Levels
    Map<String,Level> _cubeLevelMap = new CaseInsensitiveMap<>();

    void initMetadataCache()
    {
        if (!_cubeHierarchyMap.isEmpty())
            return;
        for (Dimension d : getCube().getDimensions())
        {
            if (d.getHierarchies().size() == 1)
            {
                Hierarchy h = d.getHierarchies().get(0);
                _cubeHierarchyMap.put(d.getName(),h);
                _cubeHierarchyMap.put(d.getUniqueName(), h);
            }
        }
        for (Hierarchy h : getCube().getHierarchies())
        {
            _cubeHierarchyMap.put(h.getName(),h);
            _cubeHierarchyMap.put(h.getUniqueName(),h);
            for (Level l : h.getLevels())
            {
                _cubeLevelMap.put(l.getUniqueName(),l);
            }
        }
    }



    Hierarchy _getHierarchy(String hierarchyName)
    {
        initMetadataCache();
        return _cubeHierarchyMap.get(hierarchyName);
    }

    Level _getLevel(String levelName, Hierarchy scope)
    {
        initMetadataCache();
        Level l = _cubeLevelMap.get(levelName);
        if (null != l || null == scope)
            return l;
        l = scope.getLevels().get(levelName);
        if (null != l)
            return l;
        return scope.getLevels().get(levelName);
    }


    Member _getMember(String memberUniqueName, Hierarchy h, Level l) throws OlapException
    {
        Object mde = uniqueNameMap.get(memberUniqueName);
        if (mde instanceof Member)
            return (Member)mde;

        List<Level> listOfLevels;
        if (null != l)
            listOfLevels = Collections.singletonList(l);
        else
            listOfLevels = h.getLevels();
        for (Level hl : listOfLevels)
        {
            List<Member> list = hl.getMembers();
            if (cube instanceof CachedCube)
            {
                Member m = ((NamedList<Member>)list).get(memberUniqueName);
                if (null != m)
                    return m;
            }
            else
            {
                for (Member m : list)
                    uniqueNameMap.put(m.getUniqueName(), m);
            }
        }

        mde = uniqueNameMap.get(memberUniqueName);
        if (mde instanceof Member)
            return (Member)mde;
        return null;
    }


    /* NAME Helpers */

    Path uniqueNametoPath(MetadataElement m) throws BindException
    {
        return uniqueNametoPath(m.getUniqueName());
    }

    Path uniqueNametoPath(String uniqueName) throws BindException
    {
        Path p = new Path();
        int start=0;
        while (start < uniqueName.length());
        {
            String part = null;
            if (uniqueName.charAt(start) == '[')
            {
                int end = uniqueName.indexOf(']');
                if (-1 == end)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Illegal name: " + uniqueName);
                    throw errors;
                }
                part = uniqueName.substring(start+1,end-1);
                if (end < uniqueName.length()-1 && uniqueName.charAt(end+1) != '.')
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Illegal name: " + uniqueName);
                    throw errors;
                }
                start = end+2;
            }
            else
            {
                int end = uniqueName.indexOf('.');
                if (-1 == end)
                    end = uniqueName.length();
                part = uniqueName.substring(start+1,end-1);
                start = end+1;
            }
            p = p.append(part);
        }
        return p;
    }

    String pathToUniqueName(Path path)
    {
        if (path.getName().equals("members") || path.getName().equals("children"))
        {
            String u = pathToUniqueName(path.getParent());
            return u + "." + path.getName();
        }
        else
        {
            return "[" + StringUtils.join(path,"].[") + "]";
        }
    }


    /*
     * JSON
     */

    public void fromJson(JSONObject json, BindException errors) throws OlapException, BindException
    {
        this.errors = errors;
        Object v = json.get("showEmpty");
        showEmpty = null==v ? false : (Boolean)ConvertUtils.convert(v.toString(), Boolean.class);
        onRows = parseJsonExpr(json.get("onRows"), OP.MEMBERS, OP.XINTERSECT);
        Object cols = null != json.get("onColumns") ? json.get("onColumns") : json.get("onCols");
        onColumns = parseJsonExpr(cols, OP.MEMBERS, OP.XINTERSECT);
        filters = parseJsonExpr(json.get("filter"), OP.MEMBERS, OP.XINTERSECT);

        Object countDistinctLevelNameSpec = json.get("countDistinctLevel");
        String countDistinctLevelName = null;
        if (countDistinctLevelNameSpec instanceof String)
        {
            countDistinctLevelName = (String)countDistinctLevelNameSpec;
        }
        else if (countDistinctLevelNameSpec instanceof Map && ((Map)countDistinctLevelNameSpec).get("uniqueName") instanceof String)
        {
            countDistinctLevelName = (String)((Map)countDistinctLevelNameSpec).get("uniqueName");
        }
        if (!StringUtils.isEmpty(countDistinctLevelName))
        {
            this.countDistinctLevel = _getLevel(countDistinctLevelName, null);
            if (null == this.countDistinctLevel)
                errors.reject(SpringActionController.ERROR_MSG, ("Could not find level: " + countDistinctLevelName));
        }
    }


    private QubeExpr parseJsonExpr(Object o, OP defaultOp, OP defaultArrayOperator) throws OlapException, BindException
    {
        if (null == o)
            return null;

        JSONObject expr = null;

        if (o instanceof JSONObject)
        {
            expr = (JSONObject)o;
            if (null == expr.get("operator"))
            {
                if (null != expr.get("membersQuery") || null != expr.get("members") || null==defaultOp)
                    expr.put("operator","MEMBERS");
                else
                    expr.put("operator",defaultOp.toString());
            }
        }
        else if (o instanceof JSONArray)
        {
            expr = new JSONObject();
            expr.put("operator", defaultArrayOperator.toString());
            expr.put("arguments", o);
        }
        return parseJsonObject(expr, OP.MEMBERS);
    }


    private QubeExpr parseJsonObject(Object o, OP defaultOp) throws OlapException, BindException
    {
        if (null == o)
            return null;
        if (!(o instanceof Map))
            throw new IllegalArgumentException();

        Map json = (Map)o;
        Object v = json.get("operator");
        OP operator = null==v ? defaultOp : OP.valueOf(v.toString());
        if (operator != OP.MEMBERS)
        {
            JSONArray array;
            ArrayList<QubeExpr> arguments = new ArrayList<>();
            v = json.get("arguments");
            if (null == v)
            {
                errors.reject(SpringActionController.ERROR_MSG, ("Operator with no arguments: " + operator));
                throw errors;
            }
            if (!(v instanceof JSONArray))
                array = new JSONArray(Collections.singletonList(v));
            else
                array = (JSONArray)v;
            for (int i=0 ; i<array.length() ; i++)
                arguments.add(parseJsonExpr(array.get(i),OP.MEMBERS, OP.XINTERSECT));
            return new QubeExpr(operator,arguments);
        }
        else
        {
            Hierarchy h = _getHierarchy(json);
            Level l = _getLevel(json, h);

            QubeMembersExpr e = new QubeMembersExpr(h,l,errors);
            Object membersObj = json.get("members");
            if (null != membersObj)
            {
                if (membersObj instanceof String)
                {
                    if ("members".equals(membersObj))
                        e.membersMember = true;
                    else if ("children".equals(membersObj))
                        e.childrenMember = true;
                    else
                    {
                        Member m = _getMember(membersObj, h, l);
                        if (null == m)
                        {
                            errors.reject(SpringActionController.ERROR_MSG, "Member not found: " + String.valueOf(membersObj));
                            throw errors;
                        }
                        e.membersSet = Collections.singleton(m);
                    }
                }
                else if (membersObj instanceof JSONArray)
                {
                    TreeSet<Member> set = new TreeSet<>(new CompareMetaDataElement());
                    JSONArray arr = (JSONArray) membersObj;
                    for (int i=0 ; i<arr.length() ; i++)
                    {
                        Member m = _getMember(arr.get(i), h, l);
                        if (null == m)
                        {
                            errors.reject(SpringActionController.ERROR_MSG, "Member not found: " + arr.get(i));
                            throw errors;
                        }
                        set.add(m);
                    }
                    e.membersSet = set;
                }
                else if (membersObj instanceof JSONObject)
                {
                    if (((Map)membersObj).containsKey("namedSet"))
                    {
                        // For now we're only expecting a single optional property in the json map, to use a previously
                        // saved named set substition for the members enumeration.
                        Object setName = ((Map) membersObj).get("namedSet");
                        if (setName == null || !(setName instanceof String) || setName.toString().equals(""))
                        {
                            errors.reject(SpringActionController.ERROR_MSG, "Could not parse namedSet for members property");
                            throw errors;
                        }
                        List<String> namedSet = QueryService.get().getNamedSet(setName.toString());
                        TreeSet<Member> set = new TreeSet<>(new CompareMetaDataElement());
                        for (String nsEntry : namedSet)
                        {
                            Member m = _getMember(nsEntry, h, l);
                            if (null == m)
                            {
                                errors.reject(SpringActionController.ERROR_MSG, "Member not found: " + nsEntry);
                                throw errors;
                            }
                            set.add(m);
                        }
                        e.membersSet = set;
                    }
                }
                else
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Could not parse members property");
                    throw errors;
                }
            }
            else if (null != json.get("membersQuery"))
            {
                Object membersQuery = json.get("membersQuery");
                if (!(membersQuery instanceof JSONObject))
                {
                    errors.reject("expected membersQuery to be an object found " + membersQuery.getClass().getSimpleName());
                    throw errors;
                }
                e.membersQuery = parseJsonExpr(membersQuery, OP.MEMBERS, OP.XINTERSECT);
            }
            else if (null != json.get("sql"))
            {
                // execution code needs to make sure user has permission to execute this query, if it is running as a service user
                e.sql = (String)json.get("sql");
            }
            else if (null != json.get("getData"))
            {
                // execution code needs to make sure user has permission to execute this query, if it is running as a service user
                e.getData = (JSONObject)json.get("getData");
            }
            else
            {
                // treat like membersObj:'membersObj'
                e.membersMember = true;
            }
            return e;
        }
    }


    Hierarchy _getHierarchy(Map json) throws BindException
    {
        String hierarchyName = (String)json.get("hierarchy");
        if (null == hierarchyName)
            return null;
        Hierarchy h = _getHierarchy(hierarchyName);
        if (null == h)
        {
            errors.reject(SpringActionController.ERROR_MSG, "Hierarchy not found: " + hierarchyName);
            throw errors;
        }
        return h;
    }


    Level _getLevel(Map json, Hierarchy scope) throws BindException
    {
        String levelName = (String)json.get("level");
        if (null != levelName)
        {
            Level l = _getLevel(levelName,scope);
            if (null != l)
                return l;

            Hierarchy h = _getHierarchy(levelName);
            String msg;
            if (null == h)
                msg = "Level not found: " + levelName;
            else
                msg = ("Level not found: " + levelName + ".  This looks like a hierarchyName.");
            errors.reject(SpringActionController.ERROR_MSG, msg);
            throw errors;
        }
        Object lnumO = json.get("lnum");
        if (null != lnumO)
        {
            int lnum = (Integer)ConvertUtils.convert(String.valueOf(lnumO), Integer.class);
            return scope.getLevels().get(lnum);
        }
        return null;
    }


    // String or Member
    Member _getMember(Object memberSpec, Hierarchy h, Level l) throws OlapException, BindException
    {
        if (memberSpec instanceof String)
            return _getMember((String)memberSpec, h, l);
        JSONObject json = (JSONObject)memberSpec;

        String uniqueName = null;
        if (json.get("uniqueName") instanceof String)
            uniqueName =  (String)json.get("uniqueName");
        else if (null != json.get("uname"))
        {
            JSONArray uname = (JSONArray)json.get("uname");
            Path path = new Path();
            for (Object o : uname.toArray())
                path = path.append(String.valueOf(o));
            uniqueName = pathToUniqueName(path);
        }

        if (null != uniqueName)
        {
            return _getMember(uniqueName, h, l);
        }
        else
        {
            errors.reject(SpringActionController.ERROR_MSG, "Member not found: " + String.valueOf(memberSpec));
            throw errors;
        }
    }


    public static class CompareMetaDataElement implements Comparator<MetadataElement>
    {
        @Override
        public int compare(MetadataElement o1, MetadataElement o2)
        {
            return o1.getUniqueName().compareTo(o2.getUniqueName());
        }
    }


    /*
     * TEST
     */
    public static class QueryTest extends Assert
    {
        String jsonString;
        String mdxExpected;

        QueryTest(String json, String mdx)
        {
            jsonString = json;
            mdxExpected = mdx;
            _tests.add(this);
        }

        public void run(Cube cube) throws OlapException, BindException
        {
            if (null == cube)
                throw new IllegalStateException("cube not provided");

            BindException errors = new NullSafeBindException(new Object(), "command");
            JSONObject o = new JSONObject(jsonString);
            Measure rows=null, distinct=null;
            for (Measure m : cube.getMeasures())
            {
                if (m.getUniqueName().contains("Row"))
                    rows = m;
                else if (m.getUniqueName().contains("Subject") || m.getUniqueName().contains("Participant"))
                    distinct = m;
            }
            QubeQuery qq = new QubeQuery(cube);
            qq.countRowsMember = rows;
            qq.countDistinctMember = distinct;
//            qq.countDistinctLevel = cube.getHierarchies().get("Subject").getLevels().get("Subject");
            qq.fromJson(o, errors);
            String mdx = (new MdxQueryImpl(qq, errors)).generateMDX();
            compare(this.mdxExpected, mdx);
        }
        void compare(String a, String b)
        {
            a = a.trim().replaceAll("\\s*,", ",").replaceAll("\\s+", " ");
            b = b.trim().replaceAll("\\s*,", ",").replaceAll("\\s+", " ");
            if (a.equals(b))
                return;
            System.err.println(a);
            System.err.println(b);
            int i = StringUtils.indexOfDifference(a,b);
            System.err.println(StringUtils.repeat(' ',i) + "^");
            assertEquals(a,b);
        }
    }
    static ArrayList<QueryTest> _tests = null;


    public static class TestCase extends Assert
    {
    void init()
    {
        if (_tests != null)
            return;
        _tests = new ArrayList<>();
    new QueryTest(
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','Adenovirus']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[Adenovirus]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest(
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','Adenovirus','VRC-HIVADV014-00-VP']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[Adenovirus].[VRC-HIVADV014-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest(
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[DNA]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest(
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest(
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Clade].[Clade]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Clade].[Clade].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Sample Type].[Sample Type]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Sample Type].[Sample Type].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Tier]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Tier].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Clade].[Name]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Clade].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Assay.Target Area','lnum':2}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Lab','lnum':1}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Subject.Race','lnum':0}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Subject.Race].[(All)].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Subject.Race','lnum':1}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Subject.Race].[Race].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Subject.Country','lnum':1}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Subject.Country].[Country].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Subject.Sex].[Sex]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Subject.Sex].[Sex].members ON ROWS\n" +
        "FROM [DataspaceCube]");
//  TODO: looks wrong [Vaccine.Type] is a hierarchy not a level!
//    new QueryTest("{'onRows':[{'level':'[Vaccine.Type]'}],'filter':[]}",
//        "SELECT\n" +
//        "[Measures].DefaultMember ON COLUMNS\n" +
//        ",  NON EMPTY [Vaccine.Type].members ON ROWS\n" +
//        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'[Vaccine.Type]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Vaccine.Type].members ON ROWS\n" +
        "FROM [DataspaceCube]");
//    new QueryTest("{'onRows':[{'level':'[Vaccine Component]'}],'filter':[]}",
//        "SELECT\n" +
//        "[Measures].DefaultMember ON COLUMNS\n" +
//        ",  NON EMPTY [Vaccine Component].members ON ROWS\n" +
//        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'[Vaccine Component]'}],'filter':[]}",
        "SELECT [Measures].DefaultMember ON COLUMNS, NON EMPTY [Vaccine Component.Vaccine Insert].members ON ROWS FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env','gp140']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env].[gp140]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','gag']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[gag]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +


        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env','gp145']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env].[gp145]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','gag','gag']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[gag].[gag]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','pol']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[pol]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','nef','nef']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[nef].[nef]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','nef']}]}}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[nef]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','#null']}]}},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[#null])),Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))),Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Study','members':'members'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ", [Study].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Study','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Subject','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Subject].[Subject].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Subject].[Subject].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Study].members ON ROWS\n" +
        "FROM [DataspaceCube]");
    }


        // REQUIRES CDS selenium test to be run first, so this is not part of the Junit suite
        public void parseTest(Container c, User u) throws Exception
        {
            OlapSchemaDescriptor d = ServerManager.getDescriptor(c, "CDS:/CDS");
            Schema s = d.getSchema(d.getConnection(c, u), c, u, "CDS");
            Cube cube = s.getCubes().get("DataspaceCube");

            init();
            for (QueryTest t : _tests)
            {
                if (null == t)
                    continue; //???
                try
                {
                    t.run(cube);
                }
                catch (Exception x)
                {
                    throw new AssertionError(t.jsonString, x);
                }
            }
        }

        public void parseAdHoc(Container c, User u) throws Exception
        {
            OlapSchemaDescriptor d = ServerManager.getDescriptor(c, "CDS:/CDS");
            Schema s = d.getSchema(d.getConnection(c, u), c, u, "CDS");
            Cube cube = s.getCubes().get("DataspaceCube");

            init();

            QueryTest t = new QueryTest(
                    "{\"showEmpty\":false,\"onRows\":[{\"hierarchy\":\"Antigen.Tier\",\"members\":\"members\"}],\"filter\":[{\"operator\":\"INTERSECT\",\"arguments\":[{\"hierarchy\":\"Subject\",\"membersQuery\":{\"hierarchy\":\"Antigen.Tier\",\"members\":[{\"uname\":[\"Antigen.Tier\",\"1B\",\"DJ263.8\"]}]}},{\"hierarchy\":\"Subject\",\"membersQuery\":{\"hierarchy\":\"Antigen.Tier\",\"members\":[{\"uname\":[\"Antigen.Tier\",\"1A\",\"SF162.LS\"]}]}}]},{\"operator\":\"INTERSECT\",\"arguments\":[{\"hierarchy\":\"Subject\",\"membersQuery\":{\"hierarchy\":\"Antigen.Tier\",\"members\":[{\"uname\":[\"Antigen.Tier\",\"1B\"]}]}}]}]}",
                    "");

            try
            {
                t.run(cube);
            }
            catch (Exception x)
            {
                throw new AssertionError(t.jsonString, x);
            }
        }
    }
}
