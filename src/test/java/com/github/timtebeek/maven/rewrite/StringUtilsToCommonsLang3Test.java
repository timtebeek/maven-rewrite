/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.timtebeek.maven.rewrite;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class StringUtilsToCommonsLang3Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("plexus-utils", "maven-shared-utils"))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath()
                        .build()
                        .activateRecipes("com.github.timtebeek.maven.rewrite.StringUtilsToCommonsLang3"));
    }

    @Nested
    class MavenShared {
        @Nested
        class DirectReplacements {
            @Test
            void reverse() {
                rewriteRun(
                        java(
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.apache.maven.shared.utils.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """,
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """));
            }
        }

        @Nested
        class ManualReplacements {
            @Test
            void capitalise() {
                rewriteRun(
                        java(
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.apache.maven.shared.utils.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.capitalise("foo");
                                            }
                                        }
                                        """,
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.capitalize("foo");
                                            }
                                        }
                                        """));
            }

            @Test
            void clean() {
                rewriteRun(
                        java(
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.apache.maven.shared.utils.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.clean("foo");
                                            }
                                        }
                                        """,
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.trimToEmpty("foo");
                                            }
                                        }
                                        """));
            }
        }
    }

    @Nested
    class Plexus {
        @Nested
        class DirectReplacements {
            @Test
            void reverse() {
                rewriteRun(
                        java(
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.codehaus.plexus.util.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """,
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """));
            }
        }

        @Nested
        class ManualReplacements {
            @Test
            void clean() {
                rewriteRun(
                        java(
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.codehaus.plexus.util.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.clean("foo");
                                            }
                                        }
                                        """,
                                """
                                        package org.apache.maven.shared.utils;

                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.trimToEmpty("foo");
                                            }
                                        }
                                        """));
            }
        }
    }
}
