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

package org.apache.jena.dboe.base.block;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.dboe.ConfigTestDBOE;
import org.apache.jena.dboe.base.file.BlockAccess;
import org.apache.jena.dboe.base.file.BlockAccessMapped;

public class TestBlockMgrMapped extends AbstractTestBlockMgr
{
    static final String filename = ConfigTestDBOE.getTestingDir()+"/block-mgr";

    // Windows is iffy about deleting memory mapped files.

    @AfterEach public void after1()     { clearBlockMgr(); }

    private void clearBlockMgr() {
        if ( blockMgr != null ) {
            blockMgr.close();
            FileOps.deleteSilent(filename);
            blockMgr = null;
        }
    }

    @BeforeAll static public void remove1() { FileOps.deleteSilent(filename); }
    @AfterAll  static public void remove2() { FileOps.deleteSilent(filename); }

    @Override
    protected BlockMgr make() {
        clearBlockMgr();
        BlockAccess file = new BlockAccessMapped(filename, BlkSize);
        return new BlockMgrFileAccess(file, BlkSize);
    }
}
