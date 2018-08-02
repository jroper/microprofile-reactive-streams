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

import org.jboss.arquillian.test.spi.LifecycleMethodExecutor;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;
import org.jboss.arquillian.test.spi.TestRunnerAdaptor;
import org.jboss.arquillian.test.spi.TestRunnerAdaptorBuilder;
import org.jboss.arquillian.test.spi.execution.SkippedTestExecutionException;
import org.testng.IClassListener;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestClass;
import org.testng.ITestResult;
import org.testng.SkipException;

import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Mostly copied from Arquillian TestNG support class
 */
public class ArquillianTestNGLifecycleListener implements ISuiteListener, IClassListener, IInvokedMethodListener, IHookable {
    private static ThreadLocal<TestRunnerAdaptor> deployableTest = new ThreadLocal<>();
    private static ThreadLocal<Stack<Cycle>> cycleStack = ThreadLocal.withInitial(Stack::new);

    @Override
    public void onStart(ISuite suite) {
        if (deployableTest.get() == null) {
            TestRunnerAdaptor adaptor = TestRunnerAdaptorBuilder.build();
            try {
                adaptor.beforeSuite();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            deployableTest.set(adaptor); // don't set TestRunnerAdaptor if beforeSuite fails
            cycleStack.get().push(Cycle.BEFORE_SUITE);

        }
    }

    @Override
    public void onFinish(ISuite suite) {
        // Now after suite
        if (deployableTest.get() == null) {
            return; // beforeSuite failed
        }
        if (cycleStack.get().empty()) {
            return;
        }
        if (cycleStack.get().peek() != Cycle.BEFORE_SUITE) {
            return; // Arquillian lifecycle called out of order, expected " + Cycle.BEFORE_SUITE
        }
        else {
            cycleStack.get().pop();
        }
        try {
            deployableTest.get().afterSuite();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        deployableTest.get().shutdown();
        deployableTest.set(null);
        deployableTest.remove();
        cycleStack.set(null);
        cycleStack.remove();
    }

    @Override
    public void onBeforeClass(ITestClass testClass) {
        // And now execute before class, we want the entire suite to run in one deployment.
        verifyTestRunnerAdaptorHasBeenSet();
        cycleStack.get().push(Cycle.BEFORE_CLASS);
        try {
            deployableTest.get().beforeClass(testClass.getRealClass(), LifecycleMethodExecutor.NO_OP);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onAfterClass(ITestClass testClass) {
        // First, run after class callback
        if (cycleStack.get().empty()) {
            return;
        }
        if (cycleStack.get().peek() != Cycle.BEFORE_CLASS) {
            return; // Arquillian lifecycle called out of order, expected " + Cycle.BEFORE_CLASS
        }
        else {
            cycleStack.get().pop();
        }
        verifyTestRunnerAdaptorHasBeenSet();
        try {
            deployableTest.get().afterClass(testClass.getRealClass(), LifecycleMethodExecutor.NO_OP);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        verifyTestRunnerAdaptorHasBeenSet();
        cycleStack.get().push(Cycle.BEFORE);
        try {
            deployableTest.get().before(testResult.getInstance(),
                method.getTestMethod().getConstructorOrMethod().getMethod(), LifecycleMethodExecutor.NO_OP);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (cycleStack.get().empty()) {
            return;
        }
        if (cycleStack.get().peek() != Cycle.BEFORE) {
            return; // Arquillian lifecycle called out of order, expected " + Cycle.BEFORE_CLASS
        }
        else {
            cycleStack.get().pop();
        }
        verifyTestRunnerAdaptorHasBeenSet();
        try {
            deployableTest.get().after(testResult.getInstance(), method.getTestMethod().getConstructorOrMethod().getMethod(),
                LifecycleMethodExecutor.NO_OP);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void run(final IHookCallBack callback, final ITestResult testResult) {
        System.out.println("I've been told to run " + testResult.getTestName());
        verifyTestRunnerAdaptorHasBeenSet();
        TestResult result;
        try {
            result = deployableTest.get().test(new TestMethodExecutor() {
                public void invoke(Object... parameters) throws Throwable {
                    System.out.println("Now I'm in the inner of " + testResult.getTestName());
                    /*
                     *  The parameters are stored in the InvocationHandler, so we can't set them on the test result directly.
                     *  Copy the Arquillian found parameters to the InvocationHandlers parameters
                     */
                    copyParameters(parameters, callback.getParameters());
                    callback.runTestMethod(testResult);

                    // Parameters can be contextual, so extract information
                    swapWithClassNames(callback.getParameters());
                    testResult.setParameters(callback.getParameters());
                    if (testResult.getThrowable() != null) {
                        throw testResult.getThrowable();
                    }
                }

                private void copyParameters(Object[] source, Object[] target) {
                    for (int i = 0; i < source.length; i++) {
                        if (source[i] != null) {
                            target[i] = source[i];
                        }
                    }
                }

                private void swapWithClassNames(Object[] source) {
                    // clear parameters. they can be contextual and might fail TestNG during the report writing.
                    for (int i = 0; source != null && i < source.length; i++) {
                        Object parameter = source[i];
                        if (parameter != null) {
                            source[i] = parameter.toString();
                        }
                        else {
                            source[i] = "null";
                        }
                    }
                }

                public Method getMethod() {
                    return testResult.getMethod().getConstructorOrMethod().getMethod();
                }

                public Object getInstance() {
                    return testResult.getInstance();
                }
            });
            Throwable throwable = result.getThrowable();
            if (throwable != null) {
                if (result.getStatus() == TestResult.Status.SKIPPED) {
                    if (throwable instanceof SkippedTestExecutionException) {
                        result.setThrowable(new SkipException(throwable.getMessage()));
                    }
                }
                testResult.setThrowable(result.getThrowable());

                // setting status as failed.
                testResult.setStatus(2);
            }

            // calculate test end time. this is overwritten in the testng invoker..
            testResult.setEndMillis((result.getStart() - result.getEnd()) + testResult.getStartMillis());
        }
        catch (Exception e) {
            testResult.setThrowable(e);
        }
    }

    private void verifyTestRunnerAdaptorHasBeenSet() {
        if (deployableTest.get() == null) {
            throw new IllegalStateException("No TestRunnerAdaptor found, @BeforeSuite has not been called");
        }
    }

    private enum Cycle {
        BEFORE_SUITE, BEFORE_CLASS, BEFORE
    }
}
