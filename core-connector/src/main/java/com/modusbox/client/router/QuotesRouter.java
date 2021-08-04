package com.modusbox.client.router;

import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.processor.EncodeAuthHeader;
import com.modusbox.client.processor.TrimMFICode;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

public class QuotesRouter extends RouteBuilder {

	private final EncodeAuthHeader encodeAuthHeader = new EncodeAuthHeader();
	private final TrimMFICode trimMFICode = new TrimMFICode();
	private final RouteExceptionHandlingConfigurer exceptionHandlingConfigurer = new RouteExceptionHandlingConfigurer();

	private static final String TIMER_NAME = "histogram_post_quoterequests_timer";

	public static final Counter reqCounter = Counter.build()
			.name("counter_post_quoterequests_requests_total")
			.help("Total requests for POST /quoterequests.")
			.register();

	private static final Histogram reqLatency = Histogram.build()
			.name("histogram_post_quoterequests_request_latency")
			.help("Request latency in seconds for POST /quoterequests.")
			.register();

    public void configure() {

		// Add our global exception handling strategy
		exceptionHandlingConfigurer.configureExceptionHandling(this);

        from("direct:postQuoteRequests").routeId("com.modusbox.postQuoterequests").doTry()
				.process(exchange -> {
					reqCounter.inc(1); // increment Prometheus Counter metric
					exchange.setProperty(TIMER_NAME, reqLatency.startTimer()); // initiate Prometheus Histogram metric
				})
				.to("bean:customJsonMessage?method=logJsonMessage(" +
						"'info', " +
						"${header.X-CorrelationId}, " +
						"'Request received POST /quoterequests', " +
						"'Tracking the request', " +
						"'Call the Mambu API,  Track the response', " +
						"'fspiop-source: ${header.fspiop-source} Input Payload: ${body}')") // default logger
				/*
				 * BEGIN processing
				 */
				.setHeader("mfiName", simple("{{dfsp.name}}"))
				.setHeader("idType", simple("${body.getTo().getIdType()}"))
				.setHeader("idValue", simple("${body.getTo().getIdValue()}"))
				.setHeader("requestAmount", simple("${body.getAmount()}"))
				.process(trimMFICode)
				.setProperty("origPayload", simple("${body}"))

				// Fetch the loan account by ID so we can find customer ID
				.to("direct:getLoanById")

				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/postQuoterequestsResponse.ds"))
				.setBody(simple("${body.content}"))
				.removeHeaders("getLoanByIdResponse")
				.removeHeaders("getLoanScheduleByIdResponse")
				.to("direct:choiceRoute")
				/*
				 * END processing
				 */
				.to("bean:customJsonMessage?method=logJsonMessage(" +
						"'info', " +
						"${header.X-CorrelationId}, " +
						"'Response for POST /quoterequests', " +
						"'Tracking the response', " +
						"null, " +
						"'Output Payload: ${body}')") // default logger
				.doFinally().process(exchange -> {
					((Histogram.Timer) exchange.getProperty(TIMER_NAME)).observeDuration(); // stop Prometheus Histogram metric
				}).end()
		;

		from("direct:choiceRoute")
				.choice()
					.when(simple("${body?.get('extensionList')[1].get('value')} == '0'"))
					.to("direct:getLoanScheduleById")
				.endChoice()
		;

		from("direct:getLoanScheduleById")
				.routeId("getLoanScheduleById")
				.log("Get loan account schedule by ID route called")
				.setBody(simple("{}"))

				.removeHeaders("CamelHttp*")
				.setHeader("detailsLevel", constant("Basic"))
				.setHeader("Content-Type", constant("application/json"))
				.setHeader("Accept", constant("application/vnd.mambu.v2+json"))
				.setHeader(Exchange.HTTP_METHOD, constant("GET"))
				.setProperty("authHeader", simple("${properties:dfsp.username}:${properties:dfsp.password}"))
				.process(encodeAuthHeader)

				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Calling Mambu API, getLoanScheduleById', " +
						"'Tracking the request', 'Track the response', " +
						"'Request sent to, GET {{dfsp.host}}/loans/${exchangeProperty.getLoanByIdResponse[0]?.get('id')}/schedule')")
				.toD("{{dfsp.host}}/loans/${exchangeProperty.getLoanByIdResponse[0]?.get('id')}/schedule")

				.unmarshal().json()
				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Response from Mambu API, getLoanScheduleById: ${body}', " +
						"'Tracking the response', 'Verify the response', null)")

				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/postQuoterequestsResponseFromSchedule.ds"))
				.setBody(simple("${body.content}"))
		;
    }
}
