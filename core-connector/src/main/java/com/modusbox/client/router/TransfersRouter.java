package com.modusbox.client.router;

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

    private static final String ROUTE_ID = "com.modusbox.putTransfersByTransferId";
    private static final String COUNTER_NAME = "counter_put_transfers_requests";
    private static final String TIMER_NAME = "histogram_put_transfers_timer";
    private static final String HISTOGRAM_NAME = "histogram_put_transfers_requests_latency";

    public static final Counter requestCounter = Counter.build()
            .name(COUNTER_NAME)
            .help("Total requests for POST /transfers.")
            .register();

    private static final Histogram requestLatency = Histogram.build()
            .name(HISTOGRAM_NAME)
            .help("Request latency in seconds for POST /transfers.")
            .register();

    public void configure() {
        // Add our global exception handling strategy
        exceptionHandlingConfigurer.configureExceptionHandling(this);

        from("direct:postTransfers")
                .routeId("com.modusbox.postTransfers")
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Request received, POST /transfers', " +
                        "null, null, 'Input Payload: ${body}')")
                .setBody(constant("{\"homeTransactionId\": \"1234\"}"))
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Final Response: ${body}', " +
                        "null, null, 'Response of POST /transfers API')")
        ;

        from("direct:putTransfersByTransferId").routeId(ROUTE_ID).doTry()
            .process(exchange -> {
                requestCounter.inc(1); // increment Prometheus Counter metric
                exchange.setProperty(TIMER_NAME, requestLatency.startTimer()); // initiate Prometheus Histogram metric
            })
            .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                                                                "'Request received, PUT /transfers/${header.transferId}', " +
                                                                "null, null, null)")
            .marshal().json()
            .transform(datasonnet("resource:classpath:mappings/putTransfersRequest.ds"))
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
                                                                   "POST https://{{dfsp.host}}/loans/${exchangeProperty.getLoanByIdResponse[0]?.get('id')}/repayment-transactions ', " +
                                                                "'Tracking the request', 'Track the response', 'Input Payload: ${body}')")
            .toD("https://{{dfsp.host}}/loans/${exchangeProperty.getLoanByIdResponse[0]?.get('id')}/repayment-transactions ")
            .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                                                                "'Response from Mambu API, postTransaction: ${body}', " +
                                                                "'Tracking the response', 'Verify the response', null)")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
            .setBody(constant(""))
            .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                                                                "'Final Response: ${body}', " +
                                                                "null, null, 'Response of PUT /transfers/${header.transferId} API')")
            .doFinally().process(exchange -> {
                ((Histogram.Timer) exchange.getProperty(TIMER_NAME)).observeDuration(); // stop Prometheus Histogram metric
            }).end()
        ;

    }
}
