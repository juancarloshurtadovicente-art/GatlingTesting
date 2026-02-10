package org.example;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class RcsStressSimulation extends Simulation {

    private static final Logger LOG = LoggerFactory.getLogger(RcsStressSimulation.class);

    private static String authHeader() {
        return "Bearer " + TokenProvider.getValidToken();
    }

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(System.getProperty("baseUrl", "https://botplatform.stg.telefonica.es/bot"))
            .contentTypeHeader("application/json")
            .acceptHeader("application/json");

    private final FeederBuilder.Batchable<String> feeder = csv("rcs_targets.csv").circular();

    private final ChainBuilder sendMessage =
            feed(feeder)
                    .exec(
                            http("RCS Send")
                                    .post("/v1/TEF-TEC-BOT-PRO/multichannel_messages")
                                    .header("Authorization", session -> authHeader())
                                    .body(StringBody(session -> {
                                        String unique = UUID.randomUUID().toString();
                                        String msisdn = session.getString("msisdn");
                                        return """
                        {
                          "RCSMessage": {
                            "textMessage": "hello world-%s"
                          },
                          "messageContact": {
                            "userContact": "%s"
                          }
                        }
                        """.formatted(unique, msisdn);
                                    }))
                                    .check(status().saveAs("httpStatus"))
                                    .check(jsonPath("$.RCSMessage.msgId").optional().saveAs("msgId"))
                                    .check(status().in(200, 201, 202))
                                    .check(jsonPath("$.RCSMessage.status").in("sent", "queued", "accepted", "pending"))
                    )
                    .exec(session -> {
                        long ts = System.currentTimeMillis();
                        int st = session.contains("httpStatus") ? session.getInt("httpStatus") : -1;
                        boolean is202 = (st == 202);
                        String msgId = session.contains("msgId") ? session.getString("msgId") : "";

                        // Antes: CSV timestamp,msgId,is202
                        // Ahora: simulation.log
                        LOG.info("rcs_request timestamp={}, msgId={}, is202={}", ts, msgId, is202);

                        return session;
                    });

    private final ScenarioBuilder scn = scenario("RCS Stress")
            .exec(sendMessage);

    {
        setUp(
                scn.injectOpen(
                        rampUsersPerSec(1).to(20).during(Duration.ofMinutes(1)),
                        constantUsersPerSec(20).during(Duration.ofMinutes(1))
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile(95.0).lt(800)
                );
    }
}
