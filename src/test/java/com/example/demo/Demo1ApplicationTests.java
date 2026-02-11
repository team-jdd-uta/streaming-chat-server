package com.example.demo;

import com.example.demo.model.WsControlMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"chat.ws.lifecycle.ttl=10m",
		"chat.ws.lifecycle.scheduler-interval=1s",
		"chat.ws.lifecycle.reconnect-retry-after-ms=1000",
		"chat.ws.lifecycle.reconnect-jitter-max-ms=500"
})
class Demo1ApplicationTests {

	@LocalServerPort
	private int port;

	@Test
	void reconnectsWithinFewSecondsAfterDisconnect() throws Exception {
		ReconnectingClient client = new ReconnectingClient("ws://localhost:" + port + "/ws/chat");
		try {
			client.connect();
			assertTrue(waitUntil(() -> client.getConnectCount() >= 1, Duration.ofSeconds(5)),
					"Client did not establish initial connection");

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("http://localhost:" + port + "/ops/ws/disconnect-all?reason=integration-test"))
					.POST(HttpRequest.BodyPublishers.noBody())
					.build();
			HttpResponse<String> response = HttpClient.newHttpClient()
					.send(request, HttpResponse.BodyHandlers.ofString());
			assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, "disconnect-all API failed");

			assertTrue(waitUntil(() -> client.getConnectCount() >= 2, Duration.ofSeconds(8)),
					"Client did not reconnect in expected window");
			assertTrue(client.getLastDowntimeMs() > 0 && client.getLastDowntimeMs() <= 5_000,
					"Reconnect downtime was longer than expected: " + client.getLastDowntimeMs() + "ms");
		} finally {
			client.shutdown();
		}
	}

	private boolean waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return true;
			}
			Thread.sleep(100);
		}
		return false;
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean();
	}

	private static class ReconnectingClient {
		private final URI uri;
		private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		private final WebSocketStompClient stompClient;
		private final AtomicReference<StompSession> sessionRef = new AtomicReference<>();
		private final AtomicInteger connectCount = new AtomicInteger(0);
		private final AtomicLong disconnectedAtMillis = new AtomicLong(0);
		private final AtomicLong lastDowntimeMs = new AtomicLong(0);
		private final AtomicInteger retryAttempt = new AtomicInteger(0);
		private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

		private ReconnectingClient(String endpoint) {
			this.uri = URI.create(endpoint);
			this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
			this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
		}

		void connect() {
			connectInternal();
		}

		int getConnectCount() {
			return connectCount.get();
		}

		long getLastDowntimeMs() {
			return lastDowntimeMs.get();
		}

		void shutdown() {
			StompSession session = sessionRef.getAndSet(null);
			if (session != null && session.isConnected()) {
				session.disconnect();
			}
			stompClient.stop();
			scheduler.shutdownNow();
		}

		private void connectInternal() {
			reconnectScheduled.set(false);
			CompletableFuture<StompSession> future = stompClient.connectAsync(uri.toString(), new Handler());
			future.exceptionally(ex -> {
				scheduleReconnect();
				return null;
			});
		}

		private void scheduleReconnect() {
			if (!reconnectScheduled.compareAndSet(false, true)) {
				return;
			}
			int attempt = retryAttempt.incrementAndGet();
			long delayMs = Math.min(1_000L * attempt, 3_000L);
			scheduler.schedule(this::connectInternal, delayMs, TimeUnit.MILLISECONDS);
		}

		private void reconnectWithDelay(int delayMs) {
			if (!reconnectScheduled.compareAndSet(false, true)) {
				return;
			}
			StompSession current = sessionRef.getAndSet(null);
			if (current != null && current.isConnected()) {
				current.disconnect();
			}
			disconnectedAtMillis.compareAndSet(0, Instant.now().toEpochMilli());
			scheduler.schedule(this::connectInternal, delayMs, TimeUnit.MILLISECONDS);
		}

		private class Handler extends StompSessionHandlerAdapter {
			@Override
			public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
				sessionRef.set(session);
				retryAttempt.set(0);
				connectCount.incrementAndGet();

				long disconnectedAt = disconnectedAtMillis.getAndSet(0);
				if (disconnectedAt > 0) {
					lastDowntimeMs.set(Instant.now().toEpochMilli() - disconnectedAt);
				}

				session.subscribe("/sub/system/control", new StompFrameHandler() {
					@Override
					public Type getPayloadType(StompHeaders headers) {
						return WsControlMessage.class;
					}

					@Override
					public void handleFrame(StompHeaders headers, Object payload) {
						WsControlMessage controlMessage = (WsControlMessage) payload;
						if ("RECONNECT".equals(controlMessage.getType())) {
							int delay = controlMessage.getRetryAfterMs() + (int) (Math.random() * Math.max(1, controlMessage.getReconnectJitterMaxMs()));
							reconnectWithDelay(delay);
						}
					}
				});
			}

			@Override
			public void handleTransportError(StompSession session, Throwable exception) {
				disconnectedAtMillis.compareAndSet(0, Instant.now().toEpochMilli());
				scheduleReconnect();
			}
		}
	}

}
