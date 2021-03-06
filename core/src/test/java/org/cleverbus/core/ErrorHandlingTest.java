/*
 * Copyright (C) 2015
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.cleverbus.core;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.cleverbus.api.asynch.AsynchConstants;
import org.cleverbus.api.exception.IntegrationException;
import org.cleverbus.api.exception.LockFailureException;
import org.cleverbus.api.exception.NoDataFoundException;
import org.cleverbus.api.route.AbstractBasicRoute;
import org.cleverbus.test.ErrorTestEnum;

import org.apache.camel.CamelException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.junit.Before;
import org.junit.Test;


/**
 * Test suite that verifies common error handling policy in {@link AbstractBasicRoute}.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 */
public class ErrorHandlingTest extends AbstractCoreTest {

    private static final String ERROR_HANDLING_PROP = "errorHandling";
    private static final String ERROR_FATAL_PROP = "errorFatal";

    @Produce(uri = "direct:start")
    private ProducerTemplate producer;

    @EndpointInject(uri = "mock:test")
    private MockEndpoint mock;

    @Before
    public void createErrorRoutes() throws Exception {
        // prepare routes
        RouteBuilder testRoute = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(AsynchConstants.URI_ERROR_HANDLING)
                        .setProperty(ERROR_HANDLING_PROP, constant(true))
                        .to("mock:test");

                from(AsynchConstants.URI_ERROR_FATAL)
                        .setProperty(ERROR_FATAL_PROP, constant(true))
                        .to("mock:test");
            }
        };

        getCamelContext().addRoutes(testRoute);
    }

    @Test
    public void testNoException() throws Exception {
        class TestRoute extends AbstractBasicRoute {
            @Override
            protected void doConfigure() throws Exception {
                from("direct:start")
                    .to("mock:test");
            }
        }

        getCamelContext().addRoutes(new TestRoute());

        processAndVerify(false, false, false);
    }

    @Test
    public void testSyncException() throws Exception {
        class TestRoute extends AbstractBasicRoute {
            @Override
            protected void doConfigure() throws Exception {
                from("direct:start")
                    .throwException(new IntegrationException(ErrorTestEnum.E200));
            }
        }

        getCamelContext().addRoutes(new TestRoute());

        mock.expectedMessageCount(1);

        // action
        try {
            producer.sendBody("some body");
            fail("No expected IntegrationException");
        } catch (Exception ex) {
            assertThat(ExceptionUtils.indexOfThrowable(ex, IntegrationException.class), is(1));
        }
    }

    @Test
    public void testAsyncException() throws Exception {
        class TestRoute extends AbstractBasicRoute {
            @Override
            protected void doConfigure() throws Exception {
                from("direct:start")
                    .throwException(new IllegalArgumentException("wrong param"));
            }
        }

        getCamelContext().addRoutes(new TestRoute());

        processAndVerify(true, true, false);
    }

    @Test
    public void testAsyncFatalException() throws Exception {
        class TestRoute extends AbstractBasicRoute {
            @Override
            protected void doConfigure() throws Exception {
                from("direct:start")
                    .throwException(new NoDataFoundException("no data"));
            }
        }

        getCamelContext().addRoutes(new TestRoute());

        processAndVerify(true, false, true);
    }

    @Test
    public void testAsyncLockException() throws Exception {
        class TestRoute extends AbstractBasicRoute {
            @Override
            protected void doConfigure() throws Exception {
                from("direct:start")
                    .throwException(new LockFailureException("no lock"));
            }
        }

        getCamelContext().addRoutes(new TestRoute());

        processAndVerify(true, true, false);
    }

    @Test
    public void testAsyncNestedFatalException() throws Exception {
        class TestRoute extends AbstractBasicRoute {
            @Override
            protected void doConfigure() throws Exception {
                from("direct:start")
                    .throwException(new CamelException(new NoDataFoundException("no data")));
            }
        }

        getCamelContext().addRoutes(new TestRoute());

        processAndVerify(true, false, true);
    }

    private void processAndVerify(boolean asynch, boolean expHandlingFlag, boolean expFatalFlag) throws Exception {
        mock.expectedMessageCount(1);

        // action
        if (asynch) {
            producer.sendBodyAndHeader("some body", AsynchConstants.ASYNCH_MSG_HEADER, Boolean.TRUE);
        } else {
            producer.sendBody("some body");
        }

        // verify
        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        Boolean errHandlingFlag = (Boolean) exchange.getProperty(ERROR_HANDLING_PROP);
        Boolean errFatalFlag = (Boolean) exchange.getProperty(ERROR_FATAL_PROP);

        assertThat(BooleanUtils.isTrue(errHandlingFlag), is(expHandlingFlag));
        assertThat(BooleanUtils.isTrue(errFatalFlag), is(expFatalFlag));
    }
}
