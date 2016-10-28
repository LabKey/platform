/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.query.QueryService;
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
 * This is a data structure used to represent an MDX query in a JSON-like way
 */
public class QubeQuery
{
    enum OP
    {
        XINTERSECT,     // need to explain this one
        INTERSECT,
        UNION,
        EXCEPT,
        CROSSJOIN,
        MEMBERS
    }

    Cube cube;
    boolean showEmpty;
    QubeExpr onRows;
    QubeExpr onColumns;

    // for mdx queries
    QubeExpr sliceFilters;

    // for count distinct queries
    Level countDistinctLevel;       // [Subject].[Subject]
    boolean includeNullMemberInCount = true;
    QubeExpr countFilters;          // subset the members that are counted (e.g. restrict [Subject].[Subject] by [Species].[Homo Sapiens])

    Level joinLevel;                // [Subject].[SubjectVisit], affects how onRows,onColumns,wherefilters intersect
    QubeExpr whereFilters;          // like a regular WHERE

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
        QubeExpr membersQuery;       // subset the member list based on intersection with this orthogonal query
        String sql;                  // SQL query with one column per level matching the keyExpression for each level
        JSONObject getData;
        JSONObject getDataCDS;
    }






    // cache Hierarchies
    Map<String, Hierarchy> _cubeHierarchyMap = new CaseInsensitiveHashMap<>();
    // cache Levels
    Map<String, Level> _cubeLevelMap = new CaseInsensitiveHashMap<>();

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
        Member m = _getMemberInner(memberUniqueName, h, l);
        if (null == m)
            return null;
        // Don't allow selection of hidden measures
        if (m.getMemberType() == Member.Type.MEASURE && m.getName().startsWith("_"))
            return null;
        return m;
    }


    Member _getMemberInner(String memberUniqueName, Hierarchy h, Level l) throws OlapException
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


    /* NAME Helpers

    Path uniqueNametoPath(MetadataElement m) throws BindException
    {
        return uniqueNametoPath(m.getUniqueName());
    }

    Path uniqueNametoPath(String uniqueName) throws BindException
    {
        Path p = new Path();
        int start=0;
        while (start < uniqueName.length())
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
 */


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

        v = json.get("includeNullMemberInCount");
        includeNullMemberInCount = null==v ? true : (Boolean)ConvertUtils.convert(v.toString(), Boolean.class);

        onRows = parseJsonExpr(json.get("onRows"), OP.MEMBERS, OP.XINTERSECT);

        Object cols = null != json.get("onColumns") ? json.get("onColumns") : json.get("onCols");
        onColumns = parseJsonExpr(cols, OP.MEMBERS, OP.XINTERSECT);


        Object countFilter = json.get("countFilter");
        if (null == countFilter)
            countFilter = json.get("filter");
        if (countFilter instanceof JSONObject)
            countFilter = new JSONArray(Collections.singleton(countFilter));
        countFilters = parseJsonExpr(countFilter, OP.MEMBERS, OP.XINTERSECT);


        Object sliceFilter = json.get("sliceFilter");
        if (sliceFilter instanceof JSONObject)
            sliceFilter = new JSONArray(Collections.singleton(sliceFilter));
        sliceFilters = parseJsonExpr(sliceFilter, OP.MEMBERS, OP.XINTERSECT);


        Object whereFilter = json.get("whereFilter");
        if (whereFilter instanceof JSONObject)
            whereFilter = new JSONArray(Collections.singleton(whereFilter));
        whereFilters = parseJsonExpr(whereFilter, OP.MEMBERS, OP.XINTERSECT);


        Object countDistinctLevelNameSpec = json.get("countDistinctLevel");
        String countDistinctLevelName = toLevelName(countDistinctLevelNameSpec);

        Object joinLevelNameSpec = json.get("joinLevel");
        String joinLevelName = toLevelName(joinLevelNameSpec);

        if (!StringUtils.isEmpty(countDistinctLevelName))
        {
            this.countDistinctLevel = _getLevel(countDistinctLevelName, null);
            if (null == this.countDistinctLevel)
                errors.reject(SpringActionController.ERROR_MSG, ("Could not find level: " + countDistinctLevelName));
        }

        if (!StringUtils.isEmpty(joinLevelName))
        {
            this.joinLevel = _getLevel(joinLevelName, null);
            if (null == this.joinLevel)
                errors.reject(SpringActionController.ERROR_MSG, ("Could not find level: " + joinLevelName));
        }
        if (null == this.joinLevel)
            this.joinLevel = countDistinctLevel;
    }


    private String toLevelName(Object levelSpec)
    {
        if (levelSpec instanceof String)
        {
            return (String)levelSpec;
        }
        else if (levelSpec instanceof Map && ((Map)levelSpec).get("uniqueName") instanceof String)
        {
            return (String)((Map)levelSpec).get("uniqueName");
        }
        return null;
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
            else if (null != json.get("getDataCDS"))
            {
                // execution code needs to make sure user has permission to execute this query, if it is running as a service user
                e.getDataCDS = (JSONObject)json.get("getDataCDS");
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
}
