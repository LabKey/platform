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
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.olap4j.OlapException;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Dimension;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Measure;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.MetadataElement;
import org.olap4j.metadata.Schema;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Created with IntelliJ IDEA.
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
//    Level countDistinctLevel;       // [Participant].[Participant]
    Member countDistinctMember;     // [Measures].[ParticipantCount]
    Member countRowsMember;         // [Measures].[RowCount]

    // CONSIDER using List<QueryParseException>, but BindException is fine for now
    BindException errors = null;


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


    public class QubeMembersExpr extends QubeExpr
    {
        QubeMembersExpr(Hierarchy h, Level l) throws BindException
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
    }



    /*
     * MDX
     */


    // this class is an intermediary data structure between the input QueryExpr and final string MDX
    enum FN
    {
        Intersect, CrossJoin, Union,
        Filter,
        MemberSet
        {
            @Override
            public String toString()
            {
                return "{";
            }
        }
    }
    enum TYPE
    {
        set
    }

    private class _MDX
    {
        _MDX(FN fn, List<Object> arguments)
        {
            this.fn = fn;
            this.arguments = arguments;
        }
        _MDX(FN fn, Level level, List<Object> arguments)
        {
            this.fn = fn;
            this.level = level;
            this.arguments = arguments;
        }
        _MDX(FN fn, Level level, Object[] arguments)
        {
            this.fn = fn;
            this.level = level;
            this.arguments = new ArrayList(Arrays.asList(arguments));
        }

        FN  fn;
        Level level;
        List<Object> arguments;     // String,Member,_MDX
    }



    _MDX _toFilterExistsExpr(_MDX levelExpr, _MDX membersExpr, String andor, Member measure)
    {
        String op = " " + andor + " ";
        String opConnector = "";
        StringBuilder filterExpr = new StringBuilder();
        for (int m=0 ; m<membersExpr.arguments.size() ; m++)
        {
            String term = "NOT ISEMPTY(" + this._toSetString(membersExpr.arguments.get(m)) + ")";
            filterExpr.append(opConnector).append(term);
            opConnector = op;
        }
        return new _MDX(FN.Filter, levelExpr.level, new Object[]{levelExpr,filterExpr});
    }


    _MDX _toMembersExpr(QubeExpr e) throws BindException
    {
        QubeMembersExpr membersDef = (QubeMembersExpr)e;
        if (membersDef.childrenMember || membersDef.membersMember)
        {
            if (null != membersDef.level && membersDef.membersMember)
                return new _MDX(FN.MemberSet, membersDef.level, new Object[] {membersDef.level.getUniqueName() + ".members"});
            else if (null != membersDef.hierarchy)
                return new _MDX(FN.MemberSet, null, new Object[] {membersDef.hierarchy.getUniqueName() + ".members"});
            errors.reject(SpringActionController.ERROR_MSG, "unexpected members expression");
            throw errors;
        }
        else if (null != membersDef.membersSet && !membersDef.membersSet.isEmpty())
        {
            return new _MDX(FN.MemberSet, membersDef.level, membersDef.membersSet.toArray());
        }
        else
        {
            if (null == membersDef.level && null == membersDef.hierarchy)
            {
                errors.reject(SpringActionController.ERROR_MSG, "level or hiearchy must be specified");
                throw errors;
            }
            Level l = null != membersDef.level ? membersDef.level : membersDef.hierarchy.getLevels().get(membersDef.hierarchy.getLevels().size()-1);
            _MDX levelExpr = new _MDX(FN.MemberSet, l, new Object[] {l.getUniqueName() + ".members"});
            if (null == membersDef.membersQuery)
                return levelExpr;
            _MDX membersExpr = this._processExpr(membersDef.membersQuery);
            return this._toFilterExistsExpr(levelExpr, membersExpr, "OR", this.countRowsMember);
        }
    }

    _MDX _toIntersectExpr(QubeExpr expr) throws BindException
    {
        List<Object> sets = new ArrayList<>();
        for (int e=0 ; e<expr.arguments.size() ; e++)
        {
            _MDX set = this._processExpr(expr.arguments.get(e));
            sets.add(set);
        }
        if (sets.size() == 1)
            return (_MDX)sets.get(0);
        else
        {
            Level level = ((_MDX)sets.get(0)).level;
            for (int s=0 ; s<sets.size() ; s++)
                if (level != ((_MDX)sets.get(s)).level)
                    level = null;
            return new _MDX(FN.Intersect, level, sets);
        }
    }


    // smart cross-join: intersect within level, crossjoin across levels
    _MDX _toSmartCrossJoinExpr(QubeExpr expr) throws BindException
    {
        Map<String,List<Object>> setsByLevel = new TreeMap<>();
        for (int e=0 ; e<expr.arguments.size() ; e++)
        {
            _MDX set = this._processExpr(expr.arguments.get(e));
            String key = null != set.level ? set.level.getUniqueName() : "-";
            if (!setsByLevel.containsKey(key))
                setsByLevel.put(key, new ArrayList<>());
            setsByLevel.get(key).add(set);
        }
        List<Object> sets = new ArrayList<>(setsByLevel.size());
        for (String k : setsByLevel.keySet())
        {
            List<Object> arr = setsByLevel.get(k);
            if (arr.size() == 1)
                sets.add(arr.get(0));
            else
                sets.add(new _MDX(FN.Intersect, ((_MDX) arr.get(0)).level, arr));
        }
        if (sets.size() == 1)
            return (_MDX)sets.get(0);
        else
            return new _MDX(FN.CrossJoin, sets);
    }


    _MDX _toCrossJoinExpr(QubeExpr expr) throws BindException
    {
        List<Object> sets = new ArrayList<>();
        for (int e=0 ; e<expr.arguments.size() ; e++)
        {
            _MDX set = this._processExpr(expr.arguments.get(e));
            sets.add(set);
        }
        if (sets.size() == 1)
            return (_MDX)sets.get(0);
        else
            return new _MDX(FN.CrossJoin, sets);
    }


    _MDX _toUnionExpr(QubeExpr expr) throws BindException
    {
        List<Object> sets = new ArrayList<>();
        for (int e=0 ; e<expr.arguments.size() ; e++)
        {
            _MDX set = this._processExpr(expr.arguments.get(e));
            if (set.fn==FN.MemberSet)
            {
                // flatten nested unions
                sets.addAll(set.arguments);
                for (int i=0 ; i<set.arguments.size() ; i++)
                    sets.add(set.arguments.get(i));
            }
            else
            {
                sets.add(set);
            }
        }
        if (sets.size() == 1)
            return (_MDX)sets.get(0);
        else
        {
            Level level = ((_MDX)sets.get(0)).level;
            for (int s=1 ; s<sets.size() ; s++)
                if (level != ((_MDX)sets.get(s)).level)
                    level = null;
            return new _MDX(FN.Union, level, sets);
        }
    }


    _MDX  _processExpr(QubeExpr expr) throws BindException
    {

//        if (Ext4.isArray(expr))
//            expr = {operator:(defaultArrayOperator || "UNION"), arguments:expr};
//        var op;
//        if (expr.operator)
//            op = expr.operator;
//        else if (expr.membersQuery || expr.members)
//            op = "MEMBERS";
//        else
//            op = defaultOperator || "MEMBERS";

        switch (expr.op)
        {
            case UNION:     return this._toUnionExpr(expr);
            case MEMBERS:   return this._toMembersExpr(expr);
            case INTERSECT: return this._toIntersectExpr(expr);
            case CROSSJOIN: return this._toCrossJoinExpr(expr);
            case XINTERSECT:return this._toSmartCrossJoinExpr(expr);
            default:
                errors.reject(SpringActionController.ERROR_MSG, "unexpected operator: " + expr.op);
                throw errors;
        }
    }


    String _toSetString(Object o)
    {
        if (o instanceof CharSequence)
            return o.toString();
        if (o instanceof MetadataElement)
            return ((MetadataElement)o).getUniqueName();

        _MDX expr = (_MDX)o;

        boolean binarySetFn = true;
        String start = expr.fn.name() + "(", end = ")";

        switch (expr.fn)
        {
//            if (expr.fn == "(")
//            {
//                start = "(";
//                binarySetFn = false;
//            }
            case MemberSet:
                start = "{"; end = "}";
                if (expr.arguments.size() == 1)
                    start = end = "";
                binarySetFn = false;
                break;
            case Intersect:
                binarySetFn = true;
                break;
            case Union:
                binarySetFn = true;
                break;
            case CrossJoin:
                binarySetFn = true;
                break;
            case Filter:
                break;
            default:
                assert false;
        }

        if (binarySetFn)
        {
            while (expr.arguments.size() > 2)
            {
                _MDX binary = new _MDX(expr.fn, Arrays.asList(expr.arguments.get(0), expr.arguments.get(1)));
                expr.arguments.set(0,binary);
                expr.arguments.remove(1);
            }
        }

        StringBuilder s = new StringBuilder(start);
        String comma = "";
        for (int a=0 ; a<expr.arguments.size() ; a++)
        {
            s.append(comma); comma=",";
            Object arg = expr.arguments.get(a);
            if (arg instanceof CharSequence)
                s.append((CharSequence)arg);
            else
                s.append(this._toSetString(arg));
        }
        s.append(end);
        return s.toString();
    }


    public String generateMDX(BindException errors) throws BindException
    {
        this.errors = errors;
        String rowset=null, columnset = null, filterset = null;
        if (null != onColumns)
            columnset = this._toSetString(this._processExpr(onColumns));
        if (null != onRows)
            rowset = this._toSetString(this._processExpr(onRows));
        if (null != filters && filters.arguments.size() > 0)
            filterset = this._toSetString(this._processExpr(filters));

        String countMeasure = "[Measures].DefaultMember";
        String withDefinition = "";
        if (null != filterset)
        {
            countMeasure = "[Measures]." + countDistinctMember.getName();
            withDefinition = "WITH SET ptids AS " + filterset + "\n" +
                    "MEMBER " + countMeasure + " AS " + "COUNT(ptids,EXCLUDEEMPTY)\n";
        }
        if (null == columnset)
            columnset = countMeasure;
        else
            columnset = "(" + columnset + " , " + countMeasure + ")";

        StringBuilder query = new StringBuilder(withDefinition + "SELECT\n" + "  "  + columnset + " ON COLUMNS");
        if (null != rowset)
            query.append(",\n" + (showEmpty ? "" : " NON EMPTY ") + rowset + " ON ROWS\n");
        query.append("\nFROM [" + getCube().getName() + "]\n");

        return query.toString();
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


    Member _getMember(String memberName, Hierarchy h, Level l) throws OlapException
    {
        /* TODO caching (maybe on Qube object) */
        List<Level> listOfLevels;
        if (null != l)
            listOfLevels = Collections.singletonList(l);
        else
            listOfLevels = h.getLevels();
        for (Level hl : listOfLevels)
        {
            List<Member> list = hl.getMembers();
            for (Member m : list)
                if (m.getUniqueName().equalsIgnoreCase(memberName))
                    return m;
        }
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
        onRows = parseJsonExpr(json.get("onRows"), OP.XINTERSECT, OP.XINTERSECT);
        Object cols = null != json.get("onColumns") ? json.get("onColumns") : json.get("onCols");
        onColumns = parseJsonExpr(cols, OP.XINTERSECT, OP.XINTERSECT);
        filters = parseJsonExpr(json.get("filter"), OP.XINTERSECT, OP.XINTERSECT);
//        distinctMeasureFilters = parseJsonExpr(json.get("distinctMeasureFilters"), OP.XINTERSECT, OP.XINTERSECT);
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

            QubeMembersExpr e = new QubeMembersExpr(h,l);
            if (null != json.get("members"))
            {
                if ("members".equals(json.get("members")))
                    e.membersMember = true;
                else if ("children".equals(json.get("children")))
                    e.childrenMember = true;
                else if (json.get("members") instanceof JSONArray)
                {
                    TreeSet<Member> set = new TreeSet<>(new CompareMetaDataElement());
                    JSONArray arr = (JSONArray)json.get("members");
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
                else
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Could not parse members property");
                    throw errors;
                }
            }
            else if (json.get("membersQuery") instanceof JSONObject)
            {
                e.membersQuery = parseJsonExpr(json.get("membersQuery"), OP.MEMBERS, OP.XINTERSECT);
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
        if (null != json.get(("uname")))
        {
            JSONArray uname = (JSONArray)json.get("uname");
            Path path = new Path();
            for (Object o : uname.toArray())
                path = path.append(String.valueOf(o));
            String uniqueName = pathToUniqueName(path);
            return _getMember(uniqueName, h, l);
        }
        errors.reject(SpringActionController.ERROR_MSG, "member not found: " + String.valueOf(memberSpec));
        throw errors;
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
                else if (m.getUniqueName().contains("Participant"))
                    distinct = m;
            }
            QubeQuery qq = new QubeQuery(cube);
            qq.countRowsMember = rows;
            qq.countDistinctMember = distinct;
//            qq.countDistinctLevel = cube.getHierarchies().get("Participant").getLevels().get("Participant");
            qq.fromJson(o, errors);
            String mdx = qq.generateMDX(errors);
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
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','Adenovirus']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[Adenovirus]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest(
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','Adenovirus','VRC-HIVADV014-00-VP']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[Adenovirus].[VRC-HIVADV014-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest(
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest(
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest(
        "{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine.Type','members':[{'uname':['Vaccine.Type','DNA','VRC-HIVDNA016-00-VP']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine.Type].[DNA].[VRC-HIVDNA016-00-VP]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine.Type','members':'members'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ", [Vaccine.Type].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Clade].[Clade]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Clade].[Clade].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Sample Type].[Sample Type]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Sample Type].[Sample Type].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Tier]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Tier].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Clade].[Name]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Clade].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Assay.Target Area','lnum':2}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Lab','lnum':1}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Participant.Race','lnum':0}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Participant.Race].[(All)].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Participant.Race','lnum':1}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Participant.Race].[Race].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Participant.Country','lnum':1}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Participant.Country].[Country].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Participant.Sex].[Sex]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Participant.Sex].[Sex].members ON ROWS\n" +
        "FROM [ParticipantCube]");
