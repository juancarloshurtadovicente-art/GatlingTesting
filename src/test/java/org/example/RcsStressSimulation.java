package org.example;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class RcsStressSimulation extends Simulation {

    private static String authHeader() {
        return "Bearer " + TokenProvider.getValidToken();
    }

    // ---- Buffered CSV logger (batch flush) ----
    private static final class BufferedCsvLogger {
        private static final Object LOCK = new Object();
        private static BufferedWriter writer;

        private static int flushEvery = 2_000;
        private static List<String> buffer; // <-- se crea en init()

        static void init(String path, int flushEveryLines) {
            synchronized (LOCK) {
                if (writer != null) return;
                flushEvery = Math.max(1, flushEveryLines);
                buffer = new ArrayList<>(flushEvery + 64);

                try {
                    Path p = Paths.get(path);
                    Path parent = p.getParent();
                    if (parent != null) Files.createDirectories(parent);

                    boolean exists = Files.exists(p);
                    writer = Files.newBufferedWriter(
                            p,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    );

                    if (!exists) {
                        writer.write("timestamp,msgId,is202");
                        writer.newLine();
                        writer.flush();
                    }

                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            flushAndClose();
                        } catch (Exception ignored) {
                        }
                    }));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        static void log(long timestampMs, String msgId, boolean is202) {
            String line = timestampMs + "," + csv(msgId) + "," + is202;

            synchronized (LOCK) {
                if (buffer == null) {
                    // por si alguien llama log antes de init (no debería)
                    buffer = new ArrayList<>(flushEvery + 64);
                }
                buffer.add(line);
                if (buffer.size() >= flushEvery) {
                    flushNoClose();
                }
            }
        }

        static void flush() {
            synchronized (LOCK) {
                flushNoClose();
                try {
                    if (writer != null) writer.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        static void flushAndClose() { // <-- lo usamos también desde after()
            synchronized (LOCK) {
                flushNoClose();
                try {
                    if (writer != null) {
                        writer.flush();
                        writer.close();
                        writer = null;
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        private static void flushNoClose() {
            if (writer == null || buffer == null || buffer.isEmpty()) return;
            try {
                for (String line : buffer) {
                    writer.write(line);
                    writer.newLine();
                }
                buffer.clear();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static String csv(String s) {
            if (s == null) return "";
            if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                return "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }
    }
    // ------------------------------------------

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
                        BufferedCsvLogger.log(ts, msgId, is202);
                        return session;
                    });

    private final ScenarioBuilder scn = scenario("RCS Stress")
            .exec(sendMessage);

    {
        String out = System.getProperty("reqCsv", "target/gatling/requests.csv");
        int flushEvery = Integer.parseInt(System.getProperty("reqCsvFlushEvery", "2000"));
        BufferedCsvLogger.init(out, flushEvery);

        setUp(
                scn.injectOpen(
                        rampUsersPerSec(1).to(20).during(Duration.ofMinutes(1)),
                        constantUsersPerSec(20).during(Duration.ofHours(1))
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile(95.0).lt(800)
                );


    }

    @Override
    public void after() {
        BufferedCsvLogger.flushAndClose();
    }

}
