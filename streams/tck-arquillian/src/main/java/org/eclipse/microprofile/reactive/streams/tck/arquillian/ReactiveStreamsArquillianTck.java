/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.eclipse.microprofile.reactive.streams.tck.arquillian;

import org.eclipse.microprofile.reactive.streams.CompletionSubscriber;
import org.eclipse.microprofile.reactive.streams.spi.Graph;
import org.eclipse.microprofile.reactive.streams.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.spi.UnsupportedStageException;
import org.eclipse.microprofile.reactive.streams.tck.ReactiveStreamsTck;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.TestNG;
import org.testng.TestRunner;
import org.testng.annotations.Factory;
import org.testng.annotations.Listeners;

import java.util.concurrent.CompletionStage;

/**
 * Test runner for running the TCK in Arquillian.
 * <p>
 * Because the Reactive Streams TCK uses factories, which are incompatible with the Arquillian TestNG runner, we
 * implement our own TestNG support.
 */
@Listeners(ArquillianTestNGLifecycleListener.class)
public class ReactiveStreamsArquillianTck {
    @Deployment
    public static JavaArchive tckDeployment() {
        return ShrinkWrap.create(JavaArchive.class);
            // Add TestNG
            //.addPackages(true, TestNG.class.getPackage())
            // Add everything from the TCK
            //.addPackages(true, ReactiveStreamsTck.class.getPackage())
            // Add the reactive streams TCK
            //.addPackages(true, TestEnvironment.class.getPackage());
            // Add this TCK support
            //.addPackages(true, ContainerTestRunner.class.getPackage())
            // And set the test runner
            //.addAsServiceProvider(TestRunner.class, ContainerTestRunner.class);
    }

    /**
     * Checkstyle says this is a utility class because it has only static methods, and so must have a private constructor,
     * but TestNG needs to instantiate it.
     */
    public void checkstyleIsDumb() {

    }

    @Factory
    public static Object[] allTests() {
        // Instantiate a mock tck with a mock engine so that we can discover all the test methods
        return new ReactiveStreamsTck<ReactiveStreamsEngine>(new TestEnvironment()) {
            @Override
            protected ReactiveStreamsEngine createEngine() {
                return new ReactiveStreamsEngine() {
                    @Override
                    public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
                        return null;
                    }

                    @Override
                    public <T, R> CompletionSubscriber<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
                        return null;
                    }

                    @Override
                    public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
                        return null;
                    }

                    @Override
                    public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
                        return null;
                    }
                };
            }
        }.allTests();
    }

}
