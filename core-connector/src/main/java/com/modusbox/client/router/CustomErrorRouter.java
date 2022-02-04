package com.modusbox.client.router;

import com.modusbox.client.processor.CustomErrorProcessor;
import com.modusbox.client.processor.CheckMojaloopError;
import org.apache.camel.builder.RouteBuilder;

public final class CustomErrorRouter extends RouteBuilder {
    private CustomErrorProcessor customErrorProcessor = new CustomErrorProcessor();
    private CheckMojaloopError checkMojaloopError = new CheckMojaloopError();

    public void configure() {

        from("direct:extractCustomErrors")
            .process(customErrorProcessor)
        ;
        
        from("direct:catchMojaloopError")
//                .process(exchange -> System.out.println())
                .process(checkMojaloopError)
        ; 
    }
}
