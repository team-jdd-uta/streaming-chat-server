# WebSocket Draining / Reconnect Test Guide

## 1. 목적
- 드레이닝 모드에서 신규 연결이 차단되는지 확인
- 기존 연결이 `RECONNECT` 신호를 받아 재연결되는지 확인
- 연결이 강제로 끊겼을 때 수 초 내 재연결되는지 확인

## 2. 사전 준비
1. Redis Cluster 실행
```bash
docker compose up -d
```
2. Redis Cluster 초기화(최초 1회)
```bash
docker exec -it redis-node-1 redis-cli --cluster create \
  redis-node-1:7000 redis-node-2:7001 redis-node-3:7002 \
  redis-node-4:7003 redis-node-5:7004 redis-node-6:7005 \
  --cluster-replicas 1
```

## 3. 자동 테스트 실행
```bash
./gradlew test --tests com.example.demo.Demo1ApplicationTests.reconnectsWithinFewSecondsAfterDisconnect
```

기대 결과:
- 테스트가 성공해야 함
- 테스트 내부에서 `/ops/ws/disconnect-all` 호출 후 재연결까지의 downtime이 5초 이하로 검증됨

## 4. 수동 테스트(두 서버)
1. 애플리케이션 빌드 후 2개 인스턴스 실행
```bash
./gradlew clean bootJar
./start_servers.sh
```
2. 상태 확인
```bash
curl http://localhost:8080/ops/ws/status
curl http://localhost:8081/ops/ws/status
```
- 둘 중 하나라도 응답이 없으면 서버가 떠 있지 않은 상태이므로 `./start_servers.sh`를 다시 실행
- 이전 테스트에서 드레인이 켜져 있으면 접속이 막힐 수 있으므로 아래로 초기화:
```bash
curl -X POST "http://localhost:8080/ops/ws/drain?enabled=false"
curl -X POST "http://localhost:8081/ops/ws/drain?enabled=false"
```
3. 8080 드레인 활성화
```bash
curl -X POST "http://localhost:8080/ops/ws/drain?enabled=true"
```
4. 클라이언트 실행(`../stream-chat-client`)
```bash
cd ../stream-chat-client
python3 -m http.server 5500
```
- 브라우저에서 `http://localhost:5500/index.html`, `http://localhost:5500/index1.html` 둘 다 열기

5. `index.html` / `index1.html`에 시스템 채널 구독 추가
- STOMP `connect` 성공 콜백 안에 아래 코드 추가:
```javascript
stompClient.subscribe('/sub/system/control', (frame) => {
  const control = JSON.parse(frame.body);
  if (control.type !== 'RECONNECT') return;

  const base = control.retryAfterMs ?? 2000;
  const jitterMax = control.reconnectJitterMaxMs ?? 10000;
  const delay = base + Math.floor(Math.random() * jitterMax);

  console.log('[CONTROL] RECONNECT received:', control);
  console.log('[CONTROL] reconnect after(ms):', delay);

  setTimeout(() => {
    if (stompClient.connected) {
      stompClient.disconnect(() => connect()); // 기존 connect() 재호출
    } else {
      connect();
    }
  }, delay);
});
```
- 서버가 보내는 payload 예시:
```json
{
  "type": "RECONNECT",
  "reason": "Server draining",
  "retryAfterMs": 2000,
  "reconnectJitterMaxMs": 10000,
  "serverId": "8080"
}
```

6. 드레인 트리거 후 브라우저 콘솔 확인
```bash
curl -X POST "http://localhost:8080/ops/ws/drain?enabled=true"
```
- 기대 결과:
  - 콘솔에 `RECONNECT received` 로그 출력
  - 몇 초 후 자동 재연결
  - 채팅 송수신이 다시 정상 동작

## 7. `Error connecting to chat`가 뜰 때 빠른 체크
1. 서버 포트 응답 확인
```bash
curl -i http://localhost:8080/ops/ws/status
curl -i http://localhost:8081/ops/ws/status
```
2. 503이면 드레인 해제
```bash
curl -X POST "http://localhost:8080/ops/ws/drain?enabled=false"
curl -X POST "http://localhost:8081/ops/ws/drain?enabled=false"
```
3. 클라이언트 콘솔에서 SockJS 요청 확인
- `http://localhost:8080/ws/chat/info` 또는 `http://localhost:8081/ws/chat/info`가 `200`이어야 정상

## 5. 운영/검증용 API
- `GET /ops/ws/status`
  - `draining`, `drainStartedAt`, `activeSessions` 반환
- `POST /ops/ws/drain?enabled=true|false`
  - 드레인 on/off
- `POST /ops/ws/disconnect-all?reason=...`
  - 테스트용 전체 세션 종료

## 6. 관련 설정(application.properties)
- `chat.ws.lifecycle.ttl=15m`
- `chat.ws.lifecycle.ttl-notice-before=30s`
- `chat.ws.lifecycle.drain-force-close-after=2m`
- `chat.ws.lifecycle.scheduler-interval=5s`
- `chat.ws.lifecycle.reconnect-retry-after-ms=2000`
- `chat.ws.lifecycle.reconnect-jitter-max-ms=10000`
