package com.modusbox.client.router;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.processor.EncodeAuthHeader;
import com.modusbox.client.processor.TrimMFICode;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;


public class TransfersRouter extends RouteBuilder {

    private final EncodeAuthHeader encodeAuthHeader = new EncodeAuthHeader();
    private final TrimMFICode trimMFICode = new TrimMFICode();
    private final RouteExceptionHandlingConfigurer exceptionHandlingConfigurer = new RouteExceptionHandlingConfigurer();

    private static final String TIMER_NAME_POST = "histogram_post_transfers_timer";
    private static final String TIMER_NAME_PUT = "histogram_put_transfers_timer";

    public static final Counter reqCounterPost = Counter.build()
            .name("counter_post_transfers_requests_total")
            .help("Total requests for POST /transfers.")
            .register();

    private static final Histogram reqLatencyPost = Histogram.build()
            .name("histogram_post_transfers_request_latency")
            .help("Request latency in seconds for POST /transfers.")
            .register();

    public static final Counter reqCounterPut = Counter.build()
            .name("counter_put_transfers_requests_total")
            .help("Total requests for PUT /transfers.")
            .register();

    private static final Histogram reqLatencyPut = Histogram.build()
            .name("histogram_put_transfers_request_latency")
            .help("Request latency in seconds for PUT /transfers.")
            .register();

    public void configure() {

        // Add our global exception handling strategy
        exceptionHandlingConfigurer.configureExceptionHandling(this);

        from("direct:postTransfers").routeId("com.modusbox.postTransfers").doTry()
                .process(exchange -> {
                    reqCounterPost.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_POST, reqLatencyPost.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Request received POST /transfers/${header.transferId}', " +
                        "'Tracking the request', " +
                        "'Call the Mambu API,  Track the response', " +
                        "'fspiop-source: ${header.fspiop-source} Input Payload: ${body}')") // default logger
                /*
                 * BEGIN processing
                 */
                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/postTransfersRequest.ds"))
                .setBody(simple("${body.content}"))

                .setHeader("idValue", simple("${body?.get('accountId')}"))
                .setHeader("amount", simple("${body?.get('amount')}"))
                .process(trimMFICode)

                // Fetch the loan account by ID so we can find customer ID
                .to("direct:getLoanById")

                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/postTransactionRequest.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()

                .removeHeaders("CamelHttp*")
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Accept", constant("application/vnd.mambu.v2+json"))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setProperty("authHeader", simple("${properties:dfsp.username}:${properties:dfsp.password}"))
                .process(encodeAuthHeader)

                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Calling Mambu API, postTransaction, " +
                        "POST {{dfsp.host}}/loans/${exchangeProperty.getLoanByIdResponse[0]?.get('id')}/repayment-transactions ', " +
                        "'Tracking the request', 'Track the response', 'Input Payload: ${body}')")
                .toD("{{dfsp.host}}/loans/${exchangeProperty.getLoanByIdResponse[0]?.get('id')}/repayment-transactions ")
                .transform(datasonnet("resource:classpath:mappings/postTransfersResponse.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Response from Mambu API, postTransaction: ${body}', " +
                        "'Tracking the response', " +
                        "'Verify the response', null)")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Response for POST /transfers/${header.transferId}', " +
                        "'Tracking the response', " +
                        "null, " +
                        "'Output Payload: ${body}')") // default logger
                .doCatch(CCCustomException.class)
                    .to("direct:extractCustomErrors")
                .doFinally().process(exchange -> {
                    ((Histogram.Timer) exchange.getProperty(TIMER_NAME_POST)).observeDuration(); // stop Prometheus Histogram metric
                }).end()
        ;

        from("direct:putTransfersByTransferId").routeId("com.modusbox.putTransfersByTransferId").doTry()
                .process(exchange -> {
                    reqCounterPut.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_PUT, reqLatencyPut.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Request received PUT /transfers/${header.transferId}', " +
                        "'Tracking the request', " +
                        "'Call the Mambu API,  Track the response', " +
                        "'fspiop-source: ${header.fspiop-source} Input Payload: ${body}')") // default logger
                /*
                 * BEGIN processing
                 */
//                .marshal().json()
//                .transform(datasonnet("resource:classpath:mappings/putTransfersRequest.ds"))
//                .setBody(simple("${body.content}"))
//
//                .setHeader("idValue", simple("${body?.get('accountId')}"))
//                .setHeader("amount", simple("${body?.get('amount')}"))
//                .process(trimMFICode)
//
//                // Fetch the loan account by ID so we can find customer ID
//                .to("direct:getLoanById")
//
//                .marshal().json()
//                .transform(datasonnet("resource:classpath:mappings/postTransactionRequest.ds"))
//                .setBody(simple("${body.content}"))
//                .marshal().json()
//
//                .removeHeaders("CamelHttp*")
//                .setHeader("Content-Type", constant("application/json"))
//                .setHeader("Accept", constant("application/vnd.mambu.v2+json"))
//                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
//                .setProperty("authHeader", simple("${properties:dfsp.username}:${properties:dfsp.password}"))
//                .process(encodeAuthHeader)
//
//                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
//                        "'Calling Mambu API, postTransaction, " +
//                           "POST {{dfsp.host}}/loans/${exchangeProperty.getLoanByIdResponse[0]?.get('id')}/repayment-transactions ', " +
//                        "'Tracking the request', 'Track the response', 'Input Payload: ${body}')")
//                .toD("{{dfsp.host}}/loans/${exchangeProperty.getLoanByIdResponse[0]?.get('id')}/repayment-transactions ")
//                .to("bean:customJsonMessage?method=logJsonMessage(" +
//                        "'info', " +
//                        "${header.X-CorrelationId}, " +
//                        "'Response from Mambu API, postTransaction: ${body}', " +
//                        "'Tracking the response', " +
//                        "'Verify the response', null)")
//                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
//                .setBody(constant(""))
//                /*
//                 * END processing
//                 */
//                .to("bean:customJsonMessage?method=logJsonMessage(" +
//                        "'info', " +
//                        "${header.X-CorrelationId}, " +
//                        "'Response for PUT /transfers/${header.transferId}', " +
//                        "'Tracking the response', " +
//                        "null, " +
//                        "'Output Payload: ${body}')") // default logger
                .doFinally().process(exchange -> {
                    ((Histogram.Timer) exchange.getProperty(TIMER_NAME_PUT)).observeDuration(); // stop Prometheus Histogram metric
                }).end()
        ;

    }
}
