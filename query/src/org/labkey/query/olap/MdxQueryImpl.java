/*
 * Copyright (c) 2014-2015 LabKey Corporation
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

import org.labkey.api.action.SpringActionController;
import org.olap4j.OlapException;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Measure;
import org.olap4j.metadata.MetadataElement;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.labkey.query.olap.QubeQuery.QubeExpr;
import static org.labkey.query.olap.QubeQuery.QubeMembersExpr;

/**
 *
 * Translate QubeQuery into executeable MDX
 *
 */
public class MdxQueryImpl
{
    QubeQuery qq;
    BindException errors;

    public MdxQueryImpl(QubeQuery qq, BindException errors)
    {
        this.qq = qq;
        this.errors = errors;
    }


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
        this.arguments = new ArrayList<>(Arrays.asList(arguments));
    }

    FN  fn;
    Level level;
    List<Object> arguments;     // String,Member,_MDX
}



    _MDX _toFilterExistsExpr(_MDX levelExpr, _MDX membersExpr, String andor)
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
            Level l = null != membersDef.level ? membersDef.level : membersDef.hierarchy.getLevels().get(membersDef.hierarchy.getLevels().size()-1);
            return new _MDX(FN.MemberSet, l, membersDef.membersSet.toArray());
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
            return this._toFilterExistsExpr(levelExpr, membersExpr, "OR");
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
        {
            return (_MDX) sets.get(0);
        }
        else
        {
            Level level = ((_MDX)sets.get(0)).level;
            for (Object set : sets)
            {
                Level next = ((_MDX) set).level;
                if (null == level || null == next || !level.getUniqueName().equals(next.getUniqueName()))
                    level = null;
            }
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
        Level level = null;
        for (int e=0 ; e<expr.arguments.size() ; e++)
        {
            _MDX set = this._processExpr(expr.arguments.get(e));

            Level next = set.level;
            if (e==0)
                level = next;
            else if (null == level || null == next || !level.getUniqueName().equals(next.getUniqueName()))
                level = null;

            if (set.fn==FN.MemberSet)
            {
                // flatten nested unions
                sets.addAll(set.arguments);
            }
            else
            {
                sets.add(set);
            }
        }
        if (sets.size() == 1)
            return (_MDX)sets.get(0);
        else
            return new _MDX(FN.Union, level, sets);
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


/*
    class WithExpression
    {
        String measureUniqueName;
        String summaryMemberUniqueName;
        String summaryExpression;
    }

    Map<String,WithExpression> withExpressions = new HashMap<>();
    CaseInsensitiveHashMap<Measure> mapOfMeasures = new CaseInsensitiveHashMap<>();

    private String createSummaryMeasureExpression(Measure m)
    {
        String summaryName = "Summary" + m.getName();
        Measure summaryMeasure = mapOfMeasures.get(summaryName);
        if (null != summaryMeasure)
            return summaryName;

        if (withExpressions.containsKey(m.getUniqueName()))
            return withExpressions.get(m.getUniqueName()).summaryMemberUniqueName;

        String minSummaryValue = ((CachedCube._Measure)m).getAnnotationMap().get("MinSummaryValue");
        if (StringUtils.isEmpty(minSummaryValue))
            return m.getUniqueName();

        WithExpression with = new WithExpression();
        with.measureUniqueName = m.getUniqueName();
        with.summaryMemberUniqueName = "[Measures].[Summary" + m.getName() + "]";
        with.summaryExpression = "IIF([Measures].Population<" + minSummaryValue + ",'<" + minSummaryValue + "',CAST([Measures].Population AS STRING))";
        withExpressions.put(m.getUniqueName(),with);
        return with.summaryMemberUniqueName;
    }
*/


    public String generateMDX() throws BindException, OlapException
    {
        _MDX colmdx = null;
        String rowset = null, columnset = null, countFilterSet = null, slice = null;

        if (null != qq.joinLevel)
            errors.rejectValue(SpringActionController.ERROR_MSG, "joinLevel not supported");
        if (null != qq.whereFilters && 0 != qq.whereFilters.arguments.size())
            errors.reject(SpringActionController.ERROR_MSG, "whereFilters not supported");
        if (errors.hasErrors())
            return null;

        if (null != qq.onColumns)
        {
            colmdx = this._processExpr(qq.onColumns);
            columnset = this._toSetString(colmdx);
        }

        if (null != qq.onRows)
            rowset = this._toSetString(this._processExpr(qq.onRows));

        if (null != qq.countFilters && qq.countFilters.arguments.size() > 0)
            countFilterSet = this._toSetString(this._processExpr(qq.countFilters));

        if (null != qq.sliceFilters && qq.sliceFilters.arguments.size() > 0)
            slice = this._toSetString(this._processExpr(qq.sliceFilters));

        Measure defaultMeasure = (Measure) qq.cube.getHierarchies().get("Measures").getDefaultMember();
        if (null == defaultMeasure || defaultMeasure.getName().startsWith("_"))
        {
            for (Measure m : qq.cube.getMeasures())
            {
                if (!m.getName().startsWith("_"))
                {
                    defaultMeasure = m;
                    break;
                }
            }
        }

        StringBuilder withDefinition = new StringBuilder();

        if (null != qq.countDistinctMember)
        {
            defaultMeasure = (Measure) qq.countDistinctMember;
        }

        if (null != countFilterSet)
        {
            withDefinition.append(
                    "WITH SET ptids AS ").append(countFilterSet).append("\n").append(
                    "MEMBER ").append(defaultMeasure).append(" AS COUNT(ptids,EXCLUDEEMPTY)\n");
        }

        if (null == columnset)
        {
            columnset = defaultMeasure.getUniqueName();
        }
        else if (null != colmdx.level && !colmdx.level.getName().equals("MeasuresLevel"))
        {
            columnset = "CROSSJOIN(" + columnset + " , " + defaultMeasure.getUniqueName() + ")";
        }

        StringBuilder query = new StringBuilder();

        query.append(withDefinition);
        query.append("\nSELECT\n");
        query.append(columnset).append(" ON COLUMNS");
        if (null != rowset)
            query.append(",\n").append((qq.showEmpty ? "" : " NON EMPTY ")).append(rowset).append(" ON ROWS\n");
        query.append("\nFROM [").append(qq.getCube().getName()).append("]\n");
        if (null != slice)
            query.append("\nWHERE ").append(slice);

        return query.toString();
    }
}
