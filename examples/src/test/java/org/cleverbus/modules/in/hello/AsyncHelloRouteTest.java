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

package org.cleverbus.modules.in.hello;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.cleverbus.api.asynch.AsynchConstants;
import org.cleverbus.api.asynch.model.ConfirmationTypes;
import org.cleverbus.common.Tools;
import org.cleverbus.modules.AbstractModulesDbTest;
import org.cleverbus.modules.in.hello.model.AsyncHelloResponse;
import org.cleverbus.test.ActiveRoutes;
import org.cleverbus.test.ExternalSystemTestEnum;
import org.cleverbus.test.ServiceTestEnum;
import org.cleverbus.test.TestUtils;
import org.cleverbus.test.route.TestWsUriBuilder;

import org.apache.camel.EndpointInject;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;


/**
 * Test suite for {@link AsyncHelloRoute}.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 */
@ActiveRoutes(classes = AsyncHelloRoute.class)
public class AsyncHelloRouteTest extends AbstractModulesDbTest {

    private static final String REQ_XML =
            "<asyncHelloRequest xmlns=\"http://cleverbus.org/ws/HelloService-v1\">"
          + "    <name>Mr. Parker</name>"
          + "</asyncHelloRequest>";

    @Produce(uri = TestWsUriBuilder.URI_WS_IN + "asyncHelloRequest")
    private ProducerTemplate producer;

    @EndpointInject(uri = "mock:test")
    private MockEndpoint mock;

    @Test
    public void testRouteIn_responseOK() throws Exception {
        getCamelContext().getRouteDefinition(AsyncHelloRoute.ROUTE_ID_ASYNC_IN)
                .adviceWith(getCamelContext(), new AdviceWithRouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        TestUtils.replaceToAsynch(this);

                        weaveAddLast().to("mock:test");
                    }
                });

        mock.expectedMessageCount(1);

        // action
        String output = producer.requestBody((Object) REQ_XML, String.class);

        // verify
        mock.assertIsSatisfied();

        // verify OK response
        AsyncHelloResponse res = Tools.unmarshalFromXml(output, AsyncHelloResponse.class);
        assertThat(res.getConfirmAsyncHello().getStatus(), is(ConfirmationTypes.OK));

        // check message header
        Message inMsg = mock.getExchanges().get(0).getIn();
        assertThat((String) inMsg.getHeader(AsynchConstants.OBJECT_ID_HEADER), is("Mr. Parker"));
    }

    @Test
    public void testRouteOut() throws Exception {
        getCamelContext().getRouteDefinition(AsyncHelloRoute.ROUTE_ID_ASYNC_OUT)
                .adviceWith(getCamelContext(), new AdviceWithRouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        weaveAddLast().to("mock:test");
                    }
                });

        mock.expectedMessageCount(1);

        // action
        org.cleverbus.api.entity.Message msg = createAndSaveMessage(ExternalSystemTestEnum.CRM,
                ServiceTestEnum.ACCOUNT, "testOp", "payload");

        producer.sendBodyAndHeader(AsyncHelloRoute.URI_ASYNC_HELLO_OUT, REQ_XML, AsynchConstants.MSG_HEADER, msg);

        // verify
        mock.assertIsSatisfied();

        // nothing to verify
    }
}
