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

package org.apache.jena.riot.resultset.rw;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.resultset.ResultSetWriter;
import org.apache.jena.riot.resultset.ResultSetWriterRegistry;

public class TestResultSetWriterTSV {

    @Test
    public void testFactory() {
        ResultSetWriter writer = ResultSetWriterRegistry.getFactory(Lang.TSV).create(Lang.TSV);
        assertNotNull(writer);
    }

    @Test
    public void testWriteBoolean() {
        ResultSetWriter writer = ResultSetWriterRegistry.getFactory(Lang.TSV).create(Lang.TSV);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, true, null);
        String output = out.toString(UTF_8);
        assertThat(output, containsString("true"));
    }
}
