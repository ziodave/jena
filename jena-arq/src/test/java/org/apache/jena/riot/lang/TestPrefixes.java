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

package org.apache.jena.riot.lang;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

public class TestPrefixes {

    @Test
    public void prefixes_01() {

        String json = """
                [
                             {
                                 "@context": "http://schema.org",
                                 "@id": "https://data.example.org/dataset/entity",
                                 "@type": "Thing",
                                 "description": "A value that ends with a colon:"
                             }
                 ]
                """;

        Model model = ModelFactory.createDefaultModel();
        StringReader sr = new StringReader(json);
        RDFDataMgr.read(model, sr, null, Lang.JSONLD11);
        Assertions.assertFalse(model.isEmpty());
        Assertions.assertTrue(model.getNsPrefixMap().isEmpty(), () -> {
            StringBuilder sb = new StringBuilder("Found the following namespaces, expecting none:\n");
            model.getNsPrefixMap().forEach((s, n) -> {
                sb.append(s)
                        .append(": ")
                        .append(n)
                        .append("\n");
            });

            return sb.toString();
        });
    }

}
