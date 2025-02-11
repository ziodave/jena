/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.ext.xerces.impl.dv.xs;

import org.apache.jena.ext.xerces.impl.dv.InvalidDatatypeValueException;

/**
 * All primitive types plus ID/IDREF/ENTITY/INTEGER are derived from this abstract
 * class. It provides extra information XSSimpleTypeDecl requires from each
 * type: allowed facets, converting String to actual value, check equality,
 * comparison, etc.
 * 
 * {@literal @xerces.internal} 
 *
 * @author Neeraj Bajaj, Sun Microsystems, inc.
 * @author Sandy Gao, IBM
 *
 * @version $Id: TypeValidator.java 446745 2006-09-15 21:43:58Z mrglavas $
 */
public abstract class TypeValidator {

    // which facets are allowed for this type
    public abstract short getAllowedFacets();

    // convert a string to an actual value. for example,
    // for number types (decimal, double, float, and types derived from them),
    // get the BigDecimal, Double, Flout object.
    // for some types (string and derived), they just return the string itself
    public abstract Object getActualValue(String content)
        throws InvalidDatatypeValueException;

    // the following methods might not be supported by every DV.
    // but XSSimpleTypeDecl should know which type supports which methods,
    // and it's an *internal* error if a method is called on a DV that
    // doesn't support it.

    //order constants
    public static final short LESS_THAN     = -1;
    public static final short EQUAL         = 0;
    public static final short GREATER_THAN  = 1;
    public static final short INDETERMINATE = 2;

    // check the order relation between the two values
    // the parameters are in compiled form (from getActualValue)
    public int compare(Object value1, Object value2) {
        return -1;
    }

    // get the number of digits of the value
    // the parameters are in compiled form (from getActualValue)
    public int getTotalDigits(Object value) {
        return -1;
    }

    // get the number of fraction digits of the value
    // the parameters are in compiled form (from getActualValue)
    public int getFractionDigits(Object value) {
        return -1;
    }

    // check whether the character is in the range 0x30 ~ 0x39
    public static final boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }
    
    // if the character is in the range 0x30 ~ 0x39, return its int value (0~9),
    // otherwise, return -1
    public static final int getDigit(char ch) {
        return isDigit(ch) ? ch - '0' : -1;
    }
    
} // interface TypeValidator
