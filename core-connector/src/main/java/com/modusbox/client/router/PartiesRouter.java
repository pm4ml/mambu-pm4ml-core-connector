package com.modusbox.client.router;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.processor.EncodeAuthHeader;
import com.modusbox.client.processor.TrimMFICode;
import com.modusbox.client.validator.AccountNumberFormatValidator;
import com.modusbox.client.validator.GetPartyResponseValidator;
import com.modusbox.client.validator.IdSubValueChecker;
import com.modusbox.client.validator.PhoneNumberValidation;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.apache.camel.http.base.HttpOperationFailedException;

import java.net.SocketException;


public class PartiesRouter extends RouteBuilder {

	private final EncodeAuthHeader encodeAuthHeader = new EncodeAuthHeader();
	private final TrimMFICode trimMFICode = new TrimMFICode();
	private final AccountNumberFormatValidator accountNumberFormatValidator = new AccountNumberFormatValidator();
	private final GetPartyResponseValidator getPartyResponseValidator = new GetPartyResponseValidator();
	private final RouteExceptionHandlingConfigurer exceptionHandlingConfigurer = new RouteExceptionHandlingConfigurer();

	private final IdSubValueChecker idSubValueChecker = new IdSubValueChecker();
	private final PhoneNumberValidation phoneNumberValidation = new PhoneNumberValidation();
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
						"'Input Payload: ${body}')") // default logger
				/*
				 * BEGIN processing
				 */
				.process(idSubValueChecker)

				.doCatch(CCCustomException.class,SocketException.class)
					.to("direct:extractCustomErrors")
				.doFinally().process(exchange -> {
					((Histogram.Timer) exchange.getProperty(TIMER_NAME)).observeDuration(); // stop Prometheus Histogram metric
				}).end()
		;
		// In this case the GET parties will return the loan account with client details
		from("direct:getPartiesByIdTypeIdValueIdSubValue").routeId("com.modusbox.getPartiesByIdTypeIdValueIdSubValue").doTry()
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

				.process(accountNumberFormatValidator)

				.to("direct:getClientById")
				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/getPartiesResponse.ds"))
				.setBody(simple("${body.content}"))
				.removeHeaders("getClientByIdResponse")

				//Start for Extension List
				.setHeader("mfiName", simple("{{dfsp.name}}"))
				.setHeader("idType", simple("${header.idType}"))
				.setHeader("idValue", simple("${header.idValue}"))

				.setProperty("origPayload", simple("${body}"))

				// Fetch the loan account by ID so we can find customer ID
				.to("direct:getExtensionList")

				//.process(getPartyResponseValidator)
				.process(phoneNumberValidation)

				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/getPartyExtensionList.ds"))
				.setBody(simple("${body.content}"))

				.removeHeaders("getLoanByIdResponse")
				.removeHeaders("getLoanScheduleByIdResponse")
				.to("direct:choicePartyRoute")
				//End for Extension List
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
				.doCatch(CCCustomException.class, HttpOperationFailedException.class, SocketException.class)
					.to("direct:extractCustomErrors")
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
						"'Calling Mambu API, getClientById', " +
						"'Tracking the request', 'Track the response', " +
						"'Request sent to, GET {{dfsp.host}}/clients/${header.idValueTrimmed}')")
				.toD("{{dfsp.host}}/clients/${header.idValueTrimmed}")
				.unmarshal().json()
				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Response from Mambu API, getClientById: ${body}', " +
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
				.setHeader("Accept", constant("application/vnd.mambu.v2+json"))
				.setHeader(Exchange.HTTP_METHOD, constant("POST"))
				.setProperty("authHeader", simple("${properties:dfsp.username}:${properties:dfsp.password}"))
				.process(encodeAuthHeader)
				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Calling Mambu API, getLoanById', " +
						"'Tracking the request', 'Track the response', " +
						"'Request sent to, POST {{dfsp.host}}/loans:search')")
				.toD("{{dfsp.host}}/loans:search")
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
				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/getLoanByIdResponse.ds"))
				.setBody(simple("${body.content}"))
				// Save response as property to use later
				.setProperty("getLoanByIdResponse", body())
		;

		from("direct:getExtensionList")
				.routeId("getExtensionList")
				.log("Get loan account by ID route called")
				.setBody(simple("{}"))

				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/postLoanSearchRequest.ds"))
				.setBody(simple("${body.content}"))

				.marshal().json()

				.removeHeaders("CamelHttp*")
				.setHeader("detailsLevel", constant("Full"))
				.setHeader("Content-Type", constant("application/json"))
				.setHeader("Accept", constant("application/vnd.mambu.v2+json"))
				.setHeader(Exchange.HTTP_METHOD, constant("POST"))
				.setProperty("authHeader", simple("${properties:dfsp.username}:${properties:dfsp.password}"))
				.process(encodeAuthHeader)
				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Calling Mambu API, getExtensionList', " +
						"'Tracking the request', 'Track the response', " +
						"'Request sent to, POST {{dfsp.host}}/loans:search')")
				.toD("{{dfsp.host}}/loans:search")
				.unmarshal().json()

				.to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
						"'Response from Mambu API, getExtensionList: ${body}', " +
						"'Tracking the response', 'Verify the response', null)")
				.choice()
					.when(simple("${body.size} == 0"))


						.process(getPartyResponseValidator)

				.end()
				.marshal().json()
				.transform(datasonnet("resource:classpath:mappings/getLoanByIdResponse.ds"))
				.setBody(simple("${body.content}"))
				// Save response as property to use later

				.setProperty("getLoanByIdResponse", body())
		;

		from("direct:extensionListChecker")
				.process(getPartyResponseValidator)
		;

		from("direct:choicePartyRoute")
				.choice()
				.when(simple("${body?.get('extensionList')[1].get('value')} == '0'"))
				.to("direct:getPartyLoanScheduleById")
				.endChoice()
		;

		from("direct:getPartyLoanScheduleById")
				.routeId("getPartyLoanScheduleById")
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
				.transform(datasonnet("resource:classpath:mappings/postPartyrequestsResponseFromSchedule.ds"))
				.setBody(simple("${body.content}"))
		;
	}
}