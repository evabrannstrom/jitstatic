package io.jitstatic.utils;

/*-
 * #%L
 * jitstatic
 * %%
 * Copyright (C) 2017 - 2018 H.Hegardt
 * %%
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
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PathTest {

    @Test
    public void testPath() {
        Path p = Path.of("base");
        assertEquals("base", p.getLastElement());
    }

    @Test
    public void testPathAsDirectory() {
        Path p = Path.of("base/");
        assertEquals("", p.getLastElement());
    }

    @Test
    public void testPathAsKey() {
        Path p = Path.of("base/key");
        assertEquals("key", p.getLastElement());
    }

    @Test
    public void testDoubleSeparator() {
        Path p = Path.of("//");
        assertEquals("", p.getLastElement());
        assertTrue(p.isDirectory());
    }

    @Test
    public void testParentElement() {
        Path p = Path.of("base/element1/element2/key");
        assertEquals("base/element1/element2/", p.getParentElements());
    }
}
