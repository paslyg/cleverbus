/*
 * Copyright 2014 the original author or authors.
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

package org.cleverbus.core.reqres;

import static org.cleverbus.core.reqres.RequestResponseUtils.transformBody;

import java.util.EventObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.cleverbus.api.asynch.AsynchConstants;
import org.cleverbus.api.entity.Message;
import org.cleverbus.api.entity.Request;
import org.cleverbus.api.event.EventNotifier;
import org.cleverbus.api.event.EventNotifierBase;
import org.cleverbus.common.log.Log;

import org.apache.camel.Exchange;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;


/**
 * Listens to request sending (event = {@link ExchangeSendingEvent}).
 * If endpoint URI is successfully filtered and saving is enabled then request is saved to DB.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 * @see ResponseReceiveEventNotifier
 * @since 0.4
 */
@EventNotifier
public class RequestSendingEventNotifier extends EventNotifierBase<ExchangeSendingEvent> {

    /**
     * Header name for saving request for next use.
     */
    static final String SAVE_REQ_HEADER = "SAVE_REQ_HEADER";

    /**
     * True for enabling saving requests/responses for filtered endpoints URI.
     */
    @Value("${requestSaving.enable}")
    private boolean enable;

    /**
     * Pattern for filtering endpoints URI which requests/response should be saved.
     */
    @Value("${requestSaving.endpointFilter}")
    private String endpointFilter;

    private Pattern endpointFilterPattern;

    @Autowired
    private RequestResponseService requestResponseService;


    @PostConstruct
    public void compilePattern() {
        endpointFilterPattern = Pattern.compile(endpointFilter);
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return enable && super.isEnabled(event);
    }

    @Override
    protected void doNotify(ExchangeSendingEvent event) throws Exception {
        String endpointUri = event.getEndpoint().getEndpointUri();

        if (filter(endpointUri, endpointFilterPattern)) {
            Message msg = event.getExchange().getIn().getHeader(AsynchConstants.MSG_HEADER, Message.class);

            // create request a transforms data to string to store
            String reqBody = transformBody(((Exchange) event.getSource()).getIn());
            String joinId = createResponseJoinId(event.getExchange());

            Request req = Request.createRequest(endpointUri, joinId, reqBody, msg);

            try {
                // save request
                requestResponseService.insertRequest(req);

                // add to exchange for later use when response arrives
                event.getExchange().getIn().setHeader(SAVE_REQ_HEADER, req);
            } catch (Exception ex) {
                Log.error("Request didn't saved.", ex);
            }
        }
    }

    /**
     * Returns {@code true} if specified URI matches specified pattern.
     *
     * @param endpointURI the endpoint URI
     * @param pattern pattern
     * @return {@code true} if specified URI matches at least one of specified patterns otherwise {@code false}
     */
    static boolean filter(String endpointURI, Pattern pattern) {
        Assert.hasText(endpointURI, "the endpointURI must be defined");

        Matcher matcher = pattern.matcher(endpointURI);
        return matcher.matches();
    }

    /**
     * Creates response join ID.
     * <p/>
     * Request is uniquely identified by its URI and response join ID.
     * Both these attributes helps to join right request and response together.
     *
     * @param exchange the exchange
     * @return join ID
     */
    static String createResponseJoinId(Exchange exchange) {
        // exchange ID is unique but can be changed when the whole exchange changes
        //  - "the wire tap creates a copy of the incoming message, and the copied message will use a new exchange ID
        //  because it’s being routed as a separate process."
        String joinId = exchange.getExchangeId();

        Message msg = exchange.getIn().getHeader(AsynchConstants.MSG_HEADER, Message.class);
        if (msg != null) {
            // if it's asynchronous message then join ID is correlation ID
            joinId = msg.getCorrelationId();
        }

        return joinId;
    }
}
