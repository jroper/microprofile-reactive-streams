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

import org.eclipse.microprofile.reactive.streams.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.tck.ReactiveStreamsTck;
import org.jboss.arquillian.container.test.spi.TestRunner;
import org.jboss.arquillian.test.spi.LifecycleMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;
import org.jboss.arquillian.test.spi.TestRunnerAdaptorBuilder;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.SkipException;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ContainerTestRunner implements TestRunner {

    static {
        System.out.println("I got loaded.");
    }

    private Map<Class<?>, Object> allTestsMap;

    @Inject
    public void setEngine(ReactiveStreamsEngine engine) {
        System.out.println("I got an engine!! " + engine);
        allTestsMap = new HashMap<>();

        Object[] allTests = new ReactiveStreamsTck<ReactiveStreamsEngine>(new TestEnvironment()) {
            @Override
            protected ReactiveStreamsEngine createEngine() {
                return engine;
            }
        }.allTests();

        for (Object test : allTests) {
            allTestsMap.put(test.getClass(), test);
        }
    }


    @Override
    public TestResult execute(Class<?> testClass, String methodName) {
        System.out.println("Hello!!! Executing " + methodName + " on " + testClass);
        Method method;
        try {
            method = testClass.getMethod(methodName);
        }
        catch (NoSuchMethodException e) {
            return TestResult.failed(e);
        }

        if (allTestsMap == null) {
            try {
                // Trigger before on this, this will ensure that we'll get injected, which will give us the engine.
                TestRunnerAdaptorBuilder.build().before(this, method, LifecycleMethodExecutor.NO_OP);
                if (allTestsMap == null) {
                    return TestResult.failed(new AssertionError("Triggered before, but the engine wasn't injected into us."));
                }
            }
            catch (Exception e) {
                return TestResult.failed(e);
            }
        }

        Object test = allTestsMap.get(testClass);
        if (test == null) {
            TestResult.failed(new AssertionError("Test class not found in all tests map"));
        }
        try {
            method.invoke(test);
            return TestResult.passed();
        }
        catch (IllegalAccessException e) {
            return TestResult.failed(e);
        }
        catch (InvocationTargetException e) {
            Throwable error = e.getCause();
            if (error instanceof SkipException) {
                return TestResult.skipped(error);
            }
            else {
                return TestResult.failed(error);
            }
        }
    }
}
