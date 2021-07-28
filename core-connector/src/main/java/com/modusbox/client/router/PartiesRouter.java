package com.modusbox.client.router;

import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.processor.EncodeAuthHeader;
import com.modusbox.client.processor.TrimMFICode;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;


public class PartiesRouter extends RouteBuilder {

	private final EncodeAuthHeader encodeAuthHeader = new EncodeAuthHeader();
	private final TrimMFICode trimMFICode = new TrimMFICode();
	private final RouteExceptionHandlingConfigurer exceptionHandlingConfigurer = new RouteExceptionHandlingConfigurer();

	private static final String TIMER_NAME = "histogram_get_parties_timer";

	public static final Counter reqCounter = Counter.build()
			.name("counter_get_parties_requests_total")
			.help("Total requests for GET /parties.")
			.register();

	private static final Histogram reqLatency = Histogram.build()
			.name("histogram_get_parties_request_latency")
			.help("Request latency in seconds for GET /parties.")
			.register();

	public void configure() {

		// Add our global exception handling strategy
		exceptionHandlingConfigurer.configureExceptionHandling(this);

		// In this case the GET parties will return the loan account with client details
		from("direct:getPartiesByIdTypeIdValue").routeId("com.modusbox.getPartiesByIdTypeIdValue").doTry()
				.process(exchange -> {
					reqCounter.inc(1); // increment Prometheus Counter metric
					exchange.setProperty(TIMER_NAME, reqLatency.startTimer()); // initiate Prometheus Histogram metric
				})
				.to("bean:customJsonMessage?method=logJsonMessage(" +
						"'info', " +
						"${header.X-CorrelationId}, " +
						"'Request received GET /parties/${header.idType}/${header.idValue}', " +
						"'Tracking the request', " +
						"'Call the Mambu API,  Track the response', " +
						"'fspiop-source: ${header.fspiop-source} Input Payload: ${body}')") // default logger
				/*
				 * BEGIN processing
				 */
				// Trim MFI code from id
				.process(trimMFICode)
				// Fetch the client information for the user the loan acc belongs to and get name
				.to("direct:getClientById")
				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/getPartiesResponse.ds"))
				.setBody(simple("${body.content}"))
				.removeHeaders("getClientByIdResponse")
				/*
				 * END processing
				 */
				.to("bean:customJsonMessage?method=logJsonMessage(" +
						"'info', " +
						"${header.X-CorrelationId}, " +
						"'Response for GET /parties/${header.idType}/${header.idValue}', " +
						"'Tracking the response', " +
						"null, " +
						"'Output Payload: ${body}')") // default logger
				.doFinally().process(exchange -> {
					((Histogram.Timer) exchange.getProperty(TIMER_NAME)).observeDuration(); // stop Prometheus Histogram metric
				}).end()
		;

		// API call to Mambu for client information by ID
		from("direct:getClientById")
				.routeId("getClientById")
				.log("Get client by ID route called")
				.setBody(simple("{}"))

				.removeHeaders("CamelHttp*")
				.setHeader("detailsLevel", constant("Full"))
				.setHeader("Content-Type", constant("application/json"))
				.setHeader("Accept", constant("application//vnd.mambu.v2+json"))
				.setHeader(Exchange.HTTP_METHOD, constant("GET"))
				.setProperty("authHeader", simple("${properties:dfsp.username}:${properties:dfsp.password}"))
				.process(encodeAuthHeader)

				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Calling Mambu API, getClientByLoanId', " +
						"'Tracking the request', 'Track the response', " +
						"'Request sent to, GET https://{{dfsp.host}}/clients/${header.idValueTrimmed}')")
				.toD("https://{{dfsp.host}}/clients/${header.idValueTrimmed}")

				.unmarshal().json()
				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Response from Mambu API, getClientByLoanId: ${body}', " +
						"'Tracking the response', 'Verify the response', null)")
				// Save response as property to use later
				.setProperty("getClientByIdResponse", body())
		;

		// API call to Mambu for loan information by ID
		from("direct:getLoanById")
				.routeId("getLoanById")
				.log("Get loan account by ID route called")
				.setBody(simple("{}"))

				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/postLoanSearchRequest.ds"))
				.setBody(simple("${body.content}"))
				.marshal().json()

				.removeHeaders("CamelHttp*")
				.setHeader("detailsLevel", constant("Full"))
				.setHeader("Content-Type", constant("application/json"))
				.setHeader("Accept", constant("application//vnd.mambu.v2+json"))
				.setHeader(Exchange.HTTP_METHOD, constant("POST"))
				.setProperty("authHeader", simple("${properties:dfsp.username}:${properties:dfsp.password}"))
				.process(encodeAuthHeader)

				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Calling Mambu API, getLoanById', " +
						"'Tracking the request', 'Track the response', " +
						"'Request sent to, POST https://{{dfsp.host}}/loans/search')")
				.toD("https://{{dfsp.host}}/loans/search")

				.unmarshal().json()
				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Response from Mambu API, getLoanById: ${body}', " +
						"'Tracking the response', 'Verify the response', null)")
				.choice()
					.when(simple("${body.size} == 0"))
						.setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
						.setBody(simple("{ \"statusCode\": \"3203\"," +
												"\"message\": \"No data found\"} "))
						.stop()
				.end()
				// Save response as property to use later
				.setProperty("getLoanByIdResponse", body())
		;
	}
}