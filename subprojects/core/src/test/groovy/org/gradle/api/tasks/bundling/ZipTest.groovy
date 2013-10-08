/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.bundling

import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals

class ZipTest extends AbstractArchiveTaskTest {
    Zip zip
    File intialFile

    @Before public void setUp()  {
        zip = createTask(Zip)
        configure(zip)
        intialFile = tmpDir.createFile('file.txt')
        zip.from intialFile
    }

    AbstractArchiveTask getArchiveTask() {
        zip
    }

    @Override
    protected List<File> getInitalFiles() {
        [intialFile]
    }

    @Test public void testZip() {
        zip = createTask(Zip)
        assertEquals(Zip.ZIP_EXTENSION, zip.extension)
        checkConstructor()
    }
}