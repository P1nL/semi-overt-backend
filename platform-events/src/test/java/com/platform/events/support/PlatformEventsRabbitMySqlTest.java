package com.platform.events.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.event.ArticleSubmittedEvent;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "S3_RABBIT_HOST", matches = "127\\.0\\.0\\.1")
class PlatformEventsRabbitMySqlTest {

    private static String mysqlBase;
    private static String mysqlUsername;
    private static String mysqlPassword;
    private static String schema;
    private static JdbcTemplate jdbc;
    private static EventConsumeService inbox;
    private static EventOutboxService outbox;
    private static CachingConnectionFactory rabbitConnectionFactory;
    private static RabbitAdmin rabbitAdmin;
    private static RabbitPublishConfirmSupport confirmedPublisher;
    private static final List<Topology> TOPOLOGIES = new ArrayList<>();

    @BeforeAll
    static void connectIsolatedInfrastructure() throws Exception {
        mysqlBase = System.getenv("S3_MYSQL_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(mysqlBase != null
                && mysqlBase.matches("jdbc:mysql://127\\.0\\.0\\.1:13306/"));
        mysqlUsername = environmentOrDefault("S3_MYSQL_USERNAME", "root");
        mysqlPassword = System.getenv("S3_MYSQL_PASSWORD");
        schema = "s3_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(mysqlBase, mysqlUsername, mysqlPassword);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(
                mysqlBase + schema + "?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
                mysqlUsername,
                mysqlPassword
        );
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE TABLE event_outbox (
                    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
                    aggregate_type VARCHAR(64) NOT NULL,
                    aggregate_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    payload LONGTEXT NOT NULL,
                    status ENUM('PENDING','PUBLISHED','DEAD') NOT NULL DEFAULT 'PENDING',
                    retry_count INT NOT NULL DEFAULT 0,
                    next_retry_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                    published_at DATETIME(6) NULL,
                    last_error VARCHAR(500) NULL,
                    lease_owner VARCHAR(128) NULL,
                    lease_token VARCHAR(64) NULL,
                    lease_until DATETIME(6) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
                ) ENGINE=InnoDB
                """);        jdbc.execute("""
                CREATE TABLE event_consume_log (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    event_id VARCHAR(64) NOT NULL,
                    consumer VARCHAR(128) NOT NULL,
                    status ENUM('PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PROCESSING',
                    consumed_at DATETIME(6) NULL,
                    error_message VARCHAR(500) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    UNIQUE KEY uk_event_consume_log(event_id,consumer)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE test_side_effect (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    event_id VARCHAR(64) NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                ) ENGINE=InnoDB
                """);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(ds);
        inbox = new EventConsumeService(jdbc, transactionManager);
        outbox = new EventOutboxService(jdbc, new ObjectMapper(), transactionManager);

        rabbitConnectionFactory = new CachingConnectionFactory(
                System.getenv("S3_RABBIT_HOST"),
                Integer.parseInt(System.getenv("S3_RABBIT_PORT"))
        );
        rabbitConnectionFactory.setUsername(System.getenv("S3_RABBIT_USERNAME"));
        rabbitConnectionFactory.setPassword(System.getenv("S3_RABBIT_PASSWORD"));
        rabbitConnectionFactory.setVirtualHost(environmentOrDefault("S3_RABBIT_VIRTUAL_HOST", "/"));
        rabbitConnectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        rabbitConnectionFactory.setPublisherReturns(true);
        rabbitAdmin = new RabbitAdmin(rabbitConnectionFactory);
        RabbitTemplate template = new RabbitTemplate(rabbitConnectionFactory);
        template.setMandatory(true);
        confirmedPublisher = new RabbitPublishConfirmSupport(template, 2_000);
    }

    @AfterEach
    void resetDatabase() {
        jdbc.update("DELETE FROM test_side_effect");
        jdbc.update("DELETE FROM event_outbox");
        jdbc.update("DELETE FROM event_consume_log");
    }

    @AfterAll
    static void cleanupInfrastructure() throws Exception {
        if (rabbitAdmin != null) {
            for (Topology topology : TOPOLOGIES) {
                rabbitAdmin.deleteQueue(topology.mainQueue());
                rabbitAdmin.deleteQueue(topology.retryQueue());
                rabbitAdmin.deleteQueue(topology.deadQueue());
                rabbitAdmin.deleteExchange(topology.mainExchange());
                rabbitAdmin.deleteExchange(topology.retryExchange());
                rabbitAdmin.deleteExchange(topology.deadExchange());
            }
        }
        if (rabbitConnectionFactory != null) {
            rabbitConnectionFactory.destroy();
        }
        if (schema != null) {
            try (var connection = DriverManager.getConnection(mysqlBase, mysqlUsername, mysqlPassword);
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    @Test
    void realConfirmAndRouteSuccessMarksOutboxPublished() throws Exception {
        Topology topology = topology();
        insertOutbox("event-real-success");
        com.platform.events.entity.EventOutbox claim = outbox.claimNextPublishable(
                "article", List.of(EventConstants.ARTICLE_SUBMITTED), "real-publisher", Duration.ofSeconds(10));
        assertNotNull(claim);
        OutboxPublisherSupport publisher = new OutboxPublisherSupport(outbox, confirmedPublisher, "real-publisher", 10_000);
        assertTrue(publisher.publishClaim(claim, topology.mainExchange(), topology.queueRoute()));
        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT status FROM event_outbox WHERE event_id='event-real-success'", String.class));
        assertNotNull(jdbc.queryForObject(
                "SELECT published_at FROM event_outbox WHERE event_id='event-real-success'", java.time.LocalDateTime.class));
        try (com.rabbitmq.client.Connection connection = nativeConnection();
             Channel channel = connection.createChannel()) {
            GetResponse delivery = awaitDelivery(channel, topology.mainQueue());
            assertEquals("event-real-success", new ObjectMapper().readTree(delivery.getBody()).get("eventId").asText());
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        }
    }

    @Test
    void realMandatoryReturnPersistsRetryAndNeverMarksPublished() {
        Topology topology = topology();
        String unboundExchange = "s3.unbound." + randomSuffix();
        rabbitAdmin.declareExchange(new DirectExchange(unboundExchange, false, true));
        try {
            insertOutbox("event-real-return");
            com.platform.events.entity.EventOutbox claim = outbox.claimNextPublishable(
                    "article", List.of(EventConstants.ARTICLE_SUBMITTED), "return-publisher", Duration.ofSeconds(10));
            assertNotNull(claim);
            OutboxPublisherSupport publisher = new OutboxPublisherSupport(outbox, confirmedPublisher, "return-publisher", 10_000);
            assertTrue(publisher.publishClaim(claim, unboundExchange, topology.queueRoute()));
            assertEquals("PENDING", jdbc.queryForObject(
                    "SELECT status FROM event_outbox WHERE event_id='event-real-return'", String.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT retry_count FROM event_outbox WHERE event_id='event-real-return'", Integer.class));
            assertNull(jdbc.queryForObject(
                    "SELECT published_at FROM event_outbox WHERE event_id='event-real-return'", java.time.LocalDateTime.class));
            assertTrue(jdbc.queryForObject(
                    "SELECT last_error FROM event_outbox WHERE event_id='event-real-return'", String.class)
                    .contains("mandatory return"));
        } finally {
            rabbitAdmin.deleteExchange(unboundExchange);
        }
    }
    @Test
    void realMandatoryReturnIsNotAConfirmedSuccess() {
        Topology topology = topology();
        String unboundExchange = "s3.unbound." + randomSuffix();
        rabbitAdmin.declareExchange(new DirectExchange(unboundExchange, false, true));
        try {
            assertThrows(RabbitPublishConfirmSupport.RabbitPublishException.class,
                    () -> confirmedPublisher.sendConfirmed(
                            unboundExchange, topology.queueRoute(), jsonMessage("{\"eventId\":\"returned\"}"),
                            "return-" + randomSuffix()));
        } finally {
            rabbitAdmin.deleteExchange(unboundExchange);
        }
    }

    @Test
    void commitBeforeAckRedeliveryIsAbsorbedByInbox() throws Exception {
        Topology topology = topology();
        confirmedPublisher.sendConfirmed(topology.mainExchange(), topology.queueRoute(),
                jsonMessage("{\"eventId\":\"event-commit-ack\"}"), "initial-" + randomSuffix());
        com.rabbitmq.client.Connection firstConnection = nativeConnection();
        Channel firstChannel = firstConnection.createChannel();
        GetResponse first = awaitDelivery(firstChannel, topology.mainQueue());
        AtomicInteger handlerCalls = new AtomicInteger();
        EventListenerExecutor executor = executor(new RabbitRetrySupport(confirmedPublisher, 1));
        Channel ackFailureChannel = failAckChannel(firstChannel);

        assertThrows(IOException.class, () -> executor.execute(
                "consumer-commit-ack", EventConstants.ARTICLE_SUBMITTED,
                receivedMessage(first, topology.mainQueue()), ackFailureChannel,
                ArticleSubmittedEvent.class, event -> {
                    handlerCalls.incrementAndGet();
                    jdbc.update("INSERT INTO test_side_effect(event_id) VALUES(?)", event.getEventId());
                }));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_side_effect", Integer.class));
        assertEquals("SUCCESS", jdbc.queryForObject(
                "SELECT status FROM event_consume_log WHERE event_id='event-commit-ack'", String.class));
        // Physical connection loss leaves the delivery unacked; RabbitMQ must requeue it.
        firstConnection.abort();

        try (com.rabbitmq.client.Connection redeliveryConnection = nativeConnection();
             Channel redeliveryChannel = redeliveryConnection.createChannel()) {
            GetResponse replay = awaitDelivery(redeliveryChannel, topology.mainQueue());
            assertTrue(replay.getEnvelope().isRedeliver());
            executor.execute("consumer-commit-ack", EventConstants.ARTICLE_SUBMITTED,
                    receivedMessage(replay, topology.mainQueue()), redeliveryChannel,
                    ArticleSubmittedEvent.class, ignored -> handlerCalls.incrementAndGet());
            assertEquals(1, handlerCalls.get());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_side_effect", Integer.class));
        }
    }

    @Test
    void retryPublishFailureNacksAndRetainsOriginalMessage() throws Exception {
        Topology topology = topology();
        byte[] body = "{\"eventId\":\"event-retry-failure\"}".getBytes(StandardCharsets.UTF_8);
        confirmedPublisher.sendConfirmed(topology.mainExchange(), topology.queueRoute(),
                jsonMessage(new String(body, StandardCharsets.UTF_8)), "initial-" + randomSuffix());
        var connection = rabbitConnectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        GetResponse delivery = awaitDelivery(channel, topology.mainQueue());

        com.rabbitmq.client.ConnectionFactory brokenNative = new com.rabbitmq.client.ConnectionFactory();
        brokenNative.setHost("127.0.0.1");
        brokenNative.setPort(1);
        brokenNative.setConnectionTimeout(200);
        CachingConnectionFactory broken = new CachingConnectionFactory(brokenNative);
        broken.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        broken.setPublisherReturns(true);
        RabbitTemplate brokenTemplate = new RabbitTemplate(broken);
        brokenTemplate.setMandatory(true);
        RabbitRetrySupport brokenRetry = new RabbitRetrySupport(
                new RabbitPublishConfirmSupport(brokenTemplate, 200), 1);
        try {
            executor(brokenRetry).execute(
                    "consumer-retry-failure", EventConstants.ARTICLE_SUBMITTED,
                    receivedMessage(delivery, topology.mainQueue()), channel,
                    ArticleSubmittedEvent.class, ignored -> { throw new IllegalStateException("business failure"); });
            GetResponse retained = awaitDelivery(channel, topology.mainQueue());
            assertArrayEquals(body, retained.getBody());
            assertTrue(retained.getEnvelope().isRedeliver());
            channel.basicAck(retained.getEnvelope().getDeliveryTag(), false);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM event_consume_log", Integer.class));
        } finally {
            broken.destroy();
            channel.close();
            connection.close();
        }
    }

    @Test
    void failedMessageReachesDlqAndCanBeReplayedExactlyOnce() throws Exception {
        Topology topology = topology();
        RabbitRetrySupport retry = new RabbitRetrySupport(confirmedPublisher, 1);
        EventListenerExecutor executor = executor(retry);
        confirmedPublisher.sendConfirmed(topology.mainExchange(), topology.queueRoute(),
                jsonMessage("{\"eventId\":\"event-dlq-replay\"}"), "initial-" + randomSuffix());
        var connection = rabbitConnectionFactory.createConnection();
        Channel channel = connection.createChannel(false);

        GetResponse first = awaitDelivery(channel, topology.mainQueue());
        executor.execute("consumer-dlq", EventConstants.ARTICLE_SUBMITTED,
                receivedMessage(first, topology.mainQueue()), channel,
                ArticleSubmittedEvent.class, ignored -> { throw new IllegalStateException("attempt one"); });
        GetResponse retryDelivery = awaitDelivery(channel, topology.retryQueue());
        republishDelivery(retryDelivery, topology.mainExchange(), topology.queueRoute(), "retry-to-main");
        channel.basicAck(retryDelivery.getEnvelope().getDeliveryTag(), false);

        GetResponse second = awaitDelivery(channel, topology.mainQueue());
        executor.execute("consumer-dlq", EventConstants.ARTICLE_SUBMITTED,
                receivedMessage(second, topology.mainQueue()), channel,
                ArticleSubmittedEvent.class, ignored -> { throw new IllegalStateException("attempt two"); });
        GetResponse dead = awaitDelivery(channel, topology.deadQueue());
        assertEquals(2, ((Number) dead.getProps().getHeaders().get("x-event-retry-count")).intValue());
        assertEquals(topology.mainQueue(), dead.getProps().getHeaders().get("x-original-consumer-queue").toString());

        republishDelivery(dead, topology.mainExchange(), topology.queueRoute(), "dlq-replay");
        channel.basicAck(dead.getEnvelope().getDeliveryTag(), false);
        AtomicInteger effects = new AtomicInteger();
        GetResponse replay = awaitDelivery(channel, topology.mainQueue());
        executor.execute("consumer-dlq", EventConstants.ARTICLE_SUBMITTED,
                receivedMessage(replay, topology.mainQueue()), channel,
                ArticleSubmittedEvent.class, event -> {
                    effects.incrementAndGet();
                    jdbc.update("INSERT INTO test_side_effect(event_id) VALUES(?)", event.getEventId());
                });
        assertEquals(1, effects.get());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_side_effect", Integer.class));
        assertEquals("SUCCESS", jdbc.queryForObject(
                "SELECT status FROM event_consume_log WHERE event_id='event-dlq-replay'", String.class));
        assertNull(channel.basicGet(topology.mainQueue(), true));
        channel.close();
        connection.close();
    }

    @Test
    void invalidBodyHasBoundedRetryAndPreservesQueueRouteInDlq() throws Exception {
        Topology topology = topology();
        RabbitRetrySupport retry = new RabbitRetrySupport(confirmedPublisher, 0);
        EventListenerExecutor executor = executor(retry);
        byte[] invalid = "not-json".getBytes(StandardCharsets.UTF_8);
        confirmedPublisher.sendConfirmed(topology.mainExchange(), topology.queueRoute(),
                MessageBuilder.withBody(invalid).setDeliveryMode(MessageDeliveryMode.PERSISTENT).build(),
                "invalid-" + randomSuffix());
        var connection = rabbitConnectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        GetResponse delivery = awaitDelivery(channel, topology.mainQueue());
        executor.execute("consumer-invalid", EventConstants.ARTICLE_SUBMITTED,
                receivedMessage(delivery, topology.mainQueue()), channel,
                ArticleSubmittedEvent.class, ignored -> { throw new AssertionError("invalid JSON must not reach handler"); });
        GetResponse dead = awaitDelivery(channel, topology.deadQueue());
        assertArrayEquals(invalid, dead.getBody());
        assertEquals(1, ((Number) dead.getProps().getHeaders().get("x-event-retry-count")).intValue());
        assertEquals(topology.mainQueue(), dead.getProps().getHeaders().get("x-original-consumer-queue").toString());
        channel.basicAck(dead.getEnvelope().getDeliveryTag(), false);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM event_consume_log", Integer.class));
        channel.close();
        connection.close();
    }

    private static void insertOutbox(String eventId) {
        jdbc.update("""
                INSERT INTO event_outbox(
                    event_id,aggregate_type,aggregate_id,event_type,payload,status,retry_count,
                    next_retry_at,created_at,updated_at
                ) VALUES(?,?,?,?,?,'PENDING',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
                """, eventId, "article", "42", EventConstants.ARTICLE_SUBMITTED,
                "{\"eventId\":\"" + eventId + "\",\"articleId\":42}");
    }
    private static EventListenerExecutor executor(RabbitRetrySupport retry) {
        return new EventListenerExecutor(new ObjectMapper(), inbox, retry);
    }

    private static Topology topology() {
        String suffix = randomSuffix();
        String mainQueue = "s3.main.queue." + suffix;
        Topology topology = new Topology(
                "s3.main.exchange." + suffix,
                EventConstants.retryExchangeOf(mainQueue),
                EventConstants.deadLetterExchangeOf(mainQueue),
                mainQueue,
                "s3.retry.queue." + suffix,
                "s3.dead.queue." + suffix,
                "s3.route." + suffix
        );
        rabbitAdmin.declareExchange(new DirectExchange(topology.mainExchange(), false, true));
        rabbitAdmin.declareExchange(new DirectExchange(topology.retryExchange(), false, true));
        rabbitAdmin.declareExchange(new DirectExchange(topology.deadExchange(), false, true));
        Queue main = new Queue(topology.mainQueue(), false, false, true);
        Queue retry = new Queue(topology.retryQueue(), false, false, true);
        Queue dead = new Queue(topology.deadQueue(), false, false, true);
        rabbitAdmin.declareQueue(main);
        rabbitAdmin.declareQueue(retry);
        rabbitAdmin.declareQueue(dead);
        rabbitAdmin.declareBinding(BindingBuilder.bind(main).to(new DirectExchange(topology.mainExchange())).with(topology.queueRoute()));
        rabbitAdmin.declareBinding(BindingBuilder.bind(retry).to(new DirectExchange(topology.retryExchange())).with(topology.mainQueue()));
        rabbitAdmin.declareBinding(BindingBuilder.bind(dead).to(new DirectExchange(topology.deadExchange())).with(topology.mainQueue()));
        TOPOLOGIES.add(topology);
        return topology;
    }

    private static GetResponse awaitDelivery(Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        GetResponse response;
        while ((response = channel.basicGet(queue, false)) == null) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for queue " + queue);
            }
            Thread.sleep(25);
        }
        return response;
    }

    private static Message receivedMessage(GetResponse response, String consumerQueue) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(response.getEnvelope().getDeliveryTag());
        properties.setConsumerQueue(consumerQueue);
        properties.setReceivedExchange(response.getEnvelope().getExchange());
        properties.setReceivedRoutingKey(response.getEnvelope().getRoutingKey());
        properties.setRedelivered(response.getEnvelope().isRedeliver());
        Map<String, Object> headers = response.getProps().getHeaders();
        if (headers != null) {
            properties.getHeaders().putAll(headers);
        }
        properties.setContentType(response.getProps().getContentType());
        return new Message(response.getBody(), properties);
    }

    private static void republishDelivery(GetResponse response,
                                          String exchange,
                                          String routingKey,
                                          String correlationPrefix) {
        MessageBuilder builder = MessageBuilder.withBody(response.getBody());
        builder.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (response.getProps().getHeaders() != null) {
            response.getProps().getHeaders().forEach(builder::setHeader);
        }
        confirmedPublisher.sendConfirmed(exchange, routingKey, builder.build(),
                correlationPrefix + "-" + randomSuffix());
    }

    private static com.rabbitmq.client.Connection nativeConnection() throws Exception {
        com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(System.getenv("S3_RABBIT_HOST"));
        factory.setPort(Integer.parseInt(System.getenv("S3_RABBIT_PORT")));
        factory.setUsername(System.getenv("S3_RABBIT_USERNAME"));
        factory.setPassword(System.getenv("S3_RABBIT_PASSWORD"));
        factory.setVirtualHost(environmentOrDefault("S3_RABBIT_VIRTUAL_HOST", "/"));
        factory.setAutomaticRecoveryEnabled(false);
        return factory.newConnection("s3-test-" + randomSuffix());
    }
    private static Channel failAckChannel(Channel delegate) {
        return (Channel) Proxy.newProxyInstance(
                Channel.class.getClassLoader(),
                new Class<?>[]{Channel.class},
                (proxy, method, args) -> {
                    if ("basicAck".equals(method.getName())) {
                        throw new IOException("simulated connection loss after DB commit");
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
    }

    private static Message jsonMessage(String json) {
        return MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record Topology(String mainExchange,
                            String retryExchange,
                            String deadExchange,
                            String mainQueue,
                            String retryQueue,
                            String deadQueue,
                            String queueRoute) {
    }
}