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

package org.apache.jena.sparql.core;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.graph.GraphOps;

/** This is the test suite run over a regular DatasetGraph to check compatibility */
public class TestDatasetGraphsRegular extends AbstractTestGraphOverDatasetGraph
{
    @Override
    protected DatasetGraph createBaseDSG() { return DatasetGraphFactory.create(); }
    
    @Override
    protected Graph makeNamedGraph(DatasetGraph dsg, Node gn)
    {
        // Knowns about getting special graphs.
        return GraphOps.getGraph(dsg, gn);
    }

    @Override
    protected Graph makeDefaultGraph(DatasetGraph dsg)
    {
        return  dsg.getDefaultGraph();
    }

}

