/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.jvm.compiler

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.java.lazy.descriptors.LazyJavaPackageFragment
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.JvmResolveUtil
import org.jetbrains.kotlin.test.ConfigurationKind
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.MockLibraryUtil
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.test.testFramework.KtUsefulTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LoadJavaPackageAnnotationsTest : KtUsefulTestCase() {
    companion object {
        private val TEST_DATA_PATH = "compiler/testData/loadJavaPackageAnnotations/"
    }

    private fun doTest(useJavac: Boolean, configurator: (CompilerConfiguration) -> Unit) {
        val environment =
                KotlinCoreEnvironment.createForTests(
                        myTestRootDisposable,
                        KotlinTestUtils.newConfiguration(
                                ConfigurationKind.ALL, TestJdkKind.FULL_JDK, KotlinTestUtils.getAnnotationsJar()
                        ).also {
                            if (useJavac) {
                                it.put(JVMConfigurationKeys.USE_JAVAC, true)
                            }
                            configurator(it)
                        },
                        EnvironmentConfigFiles.JVM_CONFIG_FILES
                ).apply {
                    if (useJavac) {
                        registerJavac()
                    }
                }
        val moduleDescriptor = JvmResolveUtil.analyze(
                environment
        ).moduleDescriptor

        val packageFragmentDescriptor =
                (moduleDescriptor as ModuleDescriptorImpl)
                        .packageFragmentProvider.getPackageFragments(FqName("test"))
                        .singleOrNull {
                            it.getMemberScope().getContributedClassifier(Name.identifier("A"), NoLookupLocation.FROM_TEST) != null
                        }.let { assertInstanceOf(it, LazyJavaPackageFragment::class.java) }

        val annotation = packageFragmentDescriptor.getPackageAnnotations().findAnnotation(FqName("test.Ann"))
        assertNotNull(annotation)

        val singleAnnotation = packageFragmentDescriptor.getPackageAnnotations().singleOrNull()
        assertNotNull(singleAnnotation)

        val annotationFqName = singleAnnotation!!.type.constructor.declarationDescriptor?.fqNameSafe

        assertEquals(FqName("test.Ann"), annotationFqName)
    }

    @Test
    fun testAnnotationFromSource() {
        doTest(useJavac = false) {
            it.addJavaSourceRoots(listOf(File(TEST_DATA_PATH)))
        }
    }

    @Test
    fun testAnnotationFromSourceWithJavac() {
        doTest(useJavac = true) {
            it.addJavaSourceRoots(listOf(File(TEST_DATA_PATH)))
        }
    }

    @Rule
    @JvmField
    var tmpDir = TemporaryFolder()

    @Test
    fun testAnnotationFromCompiledCode() {
        val jar = prepareJar()

        doTest(useJavac = false) {
            it.addJvmClasspathRoot(jar)
        }
    }

    @Test
    fun testAnnotationFromCompiledCodeWithJavac() {
        val jar = prepareJar()

        doTest(useJavac = true) {
            it.addJvmClasspathRoot(jar)
        }
    }

    private fun prepareJar(): File {
        tmpDir.create()
        val outDir = tmpDir.newFolder("javac-result")
        val jar = MockLibraryUtil.compileLibraryToJar(
                TEST_DATA_PATH, outDir, "result.jar",
                /* addSources = */false, /* allowKotlinPackage = */ false
        )
        return jar
    }

}
