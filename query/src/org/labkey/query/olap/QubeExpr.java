package org.labkey.query.olap;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 11/5/13
 * Time: 10:32 AM
 */
public class QubeExpr
{
    enum TYPE
    {
        MemberSet,      // [Level] simple set of members from one level, represented as either as Set<Member> (maybe actual class MemberSet)
        TupleSet,       // [Level x Level] cross-product of two (or more) MemberSets, represented as List<List<Member>>
        SetResult,      // [MemberSet, MemberSet] or [TupleSet, MemberSet], represented as List<Pair<List<Member>,Set<Member>>>
        CountResult,    // [MemberSet, int] or [TupleSet, int], represented as List<Pair<List<Member>,Integer>>
    }
}
