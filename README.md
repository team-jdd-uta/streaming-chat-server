# Redis Cluster Pub/Sub Chat

Redis Cluster 기반 Pub/Sub으로 실시간 스트리밍 채팅을 제공하는 Spring Boot 프로젝트.

## 개요
- WebSocket(STOMP) 기반 실시간 채팅
- Redis Cluster Pub/Sub으로 메시지 브로드캐스트
- 채팅방 생성/조회 API 제공

## 아키텍처
- Client -> WebSocket `/ws/chat`
- Client -> `/pub/chat/message`로 메시지 발행
- 서버는 Redis Pub/Sub에 메시지 publish
- RedisSubscriber가 메시지 수신 후 `/sub/chat/room/{roomId}`로 브로드캐스트

## 기술 스택
- Java 21
- Spring Boot
- Spring WebSocket (STOMP)
- Spring Data Redis (Lettuce)
- Redis 7 (Cluster)
- Docker Compose

## 실행 방식(권장: Docker 단일 chat-server)

### 사전 준비
1. Docker / Docker Compose
2. `streaming-chat-server/.env` 파일 값 확인
- `COMMENT_CONSUMER_IMAGE`
- `CHAT_SERVER_ORACLE_URL`
- `CHAT_SERVER_ORACLE_USERNAME`
- `CHAT_SERVER_ORACLE_PASSWORD`
- `CHAT_SERVER_MONGO_URI`
- `CHAT_SERVER_REDIS_CLUSTER_NODES`
3. `streaming-chat-server/consumer.env` 값 확인
- `READ_BLOCK_MS`, `BATCH_INTERVAL_MS` 등 consumer 동작값은 해당 외부 파일에서 수정

### 1) 전체 인프라 실행
```bash
cd streaming-chat-server
cp .env.example .env
cp consumer.env.example consumer.env
docker compose up -d
```

`redis-cluster-init` 서비스가 자동으로 Redis Cluster를 초기화한다.

### 2) 상태 확인
```bash
docker compose ps
docker compose logs -f chat-server
docker compose logs -f comment-consumer
docker compose logs -f redis-cluster-init
```

### 3) 확인
```bash
curl http://localhost:8080/chat/rooms
```

## 리소스 제한(chat-server)
`streaming-chat-server/docker-compose.yml`에 다음 제한이 적용된다.
- CPU: `2.0`
- Memory limit: `4g`
- Memory reservation: `2g`

## 로그
- chat-server 로그 파일: `streaming-chat-server/server1.log`
- 컨테이너 로그 확인:
```bash
docker logs -f chat-server
```

## 종료
```bash
cd streaming-chat-server
docker compose down -v
```

## 참고: 로컬 Java 실행(레거시)
기존 `./gradlew clean build` + `./start_servers.sh` 방식도 사용할 수 있지만,
현재 테스트 기준은 chat-server Docker 단일 컨테이너 실행이다.