//  TODO: looks wrong [Vaccine.Type] is a hierarchy not a level!
//    new QueryTest("{'onRows':[{'level':'[Vaccine.Type]'}],'filter':[]}",
//        "SELECT\n" +
//        "[Measures].DefaultMember ON COLUMNS\n" +
//        ",  NON EMPTY [Vaccine.Type].members ON ROWS\n" +
//        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'[Vaccine.Type]'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ",  NON EMPTY [Vaccine.Type].members ON ROWS\n" +
        "FROM [ParticipantCube]");
//    new QueryTest("{'onRows':[{'level':'[Vaccine Component]'}],'filter':[]}",
//        "SELECT\n" +
//        "[Measures].DefaultMember ON COLUMNS\n" +
//        ",  NON EMPTY [Vaccine Component].members ON ROWS\n" +
//        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'[Vaccine Component]'}],'filter':[]}",
        "SELECT [Measures].DefaultMember ON COLUMNS, NON EMPTY [Vaccine Component.Vaccine Insert].members ON ROWS FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env','gp140']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env].[gp140]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','gag']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[gag]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env','gp145']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env].[gp145]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','gag','gag']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[gag].[gag]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','pol']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[pol]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','nef','nef']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[nef].[nef]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','nef']}]}}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[nef]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Vaccine Component.Vaccine Insert','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Vaccine Component.Vaccine Insert].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'hierarchy':'Study','lnum':0}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[(All)].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Study].[Study]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Study].[Study].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Assay.Target Area].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Assay.Target Area].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Lab].[Lab]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Lab].[Lab].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{'onRows':[{'level':'[Antigen.Tier].[Name]'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ",  NON EMPTY [Antigen.Tier].[Name].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Assay.Target Area','members':'members'}],'filter':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','#null']}]}},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[#null])),Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env]))),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Assay.Target Area].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Study','members':'members'}],'filter':[]}",
        "SELECT\n" +
        "[Measures].DefaultMember ON COLUMNS\n" +
        ", [Study].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    new QueryTest("{showEmpty:true, 'onRows':[{'hierarchy':'Study','members':'members'}],'filter':[{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Vaccine Component.Vaccine Insert','members':[{'uname':['Vaccine Component.Vaccine Insert','env']}]}}]},{'operator':'INTERSECT','arguments':[{'hierarchy':'Participant','membersQuery':{'hierarchy':'Assay.Target Area','members':[{'uname':['Assay.Target Area','Adaptive: humoral and B-cell']}]}}]}]}",
        "WITH SET ptids AS Intersect(Filter([Participant].[Participant].members,NOT ISEMPTY([Vaccine Component.Vaccine Insert].[env])),Filter([Participant].[Participant].members,NOT ISEMPTY([Assay.Target Area].[Adaptive: humoral and B-cell])))\n" +
        "MEMBER [Measures].ParticipantCount AS COUNT(ptids,EXCLUDEEMPTY)\n" +
        "SELECT\n" +
        "[Measures].ParticipantCount ON COLUMNS\n" +
        ", [Study].members ON ROWS\n" +
        "FROM [ParticipantCube]");
    }


        // REQUIRES CDS selenium test to be run first, so this is not part of the Junit suite
        public void parseTest(Container c, User u) throws Exception
        {
            OlapSchemaDescriptor d = ServerManager.getDescriptor(c, "CDS:/CDS");
            Schema s = d.getSchema(d.getConnection(c, u), c, u, "CDS");
            Cube cube = s.getCubes().get("ParticipantCube");

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
            Cube cube = s.getCubes().get("ParticipantCube");

            QueryTest t = new QueryTest(
                    "{\"query\":{\"showEmpty\":false,\"onRows\":[{\"hierarchy\":\"Antigen.Tier\",\"members\":\"members\"}],\"filter\":[{\"operator\":\"INTERSECT\",\"arguments\":[{\"hierarchy\":\"Participant\",\"membersQuery\":{\"hierarchy\":\"Antigen.Tier\",\"members\":[{\"uname\":[\"Antigen.Tier\",\"1B\",\"DJ263.8\"]}]}},{\"hierarchy\":\"Participant\",\"membersQuery\":{\"hierarchy\":\"Antigen.Tier\",\"members\":[{\"uname\":[\"Antigen.Tier\",\"1A\",\"SF162.LS\"]}]}}]},{\"operator\":\"INTERSECT\",\"arguments\":[{\"hierarchy\":\"Participant\",\"membersQuery\":{\"hierarchy\":\"Antigen.Tier\",\"members\":[{\"uname\":[\"Antigen.Tier\",\"1B\"]}]}}]}]},\"configId\":\"CDS:/CDS\",\"schemaName\":\"CDS\",\"cubeName\":\"ParticipantCube\"}",
                    "");

            init();
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
