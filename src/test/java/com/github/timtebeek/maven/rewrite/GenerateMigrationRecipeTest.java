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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateMigrationRecipeTest {

    public static void main(String[] args) throws Exception {
        // Usage: GenerateMigrationRecipeTest <fully qualified class name source> <fully qualified class name target>
        // <output director>
        String fullyQualifiedClassNameSource = args[0];
        String fullyQualifiedClassNameTarget = args[1];
        Path outputFolder = Path.of(args[2]);
        generateRecipe(
                Class.forName(fullyQualifiedClassNameSource),
                Class.forName(fullyQualifiedClassNameTarget),
                outputFolder);
    }

    private static final Map<Class<?>, Map<String, String>> INDIRECT_REPLACEMENTS = Map.of(
            org.apache.maven.shared.utils.StringUtils.class, Map.of(
                    "capitalise(java.lang.String)", "capitalize",
                    "clean(java.lang.String)", "trimToEmpty",
                    "replace(java.lang.String,char,char)", "replaceChars"),
            org.codehaus.plexus.util.StringUtils.class, Map.of(
                    "capitalise(java.lang.String)", "capitalize",
                    "clean(java.lang.String)", "trimToEmpty",
                    "replace(java.lang.String,char,char)", "replaceChars"));

    private static void generateRecipe(Class<?> source, Class<?> target, Path outputFolder) throws IOException {
        SortedSet<String> sourceMethodPatterns = getMethodNamesAndParameters(source.getMethods());
        SortedSet<String> targetMethodPatterns = getMethodNamesAndParameters(target.getMethods());
        // Duplicate any target method patterns that use CharSequence instead of String
        targetMethodPatterns.addAll(targetMethodPatterns.stream()
                .map(m -> m.replace("CharSequence", "String"))
                .collect(Collectors.toSet()));
        // Duplicate any target method patterns that use int instead of char
        targetMethodPatterns.addAll(
                targetMethodPatterns.stream().map(m -> m.replace("int", "char")).collect(Collectors.toSet()));

        // Determine which methods are present in both classes.
        SortedSet<String> methodsWithDirectReplacement = new TreeSet<>(sourceMethodPatterns);
        methodsWithDirectReplacement.retainAll(targetMethodPatterns);
        // Determine which methods are present in the source class but not in the target class.
        SortedSet<String> methodsWithIndirectReplacement = new TreeSet<>(sourceMethodPatterns);
        methodsWithIndirectReplacement.removeAll(targetMethodPatterns);
        methodsWithIndirectReplacement.removeAll(INDIRECT_REPLACEMENTS.get(source).keySet());

        // Write recipes for direct replacements
        writeDirectReplacements(source, target, outputFolder, methodsWithDirectReplacement);
        writeFindManualReplacements(source, target, outputFolder, methodsWithIndirectReplacement);

        // Write recipes for indirect replacements
        writeIndirectReplacements(source, target, outputFolder, INDIRECT_REPLACEMENTS.get(source));
    }

    private static SortedSet<String> getMethodNamesAndParameters(Method[] sourceMethods) {
        return Stream.of(sourceMethods)
                // Only look at static methods
                .filter(m -> (m.getModifiers() & 8) == 8)
                .map(m -> m.toString()
                        .split(m.getDeclaringClass().getSimpleName())[1]
                        .substring(1))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static void writeDirectReplacements(
            Class<?> source, Class<?> target, Path outputFolder, Set<String> methodsPatterns) throws IOException {
        // Write recipes for direct replacements
        Path yamlFile = outputFolder.resolve("META-INF/rewrite/%s.DirectReplacements.yml".formatted(source.getName()));
        Files.write(
                yamlFile,
                """
                        ---
                        type: specs.openrewrite.org/v1beta/recipe
                        name: %1$s.DirectReplacements
                        displayName: Replace `%1$s` with `%2$s`
                        description: Replace `%1$s` method calls with calls to `%2$s`.
                        recipeList:
                        """
                        .formatted(source.getName(), target.getName())
                        .getBytes());
        // Append recipe list
        Files.write(
                yamlFile,
                methodsPatterns.stream()
                        .map(m ->
                                """
                                          - org.openrewrite.java.ChangeMethodTargetToStatic:
                                              methodPattern: %s %s
                                              fullyQualifiedTargetTypeName: %s
                                        """
                                        .formatted(source.getName(), m, target.getName()))
                        .collect(Collectors.joining())
                        .getBytes(),
                StandardOpenOption.APPEND);
    }

    private static void writeIndirectReplacements(
            Class<?> source, Class<?> target, Path outputFolder, Map<String, String> indirectReplacements) throws IOException {
        // Write recipes for methods without direct replacement
        Path indirectReplacementFile =
                outputFolder.resolve("META-INF/rewrite/%s.IndirectReplacements.yml".formatted(source.getName()));
        Files.write(
                indirectReplacementFile,
                """
                        ---
                        type: specs.openrewrite.org/v1beta/recipe
                        name: %1$s.IndirectReplacements
                        displayName: Replace `%1$s` with `%2$s`
                        description: Replace `%1$s` method calls with calls to `%2$s`.
                        recipeList:
                        """
                        .formatted(source.getName(), target.getName())
                        .getBytes());
        // Append recipe list
        Files.write(
                indirectReplacementFile,
                indirectReplacements.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry ->
                                """
                                          - org.openrewrite.java.ChangeMethodTargetToStatic:
                                              methodPattern: %1$s %2$s
                                              fullyQualifiedTargetTypeName: %3$s
                                          - org.openrewrite.java.ChangeMethodName:
                                              methodPattern: %3$s %2$s
                                              newMethodName: %4$s
                                        """
                                        .formatted(
                                                source.getName(),
                                                entry.getKey(),
                                                target.getName(),
                                                entry.getValue()))
                        .collect(Collectors.joining())
                        .getBytes(),
                StandardOpenOption.APPEND);
    }


    private static void writeFindManualReplacements(
            Class<?> source, Class<?> target, Path outputFolder, Set<String> methodsPatterns) throws IOException {
        // Write recipes for methods without direct replacement
        Path directReplacementFile =
                outputFolder.resolve("META-INF/rewrite/%s.FindManualReplacements.yml".formatted(source.getName()));
        Files.write(
                directReplacementFile,
                """
                        ---
                        type: specs.openrewrite.org/v1beta/recipe
                        name: %1$s.FindManualReplacements
                        displayName: Replace `%1$s` with `%2$s`
                        description: Replace `%1$s` method calls with calls to `%2$s`.
                        recipeList:
                        """
                        .formatted(source.getName(), target.getName())
                        .getBytes());
        // Append recipe list
        Files.write(
                directReplacementFile,
                methodsPatterns.stream()
                        .map(m ->
                                """
                                          - org.openrewrite.java.search.FindMethods:
                                              methodPattern: %s %s
                                        """
                                        .formatted(source.getName(), m))
                        .collect(Collectors.joining())
                        .getBytes(),
                StandardOpenOption.APPEND);
    }

    @Test
    void assertGeneratedRecipes(@TempDir Path tempDir) throws Exception {
        Path rewriteDir = tempDir.resolve("META-INF/rewrite");
        Files.createDirectories(rewriteDir);
        generateRecipe(org.codehaus.plexus.util.StringUtils.class, org.apache.commons.lang3.StringUtils.class, tempDir);
        Path directReplacements = rewriteDir.resolve("org.codehaus.plexus.util.StringUtils.DirectReplacements.yml");
        assertThat(Files.readString(directReplacements))
                .startsWith(
                        """
                                ---
                                type: specs.openrewrite.org/v1beta/recipe
                                name: org.codehaus.plexus.util.StringUtils.DirectReplacements
                                displayName: Replace `org.codehaus.plexus.util.StringUtils` with `org.apache.commons.lang3.StringUtils`
                                description: Replace `org.codehaus.plexus.util.StringUtils` method calls with calls to `org.apache.commons.lang3.StringUtils`.
                                recipeList:
                                  - org.openrewrite.java.ChangeMethodTargetToStatic:
                                      methodPattern: org.codehaus.plexus.util.StringUtils abbreviate(java.lang.String,int)
                                      fullyQualifiedTargetTypeName: org.apache.commons.lang3.StringUtils
                                  - org.openrewrite.java.ChangeMethodTargetToStatic:
                                      methodPattern: org.codehaus.plexus.util.StringUtils abbreviate(java.lang.String,int,int)
                                      fullyQualifiedTargetTypeName: org.apache.commons.lang3.StringUtils
                                """);
        Path indirectReplacements =
                rewriteDir.resolve("org.codehaus.plexus.util.StringUtils.FindManualReplacements.yml");
        assertThat(Files.readString(indirectReplacements))
                .startsWith(
                        """
                                ---
                                type: specs.openrewrite.org/v1beta/recipe
                                name: org.codehaus.plexus.util.StringUtils.FindManualReplacements
                                displayName: Replace `org.codehaus.plexus.util.StringUtils` with `org.apache.commons.lang3.StringUtils`
                                description: Replace `org.codehaus.plexus.util.StringUtils` method calls with calls to `org.apache.commons.lang3.StringUtils`.
                                recipeList:
                                  - org.openrewrite.java.search.FindMethods:
                                      methodPattern: org.codehaus.plexus.util.StringUtils addAndDeHump(java.lang.String)
                                  - org.openrewrite.java.search.FindMethods:
                                      methodPattern: org.codehaus.plexus.util.StringUtils capitaliseAllWords(java.lang.String)
                                """);
    }

    @Test
    void generatePlexusToCommons() throws Exception {
        Path metaInfRewrite = Path.of("src/main/resources");
        generateRecipe(
                org.codehaus.plexus.util.StringUtils.class, org.apache.commons.lang3.StringUtils.class, metaInfRewrite);
    }

    @Test
    void generateMavenSharedToCommons() throws Exception {
        Path metaInfRewrite = Path.of("src/main/resources");
        generateRecipe(
                org.apache.maven.shared.utils.StringUtils.class,
                org.apache.commons.lang3.StringUtils.class,
                metaInfRewrite);
    }
}
