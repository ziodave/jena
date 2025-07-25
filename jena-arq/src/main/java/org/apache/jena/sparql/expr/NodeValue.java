/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.sparql.expr;

import static javax.xml.datatype.DatatypeConstants.*;
import static org.apache.jena.datatypes.xsd.XSDDatatype.*;
import static org.apache.jena.sparql.expr.ValueSpace.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.jena.atlas.lib.DateTimeUtils;
import org.apache.jena.atlas.logging.Log;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ext.xerces.DatatypeFactoryInst;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.TextDirection;
import org.apache.jena.graph.impl.LiteralLabel;
import org.apache.jena.sparql.ARQInternalErrorException;
import org.apache.jena.sparql.SystemARQ;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.nodevalue.*;
import org.apache.jena.sparql.function.FunctionEnv;
import org.apache.jena.sparql.graph.NodeConst;
import org.apache.jena.sparql.graph.NodeTransform;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.apache.jena.sparql.util.*;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class NodeValue extends ExprNode
{
    static { JenaSystem.init(); }

    // Maybe:: NodeValueStringLang - strings with language tag

    /* Naming:
     * getXXX => plain accessor
     * asXXX =>  force to the required thing if necessary.
     *
     * Implementation notes:
     *
     * 1. There is little point delaying turning a node into its value
     *    because it has to be verified anyway (e.g. illegal literals).
     *    Because a NodeValue is being created, it is reasonably likely it
     *    is going to be used for it's value, so processing the datatype
     *    can be done at creation time where it is clearer.
     *
     * 2. Conversely, delaying turning a value into a graph node is
     *    valuable because intermediates, like the result of 2+3, will not
     *    be needed as nodes unless assignment (and there is no assignment
     *    in SPARQL even if there is for ARQ).
     *    Node level operations like str() don't need a full node.
     *
     * 3. nodevalue.NodeFunctions contains the SPARQL builtin implementations.
     *    nodevalue.XSDFuncOp contains the implementation of the XQuery/Xpath
     *    functions and operations.
     *    See also NodeUtils.
     *
     * 4. Note that SPARQL "=" is "known to be sameValueAs". Similarly "!=" is
     *    known to be different.
     *
     * 5. To add a new number type:
     *    Add sub type into nodevalue.NodeValueXXX
     *      Must implement .hashCode() and .equals() based on value.
     *    Add Functions.add/subtract/etc code and compareNumeric
     *    Add to compare code
     *    Fix TestExprNumeric
     *    Write lots of tests.
     *    Library code Maths1 and Maths2 for maths functions
     */

    /*
     * Effective boolean value rules.
     *    boolean: value of the boolean
     *    string: length(string) > 0 is true
     *    numeric: number != Nan && number != 0 is true
     * ref:  http://www.w3.org/TR/xquery/#dt-ebv
     */

    private static Logger log = LoggerFactory.getLogger(NodeValue.class);

    // ---- Constants and initializers / public

    public static boolean VerboseWarnings = true;
    public static boolean VerboseExceptions = false;

    public static final NodeValue TRUE   = NodeValue.makeNode("true", XSDboolean);
    public static final NodeValue FALSE  = NodeValue.makeNode("false", XSDboolean);

    public static final NodeValue nvZERO = NodeValue.makeNode(NodeConst.nodeZero);
    public static final NodeValue nvNegZERO = NodeValue.makeNode("-0.0e0", XSDdouble);
    public static final NodeValue nvONE  = NodeValue.makeNode(NodeConst.nodeOne);
    public static final NodeValue nvTEN  = NodeValue.makeNode(NodeConst.nodeTen);

    public static final NodeValue nvDecimalZERO = NodeValue.makeNode("0.0", XSDdecimal);
    public static final NodeValue nvDecimalONE  = NodeValue.makeNode("1.0", XSDdecimal);

    public static final NodeValue nvNaN     = NodeValue.makeNode("NaN", XSDdouble);
    public static final NodeValue nvINF     = NodeValue.makeNode("INF", XSDdouble);
    public static final NodeValue nvNegINF  = NodeValue.makeNode("-INF",XSDdouble);

    public static final NodeValue nvEmptyString  = NodeValue.makeString("");

    public static final String xsdNamespace = XSD+"#";

    public static DatatypeFactory xmlDatatypeFactory = null;

    static {
        // JDK default regardless.
        //xmlDatatypeFactory = DatatypeFactory.newDefaultInstance();
        // Extracted Xerces.
        xmlDatatypeFactory = DatatypeFactoryInst.newDatatypeFactory();
    }

    private Node node = null;     // Null used when a value has not been turned into a Node.

    // Don't create direct - the static builders manage the value/node relationship
    protected NodeValue() { super(); }
    protected NodeValue(Node n) { super(); node = n; }

//    protected makeNodeValue(NodeValue nv)
//    {
//        if ( v.isNode() )    { ... }
//        if ( v.isBoolean() ) { ... }
//        if ( v.isInteger() ) { ... }
//        if ( v.isDouble() )  { ... }
//        if ( v.isDecimal() ) { ... }
//        if ( v.isString() )  { ... }
//        if ( v.isDate() )    { ... }
//    }

    // ----------------------------------------------------------------
    // ---- Construct NodeValue without a graph node.

    /** Convenience operation - parse a string to produce a NodeValue - common namespaces like xsd: are built-in */
    public static NodeValue parse(String string)
    { return makeNode(NodeFactoryExtra.parseNode(string)); }

    public static NodeValue makeInteger(long i)
    { return new NodeValueInteger(BigInteger.valueOf(i)); }

    public static NodeValue makeInteger(BigInteger i)
    { return new NodeValueInteger(i); }

    public static NodeValue makeInteger(String lexicalForm)
    { return new NodeValueInteger(new BigInteger(lexicalForm)); }

    public static NodeValue makeFloat(float f)
    { return new NodeValueFloat(f); }

    public static NodeValue makeDouble(double d)
    { return new NodeValueDouble(d); }

    public static NodeValue makeString(String s)
    { return new NodeValueString(s); }

    public static NodeValue makeSortKey(String s, String collation)
    { return new NodeValueSortKey(s, collation); }

    public static NodeValue makeLangString(String s, String lang)
    { return new NodeValueLang(s, lang); }

    public static NodeValue makeDirLangString(String s, String lang, String langDir)
    { return new NodeValueLangDir(s, lang, langDir); }

    public static NodeValue makeDirLangString(String s, String lang, TextDirection textDirection)
    { return new NodeValueLangDir(s, lang, textDirection); }

    public static NodeValue makeDecimal(BigDecimal d)
    { return new NodeValueDecimal(d); }

    public static NodeValue makeDecimal(long i)
    { return new NodeValueDecimal(BigDecimal.valueOf(i)); }

    public static NodeValue makeDecimal(double d)
    { return new NodeValueDecimal(BigDecimal.valueOf(d)); }

    public static NodeValue makeDecimal(String lexicalForm)
    { return NodeValue.makeNode(lexicalForm, XSDdecimal); }

    public static NodeValue makeDateTime(String lexicalForm)
    { return NodeValue.makeNode(lexicalForm, XSDdateTime); }

    public static NodeValue makeDate(String lexicalForm)
    { return NodeValue.makeNode(lexicalForm, XSDdate); }

    public static NodeValue makeDateTime(Calendar cal) {
        String lex = DateTimeUtils.calendarToXSDDateTimeString(cal);
        return NodeValue.makeNode(lex, XSDdateTime);
    }

    public static NodeValue makeDateTime(XMLGregorianCalendar cal) {
        String lex = cal.toXMLFormat();
        Node node = NodeFactory.createLiteralDT(lex, XSDdateTime);
        return NodeValueDateTime.create(lex, node);
    }

    public static NodeValue makeDate(Calendar cal) {
        String lex = DateTimeUtils.calendarToXSDDateString(cal);
        return NodeValue.makeNode(lex, XSDdate);
    }

    public static NodeValue makeDate(XMLGregorianCalendar cal) {
        String lex = cal.toXMLFormat();
        Node node = NodeFactory.createLiteralDT(lex, XSDdate);
        return NodeValueDateTime.create(lex, node);
    }

    public static NodeValue makeDuration(String lexicalForm)
    { return NodeValue.makeNode(lexicalForm, XSDduration); }

    public static NodeValue makeDuration(Duration duration)
    { return new NodeValueDuration(duration); }

    public static NodeValue makeNodeDuration(Duration duration, Node node)
    { return new NodeValueDuration(duration, node); }

    public static NodeValue makeBoolean(boolean b)
    { return b ? NodeValue.TRUE : NodeValue.FALSE; }

    public static NodeValue booleanReturn(boolean b)
    { return b ? NodeValue.TRUE : NodeValue.FALSE; }

    // ----------------------------------------------------------------
    // ---- Construct NodeValue from graph nodes

    public static NodeValue makeNode(Node node) {
        return nodeToNodeValue(node);
    }

    public static NodeValue makeNode(String lexicalForm, RDFDatatype dtype) {
        Node n = NodeFactory.createLiteralDT(lexicalForm, dtype);
        return NodeValue.makeNode(n);
    }

    // Convenience - knows that lang tags aren't allowed with datatypes.
    public static NodeValue makeNode(String lexicalForm, String langTag, Node datatype) {
        String uri = (datatype == null) ? null : datatype.getURI();
        return makeNode(lexicalForm, langTag, uri);
    }

    public static NodeValue makeNode(String lexicalForm, String langTag, String datatype) {
        if ( datatype != null && datatype.equals("") )
            datatype = null;

        if ( langTag != null && datatype != null )
            // raise??
            Log.warn(NodeValue.class, "Both lang tag and datatype defined (lexcial form '" + lexicalForm + "')");

        Node n = null;
        if ( langTag != null )
            n = NodeFactory.createLiteralLang(lexicalForm, langTag);
        else if ( datatype != null ) {
            RDFDatatype dType = TypeMapper.getInstance().getSafeTypeByName(datatype);
            n = NodeFactory.createLiteralDT(lexicalForm, dType);
        } else
            n = NodeFactory.createLiteralString(lexicalForm);

        return NodeValue.makeNode(n);
    }

    // ----------------------------------------------------------------
    // ---- Construct NodeValue with graph node and value.

    public static NodeValue makeNodeBoolean(boolean b)
    { return b ? NodeValue.TRUE : NodeValue.FALSE; }

    public static NodeValue makeNodeBoolean(String lexicalForm) {
        return makeNode(lexicalForm, null, XSDboolean.getURI());
    }

    public static NodeValue makeNodeInteger(long v) {
        return makeNode(Long.toString(v), null, XSDinteger.getURI());
    }

    public static NodeValue makeNodeInteger(String lexicalForm) {
        return makeNode(lexicalForm, null, XSDinteger.getURI());
    }

    public static NodeValue makeNodeFloat(float f) {
        return makeNode(XSDNumUtils.stringForm(f), null, XSDfloat.getURI());
    }

    public static NodeValue makeNodeFloat(String lexicalForm) {
        return makeNode(lexicalForm, null, XSDfloat.getURI());
    }

    public static NodeValue makeNodeDouble(double v) {
        return makeNode(XSDNumUtils.stringForm(v), null, XSDdouble.getURI());
    }

    public static NodeValue makeNodeDouble(String lexicalForm) {
        return makeNode(lexicalForm, null, XSDdouble.getURI());
    }

    public static NodeValue makeNodeDecimal(BigDecimal decimal) {
        String lex = XSDNumUtils.stringFormatARQ(decimal);
        return makeNode(lex, XSDDatatype.XSDdecimal);
    }

    public static NodeValue makeNodeDecimal(String lexicalForm) {
        return makeNode(lexicalForm, null, XSDdecimal.getURI());
    }

    public static NodeValue makeNodeString(String string) {
        return makeNode(string, null, (String)null);
    }

    public static NodeValue makeNodeDateTime(Calendar date) {
        String lex = DateTimeUtils.calendarToXSDDateTimeString(date);
        return makeNode(lex, XSDdateTime);
    }

    public static NodeValue makeNodeDateTime(String lexicalForm) {
        return makeNode(lexicalForm, XSDdateTime);
    }

    public static NodeValue makeNodeDate(Calendar date) {
        String lex = DateTimeUtils.calendarToXSDDateString(date);
        return makeNode(lex, XSDdate);
    }

    public static NodeValue makeNodeDate(String lexicalForm) {
        return makeNode(lexicalForm, XSDdate);
    }

    // ----------------------------------------------------------------
    // ---- Expr interface

    @Override
    public NodeValue eval(Binding binding, FunctionEnv env) {
        return this;
    }

    // NodeValues are immutable so no need to duplicate.
    @Override
    public Expr copySubstitute(Binding binding) {
        return this;
    }

    @Override
    public Expr applyNodeTransform(NodeTransform transform) {
        Node n = asNode();
        n = transform.apply(n);
        return makeNode(n);
    }

    public Node evalNode(Binding binding, ExecutionContext execCxt) {
        return asNode();
    }

    @Override
    public boolean isConstant()     { return true; }

    @Override
    public NodeValue getConstant()  { return this; }

    public boolean isIRI() {
        forceToNode();
        return node.isURI();
    }

    public boolean isBlank() {
        forceToNode();
        return node.isBlank();
    }

    public boolean isTripleTerm() {
        forceToNode();
        return node.isTripleTerm();
    }

    public ValueSpace getValueSpace() {
        return classifyValueSpace(this);
    }

    public static ValueSpace classifyValueOp(NodeValue nv1, NodeValue nv2) {
        ValueSpace c1 = classifyValueSpace(nv1);
        ValueSpace c2 = classifyValueSpace(nv2);
        if ( c1 == c2 ) return c1;
        if ( c1 == VSPACE_UNKNOWN || c2 == VSPACE_UNKNOWN )
            return VSPACE_UNKNOWN;

        // Known values spaces but incompatible
        return VSPACE_DIFFERENT;
    }

    /*package*/ static ValueSpace classifyValueSpace(NodeValue nv) {
        return ValueSpace.valueSpace(nv);
    }

    // ----------------------------------------------------------------
    // ---- sameValueAs

    // Disjoint value spaces : dateTime and dates are not comparable
    // Every langtag implies another value space as well.

    /**
     * Return true if the two NodeValues are known to be the same value return false
     * if known to be different values, throw ExprEvalException otherwise
     */
    public static boolean sameValueAs(NodeValue nv1, NodeValue nv2) {
        return NodeValueCmp.sameValueAs(nv1, nv2);
    }

    /**
     * Return true if the two Nodes are known to be different, return false if the
     * two Nodes are known to be the same, else throw ExprEvalException
     */
    public static boolean notSameValueAs(Node n1, Node n2) {
        return notSameValueAs(NodeValue.makeNode(n1), NodeValue.makeNode(n2));
    }

    /**
     * Return true if the two NodeValues are known to be different, return false if
     * the two NodeValues are known to be the same, else throw ExprEvalException
     */
    public static boolean notSameValueAs(NodeValue nv1, NodeValue nv2) {
        return NodeValueCmp.notSameValueAs(nv1, nv2);
    }

    // ----------------------------------------------------------------
    // compare

    /** Compare by value (and only value) if possible.
     *  Supports &lt;, &lt;=, &gt;, &gt;= but not = nor != (which are sameValueAs and notSameValueAs)
     * @param nv1
     * @param nv2
     * @return Expr.CMP_LESS(-1), Expr.CMP_EQUAL(0) or Expr.CMP_GREATER(+1)
     * @throws ExprNotComparableException for Expr.CMP_INDETERMINATE(+2)
     */
    public static int compare(NodeValue nv1, NodeValue nv2) {
        //return NodeValueCompare.compare(nv1, nv2);
        int x = NodeValueCmp.compareByValue(nv1, nv2);
        if ( x == Expr.CMP_INDETERMINATE || x == Expr.CMP_UNEQUAL )
            throw new ExprNotComparableException(null);
        return x;
    }
    /**
     * Compare by value if possible else compare by kind/type/lexical form
     * Only use when you want an ordering regardless of form of NodeValue,
     * for example in ORDER BY
     *
     * @param nv1
     * @param nv2
     * @return negative, 0, or positive for less than, equal, greater than.
     */
    public static int compareAlways(NodeValue nv1, NodeValue nv2) {
        //return NodeValueCompare.compareAlways(nv1, nv2);
        return NodeValueCmp.compareAlways(nv1, nv2);
    }

    // ----------------------------------------------------------------
    // ---- Node operations

    public static Node toNode(NodeValue nv)
    {
        if ( nv == null )
            return null;
        return nv.asNode();
    }

    public final Node asNode()
    {
        if ( node == null )
            node = makeNode();
        return node;
    }
    protected abstract Node makeNode();

    /** getNode - return the node form - may be null (use .asNode() to force to a node) */
    public Node getNode() { return node; }

    public String getDatatypeURI() { return asNode().getLiteralDatatypeURI(); }

    public boolean hasNode() { return node != null; }

    // ----------------------------------------------------------------
    // ---- Subclass operations

    public boolean isBoolean()      { return false; }
    public boolean isString()       { return false; }
    public boolean isLangString()   { return false; }
    public boolean isSortKey()      { return false; }

    public boolean isNumber()       { return false; }
    public boolean isInteger()      { return false; }
    public boolean isDecimal()      { return false; }
    public boolean isFloat()        { return false; }
    public boolean isDouble()       { return false; }

    public boolean hasDateTime()    { return isDateTime() || isDate() || isTime() || isGYear() || isGYearMonth() || isGMonth() || isGMonthDay() || isGDay(); }
    public boolean isDateTime()     { return false; }
    public boolean isDate()         { return false; }
    public boolean isLiteral()      { return getNode() == null || getNode().isLiteral(); }
    public boolean isTime()         { return false; }
    public boolean isDuration()     { return false; }

    public boolean isYearMonthDuration() {
        if ( ! isDuration() ) return false;
        Duration dur = getDuration();
        return ( dur.isSet(YEARS) || dur.isSet(MONTHS) ) &&
               ! dur.isSet(DAYS) && ! dur.isSet(HOURS) && ! dur.isSet(MINUTES) && ! dur.isSet(SECONDS);
    }

    public boolean isDayTimeDuration() {
        if ( ! isDuration() ) return false;
        Duration dur = getDuration();
        return !dur.isSet(YEARS) && ! dur.isSet(MONTHS) &&
            ( dur.isSet(DAYS) || dur.isSet(HOURS) || dur.isSet(MINUTES) || dur.isSet(SECONDS) );
    }

    public boolean isGYear()        { return false; }
    public boolean isGYearMonth()   { return false; }
    public boolean isGMonth()       { return false; }
    public boolean isGMonthDay()    { return false; }
    public boolean isGDay()         { return false; }

    public boolean     getBoolean()     { raise(new ExprEvalTypeException("Not a boolean: "+this)); return false; }
    public String      getString()      { raise(new ExprEvalTypeException("Not a string: "+this)); return null; }
    public String      getLang()        { raise(new ExprEvalTypeException("Not a lang string: "+this)); return null; }
    public String      getLangDir()     { raise(new ExprEvalTypeException("Not a langdir string: "+this)); return null; }
    public NodeValueSortKey getSortKey()        { raise(new ExprEvalTypeException("Not a sort key: "+this)); return null; }

    public BigInteger  getInteger()     { raise(new ExprEvalTypeException("Not an integer: "+this)); return null; }
    public BigDecimal  getDecimal()     { raise(new ExprEvalTypeException("Not a decimal: "+this)); return null; }
    public float       getFloat()       { raise(new ExprEvalTypeException("Not a float: "+this)); return Float.NaN; }
    public double      getDouble()      { raise(new ExprEvalTypeException("Not a double: "+this)); return Double.NaN; }
    // Value representation for all date and time values.
    public XMLGregorianCalendar getDateTime()    { raise(new ExprEvalTypeException("No DateTime value: "+this)); return null; }
    public Duration    getDuration() { raise(new ExprEvalTypeException("Not a duration: "+this)); return null; }

    // ----------------------------------------------------------------
    // ---- Setting : used when a node is used to make a NodeValue

    private static NodeValue nodeToNodeValue(Node node) {
        if ( node.isVariable() )
            Log.warn(NodeValue.class, "Variable passed to NodeValue.nodeToNodeValue");

        if ( ! node.isLiteral() )
            // Not a literal - no value to extract
            return new NodeValueNode(node);

        boolean hasLangTag = NodeUtils.isLangString(node);
        boolean isPlainLiteral = ( node.getLiteralDatatypeURI() == null && ! hasLangTag );

        if ( isPlainLiteral )
            return new NodeValueString(node.getLiteralLexicalForm(), node);

        if ( hasLangTag ) {
            // Works for RDF 1.0 and RDF 1.1
            if ( node.getLiteralDatatype() != null && ! RDF.dtLangString.equals(node.getLiteralDatatype()) ) {
                if ( NodeValue.VerboseWarnings )
                    Log.warn(NodeValue.class, "Lang tag and datatype (datatype ignored)");
            }
            // RDF 1.2
            if ( NodeUtils.hasLangDir(node) )
                    return new NodeValueLangDir(node);
            return new NodeValueLang(node);
        }

        // Typed literal
        LiteralLabel lit = node.getLiteral();

        // This includes type testing
        // if ( ! lit.getDatatype().isValidLiteral(lit) )

        // Use this - already calculated when the node is formed.
        if ( !lit.isWellFormed() ) {
            if ( NodeValue.VerboseWarnings ) {
                String tmp = FmtUtils.stringForNode(node);
                Log.warn(NodeValue.class, "Datatype format exception: " + tmp);
            }
            // Invalid lexical form.
            return new NodeValueNode(node);
        }

        NodeValue nv = _setByValue(node);
        if ( nv != null )
            return nv;

        return new NodeValueNode(node);
        //raise(new ExprException("NodeValue.nodeToNodeValue: Unknown Node type: "+n));
    }

    // Jena code does not have these types (yet)
    private static final String dtXSDprecisionDecimal   = XSD+"#precisionDecimal";

    // Returns null for unrecognized literal.
    private static NodeValue _setByValue(Node node) {
        // This should not happen.
        // nodeToNodeValue should have been dealt with it.
        if ( NodeUtils.hasLang(node) ) {
            if ( NodeUtils.hasLangDir(node) )
                return new NodeValueLangDir(node);
            return new NodeValueLang(node);
        }
        LiteralLabel lit = node.getLiteral();
        RDFDatatype datatype = lit.getDatatype();

        // Quick check.
        // Only XSD supported.
        // And (for testing) roman numerals.
        String datatypeURI = datatype.getURI();
        if ( !datatypeURI.startsWith(xsdNamespace) && !SystemARQ.EnableRomanNumerals ) {
            // Not XSD.
            return null;
        }

        String lex = lit.getLexicalForm();

        try { // DatatypeFormatException - should not happen
            if ( XSDstring.isValidLiteral(lit) )
                // String - plain or xsd:string, or derived datatype.
                return new NodeValueString(lex, node);

            // Otherwise xsd:string is like any other unknown datatype.
            // Ditto literals with language tags (which are handled by nodeToNodeValue)

            // isValidLiteral is a value test - not a syntactic test.
            // This makes a difference in that "1"^^xsd:decimal" is a
            // valid literal for xsd:integer (all other cases are subtypes of xsd:integer)
            // which we want to become integer anyway).

            // Order here is promotion order integer-decimal-float-double

            // XSD allows whitespace. Java String.trim removes too much
            // so must test for validity on the untrimmed lexical form.
            String lexTrimmed = lex.trim();

            if ( ! datatype.equals(XSDdecimal) ) { // ! decimal is short for integers and all derived types.
                // XSD integer and derived types
                if ( XSDinteger.isValidLiteral(lit) )
                {
                    // BigInteger does not accept such whitespace.
                    String s = lexTrimmed;
                    if ( s.startsWith("+") )
                        // BigInteger does not accept leading "+"
                        s = s.substring(1);
                    // Includes subtypes (int, byte, postiveInteger etc).
                    // NB Known to be valid for type by now
                    BigInteger integer = new BigInteger(s);
                    return new NodeValueInteger(integer, node);
                }
            }

            if ( datatype.equals(XSDdecimal) && XSDdecimal.isValidLiteral(lit) ) {
                BigDecimal decimal = new BigDecimal(lexTrimmed);
                return new NodeValueDecimal(decimal, node);
            }

            if ( datatype.equals(XSDfloat) && XSDfloat.isValidLiteral(lit) ) {
                // NB If needed, call to floatValue, then assign to double.
                // Gets 1.3f != 1.3d right
                float f = ((Number)lit.getValue()).floatValue();
                return new NodeValueFloat(f, node);
            }

            if ( datatype.equals(XSDdouble) && XSDdouble.isValidLiteral(lit) ) {
                double d = ((Number)lit.getValue()).doubleValue();
                return new NodeValueDouble(d, node);
            }

            if ( datatype.equals(XSDboolean) && XSDboolean.isValidLiteral(lit) ) {
                boolean b = (Boolean) lit.getValue();
                return new NodeValueBoolean(b, node);
            }

            if ( (datatype.equals(XSDdateTime) || datatype.equals(XSDdateTimeStamp)) && XSDdateTime.isValid(lex) ) {
                return NodeValueDateTime.create(lexTrimmed, node);
            }

            if ( datatype.equals(XSDdate) && XSDdate.isValidLiteral(lit) ) {
                return NodeValueDateTime.create(lexTrimmed, node);
            }

            if ( datatype.equals(XSDtime) && XSDtime.isValidLiteral(lit) ) {
                return NodeValueDateTime.create(lexTrimmed, node);
            }

            if ( datatype.equals(XSDgYear) && XSDgYear.isValidLiteral(lit) ) {
                return NodeValueDateTime.create(lexTrimmed, node);
            }
            if ( datatype.equals(XSDgYearMonth) && XSDgYearMonth.isValidLiteral(lit) ) {
                return NodeValueDateTime.create(lexTrimmed, node);
            }
            if ( datatype.equals(XSDgMonth) && XSDgMonth.isValidLiteral(lit) ) {
                return NodeValueDateTime.create(lexTrimmed, node);
            }

            if ( datatype.equals(XSDgMonthDay) && XSDgMonthDay.isValidLiteral(lit) ) {
                return NodeValueDateTime.create(lexTrimmed, node);
            }
            if ( datatype.equals(XSDgDay) && XSDgDay.isValidLiteral(lit) ) {
                return NodeValueDateTime.create(lexTrimmed, node);
            }

            // -- Duration

            if ( datatype.equals(XSDduration) && XSDduration.isValid(lex) ) {
                Duration duration = xmlDatatypeFactory.newDuration(lexTrimmed);
                return new NodeValueDuration(duration, node);
            }

            if ( datatype.equals(XSDyearMonthDuration) && XSDyearMonthDuration.isValid(lex) ) {
                Duration duration = xmlDatatypeFactory.newDuration(lexTrimmed);
                return new NodeValueDuration(duration, node);
            }
            if ( datatype.equals(XSDdayTimeDuration) && XSDdayTimeDuration.isValid(lex) ) {
                Duration duration = xmlDatatypeFactory.newDuration(lexTrimmed);
                return new NodeValueDuration(duration, node);
            }

            // If wired into the TypeMapper via RomanNumeralDatatype.enableAsFirstClassDatatype
//            if ( RomanNumeralDatatype.get().isValidLiteral(lit) )
//            {
//                int i = ((RomanNumeral)lit.getValue()).intValue();
//                return new NodeValueInteger(i);
//            }

            // Not wired in
            if ( SystemARQ.EnableRomanNumerals )
            {
                if ( lit.getDatatypeURI().equals(RomanNumeralDatatype.get().getURI()) )
                {
                    Object obj = RomanNumeralDatatype.get().parse(lexTrimmed);
                    if ( obj instanceof Integer )
                        return new NodeValueInteger(((Integer)obj).longValue());
                    if ( obj instanceof RomanNumeral )
                        return new NodeValueInteger( ((RomanNumeral)obj).intValue() );
                    throw new ARQInternalErrorException("DatatypeFormatException: Roman numeral is unknown class");
                }
            }

        } catch (DatatypeFormatException ex)
        {
            // Should have been caught earlier by special test in nodeToNodeValue
            throw new ARQInternalErrorException("DatatypeFormatException: "+lit, ex);
        }
        return null;
    }

    // ----------------------------------------------------------------

    // Point to catch all exceptions.
    public static void raise(ExprException ex) {
        throw ex;
    }

    @Override
    public void visit(ExprVisitor visitor) { visitor.visit(this); }

    private void forceToNode() {
        if ( node == null )
            node = asNode();

        if ( node == null )
            raise(new ExprEvalException("Not a node: " + this));
    }

    // ---- Formatting (suitable for SPARQL syntax).
    // Usually done by being a Node and formatting that.
    // In desperation, will try toString() (no quoting)

    public final String asUnquotedString()
    { return asString(); }

    public final String asQuotedString()
    { return asQuotedString(new SerializationContext()); }

    public final String asQuotedString(SerializationContext context) {
        // If possible, make a node and use that as the formatted output.
        if ( node == null )
            node = asNode();
        if ( node != null )
            return FmtUtils.stringForNode(node, context);
        return toString();
    }

    // Convert to a string - usually overridden.
    public String asString() {
        // Do not call .toString()
        forceToNode();
        return NodeFunctions.str(node);
    }

    @Override
    public int hashCode() {
        return asNode().hashCode();
    }

    @Override
    public boolean equals(Expr other, boolean bySyntax) {
        if ( other == null ) return false;
        if ( this == other ) return true;
        // This is the equality condition Jena uses - lang tags are different by case.
        if ( ! ( other instanceof NodeValue nv) )
            return false;
        return asNode().equals(nv.asNode());
        // Not NodeFunctions.sameTerm (which smooshes language tags by case)
    }

    public abstract void visit(NodeValueVisitor visitor);

    public Expr apply(ExprTransform transform)  { return transform.transform(this); }

    @Override
    public String toString() {
        return asQuotedString();
    }
}
